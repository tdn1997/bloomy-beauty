package com.example.bloomybeauty.data.care

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CareRoutineRulesTest {
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun next(minute: Int, mask: Int, after: String, zone: ZoneId = vietnam) =
        CareRoutineRules.nextOccurrence(minute, mask, Instant.parse(after), zone)

    @Test fun schedulesTodayBeforeSelectedTime() {
        assertEquals(Instant.parse("2026-11-07T14:00:00Z"), next(1260, 127, "2026-11-07T13:59:00Z"))
    }
    @Test fun exactOccurrenceMovesToNextDay() {
        assertEquals(Instant.parse("2026-11-08T14:00:00Z"), next(1260, 127, "2026-11-07T14:00:00Z"))
    }
    @Test fun selectedWeekdayWrapsToFollowingWeek() {
        // November 2 is Monday; the Monday-only alarm has just passed.
        assertEquals(Instant.parse("2026-11-09T14:00:00Z"), next(1260, 1, "2026-11-02T14:01:00Z"))
    }
    @Test fun sundayUsesLastBitAndMidnightUsesLocalDate() {
        assertEquals(Instant.parse("2026-11-07T17:00:00Z"), next(0, 64, "2026-11-07T14:00:00Z"))
    }
    @Test fun timezoneChangesPreserveLocalHour() {
        assertEquals(Instant.parse("2026-11-07T21:00:00Z"), next(1260, 127, "2026-11-07T13:00:00Z", ZoneId.of("UTC")))
    }
    @Test fun daylightSavingGapAdvancesToValidLocalTime() {
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), next(150, 127, "2026-03-08T05:00:00Z", ZoneId.of("America/New_York")))
    }
    @Test fun validatesRequiredFieldsAndBounds() {
        val valid = CareRoutineDraft(title = " Serum ")
        assertTrue(CareRoutineRules.valid(valid))
        assertFalse(CareRoutineRules.valid(valid.copy(title = "  ")))
        assertFalse(CareRoutineRules.valid(valid.copy(title = "x".repeat(121))))
        assertFalse(CareRoutineRules.valid(valid.copy(note = "x".repeat(501))))
        assertFalse(CareRoutineRules.valid(valid.copy(minuteOfDay = 1440)))
        assertFalse(CareRoutineRules.valid(valid.copy(daysMask = 0)))
        assertFalse(CareRoutineRules.valid(valid.copy(daysMask = 128)))
    }
}
