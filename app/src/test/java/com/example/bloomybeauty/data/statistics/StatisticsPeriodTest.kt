package com.example.bloomybeauty.data.statistics

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class StatisticsPeriodTest {
    @Test fun vietnamDateConvertsToHalfOpenUtcBoundsIndependentlyOfDefaultZone() {
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            val period = StatisticsPeriod.parse("2026-11-08","2026-11-08")
            assertEquals(Instant.parse("2026-11-07T17:00:00Z").toEpochMilli(),period.startInclusive)
            assertEquals(Instant.parse("2026-11-08T17:00:00Z").toEpochMilli(),period.endExclusive)
        } finally {java.util.TimeZone.setDefault(original)}
    }
    @Test(expected = IllegalArgumentException::class) fun backwardsPeriodIsRejected() {StatisticsPeriod.parse("2026-11-09","2026-11-08")}
    @Test(expected = java.time.DateTimeException::class) fun impossibleDateIsRejectedStrictly() {StatisticsPeriod.parse("2026-02-30","2026-03-01")}
    @Test(expected = ArithmeticException::class) fun totalsCannotSilentlyOverflow() {RevenueTotals(1,Long.MAX_VALUE,0).plus(RevenueTotals(1,1,0))}
}
