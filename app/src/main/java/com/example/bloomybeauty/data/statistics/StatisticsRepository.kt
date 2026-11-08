package com.example.bloomybeauty.data.statistics

import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.TreeMap

class StatisticsRepository(private val helper: AuthDatabaseHelper) {
    suspend fun load(userId: Long, period: StatisticsPeriod?): StatisticsSnapshot = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT 1 FROM auth_session s JOIN users u ON u._id=s.user_id WHERE s._id=1 AND u._id=? AND u.role='ADMIN'",arrayOf("$userId")).use {
                if(!it.moveToFirst()) throw StatisticsException(StatisticsError.PERMISSION)
            }
            val bounds = period?.let {arrayOf(it.startInclusive.toString(),it.endExclusive.toString())}
            val createdWhere = if(bounds == null) "" else " WHERE created_at>=? AND created_at<?"
            val statuses = linkedMapOf("PENDING" to 0L,"CONFIRMED" to 0L,"SHIPPING" to 0L,"DELIVERED" to 0L,"CANCELLED" to 0L)
            db.rawQuery("SELECT status,COUNT(*) FROM orders$createdWhere GROUP BY status",bounds).use {c -> while(c.moveToNext()) statuses[c.getString(0)] = c.getLong(1)}
            val deliveryWhere = if(bounds == null) "" else " AND (delivered_at IS NULL OR (delivered_at>=? AND delivered_at<?))"
            var known = RevenueTotals();var unknown = RevenueTotals()
            val days = TreeMap<LocalDate,RevenueTotals>()
            // Stream only accounting columns into aggregates. No CustomerOrder/recipient data enters the dashboard.
            db.rawQuery("SELECT delivered_at,subtotal_vnd,discount_vnd,fee_vnd FROM orders WHERE status='DELIVERED'$deliveryWhere",bounds).use {c ->
                while(c.moveToNext()) {
                    val gross = c.getLong(1);val discount = c.getLong(2);val fee = c.getLong(3)
                    if(gross < 0 || discount !in 0..gross || fee < 0) throw StatisticsException(StatisticsError.STORAGE)
                    val amount = RevenueTotals(1,Math.subtractExact(gross,discount),fee)
                    if(c.isNull(0)) unknown = unknown.plus(amount)
                    else {
                        known = known.plus(amount)
                        val day = Instant.ofEpochMilli(c.getLong(0)).atZone(STATISTICS_ZONE).toLocalDate()
                        days[day] = (days[day] ?: RevenueTotals()).plus(amount)
                    }
                }
            }
            val productWhere = if(bounds == null) "" else " AND o.delivered_at>=? AND o.delivered_at<?"
            val sellers = db.rawQuery("""SELECT i.product_id,p.name,SUM(i.quantity) AS units FROM order_items i
                JOIN orders o ON o._id=i.order_id JOIN products p ON p.id=i.product_id
                WHERE o.status='DELIVERED'$productWhere GROUP BY i.product_id,p.name
                ORDER BY units DESC,i.product_id ASC LIMIT 10""",bounds).use {c -> buildList {
                while(c.moveToNext()) add(BestSeller(c.getString(0),c.getString(1),c.getLong(2)))
            }}
            val low = db.rawQuery("SELECT id,name,stock FROM products WHERE active=1 AND stock<=5 ORDER BY stock,id",null).use {c -> buildList {
                while(c.moveToNext()) add(StockAlert(c.getString(0),c.getString(1),c.getInt(2)))
            }}
            val displayed = if(period == null) known.plus(unknown) else known
            val snapshot = StatisticsSnapshot(period,statuses,displayed,known,unknown,
                if(displayed.count == 0L) null else displayed.netVnd/displayed.count,
                days.map {RevenueDay(it.key,it.value)},sellers,low)
            db.setTransactionSuccessful();snapshot
        } catch(e: ArithmeticException) {throw StatisticsException(StatisticsError.OVERFLOW)}
        catch(e: java.time.DateTimeException) {throw StatisticsException(StatisticsError.INVALID_RANGE)}
        finally {db.endTransaction()}
    }
}
