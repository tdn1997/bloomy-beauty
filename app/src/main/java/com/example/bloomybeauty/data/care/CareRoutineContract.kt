package com.example.bloomybeauty.data.care

import android.content.ContentUris
import android.net.Uri
import com.example.bloomybeauty.BuildConfig

object CareRoutineContract {
    const val AUTHORITY = BuildConfig.APPLICATION_ID + ".care"
    const val EXPECTED_USER = "expected_user_id"
    const val EXPECTED_REVISION = "expected_revision"
    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/routines")
    val columns = arrayOf("_id", "user_id", "product_id", "title", "note", "minute_of_day", "days_mask", "enabled", "revision", "last_occurrence_at", "created_at", "updated_at")

    fun uri(userId: Long, id: Long? = null, revision: Long? = null): Uri =
        (if (id == null) CONTENT_URI else ContentUris.withAppendedId(CONTENT_URI, id)).buildUpon()
            .appendQueryParameter(EXPECTED_USER, userId.toString()).apply {
                if (revision != null) appendQueryParameter(EXPECTED_REVISION, revision.toString())
            }.build()
}
