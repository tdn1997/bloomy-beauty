package com.example.bloomybeauty.data.order

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.SessionAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReorderRepository(private val helper: AuthDatabaseHelper) {
    suspend fun preview(userId: Long, orderId: Long): ReorderPreview = withContext(Dispatchers.IO) {
        transaction { db -> buildPreview(db, userId, orderId) }
    }

    suspend fun confirm(userId: Long, preview: ReorderPreview): ReorderPreview = withContext(Dispatchers.IO) {
        synchronized(preview) {
            if (preview.consumed) throw ReorderException(ReorderError.USED)
            val result = transaction { db ->
                if (userId != preview.userId) throw ReorderException(ReorderError.SESSION)
                val current = buildPreview(db, userId, preview.orderId)
                if (current.source != preview.source || current.lines != preview.lines) throw ReorderException(ReorderError.CHANGED)
                if (!current.canConfirm) throw ReorderException(ReorderError.EMPTY)
                current.lines.filter { it.added > 0 }.forEach { line ->
                    val values = ContentValues().apply {
                        put("quantity", line.existing + line.added)
                        put("accepted_price_vnd", line.currentPriceVnd)
                    }
                    val args = arrayOf(userId.toString(), line.productId)
                    if (db.update("cart_items", values, "user_id = ? AND product_id = ?", args) == 0) {
                        values.put("user_id", userId); values.put("product_id", line.productId)
                        db.insertOrThrow("cart_items", null, values)
                    }
                }
                // Only uncommitted drafts reach this point. Invalidate their old quote/coupon atomically.
                db.delete("checkout_requests", "user_id = ?", arrayOf(userId.toString()))
                current
            }
            preview.consumed = true
            result
        }
    }

    private fun buildPreview(db: SQLiteDatabase, userId: Long, orderId: Long): ReorderPreview {
        if (!SessionAccess.matches(db, userId)) throw ReorderException(ReorderError.SESSION)
        val order = db.query("orders", null, "_id = ? AND user_id = ?", arrayOf(orderId.toString(), userId.toString()), null, null, null).use {
            if (!it.moveToFirst()) throw ReorderException(ReorderError.NOT_FOUND)
            OrderRepository(helper).readOrder(db, it)
        }
        if (order.status !in listOf("DELIVERED", "CANCELLED")) throw ReorderException(ReorderError.STATUS)
        val checkout = db.rawQuery("SELECT request_id, fingerprint, payload FROM checkout_requests WHERE user_id = ?", arrayOf(userId.toString())).use {
            if (it.moveToFirst()) listOf(it.getString(0), it.getString(1), it.getString(2)) else emptyList()
        }
        if (checkout.isNotEmpty()) {
            val committed = db.rawQuery("SELECT 1 FROM orders WHERE user_id = ? AND request_id = ?", arrayOf(userId.toString(), checkout[0])).use { it.moveToFirst() }
            if (committed) throw ReorderException(ReorderError.CHECKOUT_PENDING)
        }
        val cart = db.rawQuery("SELECT product_id, quantity, accepted_price_vnd FROM cart_items WHERE user_id = ? ORDER BY product_id", arrayOf(userId.toString())).use {
            buildList { while (it.moveToNext()) add(ReorderCartLine(it.getString(0), it.getInt(1), it.getLong(2))) }
        }
        val products = (order.lines.map { it.productId } + cart.map { it.productId }).distinct().associateWith { id ->
            db.rawQuery("SELECT name, price_vnd, stock, active, revision FROM products WHERE id = ?", arrayOf(id)).use {
                if (it.moveToFirst()) ReorderProduct(it.getString(0), it.getLong(1), it.getInt(2), it.getInt(3) == 1, it.getLong(4)) else null
            }
        }
        val lines = order.lines.map { old ->
            ReorderRules.line(old, products[old.productId], cart.firstOrNull { it.productId == old.productId })
        }
        val result = ReorderPreview(userId, orderId, lines, ReorderSource(order, cart, products, checkout))
        // Check both the added subtotal and the resulting whole cart before any writes.
        try {
            result.subtotalVnd
            cart.fold(0L) { total, item -> Math.addExact(total, Math.multiplyExact(products[item.productId]?.price ?: item.price, item.quantity.toLong())) }
                .let { Math.addExact(it, result.subtotalVnd) }
        } catch (exception: ArithmeticException) { throw ReorderException(ReorderError.TOTAL_OVERFLOW) }
        return result
    }

    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { return block(db).also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
}
