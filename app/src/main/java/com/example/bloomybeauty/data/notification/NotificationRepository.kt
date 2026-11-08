package com.example.bloomybeauty.data.notification

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.SessionAccess
import com.example.bloomybeauty.data.order.CustomerOrder
import com.example.bloomybeauty.data.order.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InboxCursor(val time: Long, val id: Long)
data class InboxItem(val id: Long, val eventId: String, val type: String, val entityId: Long,
    val title: String, val body: String, val createdAt: Long, val readAt: Long?)
data class InboxPage(val items: List<InboxItem>, val unread: Long, val next: InboxCursor?)
class NotificationUnavailable : Exception("Notification target is unavailable")

object NotificationSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE user_notifications (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL REFERENCES users(_id) ON DELETE CASCADE,
            event_id TEXT NOT NULL, type TEXT NOT NULL CHECK(type='ORDER'), entity_id INTEGER NOT NULL,
            title TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL, read_at INTEGER,
            UNIQUE(user_id,event_id))""")
        db.execSQL("CREATE INDEX inbox_owner_created ON user_notifications(user_id,created_at DESC,_id DESC)")
        db.execSQL("CREATE INDEX inbox_owner_read ON user_notifications(user_id,read_at)")
        // Preserve actual audit times. Older orders with no audit do not receive invented events.
        db.rawQuery("SELECT _id,order_id,to_status,changed_at FROM order_events ORDER BY _id",null).use {c ->
            while(c.moveToNext()) record(db,c.getLong(0),c.getLong(1),c.getString(2),c.getLong(3))
        }
    }
    fun record(db: SQLiteDatabase, eventId: Long, orderId: Long, status: String, at: Long) {
        check(db.inTransaction()) {"Inbox must be committed with its order audit"}
        val body = when(status) {
            "PENDING" -> "Đơn đã được tạo, đang chờ xác nhận."
            "CONFIRMED" -> "Đơn đã được xác nhận."
            "SHIPPING" -> "Đơn đang được giao."
            "DELIVERED" -> "Đơn đã giao thành công."
            "CANCELLED" -> "Đơn đã được hủy."
            else -> error("Unsupported order status")
        }
        val owner = db.rawQuery("SELECT user_id FROM orders WHERE _id=?",arrayOf("$orderId")).use {check(it.moveToFirst());it.getLong(0)}
        db.insertOrThrow("user_notifications",null,ContentValues().apply {
            put("user_id",owner);put("event_id","order-event:$eventId");put("type","ORDER");put("entity_id",orderId)
            put("title","Đơn BB-${orderId.toString().padStart(6,'0')}");put("body",body);put("created_at",at)
        })
    }
}

class NotificationRepository(private val helper: AuthDatabaseHelper) {
    private suspend fun <T> transaction(userId: Long, block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase;db.beginTransaction()
        try {SessionAccess.require(db,userId);val result = block(db);db.setTransactionSuccessful();result} finally {db.endTransaction()}
    }
    suspend fun page(userId: Long, before: InboxCursor? = null): InboxPage = transaction(userId) {db ->
        val args = mutableListOf("$userId")
        val clause = before?.let {args.addAll(listOf("${it.time}","${it.time}","${it.id}"));" AND (created_at<? OR (created_at=? AND _id<?))"}.orEmpty()
        val all = db.rawQuery("SELECT * FROM user_notifications WHERE user_id=?$clause ORDER BY created_at DESC,_id DESC LIMIT 31",args.toTypedArray()).use {c -> buildList {
            fun text(key: String) = c.getString(c.getColumnIndexOrThrow(key))
            fun long(key: String) = c.getLong(c.getColumnIndexOrThrow(key))
            while(c.moveToNext()) add(InboxItem(long("_id"),text("event_id"),text("type"),long("entity_id"),text("title"),text("body"),long("created_at"),
                if(c.isNull(c.getColumnIndexOrThrow("read_at"))) null else long("read_at")))
        }}
        val items = all.take(30)
        val unread = db.rawQuery("SELECT COUNT(*) FROM user_notifications WHERE user_id=? AND read_at IS NULL",arrayOf("$userId")).use {it.moveToFirst();it.getLong(0)}
        InboxPage(items,unread,if(all.size > 30) items.last().let {InboxCursor(it.createdAt,it.id)} else null)
    }
    suspend fun readAll(userId: Long) = transaction(userId) {db ->
        db.update("user_notifications",ContentValues().apply {put("read_at",System.currentTimeMillis())},"user_id=? AND read_at IS NULL",arrayOf("$userId"));Unit
    }
    suspend fun open(userId: Long, notificationId: Long): CustomerOrder = transaction(userId) {db ->
        val orderId = db.rawQuery("SELECT entity_id FROM user_notifications WHERE _id=? AND user_id=? AND type='ORDER'",arrayOf("$notificationId","$userId")).use {
            if(!it.moveToFirst()) throw NotificationUnavailable();it.getLong(0)
        }
        val order = db.query("orders",null,"_id=? AND user_id=?",arrayOf("$orderId","$userId"),null,null,null).use {
            if(!it.moveToFirst()) throw NotificationUnavailable();OrderRepository(helper).readOrder(db,it)
        }
        db.update("user_notifications",ContentValues().apply {put("read_at",System.currentTimeMillis())},"_id=? AND user_id=? AND read_at IS NULL",arrayOf("$notificationId","$userId"))
        order
    }
}
