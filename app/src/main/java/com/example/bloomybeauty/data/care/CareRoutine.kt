package com.example.bloomybeauty.data.care

import java.time.Instant
import java.time.ZoneId

data class CareRoutine(
    val id: Long, val userId: Long, val productId: String?, val title: String, val note: String,
    val minuteOfDay: Int, val daysMask: Int, val enabled: Boolean, val revision: Long,
)

data class CareRoutineDraft(
    val title: String = "", val note: String = "", val productId: String? = null,
    val minuteOfDay: Int = 21 * 60, val daysMask: Int = 127, val enabled: Boolean = true,
)

object CareRoutineRules {
    const val MAX_ROUTINES = 50
    fun valid(draft: CareRoutineDraft): Boolean = draft.title.trim().length in 1..120 &&
        draft.note.length <= 500 && draft.minuteOfDay in 0..1439 && draft.daysMask in 1..127

    // Compute from local dates, not a fixed 24-hour duration (DST and timezone changes).
    fun nextOccurrence(minuteOfDay: Int, daysMask: Int, after: Instant, zone: ZoneId): Instant {
        require(minuteOfDay in 0..1439 && daysMask in 1..127)
        val today = after.atZone(zone).toLocalDate()
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (daysMask and (1 shl (date.dayOfWeek.value - 1)) == 0) continue
            val candidate = date.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone).toInstant()
            if (candidate > after) return candidate
        }
        error("No selected day")
    }
}
