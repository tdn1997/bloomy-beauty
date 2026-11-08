package com.example.bloomybeauty.data.extension

import android.database.sqlite.SQLiteDatabase

/** Version 7 is deliberately independent of future coupon/review migrations. */
object CommerceFoundation {
    fun upgrade(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE orders ADD COLUMN delivered_at INTEGER")
        db.execSQL("""UPDATE orders SET delivered_at=(
            SELECT MAX(changed_at) FROM order_events
            WHERE order_id=orders._id AND to_status='DELIVERED'
        ) WHERE status='DELIVERED'""")
        db.execSQL("CREATE INDEX orders_delivered_at ON orders(status, delivered_at)")
    }
}
