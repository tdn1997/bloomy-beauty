package com.example.bloomybeauty.data.order

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.cart.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class OrderRepository(private val helper: AuthDatabaseHelper) {
    private val cart = CartRepository(helper)

    suspend fun prepare(userId: Long): CheckoutRequest = withContext(Dispatchers.IO) {
        transaction { db ->
            checkSession(db, userId)
            val previous = pending(db, userId)
            // Recover a committed response before considering a different cart.
            if (previous != null && findByRequest(db, userId, previous.id) != null) return@transaction previous
            val snapshot = cart.read(db, userId)
            cart.checkCart(snapshot)
            ShippingValidation.validate(snapshot.shipping)?.let { throw ShippingException(it) }
            val request = CheckoutRequest(UUID.randomUUID().toString(), snapshot.items.map {
                CheckoutLine(it.product.id, it.quantity, it.product.priceVnd)
            }, snapshot.shipping)
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
        transaction { db ->
            checkSession(db, userId)
            // Replay before checking the current cart: it may already have been cleared.
            db.query("orders", arrayOf("user_id", "fingerprint"), "request_id = ?", arrayOf(request.id), null, null, null).use {
                if (it.moveToFirst()) {
                    if (it.long("user_id") != userId || it.text("fingerprint") != request.fingerprint) throw OrderException(OrderError.REQUEST_CONFLICT)
                    return@transaction checkNotNull(findByRequest(db, userId, request.id))
                }
            }
            val saved = pending(db, userId)
            if (saved?.id != request.id || saved.fingerprint != request.fingerprint) throw OrderException(OrderError.REQUEST_CONFLICT)
            if (request.lines.isEmpty() || request.lines.map { it.productId }.distinct().size != request.lines.size ||
                request.lines.any { it.quantity !in 1..99 || it.unitPriceVnd < 0 }) throw OrderException(OrderError.INVALID_REQUEST)
            ShippingValidation.validate(request.shipping)?.let { throw ShippingException(it) }
            val snapshot = cart.read(db, userId)
            cart.checkCart(snapshot)
            val current = snapshot.items.associate { it.product.id to (it.quantity to it.product.priceVnd) }
            val expected = request.lines.associate { it.productId to (it.quantity to it.unitPriceVnd) }
            if (current != expected || snapshot.shipping != request.shipping) throw OrderException(OrderError.CART_CHANGED)
            val subtotal = snapshot.totalVnd
            val fee = DeliveryPricing.fee(subtotal)
            val total = Math.addExact(subtotal, fee)
            val orderId = db.insertOrThrow("orders", null, ContentValues().apply {
                put("user_id", userId); put("request_id", request.id); put("fingerprint", request.fingerprint)
                put("created_at", System.currentTimeMillis()); put("recipient", request.shipping.recipient)
                put("phone", request.shipping.phone); put("address", request.shipping.address)
                put("subtotal_vnd", subtotal); put("fee_vnd", fee); put("total_vnd", total)
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
            checkNotNull(findByRequest(db, userId, request.id))
        }
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
        transaction { db ->
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
            current()
        }
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
            cursor.long("subtotal_vnd"), cursor.long("fee_vnd"), cursor.long("total_vnd"), lines)
    }

    private fun pending(db: SQLiteDatabase, userId: Long): CheckoutRequest? =
        db.query("checkout_requests", arrayOf("request_id", "payload"), "user_id = ?", arrayOf(userId.toString()), null, null, null).use {
            if (it.moveToFirst()) decode(it.text("request_id"), it.text("payload")) else null
        }

    private fun checkSession(db: SQLiteDatabase, userId: Long) {
        db.rawQuery("SELECT 1 FROM auth_session WHERE user_id = ?", arrayOf(userId.toString())).use {
            if (!it.moveToFirst()) throw OrderException(OrderError.SESSION)
        }
    }

    private fun encode(request: CheckoutRequest) = JSONObject().apply {
        put("recipient", request.shipping.recipient); put("phone", request.shipping.phone); put("address", request.shipping.address)
        put("lines", JSONArray().apply { request.lines.forEach { line -> put(JSONObject().apply {
            put("id", line.productId); put("quantity", line.quantity); put("price", line.unitPriceVnd)
        }) } })
    }.toString()

    private fun decode(id: String, payload: String): CheckoutRequest {
        val data = JSONObject(payload)
        val lines = data.getJSONArray("lines")
        return CheckoutRequest(id, (0 until lines.length()).map { index -> lines.getJSONObject(index).let {
            CheckoutLine(it.getString("id"), it.getInt("quantity"), it.getLong("price"))
        } }, ShippingDetails(data.getString("recipient"), data.getString("phone"), data.getString("address")))
    }

    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { return block(db).also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
    private fun Cursor.text(column: String) = getString(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String) = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String) = getInt(getColumnIndexOrThrow(column))
}
