package com.example.bloomybeauty.data.order

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.coupon.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.example.bloomybeauty.events.*

class OrderRepository(private val helper: AuthDatabaseHelper, private val changes: OrderChangePublisher = OrderChangePublisher.None,
    private val coupons: LocalCouponRepository = LocalCouponRepository()) {
    val localCouponsEnabled: Boolean get() = coupons.enabled
    suspend fun couponRules(userId: Long): List<CouponRule> = withContext(Dispatchers.IO) {
        transaction {db -> checkSession(db,userId);coupons.list(db)}
    }
    suspend fun validateQuote(userId: Long, request: CheckoutRequest): Unit = withContext(Dispatchers.IO) {
        transaction { db ->
            checkSession(db,userId)
            if(request.version != 2 || coupons.quote(db,request.coupon?.rule?.code.orEmpty(),request.subtotalVnd) != request.coupon ||
                DeliveryPricing.fee(request.subtotalVnd) != request.feeVnd) throw OrderException(OrderError.QUOTE_CHANGED)
        }
    }
    private val cart = CartRepository(helper)

    suspend fun prepare(userId: Long, couponCode: String? = null): CheckoutRequest = withContext(Dispatchers.IO) {
        transaction { db ->
            checkSession(db, userId)
            val previous = pending(db, userId)
            // Recover a committed response before considering a different cart.
            if (previous != null && findByRequest(db, userId, previous.id) != null) return@transaction previous
            val snapshot = cart.read(db, userId)
            cart.checkCart(snapshot)
            ShippingValidation.validate(snapshot.shipping)?.let { throw ShippingException(it) }
            val quote = coupons.quote(db,couponCode ?: previous?.coupon?.rule?.code.orEmpty(),snapshot.totalVnd)
            val request = CheckoutRequest(UUID.randomUUID().toString(), snapshot.items.map {
                CheckoutLine(it.product.id, it.quantity, it.product.priceVnd)
            }, snapshot.shipping,quote,quotedFeeVnd = DeliveryPricing.fee(snapshot.totalVnd))
            request.totalVnd
            if (previous?.fingerprint == request.fingerprint) return@transaction previous
            db.delete("checkout_requests", "user_id = ?", arrayOf(userId.toString()))
            db.insertOrThrow("checkout_requests", null, ContentValues().apply {
                put("request_id", request.id); put("user_id", userId); put("fingerprint", request.fingerprint)
                put("payload", encode(request))
            })
            request
        }
    }

    suspend fun recover(userId: Long): CheckoutRecovery? = withContext(Dispatchers.IO) {
        transaction { db ->
            checkSession(db, userId)
            pending(db, userId)?.let { CheckoutRecovery(it, findByRequest(db, userId, it.id)) }
        }
    }

    suspend fun create(userId: Long, request: CheckoutRequest): CustomerOrder = withContext(Dispatchers.IO) {
        var created = false
        val result = transaction { db ->
            checkSession(db, userId)
            if (request.version !in 1..2 || request.lines.isEmpty() || request.lines.map {it.productId}.distinct().size != request.lines.size ||
                request.lines.any {it.quantity !in 1..99 || it.unitPriceVnd < 0}) throw OrderException(OrderError.INVALID_REQUEST)
            if (request.discountVnd !in 0..request.subtotalVnd || request.feeVnd < 0) throw OrderException(OrderError.INVALID_REQUEST)
            // Replay before checking the current cart: it may already have been cleared.
            db.query("orders", arrayOf("user_id", "fingerprint"), "request_id = ?", arrayOf(request.id), null, null, null).use {
                if (it.moveToFirst()) {
                    if (it.long("user_id") != userId || it.text("fingerprint") != request.fingerprint) throw OrderException(OrderError.REQUEST_CONFLICT)
                    return@transaction checkNotNull(findByRequest(db, userId, request.id))
                }
            }
            if (request.version != 2) throw OrderException(OrderError.QUOTE_CHANGED)
            val saved = pending(db, userId)
            if (saved?.id != request.id || saved.fingerprint != request.fingerprint) throw OrderException(OrderError.REQUEST_CONFLICT)
            ShippingValidation.validate(request.shipping)?.let { throw ShippingException(it) }
            val snapshot = cart.read(db, userId)
            cart.checkCart(snapshot)
            val current = snapshot.items.associate { it.product.id to (it.quantity to it.product.priceVnd) }
            val expected = request.lines.associate { it.productId to (it.quantity to it.unitPriceVnd) }
            if (current != expected || snapshot.shipping != request.shipping) throw OrderException(OrderError.CART_CHANGED)
            val subtotal = snapshot.totalVnd
            val fee = DeliveryPricing.fee(subtotal)
            val currentCoupon = coupons.quote(db,request.coupon?.rule?.code.orEmpty(),subtotal)
            if (currentCoupon != request.coupon || fee != request.feeVnd) throw OrderException(OrderError.QUOTE_CHANGED)
            val discount = currentCoupon?.discountVnd ?: 0
            val total = Math.addExact(Math.subtractExact(subtotal,discount),fee)
            val orderId = db.insertOrThrow("orders", null, ContentValues().apply {
                put("user_id", userId); put("request_id", request.id); put("fingerprint", request.fingerprint)
                put("created_at", System.currentTimeMillis()); put("recipient", request.shipping.recipient)
                put("phone", request.shipping.phone); put("address", request.shipping.address)
                put("subtotal_vnd", subtotal); put("fee_vnd", fee); put("total_vnd", total); put("discount_vnd",discount)
                currentCoupon?.let {put("coupon_code",it.rule.code);put("coupon_rule_snapshot",encodeRule(it.rule).toString())}
            })
            snapshot.items.forEach { item ->
                val p = item.product
                db.execSQL("UPDATE products SET stock = stock - ?, revision = revision + 1 WHERE id = ? AND active = 1 AND stock >= ?", arrayOf(item.quantity, p.id, item.quantity))
                val changed = db.rawQuery("SELECT changes()", null).use { it.moveToFirst(); it.getInt(0) }
                if (changed != 1) throw CartException(CartError.STOCK)
                db.insertOrThrow("order_items", null, ContentValues().apply {
                    put("order_id", orderId); put("product_id", p.id); put("name", p.name); put("volume", p.volume)
                    put("image_key", p.imageKey); put("quantity", item.quantity); put("unit_price_vnd", p.priceVnd)
                })
            }
            // All cart contents match the reviewed request inside this transaction.
            // A later add executes after commit and stays in the cart; replay never deletes it.
            com.example.bloomybeauty.data.admin.AdminSchema.record(db, orderId, userId, null, "PENDING")
            db.delete("cart_items", "user_id = ?", arrayOf(userId.toString()))
            created = true
            checkNotNull(findByRequest(db, userId, request.id))
        }
        if (created) changes.publishAfterCommit(OrderChange(result.id, userId, OrderChangeKind.CREATED))
        result
    }

    suspend fun acknowledge(userId: Long, requestId: String) = withContext(Dispatchers.IO) {
        transaction {db -> checkSession(db,userId); db.delete("checkout_requests", "user_id = ? AND request_id = ?", arrayOf(userId.toString(), requestId)) }
        Unit
    }

    suspend fun list(userId: Long): List<CustomerOrder> = withContext(Dispatchers.IO) {
        transaction { db ->
            checkSession(db,userId)
            db.query("orders", null, "user_id = ?", arrayOf(userId.toString()), null, null, "created_at DESC, _id DESC").use {
                buildList { while (it.moveToNext()) add(readOrder(db, it)) }
            }
        }
    }

    suspend fun detail(userId: Long, orderId: Long): CustomerOrder? = withContext(Dispatchers.IO) {
        transaction { db ->
            checkSession(db,userId)
            db.query("orders", null, "user_id = ? AND _id = ?", arrayOf(userId.toString(), orderId.toString()), null, null, null).use {
                if (it.moveToFirst()) readOrder(db, it) else null
            }
        }
    }

    suspend fun cancel(userId: Long, orderId: Long): CustomerOrder = withContext(Dispatchers.IO) {
        var cancelled = false
        val result = transaction { db ->
            checkSession(db, userId)
            fun current(): CustomerOrder = db.query("orders", null, "user_id = ? AND _id = ?",
                arrayOf(userId.toString(), orderId.toString()), null, null, null).use {
                if (!it.moveToFirst()) throw OrderException(OrderError.NOT_FOUND)
                readOrder(db, it)
            }
            val order = current()
            if (order.status == "CANCELLED") return@transaction order
            if (order.status != "PENDING") throw OrderException(OrderError.CANNOT_CANCEL)
            val changed = db.update("orders", ContentValues().apply { put("status", "CANCELLED") },
                "_id = ? AND user_id = ? AND status = 'PENDING'", arrayOf(orderId.toString(), userId.toString()))
            if (changed != 1) throw OrderException(OrderError.CANNOT_CANCEL)
            order.lines.forEach { line ->
                val stock = db.query("products", arrayOf("stock"), "id = ?", arrayOf(line.productId), null, null, null).use {
                    if (!it.moveToFirst()) throw OrderException(OrderError.NOT_FOUND)
                    it.int("stock")
                }
                val restored = try { Math.addExact(stock, line.quantity) }
                catch (exception: ArithmeticException) { throw OrderException(OrderError.STOCK_OVERFLOW) }
                if (db.update("products", ContentValues().apply { put("stock", restored) }, "id = ?", arrayOf(line.productId)) != 1) {
                    throw OrderException(OrderError.STORAGE)
                }
            }
            order.lines.forEach { db.execSQL("UPDATE products SET revision = revision + 1 WHERE id = ?", arrayOf(it.productId)) }
            com.example.bloomybeauty.data.admin.AdminSchema.record(db, orderId, userId, "PENDING", "CANCELLED")
            cancelled = true
            current()
        }
        if (cancelled) changes.publishAfterCommit(OrderChange(result.id, userId, OrderChangeKind.CANCELLED))
        result
    }

    private fun findByRequest(db: SQLiteDatabase, userId: Long, requestId: String): CustomerOrder? =
        db.query("orders", null, "user_id = ? AND request_id = ?", arrayOf(userId.toString(), requestId), null, null, null).use {
            if (it.moveToFirst()) readOrder(db, it) else null
        }

    internal fun readOrder(db: SQLiteDatabase, cursor: Cursor): CustomerOrder {
        val id = cursor.long("_id")
        val lines = db.query("order_items", null, "order_id = ?", arrayOf(id.toString()), null, null, "product_id ASC").use {
            buildList { while (it.moveToNext()) add(OrderLine(it.text("product_id"), it.text("name"), it.text("volume"), it.text("image_key"), it.int("quantity"), it.long("unit_price_vnd"))) }
        }
        return CustomerOrder(id, cursor.text("request_id"), cursor.long("created_at"), cursor.text("status"),
            ShippingDetails(cursor.text("recipient"), cursor.text("phone"), cursor.text("address")),
            cursor.long("subtotal_vnd"), cursor.long("fee_vnd"), cursor.long("total_vnd"), lines,
            cursor.long("discount_vnd"),cursor.optionalText("coupon_code"),cursor.optionalText("coupon_rule_snapshot"))
    }

    private fun pending(db: SQLiteDatabase, userId: Long): CheckoutRequest? =
        db.query("checkout_requests", arrayOf("request_id", "payload", "fingerprint"), "user_id = ?", arrayOf(userId.toString()), null, null, null).use {
            if(it.moveToFirst()) decode(it.text("request_id"),it.text("payload")).also { request ->
                if(request.fingerprint != it.text("fingerprint")) throw OrderException(OrderError.INVALID_REQUEST)
            } else null
        }

    private fun checkSession(db: SQLiteDatabase, userId: Long) {
        db.rawQuery("SELECT 1 FROM auth_session WHERE user_id = ?", arrayOf(userId.toString())).use {
            if (!it.moveToFirst()) throw OrderException(OrderError.SESSION)
        }
    }

    private fun encodeRule(rule: CouponRule) = JSONObject().apply {
        put("code",rule.code);put("kind",rule.kind.name);put("value",rule.value);put("minimum",rule.minimumSubtotal)
        put("maximum",rule.maximumDiscount ?: JSONObject.NULL);put("starts",rule.startsAt);put("ends",rule.endsAt)
        put("active",rule.active);put("revision",rule.revision)
    }
    private fun decodeRule(data: JSONObject) = CouponRule(data.getString("code"),CouponKind.valueOf(data.getString("kind")),
        data.getLong("value"),data.getLong("minimum"),if(data.isNull("maximum")) null else data.getLong("maximum"),
        data.getLong("starts"),data.getLong("ends"),data.getBoolean("active"),data.getLong("revision"))
    private fun encode(request: CheckoutRequest) = JSONObject().apply {
        put("version",request.version);put("fee",request.feeVnd);put("subtotal",request.subtotalVnd)
        put("discount",request.discountVnd);put("total",request.totalVnd)
        put("recipient",request.shipping.recipient);put("phone",request.shipping.phone);put("address",request.shipping.address)
        put("coupon",request.coupon?.let {quote -> JSONObject().apply {put("rule",encodeRule(quote.rule));put("discount",quote.discountVnd)}} ?: JSONObject.NULL)
        put("lines",JSONArray().apply {request.lines.forEach {line -> put(JSONObject().apply {
            put("id",line.productId);put("quantity",line.quantity);put("price",line.unitPriceVnd)
        })}})
    }.toString()
    private fun decode(id: String, payload: String): CheckoutRequest {
        try {
            val data = JSONObject(payload)
            val version = data.optInt("version",1)
            if(version !in 1..2) throw OrderException(OrderError.INVALID_REQUEST)
            val lines = data.getJSONArray("lines")
            val coupon = if(version == 1 || data.isNull("coupon")) null else data.getJSONObject("coupon").let {
                CouponQuote(decodeRule(it.getJSONObject("rule")),it.getLong("discount"))
            }
            val request = CheckoutRequest(id,(0 until lines.length()).map {index -> lines.getJSONObject(index).let {
                CheckoutLine(it.getString("id"),it.getInt("quantity"),it.getLong("price"))
            }},ShippingDetails(data.getString("recipient"),data.getString("phone"),data.getString("address")),coupon,version,
                if(version == 1) null else data.getLong("fee"))
            if(version == 2 && (request.subtotalVnd != data.getLong("subtotal") || request.discountVnd != data.getLong("discount") ||
                request.totalVnd != data.getLong("total"))) throw OrderException(OrderError.INVALID_REQUEST)
            return request
        } catch(e: org.json.JSONException) {throw OrderException(OrderError.INVALID_REQUEST)}
        catch(e: IllegalArgumentException) {throw OrderException(OrderError.INVALID_REQUEST)}
        catch(e: ArithmeticException) {throw OrderException(OrderError.INVALID_REQUEST)}
    }

    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { return block(db).also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
    private fun Cursor.optionalText(column: String): String? = getColumnIndexOrThrow(column).let {if(isNull(it)) null else getString(it)}
    private fun Cursor.text(column: String) = getString(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String) = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String) = getInt(getColumnIndexOrThrow(column))
}
