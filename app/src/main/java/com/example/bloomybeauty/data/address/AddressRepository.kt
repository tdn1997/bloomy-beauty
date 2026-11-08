package com.example.bloomybeauty.data.address

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.SessionAccess
import com.example.bloomybeauty.data.cart.ShippingDetails
import com.example.bloomybeauty.data.cart.ShippingValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AddressSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE addresses (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL REFERENCES users(_id) ON DELETE CASCADE,
            label TEXT NOT NULL CHECK(length(trim(label)) BETWEEN 1 AND 40),
            recipient TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL,
            is_default INTEGER NOT NULL CHECK(is_default IN (0,1)),
            revision INTEGER NOT NULL DEFAULT 0 CHECK(revision >= 0),
            created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
        )""")
        db.execSQL("CREATE UNIQUE INDEX addresses_one_default ON addresses(user_id) WHERE is_default=1")
        db.execSQL("CREATE INDEX addresses_owner_created ON addresses(user_id,created_at,_id)")
        val now = System.currentTimeMillis()
        db.execSQL("""INSERT INTO addresses(user_id,label,recipient,phone,address,is_default,created_at,updated_at)
            SELECT user_id,'Địa chỉ đã lưu',recipient,phone,address,1,?,? FROM shipping_details""", arrayOf(now,now))
    }
}

data class AddressToken(val id: Long, val revision: Long)
data class SavedAddress(val id: Long, val label: String, val shipping: ShippingDetails,
    val isDefault: Boolean, val revision: Long) { val token get() = AddressToken(id,revision) }
class AddressException(message: String) : IllegalArgumentException(message)

class AddressRepository(private val helper: AuthDatabaseHelper) {
    private suspend fun <T> transaction(userId: Long, block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { SessionAccess.require(db,userId); val result = block(db); db.setTransactionSuccessful(); result }
        finally { db.endTransaction() }
    }
    suspend fun list(userId: Long): List<SavedAddress> = transaction(userId) { read(it,userId) }

    suspend fun save(userId: Long, label: String, shipping: ShippingDetails, makeDefault: Boolean,
        expected: AddressToken? = null): List<SavedAddress> = transaction(userId) { db ->
        val cleanLabel = label.trim()
        if (cleanLabel.length !in 1..40) throw AddressException("Nhãn địa chỉ cần từ 1 đến 40 ký tự.")
        if (ShippingValidation.validate(shipping) != null) throw AddressException("Kiểm tra người nhận (2–80 ký tự), số di động Việt Nam và địa chỉ (10–300 ký tự).")
        val rows = read(db,userId)
        val old = expected?.let { token -> current(rows,token) }
        if (old == null && rows.size >= 10) throw AddressException("Mỗi tài khoản lưu tối đa 10 địa chỉ.")
        val normalized = ShippingValidation.normalize(shipping)
        val now = System.currentTimeMillis()
        val default = rows.isEmpty() || makeDefault || old?.isDefault == true
        if (default) clearDefault(db,userId,old?.id,now)
        val values = ContentValues().apply {
            put("label",cleanLabel); put("recipient",normalized.recipient); put("phone",normalized.phone)
            put("address",normalized.address); put("is_default",if(default) 1 else 0); put("updated_at",now)
        }
        if (old == null) {
            values.put("user_id",userId); values.put("created_at",now)
            db.insertOrThrow("addresses",null,values)
        } else {
            values.put("revision",old.revision+1)
            check(db.update("addresses",values,"_id=? AND user_id=? AND revision=?",arrayOf("${old.id}","$userId","${old.revision}")) == 1)
        }
        read(db,userId)
    }

    suspend fun setDefault(userId: Long, token: AddressToken): List<SavedAddress> = transaction(userId) { db ->
        val selected = current(read(db,userId),token)
        if (!selected.isDefault) {
            val now = System.currentTimeMillis()
            clearDefault(db,userId,selected.id,now)
            db.execSQL("UPDATE addresses SET is_default=1,revision=revision+1,updated_at=? WHERE _id=? AND user_id=?",arrayOf(now,selected.id,userId))
        }
        read(db,userId)
    }

    suspend fun delete(userId: Long, token: AddressToken): List<SavedAddress> = transaction(userId) { db ->
        val old = current(read(db,userId),token)
        check(db.delete("addresses","_id=? AND user_id=? AND revision=?",arrayOf("${old.id}","$userId","${old.revision}")) == 1)
        val remaining = read(db,userId)
        if (old.isDefault && remaining.isNotEmpty()) {
            // Earliest creation/ID is stable, independent of labels and locale.
            val next = db.rawQuery("SELECT _id FROM addresses WHERE user_id=? ORDER BY created_at,_id LIMIT 1",arrayOf("$userId")).use {it.moveToFirst();it.getLong(0)}
            db.execSQL("UPDATE addresses SET is_default=1,revision=revision+1,updated_at=? WHERE _id=?",arrayOf(System.currentTimeMillis(),next))
        }
        read(db,userId)
    }

    // Returns a value snapshot; checkout owns its copy after this explicit selection.
    suspend fun select(userId: Long, token: AddressToken): ShippingDetails = transaction(userId) { db -> current(read(db,userId),token).shipping }

    private fun current(rows: List<SavedAddress>, token: AddressToken): SavedAddress =
        rows.firstOrNull {it.id == token.id && it.revision == token.revision}
            ?: throw AddressException("Địa chỉ đã thay đổi hoặc không thuộc tài khoản này. Hãy tải lại.")

    private fun clearDefault(db: SQLiteDatabase, userId: Long, except: Long?, now: Long) {
        db.execSQL("UPDATE addresses SET is_default=0,revision=revision+1,updated_at=? WHERE user_id=? AND is_default=1 AND _id<>?",
            arrayOf(now,userId,except ?: -1L))
    }
    private fun read(db: SQLiteDatabase, userId: Long): List<SavedAddress> =
        db.rawQuery("SELECT _id,label,recipient,phone,address,is_default,revision FROM addresses WHERE user_id=? ORDER BY is_default DESC,created_at,_id",arrayOf("$userId")).use { c -> buildList {
            while(c.moveToNext()) add(SavedAddress(c.getLong(0),c.getString(1),ShippingDetails(c.getString(2),c.getString(3),c.getString(4)),c.getInt(5)==1,c.getLong(6)))
        } }
    companion object {
        internal fun readDefault(db: SQLiteDatabase, userId: Long): ShippingDetails? =
            db.rawQuery("SELECT recipient,phone,address FROM addresses WHERE user_id=? AND is_default=1",arrayOf("$userId")).use {
                if(it.moveToFirst()) ShippingDetails(it.getString(0),it.getString(1),it.getString(2)) else null
            }
    }
}
