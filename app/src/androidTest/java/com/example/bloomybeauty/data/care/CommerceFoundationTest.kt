package com.example.bloomybeauty.data.care

import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.extension.CommerceFoundation
import org.junit.Test
import org.junit.Assert.*

class CommerceFoundationTest {
    @Test fun backfillsOnlyActualDeliveryEvidence() {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE orders(_id INTEGER PRIMARY KEY,status TEXT,created_at INTEGER)")
            db.execSQL("CREATE TABLE order_events(order_id INTEGER,to_status TEXT,changed_at INTEGER)")
            db.execSQL("INSERT INTO orders VALUES(1,'DELIVERED',10),(2,'DELIVERED',20),(3,'PENDING',30)")
            db.execSQL("INSERT INTO order_events VALUES(1,'DELIVERED',100),(1,'DELIVERED',200),(3,'CONFIRMED',300)")
            CommerceFoundation.upgrade(db)
            db.rawQuery("SELECT delivered_at FROM orders ORDER BY _id", null).use {
                assertTrue(it.moveToNext());assertEquals(200L,it.getLong(0))
                assertTrue(it.moveToNext());assertTrue(it.isNull(0))
                assertTrue(it.moveToNext());assertTrue(it.isNull(0))
            }
        }
    }
}
