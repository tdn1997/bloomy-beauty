package com.example.bloomybeauty.data.care

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.example.bloomybeauty.BloomyBeautyApplication
import com.example.bloomybeauty.data.auth.SessionException

class CareRoutineProvider : ContentProvider() {
    private val app get() = requireNotNull(context).applicationContext as BloomyBeautyApplication
    private val store get() = app.careStore
    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(CareRoutineContract.AUTHORITY, "routines", 1)
        addURI(CareRoutineContract.AUTHORITY, "routines/#", 2)
    }
    override fun onCreate() = true

    private fun match(uri: Uri): Int = matcher.match(uri).also { require(it in 1..2) { "Unsupported care URI" } }
    private fun owner(uri: Uri): Long = uri.getQueryParameter(CareRoutineContract.EXPECTED_USER)?.toLongOrNull()
        ?.takeIf { it > 0 } ?: throw SessionException()

    override fun getType(uri: Uri): String =
        "vnd.android.cursor.${if (match(uri) == 1) "dir" else "item"}/vnd.${CareRoutineContract.AUTHORITY}.care_routine"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val kind = match(uri)
        require(selection == null && selectionArgs.isNullOrEmpty()) { "Use item URIs for filtering" }
        val columns = projection?.map { require(it in CareRoutineContract.columns); it }?.toTypedArray()
        require(sortOrder == null || sortOrder in listOf("minute_of_day ASC, _id ASC", "_id ASC"))
        val userId = owner(uri)
        return store.withSession(userId) { db ->
            val clause = if (kind == 1) "user_id=?" else "user_id=? AND _id=?"
            val args = if (kind == 1) arrayOf(userId.toString()) else arrayOf(userId.toString(), ContentUris.parseId(uri).toString())
            db.query("care_routines", columns, clause, args, null, null, sortOrder ?: "minute_of_day ASC, _id ASC").also {
                it.count // Execute within the session transaction.
                it.setNotificationUri(requireNotNull(context).contentResolver, CareRoutineContract.CONTENT_URI)
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        require(match(uri) == 1)
        val userId = owner(uri)
        val clean = cleanValues(requireNotNull(values), true)
        val id = store.withSession(userId) { db ->
            val count = db.rawQuery("SELECT COUNT(*) FROM care_routines WHERE user_id=?", arrayOf(userId.toString())).use { it.moveToFirst(); it.getInt(0) }
            require(count < CareRoutineRules.MAX_ROUTINES) { "Tối đa 50 lịch chăm sóc" }
            validateProduct(db, clean)
            clean.put("user_id", userId)
            clean.put("created_at", System.currentTimeMillis())
            db.insertOrThrow("care_routines", null, clean)
        }
        changed()
        return ContentUris.withAppendedId(CareRoutineContract.CONTENT_URI, id)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        require(match(uri) == 2 && selection == null && selectionArgs.isNullOrEmpty())
        val clean = cleanValues(requireNotNull(values), false)
        val count = store.withSession(owner(uri)) { db ->
            validateProduct(db, clean)
            val args = itemArgs(uri)
            db.query("care_routines", arrayOf("revision"), "user_id=? AND _id=? AND revision=?", args, null, null, null).use {
                if (!it.moveToFirst()) return@withSession 0
                clean.put("revision", Math.addExact(it.getLong(0), 1))
            }
            clean.putNull("last_occurrence_at")
            db.update("care_routines", clean, "user_id=? AND _id=? AND revision=?", args)
        }
        if (count > 0) changed()
        return count
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        require(match(uri) == 2 && selection == null && selectionArgs.isNullOrEmpty())
        val count = store.withSession(owner(uri)) { db ->
            db.delete("care_routines", "user_id=? AND _id=? AND revision=?", itemArgs(uri))
        }
        if (count > 0) changed()
        return count
    }

    private fun itemArgs(uri: Uri): Array<String> {
        val revision = uri.getQueryParameter(CareRoutineContract.EXPECTED_REVISION)?.toLongOrNull()
        require(revision != null && revision > 0) { "Expected revision required" }
        return arrayOf(owner(uri).toString(), ContentUris.parseId(uri).toString(), revision.toString())
    }

    private fun cleanValues(values: ContentValues, insert: Boolean): ContentValues {
        val allowed = setOf("product_id", "title", "note", "minute_of_day", "days_mask", "enabled")
        require(values.keySet().all { it in allowed } && values.size() > 0)
        if (insert) require(values.keySet().containsAll(setOf("title", "minute_of_day", "days_mask", "enabled")))
        return ContentValues(values).apply {
            if (containsKey("title")) { val title = getAsString("title")?.trim(); require(title != null && title.length in 1..120); put("title", title) }
            if (containsKey("note")) require(getAsString("note") != null && getAsString("note").length <= 500)
            if (containsKey("minute_of_day")) require(getAsInteger("minute_of_day") in 0..1439)
            if (containsKey("days_mask")) require(getAsInteger("days_mask") in 1..127)
            if (containsKey("enabled")) require(getAsInteger("enabled") in 0..1)
            put("updated_at", System.currentTimeMillis())
        }
    }

    private fun validateProduct(db: android.database.sqlite.SQLiteDatabase, values: ContentValues) {
        val id = values.getAsString("product_id") ?: return
        db.rawQuery("SELECT 1 FROM products WHERE id=?", arrayOf(id)).use { require(it.moveToFirst()) { "Sản phẩm không tồn tại" } }
    }

    private fun changed() {
        requireNotNull(context).contentResolver.notifyChange(CareRoutineContract.CONTENT_URI, null)
        app.reconcileCareReminders()
    }
}
