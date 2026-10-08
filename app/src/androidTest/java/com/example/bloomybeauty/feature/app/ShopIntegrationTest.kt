package com.example.bloomybeauty.feature.app

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.admin.*
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.catalog.*
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.data.profile.*
import com.example.bloomybeauty.feature.auth.AuthViewModel
import com.example.bloomybeauty.feature.cart.*
import com.example.bloomybeauty.feature.admin.AdminViewModel
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ShopIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper:AuthDatabaseHelper
    private lateinit var auth:AuthViewModel
    private lateinit var repositories:ShopRepositories
    private lateinit var restoration:StateRestorationTester
    private val store=ViewModelStore()
    private var customerId=0L
    private var adminId=0L
    @Before fun setup()=runBlocking {
        context.deleteDatabase(DB);helper=AuthDatabaseHelper(context,DB)
        val accounts=AuthRepository(helper)
        adminId=(accounts.register("Quản trị thử nghiệm","admin@example.com",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        helper.writableDatabase.execSQL("UPDATE users SET role='ADMIN' WHERE _id=?",arrayOf(adminId))
        customerId=(accounts.register("Nguyễn Thị Mỹ","customer@example.com",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        repositories=ShopRepositories(CatalogRepository(helper),CartRepository(helper),OrderRepository(helper),ProfileRepository(helper),AdminRepository(helper))
        compose.runOnUiThread {compose.activity.enableEdgeToEdge();auth=AuthViewModel(accounts);store.put("auth",auth)}
        restoration=StateRestorationTester(compose)
        restoration.setContent {BloomyBeautyTheme {BloomyApp(auth,repositories)}}
        compose.waitUntil(10000){!auth.state.value.initializing && auth.state.value.user!=null}
        awaitCart()
    }
    @After fun cleanup() {compose.runOnUiThread{store.clear()};helper.close();context.deleteDatabase(DB)}
    private fun cart():CartViewModel {
        lateinit var result:CartViewModel
        val u=auth.state.value.user!!
        compose.runOnUiThread {result=ViewModelProvider(auth,CartViewModel.Factory(repositories.cart,u.id,u.name,repositories.orders))["cart-${u.id}",CartViewModel::class.java]}
        return result
    }
    private fun admin():AdminViewModel {
        lateinit var result:AdminViewModel
        compose.runOnUiThread {result=ViewModelProvider(auth,AdminViewModel.Factory(repositories.admin,adminId))["admin-$adminId",AdminViewModel::class.java]}
        return result
    }
    private fun awaitCart() {val vm=cart();compose.waitUntil(10000){!vm.state.value.loading && !vm.state.value.busy};compose.waitForIdle()}
    private fun awaitAdmin() {val vm=admin();compose.waitUntil(10000){!vm.state.value.busy && vm.state.value.snapshot!=null};assertNull(vm.state.value.error);compose.waitForIdle()}
    private fun account() {compose.onAllNodesWithText("Tài khoản").filter(hasClickAction()).onFirst().performClick()}
    private fun logout() {
        compose.onNodeWithText("Đăng xuất").performScrollTo().performClick()
        compose.waitUntil(10000){auth.state.value.user==null && !auth.state.value.busy}
    }
    private fun login(email:String) {
        compose.onNodeWithText("Email").performTextReplacement(email)
        compose.onNodeWithText("Mật khẩu").performTextReplacement(PASSWORD)
        compose.onNodeWithText("Đăng nhập").performScrollTo().performClick()
        compose.waitUntil(10000){auth.state.value.user!=null && !auth.state.value.busy}
        awaitCart()
    }
    private fun openProduct() {
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText("Gel bí đao rửa mặt"))
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
    }
    private fun addAndOpenCart() {
        openProduct();compose.onNodeWithText("Thêm vào giỏ").performScrollTo().performClick();awaitCart()
        compose.onNodeWithContentDescription("Mở giỏ hàng").performClick();awaitCart()
    }
    @Test fun customerPlacesAdminProcessesAndCustomerSeesDeliveredOrder() {
        addAndOpenCart()
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().performClick();awaitCart()
        compose.onNodeWithText("Số điện thoại").performTextInput("0912345678")
        compose.onNodeWithText("Địa chỉ nhận hàng").performTextInput("123 Nguyễn Huệ, Thành phố Hồ Chí Minh")
        compose.onNodeWithText("Lưu và kiểm tra thông tin").performScrollTo().performClick();awaitCart()
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().performClick();awaitCart()
        val placed=cart().state.value.placedOrder!!
        compose.onNodeWithText("Xem đơn hàng của tôi").performScrollTo().performClick();awaitCart()
        compose.onNodeWithText(placed.code).performClick()
        compose.onNodeWithText("Trạng thái: Chờ xác nhận").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        // Leaving the cart returns to the previously opened product.
        compose.onNodeWithContentDescription("Quay lại").performClick()
        account();compose.onNodeWithText("Quản trị cửa hàng").assertDoesNotExist();logout()
        login("admin@example.com");assertEquals(0,cart().state.value.snapshot.quantity)
        account();compose.onNodeWithText("Quản trị cửa hàng").performScrollTo().performClick();awaitAdmin()
        capture("admin-products.png")
        compose.onNodeWithText("Đơn hàng").performClick();compose.onNodeWithText(placed.code).performClick()
        capture("admin-order-detail.png")
        listOf("Đã xác nhận","Đang giao","Đã giao").forEach {label ->
            compose.onNodeWithText("Chuyển sang: $label").performScrollTo().performClick()
            compose.onNodeWithText("Xác nhận").performClick();awaitAdmin()
        }
        compose.onNodeWithText("Quay lại").performClick();compose.onNodeWithText("Quay lại").performClick()
        compose.onNodeWithText("Đăng xuất").performScrollTo().performClick()
        compose.waitUntil(10000){auth.state.value.user==null && !auth.state.value.busy}
        login("customer@example.com");account()
        compose.onNodeWithText("Đơn hàng của tôi").performScrollTo().performClick()
        compose.onNodeWithText(placed.code).performClick()
        compose.onNodeWithText("Trạng thái: Đã giao").assertIsDisplayed()
        compose.onNodeWithText("Hủy đơn").assertDoesNotExist()
        capture("customer-delivered-order.png")
        assertEquals(29,runBlocking{repositories.catalog.load(customerId)}.products.first{it.id=="cleanser"}.stock)
    }
    @Test fun returningFromCartAndProfilePreservesHomeAndLoginCreatesFreshModels() {
        val old=cart()
        addAndOpenCart()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithText("Chi tiết sản phẩm").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quay lại").performClick();account()
        compose.onNodeWithText("Chỉnh sửa hồ sơ").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithText("Đơn hàng của tôi").assertIsDisplayed()
        logout();login("admin@example.com")
        assertEquals(0,cart().state.value.snapshot.quantity)
        account();logout()
        // While the customer is logged out, an administrator edits its cart product.
        runBlocking {
            AuthRepository(helper).login("admin@example.com",PASSWORD)
            val p=repositories.admin.load(adminId).products.first{it.id=="cleanser"}
            repositories.admin.save(adminId,p.copy(priceVnd=200000),false)
            AuthRepository(helper).logout()
        }
        login("customer@example.com")
        assertNotSame(old,cart())
        assertEquals(1,cart().state.value.snapshot.quantity)
        assertTrue(cart().state.value.snapshot.items.single().priceChanged)
        openProduct();compose.onNodeWithContentDescription("Mở giỏ hàng").performClick();awaitCart()
        compose.onNodeWithText("Xác nhận giá mới cho giỏ hàng").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().assertIsNotEnabled()
    }
    @Test fun saveRestoreKeepsShippingDraftAndRouteWithoutSubmitting() {
        addAndOpenCart();compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().performClick();awaitCart()
        compose.onNodeWithText("Người nhận").performTextReplacement("Trần Thị Mỹ")
        compose.onNodeWithText("Số điện thoại").performTextInput("+84 912 345 678")
        compose.onNodeWithText("Địa chỉ nhận hàng").performTextInput("123 Nguyễn Huệ, Thành phố Hồ Chí Minh")
        restoration.emulateSavedInstanceStateRestore();awaitCart()
        compose.onNodeWithText("Thông tin giao hàng").assertIsDisplayed()
        compose.onNodeWithText("Người nhận").assertTextContains("Trần Thị Mỹ")
        assertEquals("+84 912 345 678",cart().state.value.draft.phone)
        assertTrue(runBlocking{repositories.orders.list(customerId)}.isEmpty())
        compose.onNodeWithText("Lưu và kiểm tra thông tin").performScrollTo().performClick();awaitCart()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        val vm=cart();compose.runOnUiThread {vm.reload()};awaitCart()
        compose.onNodeWithText("Thông tin giao hàng").assertIsDisplayed()
    }
    @Test fun savedAdminDraftStaysStaleAfterReloadAndRestore() {
        account();logout();login("admin@example.com");account()
        compose.onNodeWithText("Quản trị cửa hàng").performScrollTo().performClick();awaitAdmin()
        compose.onNodeWithText("Tìm sản phẩm").performTextInput("gel bi dao")
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
        compose.onNodeWithText("Tồn kho").performTextReplacement("500")
        runBlocking {
            val current=repositories.admin.load(adminId).products.first{it.id=="cleanser"}
            repositories.admin.save(adminId,current.copy(stock=20),false)
        }
        compose.onNodeWithText("Tải lại").performClick();awaitAdmin()
        restoration.emulateSavedInstanceStateRestore();awaitAdmin()
        compose.onNodeWithText("Lưu sản phẩm").performScrollTo().performClick()
        compose.waitUntil(10000){admin().state.value.error==AdminError.CONFLICT && !admin().state.value.busy}
        assertEquals(20,runBlocking{repositories.admin.load(adminId)}.products.first{it.id=="cleanser"}.stock)
    }
    private fun capture(name:String) {
        compose.waitForIdle();SystemClock.sleep(250)
        val dir=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        try {File(dir,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
    }
    companion object {private const val DB="shop-integration-test.db";private const val PASSWORD="StrongPassword123"}
}
