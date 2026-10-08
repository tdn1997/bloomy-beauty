package com.example.bloomybeauty.data.catalog

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.SessionAccess
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CatalogRepository(private val helper: AuthDatabaseHelper) {
    suspend fun load(userId: Long): CatalogSnapshot = withContext(Dispatchers.IO) {
        transaction(userId) { db ->
            val categories = db.query("categories", null, null, null, null, null, "sort_order ASC").use { cursor ->
                buildList { while (cursor.moveToNext()) add(Category(cursor.text("id"), cursor.text("name"))) }
            }
            val products = db.query("products", null, "active = 1", null, null, null, "sort_order ASC").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(
                        Product(
                            id = cursor.text("id"), name = cursor.text("name"), brand = cursor.text("brand"),
                            categoryId = cursor.text("category_id"), volume = cursor.text("volume"),
                            priceVnd = cursor.getLong(cursor.getColumnIndexOrThrow("price_vnd")),
                            imageKey = cursor.text("image_key"), description = cursor.text("description"),
                            ingredients = cursor.text("ingredients"), usage = cursor.text("usage"),
                            sourceUrl = cursor.text("source_url"),
                            stock = cursor.getInt(cursor.getColumnIndexOrThrow("stock")),
                            active = cursor.getInt(cursor.getColumnIndexOrThrow("active")) == 1,
                            revision = cursor.getLong(cursor.getColumnIndexOrThrow("revision")),
                        ),
                    )
                }
            }
            val favoriteIds = db.query(
                "favorites", arrayOf("product_id"), "user_id = ?", arrayOf(userId.toString()), null, null, null,
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.text("product_id")) } }
            CatalogSnapshot(categories, products, favoriteIds)
        }
    }

    suspend fun toggleFavorite(userId: Long, productId: String) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            SessionAccess.require(db, userId)
            val removed = db.delete("favorites", "user_id = ? AND product_id = ?", arrayOf(userId.toString(), productId))
            if (removed == 0) {
                db.insertOrThrow("favorites", null, ContentValues().apply {
                    put("user_id", userId); put("product_id", productId)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private inline fun <T> transaction(userId: Long, block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            SessionAccess.require(db, userId)
            return block(db).also { db.setTransactionSuccessful() }
        }
        finally { db.endTransaction() }
    }

    private fun Cursor.text(column: String): String = getString(getColumnIndexOrThrow(column))
}
