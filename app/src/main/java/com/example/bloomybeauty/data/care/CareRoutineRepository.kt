package com.example.bloomybeauty.data.care

import android.content.ContentResolver
import android.content.ContentValues
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class CareRoutineRepository(private val resolver: ContentResolver) {
    fun observe(userId: Long): Flow<List<CareRoutine>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(CareRoutineContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().map { load(userId) }.flowOn(Dispatchers.IO)

    suspend fun load(userId: Long): List<CareRoutine> = withContext(Dispatchers.IO) {
        resolver.query(CareRoutineContract.uri(userId), null, null, null, null)?.use {
            buildList { while (it.moveToNext()) add(it.toCareRoutine()) }
        } ?: emptyList()
    }

    suspend fun save(userId: Long, id: Long?, revision: Long?, draft: CareRoutineDraft): Unit = withContext(Dispatchers.IO) {
        require(CareRoutineRules.valid(draft)) { "Vui lòng kiểm tra tên, ghi chú, giờ và ngày lặp" }
        val values = ContentValues().apply {
            put("title", draft.title.trim()); put("note", draft.note); put("product_id", draft.productId)
            put("minute_of_day", draft.minuteOfDay); put("days_mask", draft.daysMask); put("enabled", if (draft.enabled) 1 else 0)
        }
        if (id == null) checkNotNull(resolver.insert(CareRoutineContract.uri(userId), values))
        else check(resolver.update(CareRoutineContract.uri(userId, id, revision), values, null, null) == 1) { "Lịch đã thay đổi hoặc không còn tồn tại. Hãy tải lại." }
        Unit
    }

    suspend fun setEnabled(userId: Long, routine: CareRoutine, enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        check(resolver.update(CareRoutineContract.uri(userId, routine.id, routine.revision), ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
        }, null, null) == 1) { "Lịch đã thay đổi. Hãy thử lại." }
    }

    suspend fun delete(userId: Long, routine: CareRoutine): Unit = withContext(Dispatchers.IO) {
        check(resolver.delete(CareRoutineContract.uri(userId, routine.id, routine.revision), null, null) == 1) { "Lịch đã thay đổi. Hãy tải lại." }
    }
}
