package com.example.bloomybeauty.data.profile

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.AuthUser
import com.example.bloomybeauty.data.cart.ShippingDetails
import com.example.bloomybeauty.data.cart.ShippingError
import com.example.bloomybeauty.data.cart.ShippingValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProfileSnapshot(val user: AuthUser, val shipping: ShippingDetails?)
enum class ProfileError { NAME, RECIPIENT, PHONE, ADDRESS, SESSION, STORAGE }
class ProfileException(val error: ProfileError) : Exception(error.name)

class ProfileRepository(private val helper: AuthDatabaseHelper) {
    suspend fun load(userId: Long): ProfileSnapshot = withContext(Dispatchers.IO) {
        transaction { db -> checkSession(db, userId); read(db, userId) }
    }

    suspend fun save(userId: Long, name: String, shipping: ShippingDetails?): ProfileSnapshot = withContext(Dispatchers.IO) {
        if (name.trim().length !in 2..80) throw ProfileException(ProfileError.NAME)
        shipping?.let { details -> ShippingValidation.validate(details)?.let { error ->
            throw ProfileException(when (error) {
                ShippingError.NAME -> ProfileError.RECIPIENT
                ShippingError.PHONE -> ProfileError.PHONE
                ShippingError.ADDRESS -> ProfileError.ADDRESS
            })
        } }
        transaction { db ->
            checkSession(db, userId)
            if (db.update("users", ContentValues().apply { put("name", name.trim()) }, "_id = ?", arrayOf(userId.toString())) != 1) {
                throw ProfileException(ProfileError.SESSION)
            }
            if (shipping == null) db.delete("shipping_details", "user_id = ?", arrayOf(userId.toString()))
            else {
                val normalized = ShippingValidation.normalize(shipping)
                val values = ContentValues().apply {
                    put("recipient", normalized.recipient); put("phone", normalized.phone); put("address", normalized.address)
                }
                if (db.update("shipping_details", values, "user_id = ?", arrayOf(userId.toString())) == 0) {
                    values.put("user_id", userId)
                    db.insertOrThrow("shipping_details", null, values)
                }
            }
            read(db, userId)
        }
    }

    private fun read(db: SQLiteDatabase, userId: Long): ProfileSnapshot {
        val user = db.query("users", arrayOf("_id", "name", "email", "role"), "_id = ?", arrayOf(userId.toString()), null, null, null).use {
            if (!it.moveToFirst()) throw ProfileException(ProfileError.SESSION)
            AuthUser(it.getLong(0), it.getString(1), it.getString(2), com.example.bloomybeauty.data.auth.UserRole.valueOf(it.getString(3)))
        }
        val shipping = db.query("shipping_details", arrayOf("recipient", "phone", "address"), "user_id = ?", arrayOf(userId.toString()), null, null, null).use {
            if (it.moveToFirst()) ShippingDetails(it.getString(0), it.getString(1), it.getString(2)) else null
        }
        return ProfileSnapshot(user, shipping)
    }

    private fun checkSession(db: SQLiteDatabase, userId: Long) {
        db.rawQuery("SELECT 1 FROM auth_session WHERE user_id = ?", arrayOf(userId.toString())).use {
            if (!it.moveToFirst()) throw ProfileException(ProfileError.SESSION)
        }
    }
    private inline fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { return block(db).also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
}
