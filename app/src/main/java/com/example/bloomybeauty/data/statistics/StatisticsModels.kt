package com.example.bloomybeauty.data.statistics

import java.time.LocalDate
import java.time.ZoneId

val STATISTICS_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
data class StatisticsPeriod(val from: LocalDate, val through: LocalDate) {
    init {require(!through.isBefore(from))}
    val startInclusive: Long get() = from.atStartOfDay(STATISTICS_ZONE).toInstant().toEpochMilli()
    val endExclusive: Long get() = through.plusDays(1).atStartOfDay(STATISTICS_ZONE).toInstant().toEpochMilli()
    companion object {fun parse(from: String, through: String) = StatisticsPeriod(LocalDate.parse(from.trim()),LocalDate.parse(through.trim()))}
}
data class RevenueTotals(val count: Long = 0, val netVnd: Long = 0, val shippingVnd: Long = 0) {
    internal fun plus(other: RevenueTotals) = RevenueTotals(Math.addExact(count,other.count),Math.addExact(netVnd,other.netVnd),Math.addExact(shippingVnd,other.shippingVnd))
}
data class RevenueDay(val date: LocalDate, val totals: RevenueTotals)
data class BestSeller(val productId: String, val name: String, val quantity: Long)
data class StockAlert(val productId: String, val name: String, val stock: Int)
data class StatisticsSnapshot(val period: StatisticsPeriod?, val statusesByCreation: Map<String,Long>,
    val displayedRevenue: RevenueTotals, val knownDelivery: RevenueTotals, val unknownDelivery: RevenueTotals,
    val averageNetVnd: Long?, val days: List<RevenueDay>, val bestSellers: List<BestSeller>, val lowStock: List<StockAlert>)
enum class StatisticsError { PERMISSION, INVALID_RANGE, OVERFLOW, STORAGE }
class StatisticsException(val error: StatisticsError) : Exception(error.name)
