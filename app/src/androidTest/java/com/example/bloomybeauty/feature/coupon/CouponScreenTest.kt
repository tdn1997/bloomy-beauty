package com.example.bloomybeauty.feature.coupon

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.coupon.*
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.feature.cart.*
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicLong

class CouponScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var orders: OrderRepository
    private lateinit var vm: CartViewModel
    private var userId = 0L
    private val clock = AtomicLong()
    private val store = ViewModelStore()
    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB);helper = AuthDatabaseHelper(context,DB)
        userId = (AuthRepository(helper).register("Khách ưu đãi","ui@coupon.test","CouponTest123!","CouponTest123!") as AuthResult.Success).user.id
        val cart = CartRepository(helper);cart.add(userId,"cleanser");cart.setQuantity(userId,"cleanser",3)
        cart.saveShipping(userId,ShippingDetails("Người nhận ưu đãi","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
        clock.set(System.currentTimeMillis())
        helper.writableDatabase.execSQL("INSERT OR REPLACE INTO coupon_rules VALUES('DEMO50K','FIXED',50000,500000,50000,?,?,1,0)",arrayOf(clock.get()-1000,clock.get()+86400000))
        helper.writableDatabase.execSQL("INSERT INTO coupon_rules VALUES('DEMOEXPIRE','PERCENT',20,200000,50000,?,?,1,0)",arrayOf(clock.get()-1000,clock.get()+60000))
        orders = OrderRepository(helper,coupons = LocalCouponRepository(true) {clock.get()})
        compose.runOnUiThread {compose.activity.enableEdgeToEdge();vm = CartViewModel(cart,userId,"Khách ưu đãi",orders);store.put("cart",vm)}
        compose.setContent {val state by vm.state.collectAsState();BloomyBeautyTheme {
            CartScreen(state,{vm.back()},vm::setQuantity,vm::remove,vm::acceptPrices,vm::continueToShipping,
                vm::editDraft,vm::saveShipping,vm::reload,vm::placeOrder,onCouponChange = vm::editCoupon,
                onApplyCoupon = vm::applyCoupon,onRemoveCoupon = vm::removeCoupon)
        }}
        await()
        compose.runOnUiThread {vm.continueToShipping()};await()
        compose.onNodeWithText("Lưu và kiểm tra thông tin").performScrollTo().performClick();await()
    }
    @After fun cleanup() {compose.runOnUiThread {store.clear()};helper.close();context.deleteDatabase(DB)}
    private fun await() {compose.waitUntil(10000) {!vm.state.value.loading && !vm.state.value.busy}}
    private fun apply(code: String) {
        compose.onNodeWithText("Mã giảm giá demo").performScrollTo().performTextReplacement(code)
        compose.onNodeWithText("Áp dụng").performScrollTo().performClick();await()
    }
    private fun capture(name: String) {
        compose.runOnUiThread {compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}
        compose.waitForIdle();android.os.SystemClock.sleep(500)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = java.io.File(context.getExternalFilesDir(null),"pixel6-coupon-qa").apply {mkdirs()}
        java.io.File(dir,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val state = vm.state.value
        java.io.File(dir,"$name.json").writeText(JSONObject().apply {
            put("repositoryNow",clock.get());put("deviceNow",System.currentTimeMillis());put("step",state.step.name)
            put("coupon",state.request?.coupon?.rule?.code ?: JSONObject.NULL);put("discountVnd",state.request?.discountVnd ?: 0)
            put("totalVnd",state.request?.totalVnd ?: 0);put("quoteInvalid",state.quoteInvalid)
        }.toString(2))
    }
    @Test fun expiredAfterReviewCannotPlaceUntilCustomerExplicitlyRemovesCode() {
        val before = vm.state.value.request!!.id
        apply(" demoexpire ")
        assertEquals("DEMOEXPIRE",vm.state.value.couponText)
        assertEquals(50_000L,vm.state.value.request!!.discountVnd)
        assertEquals(0L,vm.state.value.request!!.feeVnd)
        assertEquals(526_000L,vm.state.value.request!!.totalVnd)
        assertNotEquals(before,vm.state.value.request!!.id)
        compose.onNodeWithText("Mã đã áp dụng: DEMOEXPIRE").performScrollTo().assertIsDisplayed()
        capture("01-ap-ma-phan-tram")
        clock.set(vm.state.value.request!!.coupon!!.rule.endsAt)
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().performClick();await()
        assertEquals(CouponError.EXPIRED,vm.state.value.couponError!!.error)
        assertTrue(runBlocking {orders.list(userId).isEmpty()})
        compose.onNodeWithText("Xác nhận đặt hàng COD").assertIsNotEnabled()
        compose.onNodeWithText("Mã đã hết hạn.",substring = true).performScrollTo().assertIsDisplayed()
        capture("02-ma-het-han")
        val expired = vm.state.value.request!!.id
        compose.onNodeWithText("Không dùng mã").performScrollTo().performClick();await()
        assertFalse(vm.state.value.quoteInvalid);assertNull(vm.state.value.request!!.coupon)
        assertNotEquals(expired,vm.state.value.request!!.id);assertEquals(576_000L,vm.state.value.request!!.totalVnd)
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().assertIsEnabled()
        capture("03-chu-dong-bo-ma")
        compose.onNodeWithText("Xác nhận đặt hàng COD").performClick();await()
        assertEquals(0L,vm.state.value.placedOrder!!.discountVnd)
        assertEquals(1,runBlocking {orders.list(userId).size})
    }
    @Test fun fixedDiscountHistorySurvivesDeletedRuleAndReload() {
        apply("DEMO50K")
        assertEquals(50_000L,vm.state.value.request!!.discountVnd)
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().performClick();await()
        val order = vm.state.value.placedOrder!!
        assertEquals(526_000L,order.totalVnd);assertEquals("DEMO50K",order.couponCode)
        helper.writableDatabase.execSQL("DELETE FROM coupon_rules WHERE code='DEMO50K'")
        clock.set(Long.MAX_VALUE)
        compose.runOnUiThread {vm.reload()};await()
        assertEquals(order,vm.state.value.placedOrder)
        compose.onNodeWithText("Mã giảm giá demo: DEMO50K").performScrollTo().assertIsDisplayed()
        capture("04-don-luu-snapshot-giam-gia")
    }
    companion object {private const val DB = "coupon-ui-test.db"}
}
