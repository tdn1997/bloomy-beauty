package com.example.bloomybeauty.feature.admin

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.admin.*
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class AdminFlowTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper:AuthDatabaseHelper
    private lateinit var vm:AdminViewModel
    private val store=ViewModelStore()
    private var userId=0L
    private var orderId=0L
    @Before fun setup()=runBlocking {
        context.deleteDatabase(DB);helper=AuthDatabaseHelper(context,DB)
        val auth=AuthRepository(helper)
        userId=(auth.register("Quản trị","admin@example.com",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        helper.writableDatabase.execSQL("UPDATE users SET role='ADMIN' WHERE _id=?",arrayOf(userId))
        val cart=CartRepository(helper);val orders=OrderRepository(helper)
        cart.add(userId,"cleanser");cart.saveShipping(userId,ShippingDetails("Nguyễn Thị Mỹ","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
        orderId=orders.create(userId,orders.prepare(userId)).id
        compose.runOnUiThread {vm=AdminViewModel(AdminRepository(helper),userId);store.put("admin",vm);vm.reload()}
        compose.setContent {val state by vm.state.collectAsState();BloomyBeautyTheme {AdminScreen(state,{},vm::reload,vm::save,vm::transition)}}
        ready()
    }
    private fun ready() {compose.waitUntil(10000){!vm.state.value.busy};assertNull(vm.state.value.error);compose.waitForIdle()}
    @After fun cleanup() {compose.runOnUiThread{store.clear()};helper.close();context.deleteDatabase(DB)}
    @Test fun editProductRejectNegativePriceThenHide() {
        compose.onNodeWithText("Tìm sản phẩm").performTextInput("gel bi dao")
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
        compose.onNodeWithText("Tên sản phẩm").performTextReplacement("Gel dưỡng Nguyễn Thị Mỹ")
        compose.onNodeWithText("Giá (VND)").performTextReplacement("-1")
        compose.onNodeWithText("Lưu sản phẩm").performScrollTo().performClick()
        compose.onNodeWithText(adminErrorText(AdminError.INVALID_PRODUCT)).assertExists()
        compose.onNodeWithText("Giá (VND)").performScrollTo().performTextReplacement("200000")
        compose.onNodeWithText("Hiển thị để bán").performScrollTo()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Lưu sản phẩm").performScrollTo().performClick();ready()
        assertEquals("Gel dưỡng Nguyễn Thị Mỹ",vm.state.value.snapshot!!.products.first{it.id=="cleanser"}.name)
        assertFalse(vm.state.value.snapshot!!.products.first{it.id=="cleanser"}.active)
    }
    @Test fun confirmProcessAndFilterOrders() {
        compose.onNodeWithText("Đơn hàng").performClick()
        val code=vm.state.value.snapshot!!.orders.single().code
        compose.onNodeWithText(code).performClick()
        compose.onNodeWithText("Chuyển sang: Đã xác nhận").performScrollTo().performClick()
        compose.onNodeWithText("Giữ nguyên").performClick()
        assertEquals("PENDING",vm.state.value.snapshot!!.orders.single().status)
        compose.onNodeWithText("Chuyển sang: Đã xác nhận").performScrollTo().performClick()
        compose.onNodeWithText("Xác nhận",useUnmergedTree=true).performClick();ready()
        compose.onNodeWithText("Chuyển sang: Đang giao").performScrollTo().performClick()
        compose.onNodeWithText("Xác nhận",useUnmergedTree=true).performClick();ready()
        compose.onNodeWithText("Hủy đơn hàng").assertDoesNotExist()
        compose.onNodeWithText("Chuyển sang: Đã giao").performScrollTo().performClick()
        compose.onNodeWithText("Xác nhận",useUnmergedTree=true).performClick();ready()
        assertEquals("DELIVERED",vm.state.value.snapshot!!.orders.single().status)
        compose.onNodeWithText("Quay lại").performClick()
        compose.onNodeWithText("Tất cả trạng thái").performClick()
        compose.onNodeWithText("Đã hủy").performClick()
        compose.onNodeWithText("Chưa có đơn hàng ở trạng thái này.").assertIsDisplayed()
    }
    @Test fun revokedPermissionHidesDataAndShowsRecoverableError() {
        helper.writableDatabase.execSQL("UPDATE users SET role='CUSTOMER' WHERE _id=?",arrayOf(userId))
        compose.onNodeWithText("Tải lại").performClick()
        compose.waitUntil(10000){!vm.state.value.busy && vm.state.value.error==AdminError.PERMISSION}
        compose.onNodeWithText(adminErrorText(AdminError.PERMISSION)).assertIsDisplayed()
        compose.onNodeWithText("Gel bí đao rửa mặt").assertDoesNotExist()
        assertNull(vm.state.value.snapshot)
    }
    companion object {private const val DB="admin-flow-test.db";private const val PASSWORD="StrongPassword123"}
}
