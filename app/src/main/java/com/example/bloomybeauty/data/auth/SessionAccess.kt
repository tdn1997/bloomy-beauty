package com.example.bloomybeauty.data.auth

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException

class SessionException : SQLiteException("Authenticated session changed")
object SessionAccess {
    fun matches(db: SQLiteDatabase, userId: Long): Boolean = db.rawQuery(
        "SELECT 1 FROM auth_session WHERE _id=1 AND user_id=?", arrayOf(userId.toString()),
    ).use { it.moveToFirst() }
    fun require(db: SQLiteDatabase, userId: Long) {
        if (!matches(db,userId)) throw SessionException()
    }
}
