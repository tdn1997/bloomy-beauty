package com.example.bloomybeauty.data.review

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.SessionAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReviewSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE product_reviews (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL REFERENCES users(_id),
            order_id INTEGER NOT NULL, product_id TEXT NOT NULL,
            rating INTEGER NOT NULL CHECK(rating BETWEEN 1 AND 5),
            comment TEXT NOT NULL CHECK(length(trim(comment)) BETWEEN 1 AND 1000),
            revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0),
            created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
            UNIQUE(user_id, order_id, product_id),
            FOREIGN KEY(order_id, product_id) REFERENCES order_items(order_id, product_id)
        )""")
        db.execSQL("CREATE INDEX reviews_product_created ON product_reviews(product_id, created_at DESC, _id DESC)")
    }
}

data class ProductReview(val id: Long, val userId: Long, val author: String, val orderId: Long,
    val productId: String, val rating: Int, val comment: String, val revision: Long, val createdAt: Long) {
    val token get() = ReviewToken(id, revision)
}
data class ReviewToken(val id: Long, val revision: Long)
data class ReviewPage(val reviews: List<ProductReview>, val count: Int, val average: Double)
class ReviewException(message: String) : IllegalArgumentException(message)

class ReviewRepository(private val helper: AuthDatabaseHelper) {
    private suspend fun <T> transaction(userId: Long, block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { SessionAccess.require(db, userId); val result = block(db); db.setTransactionSuccessful(); result }
        finally { db.endTransaction() }
    }

    suspend fun list(userId: Long, productId: String, offset: Int = 0): ReviewPage = transaction(userId) { db ->
        require(offset >= 0)
        val stats = db.rawQuery("SELECT COUNT(*), COALESCE(AVG(rating),0) FROM product_reviews WHERE product_id=?", arrayOf(productId)).use {
            it.moveToFirst(); it.getInt(0) to it.getDouble(1)
        }
        ReviewPage(read(db, "r.product_id=?", arrayOf(productId), "30 OFFSET $offset"), stats.first, stats.second)
    }

    suspend fun own(userId: Long, orderId: Long, productId: String): ProductReview? = transaction(userId) { db ->
        eligible(db, userId, orderId, productId)
        read(db, "r.user_id=? AND r.order_id=? AND r.product_id=?", arrayOf("$userId", "$orderId", productId), "1").firstOrNull()
    }

    suspend fun save(userId: Long, orderId: Long, productId: String, rating: Int, comment: String,
        expected: ReviewToken?): Unit = transaction(userId) { db ->
        val clean = comment.trim()
        if (rating !in 1..5 || clean.length !in 1..1000) throw ReviewException("Chọn 1–5 sao và nhập nhận xét từ 1–1000 ký tự.")
        eligible(db, userId, orderId, productId)
        val old = read(db, "r.user_id=? AND r.order_id=? AND r.product_id=?", arrayOf("$userId", "$orderId", productId), "1").firstOrNull()
        if (old?.revision != expected?.revision || old?.id != expected?.id) throw ReviewException("Đánh giá đã thay đổi. Hãy tải lại trước khi sửa.")
        val now = System.currentTimeMillis()
        val values = ContentValues().apply { put("rating", rating); put("comment", clean); put("updated_at", now) }
        if (old == null) {
            values.put("user_id", userId); values.put("order_id", orderId); values.put("product_id", productId); values.put("created_at", now)
            db.insertOrThrow("product_reviews", null, values)
        } else {
            values.put("revision", old.revision + 1)
            check(db.update("product_reviews", values, "_id=? AND revision=?", arrayOf("${old.id}", "${old.revision}")) == 1)
        }
    }

    suspend fun delete(userId: Long, review: ProductReview): Unit = transaction(userId) { db ->
        if (db.delete("product_reviews", "_id=? AND user_id=? AND revision=?", arrayOf("${review.id}", "$userId", "${review.revision}")) != 1)
            throw ReviewException("Đánh giá đã thay đổi hoặc không thuộc tài khoản này.")
    }

    private fun eligible(db: SQLiteDatabase, userId: Long, orderId: Long, productId: String) {
        val allowed = db.rawQuery("""SELECT 1 FROM orders o JOIN order_items i ON i.order_id=o._id
            WHERE o._id=? AND o.user_id=? AND o.status='DELIVERED' AND i.product_id=?""", arrayOf("$orderId", "$userId", productId)).use { it.moveToFirst() }
        if (!allowed) throw ReviewException("Chỉ có thể đánh giá sản phẩm trong đơn đã giao của bạn.")
    }

    private fun read(db: SQLiteDatabase, where: String, args: Array<String>, limit: String): List<ProductReview> =
        db.rawQuery("""SELECT r._id,r.user_id,u.name,r.order_id,r.product_id,r.rating,r.comment,r.revision,r.created_at
            FROM product_reviews r JOIN users u ON u._id=r.user_id WHERE $where
            ORDER BY r.created_at DESC,r._id DESC LIMIT $limit""", args).use { c -> buildList {
            while(c.moveToNext()) add(ProductReview(c.getLong(0),c.getLong(1),c.getString(2),c.getLong(3),c.getString(4),c.getInt(5),c.getString(6),c.getLong(7),c.getLong(8)))
        } }
}
