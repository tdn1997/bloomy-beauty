package com.example.bloomybeauty.feature.statistics

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.statistics.*
import com.example.bloomybeauty.feature.home.formatPrice
import com.example.bloomybeauty.fixtures.StatisticsFixture
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.Clock
import java.time.Instant

class StatisticsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var vm: StatisticsViewModel
    private val store = ViewModelStore()
    private var customer = 0L
    private var admin = 0L
    @Before fun setup(): Unit = runBlocking {
        context.deleteDatabase(DB);helper = AuthDatabaseHelper(context,DB)
        val auth = AuthRepository(helper)
        customer = (auth.register("Khách thống kê","customer@stats-ui.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        admin = (auth.register("Quản trị","admin@stats-ui.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        helper.writableDatabase.execSQL("UPDATE users SET role='ADMIN' WHERE _id=?",arrayOf(admin))
        StatisticsFixture.seed(helper.writableDatabase,customer)
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            vm = StatisticsViewModel(StatisticsRepository(helper),admin,Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"),STATISTICS_ZONE))
            store.put("statistics",vm)
        }
        compose.setContent {val state by vm.state.collectAsState();BloomyBeautyTheme {
            StatisticsScreen(state,{},vm::reload,vm::editFrom,vm::editThrough,vm::apply,vm::recent,vm::allTime)
        }}
        compose.runOnUiThread {vm.reload()};await()
    }
    @After fun cleanup() {compose.runOnUiThread {store.clear()};helper.close();context.deleteDatabase(DB)}
    private fun await() {compose.waitUntil(30000) {!vm.state.value.busy}}
    private fun scroll(text: String) {compose.onNodeWithTag("statistics-list").performScrollToNode(hasText(text))}
    private fun capture(name: String) {
        compose.runOnUiThread {compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}
        compose.waitForIdle();android.os.SystemClock.sleep(500)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = java.io.File(context.getExternalFilesDir(null),"pixel6-statistics-qa").apply {mkdirs()}
        java.io.File(directory,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun dateFilterAllTimeUnknownBucketAndLowStockAreVisible() {
        compose.onNodeWithText("Từ ngày (yyyy-MM-dd)").performTextReplacement("2026-11-08")
        compose.onNodeWithText("Đến ngày (yyyy-MM-dd)").performTextReplacement("2026-11-08")
        compose.onNodeWithText("Áp dụng khoảng ngày").performClick();await()
        assertEquals(270_000L,vm.state.value.snapshot!!.displayedRevenue.netVnd)
        scroll(formatPrice(270_000));compose.onNodeWithText(formatPrice(270_000)).assertIsDisplayed()
        capture("01-doanh-thu-theo-ngay-giao")
        scroll("Số đơn theo ngày tạo")
        compose.onNodeWithText("Đã giao: 3").assertExists()
        capture("02-don-theo-ngay-tao")
        scroll("Toàn thời gian");compose.onNodeWithText("Toàn thời gian").performClick();await()
        assertEquals(1_020_000L,vm.state.value.snapshot!!.displayedRevenue.netVnd)
        scroll(formatPrice(1_020_000));capture("03-toan-thoi-gian")
        scroll("Chưa có ngày giao — toàn thời gian");capture("04-nhom-thieu-ngay-giao")
        scroll("Sắp hết hàng — hiện tại")
        compose.onNodeWithText("Gel đã đổi tên · còn 5").assertExists()
        capture("05-ton-kho-thap")
        scroll("Từ ngày (yyyy-MM-dd)")
        compose.onNodeWithText("Từ ngày (yyyy-MM-dd)").performTextReplacement("2026-11-09")
        compose.onNodeWithText("Đến ngày (yyyy-MM-dd)").performTextReplacement("2026-11-08")
        compose.onNodeWithText("Áp dụng khoảng ngày").performClick();await()
        assertEquals(StatisticsError.INVALID_RANGE,vm.state.value.error)
        assertNull(vm.state.value.snapshot!!.period)
    }
    @Test fun revokedAdminRoleRemovesCachedPrivateMetrics() {
        compose.runOnUiThread {vm.allTime()};await()
        assertNotNull(vm.state.value.snapshot)
        helper.writableDatabase.execSQL("UPDATE users SET role='CUSTOMER' WHERE _id=?",arrayOf(admin))
        compose.runOnUiThread {vm.reload()};await()
        assertEquals(StatisticsError.PERMISSION,vm.state.value.error);assertNull(vm.state.value.snapshot)
        compose.onNodeWithText(statisticsErrorText(StatisticsError.PERMISSION)).assertIsDisplayed()
        compose.onAllNodesWithText(formatPrice(1_020_000)).assertCountEquals(0)
        capture("06-thu-hoi-quyen")
    }
    @Test fun emptyAndFreeOrderChartsNeverDivideByZero() {
        helper.writableDatabase.execSQL("DELETE FROM orders")
        compose.runOnUiThread {vm.allTime()};await()
        assertNull(vm.state.value.snapshot!!.averageNetVnd)
        scroll("Chưa có đơn đã giao có ngày trong phạm vi này.")
        compose.onNodeWithText("Chưa có đơn đã giao có ngày trong phạm vi này.").assertIsDisplayed()
        val at = StatisticsFixture.period.startInclusive
        StatisticsFixture.order(helper.writableDatabase,customer,"DELIVERED",at,at,100_000,100_000,30_000)
        compose.runOnUiThread {vm.reload()};await()
        scroll("08/11/2026 · 1 đơn · ${formatPrice(0)}")
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(0f,0f..1f))).assertExists()
        assertEquals(0L,vm.state.value.snapshot!!.averageNetVnd)
        capture("07-bieu-do-gia-tri-khong")
    }
    companion object {private const val DB = "statistics-ui-test.db";private const val PASSWORD = "StatisticsTest123!"}
}
