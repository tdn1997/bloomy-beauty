package com.example.bloomybeauty.data.profile

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.AuthUser
import com.example.bloomybeauty.data.auth.SessionAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProfileSnapshot(val user: AuthUser)
enum class ProfileError { NAME, SESSION, STORAGE }
class ProfileException(val error: ProfileError) : Exception(error.name)

class ProfileRepository(private val helper: AuthDatabaseHelper) {
    suspend fun load(userId: Long): ProfileSnapshot = withContext(Dispatchers.IO) {
        transaction { db -> checkSession(db,userId); read(db,userId) }
    }
    // Address book and checkout draft have separate owners and are never changed by a name edit.
    suspend fun save(userId: Long, name: String): ProfileSnapshot = withContext(Dispatchers.IO) {
        if(name.trim().length !in 2..80) throw ProfileException(ProfileError.NAME)
        transaction { db ->
            checkSession(db,userId)
            if(db.update("users",ContentValues().apply {put("name",name.trim())},"_id=?",arrayOf("$userId")) != 1)
                throw ProfileException(ProfileError.SESSION)
            read(db,userId)
        }
    }
    private fun read(db: SQLiteDatabase, userId: Long): ProfileSnapshot =
        db.rawQuery("SELECT _id,name,email,role FROM users WHERE _id=?",arrayOf("$userId")).use {
            if(!it.moveToFirst()) throw ProfileException(ProfileError.SESSION)
            ProfileSnapshot(AuthUser(it.getLong(0),it.getString(1),it.getString(2),com.example.bloomybeauty.data.auth.UserRole.valueOf(it.getString(3))))
        }
    private fun checkSession(db: SQLiteDatabase, userId: Long) {
        if(!SessionAccess.matches(db,userId)) throw ProfileException(ProfileError.SESSION)
    }
    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {val result = block(db);db.setTransactionSuccessful();return result}
        finally {db.endTransaction()}
    }
}
