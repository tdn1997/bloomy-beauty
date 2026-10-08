package com.example.bloomybeauty.data.auth

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.example.bloomybeauty.data.auth.AuthContract.Session
import com.example.bloomybeauty.data.auth.AuthContract.Users
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val helper: AuthDatabaseHelper,
    private val passwordHasher: PasswordHasher = PasswordHasher(),
) {
    suspend fun register(name: String, email: String, password: String, confirmation: String): AuthResult =
        withContext(Dispatchers.IO) {
            AuthValidation.validate(email, password, name, confirmation)?.let {
                return@withContext AuthResult.Failure(it)
            }
            val normalizedEmail = AuthValidation.normalizeEmail(email)
            val db = helper.writableDatabase
            val digest = passwordHasher.hash(password)
            db.beginTransaction()
            try {
                if (findUser(db, normalizedEmail) != null) {
                    return@withContext AuthResult.Failure(AuthError.DUPLICATE_EMAIL)
                }
                val values = ContentValues().apply {
                    put(Users.NAME, name.trim())
                    put(Users.EMAIL, normalizedEmail)
                    put(Users.PASSWORD_HASH, digest.hash)
                    put(Users.PASSWORD_SALT, digest.salt)
                    put(Users.PASSWORD_ITERATIONS, digest.iterations)
                }
                val id = try {
                    db.insertOrThrow(Users.TABLE, null, values)
                } catch (exception: SQLiteConstraintException) {
                    if (findUser(db, normalizedEmail) != null) {
                        return@withContext AuthResult.Failure(AuthError.DUPLICATE_EMAIL)
                    }
                    throw exception
                }
                saveSession(db, id)
                db.setTransactionSuccessful()
                AuthResult.Success(AuthUser(id, name.trim(), normalizedEmail))
            } finally {
                db.endTransaction()
            }
        }

    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        AuthValidation.validate(email, password)?.let {
            return@withContext AuthResult.Failure(it)
        }
        val db = helper.writableDatabase
        val account = findUser(db, AuthValidation.normalizeEmail(email))
            ?: return@withContext AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
        if (!passwordHasher.verify(password, account.digest)) {
            return@withContext AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
        }
        saveSession(db, account.user.id)
        AuthResult.Success(account.user)
    }

    suspend fun restoreSession(): AuthUser? = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        val userId = db.query(
            Session.TABLE, arrayOf(Session.USER_ID), "${Session.ID} = ?", arrayOf("1"),
            null, null, null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(Session.USER_ID)) else null
        } ?: return@withContext null
        db.query(
            Users.TABLE, arrayOf(Users.ID, Users.NAME, Users.EMAIL, "role"),
            "${Users.ID} = ?", arrayOf(userId.toString()), null, null, null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toUser() else null }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(Session.TABLE, "${Session.ID} = ?", arrayOf("1"))
        Unit
    }

    private fun findUser(db: SQLiteDatabase, email: String): Account? = db.query(
        Users.TABLE, null, "${Users.EMAIL} = ?", arrayOf(email), null, null, null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        Account(
            cursor.toUser(),
            PasswordDigest(
                cursor.getBlob(cursor.getColumnIndexOrThrow(Users.PASSWORD_HASH)),
                cursor.getBlob(cursor.getColumnIndexOrThrow(Users.PASSWORD_SALT)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Users.PASSWORD_ITERATIONS)),
            ),
        )
    }

    private fun saveSession(db: SQLiteDatabase, userId: Long) {
        val values = ContentValues().apply {
            put(Session.ID, 1)
            put(Session.USER_ID, userId)
        }
        if (db.insertWithOnConflict(Session.TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1L) {
            throw SQLiteException("Could not persist authentication session")
        }
    }

    private fun Cursor.toUser() = AuthUser(
        getLong(getColumnIndexOrThrow(Users.ID)),
        getString(getColumnIndexOrThrow(Users.NAME)),
        getString(getColumnIndexOrThrow(Users.EMAIL)),
        UserRole.valueOf(getString(getColumnIndexOrThrow("role"))),
    )

    private data class Account(val user: AuthUser, val digest: PasswordDigest)
}
