package com.example.bloomybeauty.data.care

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.SessionAccess

class CareRoutineStore(private val helper: AuthDatabaseHelper) {
    fun <T> withSession(userId: Long, block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            SessionAccess.require(db, userId)
            return block(db).also { db.setTransactionSuccessful() }
        } finally { db.endTransaction() }
    }

    fun currentUserId(): Long? = helper.readableDatabase.rawQuery(
        "SELECT user_id FROM auth_session WHERE _id=1", null,
    ).use { if (it.moveToFirst()) it.getLong(0) else null }

    fun enabledForCurrentUser(): List<CareRoutine> {
        val userId = currentUserId() ?: return emptyList()
        return withSession(userId) { db ->
            db.query("care_routines", null, "user_id=? AND enabled=1", arrayOf(userId.toString()), null, null, "_id").use {
                buildList { while (it.moveToNext()) add(it.toCareRoutine()) }
            }
        }
    }

    /** Run the short notification operation while the session and occurrence claim are locked. */
    fun claimOccurrence(id: Long, userId: Long, revision: Long, occurrence: Long, show: (CareRoutine) -> Unit): Boolean =
        withSession(userId) { db ->
            val routine = db.query("care_routines", null,
                "_id=? AND user_id=? AND enabled=1 AND revision=? AND (last_occurrence_at IS NULL OR last_occurrence_at < ?)",
                arrayOf(id.toString(), userId.toString(), revision.toString(), occurrence.toString()), null, null, null,
            ).use { if (it.moveToFirst()) it.toCareRoutine() else null } ?: return@withSession false
            db.execSQL("UPDATE care_routines SET last_occurrence_at=? WHERE _id=?", arrayOf(occurrence, id))
            show(routine)
            true
        }
}

fun Cursor.toCareRoutine(): CareRoutine {
    fun text(name: String) = getString(getColumnIndexOrThrow(name))
    fun number(name: String) = getLong(getColumnIndexOrThrow(name))
    return CareRoutine(number("_id"), number("user_id"), text("product_id"), text("title"), text("note"),
        number("minute_of_day").toInt(), number("days_mask").toInt(), number("enabled") == 1L, number("revision"))
}
