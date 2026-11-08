package com.example.bloomybeauty.fixtures

import android.database.sqlite.SQLiteDatabase

/** Independent pre-coupon schema fixture; works on SQLite versions without DROP COLUMN. */
object LegacyOrders {
    fun rebuild(db: SQLiteDatabase, withDeliveryTime: Boolean) {
        check(!db.inTransaction())
        db.setForeignKeyConstraintsEnabled(false)
        val delivery = if(withDeliveryTime) ", delivered_at INTEGER" else ""
        db.execSQL("""CREATE TABLE orders_legacy (
            _id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL REFERENCES users(_id),
            request_id TEXT NOT NULL UNIQUE, fingerprint TEXT NOT NULL, created_at INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','CONFIRMED','SHIPPING','DELIVERED','CANCELLED')),
            recipient TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL,
            subtotal_vnd INTEGER NOT NULL CHECK(subtotal_vnd >= 0), fee_vnd INTEGER NOT NULL CHECK(fee_vnd >= 0),
            total_vnd INTEGER NOT NULL CHECK(total_vnd >= 0), payment_method TEXT NOT NULL DEFAULT 'COD' CHECK(payment_method='COD')
            $delivery
        )""")
        val columns = "_id,user_id,request_id,fingerprint,created_at,status,recipient,phone,address,subtotal_vnd,fee_vnd,total_vnd,payment_method" +
            if(withDeliveryTime) ",delivered_at" else ""
        db.execSQL("INSERT INTO orders_legacy($columns) SELECT $columns FROM orders")
        db.execSQL("DROP TABLE orders")
        db.execSQL("ALTER TABLE orders_legacy RENAME TO orders")
        db.execSQL("CREATE INDEX orders_user_created ON orders(user_id,created_at DESC,_id DESC)")
        if(withDeliveryTime) db.execSQL("CREATE INDEX orders_delivered_at ON orders(status,delivered_at)")
    }
}
