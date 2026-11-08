package com.example.bloomybeauty.data.statistics

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.feature.statistics.StatisticsViewModel
import com.example.bloomybeauty.fixtures.StatisticsFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class StatisticsRepositoryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var repository: StatisticsRepository
    private lateinit var auth: AuthRepository
    private var admin = 0L
    private var customer = 0L
    private val store = ViewModelStore()
    @Before fun setup(): Unit = runBlocking {
        context.deleteDatabase(DB);helper = AuthDatabaseHelper(context,DB);auth = AuthRepository(helper);repository = StatisticsRepository(helper)
        customer = (auth.register("Khách hàng","customer@statistics.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        admin = (auth.register("Quản trị","admin@statistics.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        helper.writableDatabase.execSQL("UPDATE users SET role='ADMIN' WHERE _id=?",arrayOf(admin))
        StatisticsFixture.seed(helper.writableDatabase,customer)
    }
    @After fun cleanup() {instrumentation.runOnMainSync {store.clear()};helper.close();context.deleteDatabase(DB)}
    private suspend fun error(expected: StatisticsError, block: suspend () -> Unit) {
        try {block();fail("Expected $expected")} catch(e: StatisticsException) {assertEquals(expected,e.error)}
    }
    @Test fun dateWindowUsesDeliveryForMoneyAndCreationForStatus() = runBlocking {
        val result = repository.load(admin,StatisticsFixture.period)
        assertEquals(RevenueTotals(2,270_000,30_000),result.displayedRevenue)
        assertEquals(135_000L,result.averageNetVnd)
        assertEquals(RevenueTotals(1,50_000,30_000),result.unknownDelivery)
        assertEquals(1,result.days.size);assertEquals(LocalDate.parse("2026-11-08"),result.days.single().date)
        assertEquals(result.displayedRevenue,result.days.single().totals)
        assertEquals(3L,result.statusesByCreation["DELIVERED"])
        assertEquals(7L,result.statusesByCreation.values.sum())
        assertEquals(listOf("cleanser","serum"),result.bestSellers.map {it.productId})
        assertEquals(listOf(2L,2L),result.bestSellers.map {it.quantity})
    }
    @Test fun allTimeIncludesUnknownButDoesNotPlotItOrUseCurrentProductPrices() = runBlocking {
        val result = repository.load(admin,null)
        assertEquals(RevenueTotals(4,970_000,60_000),result.knownDelivery)
        assertEquals(RevenueTotals(5,1_020_000,90_000),result.displayedRevenue)
        assertEquals(204_000L,result.averageNetVnd)
        assertEquals(listOf("2026-11-07","2026-11-08","2026-11-09"),result.days.map {it.date.toString()})
        assertEquals(listOf("serum","cleanser"),result.bestSellers.map {it.productId})
        assertEquals(listOf(10L,7L),result.bestSellers.map {it.quantity})
        assertEquals("Gel đã đổi tên",result.bestSellers.last().name)
        assertEquals(listOf("serum","cleanser"),result.lowStock.map {it.productId})
        assertEquals(listOf(0,5),result.lowStock.map {it.stock})
        assertEquals(9L,result.statusesByCreation.values.sum())
    }
    @Test fun customerAndWrongSessionCannotReadStatistics() = runBlocking {
        auth.login("customer@statistics.test",PASSWORD)
        error(StatisticsError.PERMISSION) {repository.load(customer,null)}
        error(StatisticsError.PERMISSION) {repository.load(admin,null)}
        auth.login("admin@statistics.test",PASSWORD)
        assertEquals(5L,repository.load(admin,null).displayedRevenue.count)
    }
    @Test fun revokedRoleClearsViewModelSnapshot() = runBlocking {
        lateinit var vm: StatisticsViewModel
        instrumentation.runOnMainSync {vm = StatisticsViewModel(repository,admin);store.put("stats",vm);vm.allTime()}
        withTimeout(10000) {vm.state.first {it.snapshot != null && !it.busy}}
        helper.writableDatabase.execSQL("UPDATE users SET role='CUSTOMER' WHERE _id=?",arrayOf(admin))
        instrumentation.runOnMainSync {vm.reload()}
        withTimeout(10000) {vm.state.first {it.error == StatisticsError.PERMISSION && !it.busy}}
        assertNull(vm.state.value.snapshot)
    }
    @Test fun emptyAndZeroValueReportsDoNotDivideByZero() = runBlocking {
        helper.writableDatabase.execSQL("DELETE FROM orders")
        val empty = repository.load(admin,null)
        assertEquals(RevenueTotals(),empty.displayedRevenue);assertNull(empty.averageNetVnd)
        assertTrue(empty.days.isEmpty());assertTrue(empty.bestSellers.isEmpty());assertTrue(empty.statusesByCreation.values.all {it == 0L})
        StatisticsFixture.order(helper.writableDatabase,customer,"DELIVERED",StatisticsFixture.period.startInclusive,
            StatisticsFixture.period.startInclusive,100_000,100_000,30_000)
        val free = repository.load(admin,StatisticsFixture.period)
        assertEquals(RevenueTotals(1,0,30_000),free.displayedRevenue);assertEquals(0L,free.averageNetVnd)
        assertEquals(0L,free.days.single().totals.netVnd)
    }
    @Test fun overflowIsReportedRatherThanWrappedAndValidLargeValueStillWorks() = runBlocking {
        helper.writableDatabase.execSQL("DELETE FROM orders")
        val period = StatisticsFixture.period
        StatisticsFixture.order(helper.writableDatabase,customer,"DELIVERED",period.startInclusive,period.startInclusive,Long.MAX_VALUE)
        StatisticsFixture.order(helper.writableDatabase,customer,"DELIVERED",period.startInclusive,period.endExclusive,Long.MAX_VALUE)
        error(StatisticsError.OVERFLOW) {repository.load(admin,null)}
        assertEquals(Long.MAX_VALUE,repository.load(admin,period).displayedRevenue.netVnd)
    }
    @Test fun corruptDiscountDoesNotBecomeNegativeRevenue() = runBlocking {
        helper.writableDatabase.execSQL("UPDATE orders SET discount_vnd=subtotal_vnd+1 WHERE _id=(SELECT MIN(_id) FROM orders)")
        error(StatisticsError.STORAGE) {repository.load(admin,null)}
    }
    companion object {private const val DB = "statistics-repository-test.db";private const val PASSWORD = "StatisticsTest123!"}
}
