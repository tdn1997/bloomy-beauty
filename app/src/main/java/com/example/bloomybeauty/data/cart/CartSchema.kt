package com.example.bloomybeauty.data.cart

import android.database.sqlite.SQLiteDatabase

object CartSchema {
    fun upgradeFromCatalog(db: SQLiteDatabase) {
        // Demo inventory belongs to this device, not the source brand's inventory.
        db.execSQL("ALTER TABLE products ADD COLUMN stock INTEGER NOT NULL DEFAULT 30 CHECK (stock >= 0)")
        db.execSQL("ALTER TABLE products ADD COLUMN active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1))")
        db.execSQL("""CREATE TABLE cart_items (
            user_id INTEGER NOT NULL REFERENCES users(_id) ON DELETE CASCADE,
            product_id TEXT NOT NULL REFERENCES products(id),
            quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
            accepted_price_vnd INTEGER NOT NULL CHECK (accepted_price_vnd >= 0),
            PRIMARY KEY (user_id, product_id)
        )""")
        db.execSQL("""CREATE TABLE shipping_details (
            user_id INTEGER PRIMARY KEY REFERENCES users(_id) ON DELETE CASCADE,
            recipient TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL
        )""")
    }
}
