package com.example.bloomybeauty.data.admin

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import com.example.bloomybeauty.BuildConfig

object AdminSchema {
    fun upgrade(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'CUSTOMER' CHECK(role IN ('CUSTOMER','ADMIN'))")
        db.execSQL("ALTER TABLE products ADD COLUMN revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0)")
        db.execSQL("""CREATE TABLE order_events (
            _id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL REFERENCES orders(_id),
            actor_id INTEGER NOT NULL REFERENCES users(_id), actor_name TEXT NOT NULL,
            from_status TEXT, to_status TEXT NOT NULL, changed_at INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX order_events_order ON order_events(order_id, changed_at, _id)")
    }
    fun record(db: SQLiteDatabase, orderId: Long, actorId: Long, from: String?, to: String) {
        val name = db.rawQuery("SELECT name FROM users WHERE _id = ?", arrayOf(actorId.toString())).use {
            check(it.moveToFirst()); it.getString(0)
        }
        db.insertOrThrow("order_events", null, ContentValues().apply {
            put("order_id", orderId); put("actor_id", actorId); put("actor_name", name)
            put("from_status", from); put("to_status", to); put("changed_at", System.currentTimeMillis())
        })
    }
}

object DemoAdmin {
    fun seed(db: SQLiteDatabase) {
        if (!BuildConfig.DEBUG || BuildConfig.DEMO_ADMIN_EMAIL.isBlank() || BuildConfig.DEMO_ADMIN_HASH.isBlank() || BuildConfig.DEMO_ADMIN_SALT.isBlank()) return
        db.beginTransaction()
        try {
            val exists = db.rawQuery("SELECT 1 FROM users WHERE email = ?", arrayOf(BuildConfig.DEMO_ADMIN_EMAIL)).use { it.moveToFirst() }
            // Never elevate an existing customer or reset an existing administrator's password.
            if (!exists) db.insertOrThrow("users", null, ContentValues().apply {
                put("name", "Quản trị Bloomy"); put("email", BuildConfig.DEMO_ADMIN_EMAIL); put("role", "ADMIN")
                put("password_hash", Base64.decode(BuildConfig.DEMO_ADMIN_HASH, Base64.NO_WRAP))
                put("password_salt", Base64.decode(BuildConfig.DEMO_ADMIN_SALT, Base64.NO_WRAP)); put("password_iterations", 210000)
            })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
