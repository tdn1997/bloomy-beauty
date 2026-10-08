package com.example.bloomybeauty.feature.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import android.os.SystemClock
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.admin.AdminRepository
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.CartRepository
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.data.profile.ProfileRepository
import com.example.bloomybeauty.feature.auth.AuthViewModel
import com.example.bloomybeauty.feature.cart.CartViewModel
import com.example.bloomybeauty.feature.cart.CartStep
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ShopRecreationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper:AuthDatabaseHelper
    private lateinit var auth:AuthViewModel
    private lateinit var repositories:ShopRepositories
    private lateinit var factory:AuthViewModel.Factory
    private var userId=0L
    private var fontScale=1f
    @Before fun setup()=runBlocking {
        context.deleteDatabase(DB);helper=AuthDatabaseHelper(context,DB)
        val accounts=AuthRepository(helper)
        userId=(accounts.register("Nguyễn Thị Mỹ","recreation@example.com",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        repositories=ShopRepositories(CatalogRepository(helper),CartRepository(helper),OrderRepository(helper),ProfileRepository(helper),AdminRepository(helper))
        factory=AuthViewModel.Factory(accounts)
        compose.activityRule.scenario.onActivity(::installContent)
        compose.waitUntil(10000){::auth.isInitialized && !auth.state.value.initializing && auth.state.value.user!=null}
        ready()
    }
    @After fun cleanup() {compose.runOnUiThread{compose.activity.viewModelStore.clear()};helper.close();context.deleteDatabase(DB)}
    private fun cart():CartViewModel {
        lateinit var vm:CartViewModel
        compose.runOnUiThread {vm=ViewModelProvider(auth,CartViewModel.Factory(repositories.cart,userId,"Nguyễn Thị Mỹ",repositories.orders))["cart-$userId",CartViewModel::class.java]}
        return vm
    }
    private fun ready() {val vm=cart();compose.waitUntil(10000){!vm.state.value.loading && !vm.state.value.busy};compose.waitForIdle()}
    private fun installContent(activity: ComponentActivity) {
        activity.enableEdgeToEdge()
        activity.setContent {
            val retained:AuthViewModel=viewModel(factory=factory)
            auth=retained
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BloomyBeautyTheme {BloomyApp(retained,repositories)}
            }
        }
    }
    private fun recreate() {
        compose.activityRule.scenario.recreate()
        // Use the identical composition call site so automatic saved-state keys match.
        compose.activityRule.scenario.onActivity(::installContent)
        ready()
        val title=when(cart().state.value.step) {
            CartStep.SHIPPING -> "Thông tin giao hàng"
            CartStep.REVIEW -> "Kiểm tra thông tin"
            CartStep.SUCCESS -> "Đặt hàng thành công"
            CartStep.ITEMS -> "Giỏ hàng"
        }
        // Compose idleness does not imply that the recreated native window is drawn.
        compose.waitUntil(10000) {
            try { compose.onAllNodesWithText(title).onFirst().assertIsDisplayed(); true }
            catch (_: AssertionError) { false }
        }
        SystemClock.sleep(250)
        val output=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if(output!=null) {
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if(bitmap!=null) {File(output,"recreated-${cart().state.value.step}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        }

    }
    @Test fun activityRecreationPreservesDraftReviewAndCommittedOrderWithoutDuplicates() {
        val retainedAuth=auth
        val retainedCart=cart()
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText("Gel bí đao rửa mặt"))
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
        compose.onNodeWithText("Thêm vào giỏ").performScrollTo().performClick();ready()
        compose.onNodeWithContentDescription("Mở giỏ hàng").performClick();ready()
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().performClick();ready()
        compose.onNodeWithText("Người nhận").performTextReplacement("Trần Thị Mỹ")
        compose.onNodeWithText("Số điện thoại").performTextInput("+84 912 345 678")
        compose.onNodeWithText("Địa chỉ nhận hàng").performTextInput("123 Nguyễn Huệ, Thành phố Hồ Chí Minh")
        recreate()
        assertSame(retainedAuth,auth);assertSame(retainedCart,cart())
        compose.onNodeWithText("Thông tin giao hàng").assertIsDisplayed()
        compose.onNodeWithText("Người nhận").assertTextContains("Trần Thị Mỹ")
        assertEquals("+84 912 345 678",cart().state.value.draft.phone)
        compose.onNodeWithText("Lưu và kiểm tra thông tin").performScrollTo().performClick();ready()
        val request=cart().state.value.request!!.id
        recreate()
        assertEquals(CartStep.REVIEW,cart().state.value.step);assertEquals(request,cart().state.value.request!!.id)
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().performClick();ready()
        val order=cart().state.value.placedOrder!!.id
        recreate()
        assertEquals(CartStep.SUCCESS,cart().state.value.step);assertEquals(order,cart().state.value.placedOrder!!.id)
        assertEquals(1,runBlocking{repositories.orders.list(userId)}.size)
        assertEquals(29,runBlocking{repositories.catalog.load(userId)}.products.first{it.id=="cleanser"}.stock)
    }
    @Test fun landscapeWithLargeFontKeepsShippingAndConfirmationReachable() {
        fontScale=1.5f
        compose.activityRule.scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE }
        compose.activityRule.scenario.onActivity(::installContent)
        ready()
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText("Gel bí đao rửa mặt"))
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
        compose.onNodeWithText("Thêm vào giỏ").performScrollTo().performClick();ready()
        compose.onNodeWithContentDescription("Mở giỏ hàng").performClick();ready()
        compose.onNodeWithTag("cart_items").performScrollToNode(hasText("Tiếp tục giao hàng"))
        compose.onNodeWithText("Tiếp tục giao hàng").performClick();ready()
        compose.onNodeWithText("Số điện thoại").performScrollTo().performTextReplacement("0912345678")
        compose.onNodeWithText("Địa chỉ nhận hàng").performScrollTo().performTextReplacement("123 Nguyễn Huệ, Thành phố Hồ Chí Minh")
        compose.onNodeWithText("Lưu và kiểm tra thông tin").performScrollTo().performClick();ready()
        assertEquals(CartStep.REVIEW,cart().state.value.step)
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().assertIsDisplayed().performClick();ready()
        assertEquals(CartStep.SUCCESS,cart().state.value.step)
        compose.onAllNodesWithText("Đặt hàng thành công").onFirst().assertIsDisplayed()
        val output=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if(output!=null) {
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if(bitmap!=null) {File(output,"landscape-font150.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        }
    }
    companion object {private const val DB="shop-recreation-test.db";private const val PASSWORD="StrongPassword123"}
}
