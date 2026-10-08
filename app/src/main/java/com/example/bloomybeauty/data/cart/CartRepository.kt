package com.example.bloomybeauty.data.cart

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.catalog.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CartRepository(private val helper: AuthDatabaseHelper) {
    suspend fun load(userId: Long): CartSnapshot = withContext(Dispatchers.IO) { transaction(userId) { read(it, userId) } }

    suspend fun add(userId: Long, productId: String) = withContext(Dispatchers.IO) {
        transaction(userId) { db ->
            val product = product(db, productId)
            val existing = db.query("cart_items", null, "user_id = ? AND product_id = ?", arrayOf(userId.toString(), productId), null, null, null).use {
                if (it.moveToFirst()) it.int("quantity") to it.long("accepted_price_vnd") else null
            }
            val quantity = (existing?.first ?: 0) + 1
            checkQuantity(product, quantity)
            if (existing != null && existing.second != product.priceVnd) throw CartException(CartError.PRICE_CHANGED)
            if (existing == null) {
                db.insertOrThrow("cart_items", null, ContentValues().apply {
                    put("user_id", userId); put("product_id", productId); put("quantity", quantity)
                    put("accepted_price_vnd", product.priceVnd)
                })
            } else {
                db.update("cart_items", ContentValues().apply { put("quantity", quantity) }, "user_id = ? AND product_id = ?", arrayOf(userId.toString(), productId))
            }
        }
    }

    suspend fun setQuantity(userId: Long, productId: String, quantity: Int) = withContext(Dispatchers.IO) {
        transaction(userId) { db ->
            checkQuantity(product(db, productId), quantity)
            db.update("cart_items", ContentValues().apply { put("quantity", quantity) }, "user_id = ? AND product_id = ?", arrayOf(userId.toString(), productId))
        }
    }

    suspend fun remove(userId: Long, productId: String) = withContext(Dispatchers.IO) {
        transaction(userId) { db -> db.delete("cart_items", "user_id = ? AND product_id = ?", arrayOf(userId.toString(), productId)) }
        Unit
    }

    suspend fun acceptCurrentPrices(userId: Long, expectedPrices: Map<String, Long>) = withContext(Dispatchers.IO) {
        transaction(userId) { db ->
            val snapshot = read(db, userId)
            // A price changed again after rendering: show it before accepting it.
            if (snapshot.items.associate { it.product.id to it.product.priceVnd } != expectedPrices) {
                throw CartException(CartError.PRICE_CHANGED)
            }
            snapshot.items.forEach {
                db.update("cart_items", ContentValues().apply { put("accepted_price_vnd", it.product.priceVnd) },
                    "user_id = ? AND product_id = ?", arrayOf(userId.toString(), it.product.id))
            }
        }
    }

    suspend fun validateForShipping(userId: Long): CartSnapshot = withContext(Dispatchers.IO) {
        transaction(userId) { db -> read(db, userId).also(::checkCart) }
    }

    suspend fun saveShipping(userId: Long, details: ShippingDetails): CartSnapshot = withContext(Dispatchers.IO) {
        ShippingValidation.validate(details)?.let { throw ShippingException(it) }
        val normalized = ShippingValidation.normalize(details)
        transaction(userId) { db ->
            checkCart(read(db, userId))
            val values = ContentValues().apply {
                put("recipient", normalized.recipient); put("phone", normalized.phone); put("address", normalized.address)
            }
            if (db.update("shipping_details", values, "user_id = ?", arrayOf(userId.toString())) == 0) {
                values.put("user_id", userId)
                db.insertOrThrow("shipping_details", null, values)
            }
            read(db, userId)
        }
    }

    internal fun checkCart(snapshot: CartSnapshot) {
        if (snapshot.items.isEmpty()) throw CartException(CartError.EMPTY)
        snapshot.items.forEach {
            checkQuantity(it.product, it.quantity)
            if (it.priceChanged) throw CartException(CartError.PRICE_CHANGED)
        }
        try { snapshot.totalVnd } catch (exception: ArithmeticException) { throw CartException(CartError.TOTAL_OVERFLOW) }
    }

    private fun checkQuantity(product: Product, quantity: Int) {
        if (!product.active) throw CartException(CartError.UNAVAILABLE)
        if (quantity !in 1..MAX_CART_QUANTITY) throw CartException(CartError.QUANTITY)
        if (quantity > product.stock) throw CartException(CartError.STOCK)
    }

    internal fun read(db: SQLiteDatabase, userId: Long): CartSnapshot {
        val items = db.rawQuery("""SELECT p.*, c.quantity, c.accepted_price_vnd FROM cart_items c
            JOIN products p ON p.id = c.product_id WHERE c.user_id = ? ORDER BY p.sort_order""", arrayOf(userId.toString())).use { cursor ->
            buildList { while (cursor.moveToNext()) add(CartItem(cursor.product(), cursor.int("quantity"), cursor.long("accepted_price_vnd"))) }
        }
        val shipping = db.query("shipping_details", null, "user_id = ?", arrayOf(userId.toString()), null, null, null).use {
            if (it.moveToFirst()) ShippingDetails(it.text("recipient"), it.text("phone"), it.text("address")) else ShippingDetails()
        }
        return CartSnapshot(items, shipping)
    }

    private fun product(db: SQLiteDatabase, id: String): Product = db.query("products", null, "id = ?", arrayOf(id), null, null, null).use {
        if (!it.moveToFirst()) throw CartException(CartError.UNAVAILABLE)
        it.product()
    }

    private inline fun <T> transaction(userId: Long, block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            if (!com.example.bloomybeauty.data.auth.SessionAccess.matches(db,userId)) throw CartException(CartError.SESSION)
            return block(db).also { db.setTransactionSuccessful() }
        } finally { db.endTransaction() }
    }

    private fun Cursor.product() = Product(
        id = text("id"), name = text("name"), categoryId = text("category_id"), volume = text("volume"),
        priceVnd = long("price_vnd"), imageKey = text("image_key"), description = text("description"),
        ingredients = text("ingredients"), usage = text("usage"), sourceUrl = text("source_url"),
        brand = text("brand"), stock = int("stock"), active = int("active") == 1,
    )
    private fun Cursor.text(column: String) = getString(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String) = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String) = getLong(getColumnIndexOrThrow(column))
}
