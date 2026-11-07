package com.example.bloomybeauty.data.care

import android.database.sqlite.SQLiteDatabase

object CareRoutineSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE care_routines (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL REFERENCES users(_id) ON DELETE CASCADE,
            product_id TEXT REFERENCES products(id) ON DELETE SET NULL,
            title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 120),
            note TEXT NOT NULL DEFAULT '' CHECK(length(note) <= 500),
            minute_of_day INTEGER NOT NULL CHECK(minute_of_day BETWEEN 0 AND 1439),
            days_mask INTEGER NOT NULL CHECK(days_mask BETWEEN 1 AND 127),
            enabled INTEGER NOT NULL CHECK(enabled IN (0,1)),
            revision INTEGER NOT NULL DEFAULT 1,
            last_occurrence_at INTEGER,
            created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
        )""".trimIndent())
        db.execSQL("CREATE INDEX care_routines_owner ON care_routines(user_id, enabled)")
    }
}
