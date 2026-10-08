package com.example.bloomybeauty.data.order

import android.database.sqlite.SQLiteDatabase

object OrderSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE checkout_requests (
            request_id TEXT PRIMARY KEY, user_id INTEGER NOT NULL UNIQUE REFERENCES users(_id) ON DELETE CASCADE,
            fingerprint TEXT NOT NULL, payload TEXT NOT NULL
        )""")
        db.execSQL("""CREATE TABLE orders (
            _id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL REFERENCES users(_id),
            request_id TEXT NOT NULL UNIQUE, fingerprint TEXT NOT NULL, created_at INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED')),
            recipient TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL,
            subtotal_vnd INTEGER NOT NULL CHECK (subtotal_vnd >= 0), fee_vnd INTEGER NOT NULL CHECK (fee_vnd >= 0),
            total_vnd INTEGER NOT NULL CHECK (total_vnd >= 0), payment_method TEXT NOT NULL DEFAULT 'COD' CHECK (payment_method = 'COD')
        )""")
        db.execSQL("CREATE INDEX orders_user_created ON orders(user_id, created_at DESC, _id DESC)")
        db.execSQL("""CREATE TABLE order_items (
            order_id INTEGER NOT NULL REFERENCES orders(_id) ON DELETE CASCADE,
            product_id TEXT NOT NULL REFERENCES products(id), name TEXT NOT NULL, volume TEXT NOT NULL, image_key TEXT NOT NULL,
            quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
            unit_price_vnd INTEGER NOT NULL CHECK (unit_price_vnd >= 0), PRIMARY KEY (order_id, product_id)
        )""")
    }
}
