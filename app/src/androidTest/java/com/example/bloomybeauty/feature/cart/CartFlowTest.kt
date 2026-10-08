package com.example.bloomybeauty.feature.cart

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.CartRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.feature.order.OrdersViewModel
import com.example.bloomybeauty.feature.order.OrdersScreen
import com.example.bloomybeauty.feature.profile.ProfileViewModel
import com.example.bloomybeauty.feature.profile.ProfileScreen
import com.example.bloomybeauty.data.profile.ProfileRepository
import com.example.bloomybeauty.feature.home.HomeScreen
import com.example.bloomybeauty.feature.home.HomeViewModel
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class CartFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var cart: CartViewModel
    private lateinit var home: HomeViewModel
    private lateinit var orders: OrdersViewModel
    private lateinit var profile: ProfileViewModel
    private lateinit var user: AuthUser
    private val store = ViewModelStore()

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(DATABASE)
        helper = AuthDatabaseHelper(context, DATABASE)
        user = (AuthRepository(helper).register("Nguyễn Thị Mỹ", "cartui@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            cart = CartViewModel(CartRepository(helper), user.id, user.name, OrderRepository(helper))
            home = HomeViewModel(CatalogRepository(helper), user.id)
            orders = OrdersViewModel(OrderRepository(helper), user.id)
            profile = ProfileViewModel(ProfileRepository(helper), user.id)
            store.put("cart", cart)
            store.put("home", home)
            store.put("orders", orders)
            store.put("profile", profile)
        }
        compose.setContent {
            var showCart by remember { mutableStateOf(false) }
            var showOrders by remember { mutableStateOf(false) }
            var showProfile by remember { mutableStateOf(false) }
            val state by cart.state.collectAsState()
            val homeState by home.state.collectAsState()
            val ordersState by orders.state.collectAsState()
            val profileState by profile.state.collectAsState()
            BloomyBeautyTheme {
                if (showProfile) ProfileScreen(profileState, { showProfile = false }, profile::edit,
                    { profile.save { updated -> user = updated; cart.refreshDefaultAddress(updated.name); showProfile = false } }, profile::reload)
                else if (showOrders) OrdersScreen(ordersState, { showOrders = false }, orders::reload,
                    { id -> orders.cancel(id) { home.reload(); cart.reload() } })
                else if (showCart) CartScreen(state,
                    { if (!cart.back()) showCart = false }, cart::setQuantity, cart::remove,
                    cart::acceptPrices, cart::continueToShipping, cart::editDraft, cart::saveShipping, cart::reload,
                    cart::placeOrder, { cart.finishOrder { showCart = false; showOrders = true; orders.reload(); home.reload() } })
                else HomeScreen(user, homeState, home::setQuery, home::setCategory, home::setSort,
                    home::toggleFavorite, home::reload, {}, cartCount = state.snapshot.quantity, cartBusy = state.busy,
                    onOpenCart = { showCart = true; cart.open() }, onAddToCart = cart::add,
                    onOpenOrders = { showOrders = true; orders.reload() },
                    onEditProfile = { showProfile = true; profile.reload() })
            }
        }
        compose.waitUntil(10_000) { !cart.state.value.loading && !home.state.value.loading }
    }

    @After
    fun tearDown() {
        compose.runOnUiThread { store.clear() }
        helper.close()
        context.deleteDatabase(DATABASE)
    }

    @Test
    fun addFromDetailChangeQuantitySaveShippingAndReturnWithoutLosingDraft() {
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText("Gel bí đao rửa mặt"))
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
        compose.onNodeWithText("Thêm vào giỏ").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Thêm vào giỏ").performClick()
        awaitCart()
        compose.onNodeWithContentDescription("Mở giỏ hàng").performClick()
        awaitCart()
        compose.onNodeWithTag("quantity_cleanser").assertTextEquals("2")
        compose.onNodeWithTag("increase_cleanser").performClick()
        awaitCart()
        compose.onNodeWithTag("quantity_cleanser").assertTextEquals("3")
        compose.onNodeWithTag("decrease_cleanser").performClick()
        awaitCart()
        compose.onNodeWithText("384.000 ₫").assertIsDisplayed()
        capture("cart-screen.png")
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Người nhận").performTextReplacement("Trần Thị Mỹ")
        compose.onNodeWithText("Số điện thoại").performTextInput("123")
        compose.onNodeWithText("Địa chỉ nhận hàng").performTextInput("12 Nguyễn Văn A, TP Hồ Chí Minh")
        compose.onNodeWithText("Lưu và kiểm tra thông tin").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Vui lòng nhập số di động Việt Nam hợp lệ.").assertIsDisplayed()
        compose.onNodeWithText("Số điện thoại").performScrollTo().performTextReplacement("+84 912 345 678")
        compose.onNodeWithText("Lưu và kiểm tra thông tin").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Thông tin giao hàng đã lưu").assertIsDisplayed()
        compose.onNodeWithText("Trần Thị Mỹ").assertIsDisplayed()
        compose.onNodeWithText("0912345678").assertIsDisplayed()
        capture("shipping-review.png")
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithText("Trần Thị Mỹ").assertIsDisplayed()
        compose.onNodeWithText("0912345678").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithTag("quantity_cleanser").assertTextEquals("2")
        compose.runOnIdle { assertEquals("Trần Thị Mỹ", cart.state.value.draft.recipient) }
    }

    @Test
    fun emptyAndChangedPriceBlockContinueUntilCorrected() {
        compose.onNodeWithContentDescription("Mở giỏ hàng").performClick()
        awaitCart()
        compose.onNodeWithText("Tiếp tục giao hàng").assertIsNotEnabled()
        compose.runOnUiThread { cart.add("cleanser") }
        awaitCart()
        helper.writableDatabase.execSQL("UPDATE products SET price_vnd = 200000 WHERE id = 'cleanser'")
        compose.runOnUiThread { cart.reload() }
        awaitCart()
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Xác nhận giá mới cho giỏ hàng").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("remove_cleanser").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Giỏ hàng đang trống. Hãy chọn sản phẩm ở trang chủ.").assertIsDisplayed()
        compose.onNodeWithText("Tiếp tục giao hàng").assertIsNotEnabled()
    }

    private fun awaitCart() { compose.waitUntil(10_000) { !cart.state.value.loading && !cart.state.value.busy }; compose.waitForIdle() }

    @Test
    fun placeCodAndViewOrderHistoryAndDetail() {
        openConfirmation()
        compose.onNodeWithText("Tổng thanh toán COD: 222.000 ₫").performScrollTo().assertIsDisplayed()
        capture("cod-confirmation.png")
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Mã đơn: BB-000001").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, cart.state.value.snapshot.quantity) }
        capture("order-success.png")
        compose.onNodeWithText("Xem đơn hàng của tôi").performScrollTo().performClick()
        awaitCart()
        compose.waitUntil(10_000) { !orders.state.value.loading }
        compose.onNodeWithText("BB-000001").assertIsDisplayed().performClick()
        compose.onNodeWithText("Tổng COD: 222.000 ₫").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0912345678").assertIsDisplayed()
        capture("order-detail.png")
        compose.runOnIdle { assertEquals(1, orders.state.value.orders.size) }
    }

    @Test
    fun changedPriceAtSubmitReturnsToCartAndCreatesNoOrder() {
        openConfirmation()
        helper.writableDatabase.execSQL("UPDATE products SET price_vnd = 200000 WHERE id = 'cleanser'")
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Giá sản phẩm đã thay đổi. Hãy xác nhận giá mới trong giỏ hàng.").assertIsDisplayed()
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().assertIsNotEnabled()
        assertEquals(0, runBlocking { OrderRepository(helper).list(user.id).size })
    }

    private fun openConfirmation() {
        compose.runOnUiThread { cart.add("cleanser") }
        awaitCart()
        compose.onNodeWithContentDescription("Mở giỏ hàng").performClick()
        awaitCart()
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Số điện thoại").performTextInput("0912345678")
        compose.onNodeWithText("Địa chỉ nhận hàng").performTextInput("12 Nguyễn Văn A, TP Hồ Chí Minh")
        compose.onNodeWithText("Lưu và kiểm tra thông tin").performScrollTo().performClick()
        awaitCart()
    }

    @Test
    fun editProfileValidatePhoneAndUseSavedAddressAtCheckout() {
        compose.onNodeWithText("Tài khoản").performClick()
        compose.onNodeWithText("Chỉnh sửa hồ sơ").performScrollTo().performClick()
        compose.waitUntil(10_000) { !profile.state.value.loading }
        compose.onNodeWithText("Họ tên tài khoản").performTextReplacement("Trần Thị Mỹ")
        compose.onNodeWithContentDescription("Lưu địa chỉ mặc định").performClick()
        compose.onNodeWithText("Người nhận mặc định").performTextReplacement("Trần Thị Mỹ")
        compose.onNodeWithText("Số điện thoại mặc định").performTextInput("123")
        compose.onNodeWithText("Địa chỉ mặc định").performTextInput("99 Nguyễn Văn B, TP Hồ Chí Minh")
        compose.onNodeWithText("Lưu hồ sơ").performScrollTo().performClick()
        compose.waitUntil(10_000) { !profile.state.value.busy }
        compose.onNodeWithText("Vui lòng nhập số di động Việt Nam hợp lệ.").assertIsDisplayed()
        compose.onNodeWithText("Số điện thoại mặc định").performScrollTo().performTextReplacement("+84 912 345 678")
        compose.onNodeWithText("Lưu hồ sơ").performScrollTo().performClick()
        compose.waitUntil(10_000) { !profile.state.value.busy }
        awaitCart()
        compose.runOnIdle { assertNull("Profile save failed: ${profile.state.value.error}", profile.state.value.error) }
        compose.onAllNodesWithText("Tài khoản").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Trần Thị Mỹ").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Chỉnh sửa hồ sơ").performScrollTo().performClick()
        compose.waitUntil(10_000) { !profile.state.value.loading }
        compose.onNodeWithText("0912345678").performScrollTo().assertIsDisplayed()
        capture("profile-screen.png")
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithText("Trang chủ").performClick()
        compose.runOnUiThread { cart.add("cleanser") }
        awaitCart()
        compose.onNodeWithContentDescription("Mở giỏ hàng").performClick()
        awaitCart()
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Trần Thị Mỹ").assertIsDisplayed()
        compose.onNodeWithText("0912345678").assertIsDisplayed()
        compose.onNodeWithText("99 Nguyễn Văn B, TP Hồ Chí Minh").assertIsDisplayed()
    }

    @Test
    fun confirmCancellationUpdatesStatusAndRestoresStock() {
        openPlacedOrder()
        compose.onNodeWithText("Hủy đơn").performScrollTo().performClick()
        compose.onNodeWithText("Giữ đơn").performClick()
        compose.runOnIdle { assertEquals("PENDING", orders.state.value.orders.single().status) }
        compose.onNodeWithText("Hủy đơn").performClick()
        compose.onNodeWithText("Xác nhận hủy").performClick()
        compose.waitUntil(10_000) { !orders.state.value.busy }
        compose.onNodeWithText("Trạng thái: Đã hủy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hủy đơn").assertDoesNotExist()
        assertEquals(30, runBlocking { CatalogRepository(helper).load(user.id).products.first { it.id == "cleanser" }.stock })
        capture("cancelled-order.png")
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithContentDescription("Mở giỏ hàng").performClick()
        awaitCart()
        compose.onNodeWithText("Giỏ hàng đang trống. Hãy chọn sản phẩm ở trang chủ.").assertIsDisplayed()
    }

    @Test
    fun statusChangedDuringCancellationDialogRefreshesAndBlocksCancellation() {
        openPlacedOrder()
        compose.onNodeWithText("Hủy đơn").performScrollTo().performClick()
        helper.writableDatabase.execSQL("UPDATE orders SET status = 'CONFIRMED' WHERE _id = 1")
        compose.onNodeWithText("Xác nhận hủy").performClick()
        compose.waitUntil(10_000) { !orders.state.value.busy }
        compose.onNodeWithText("Đơn đã được xử lý và không còn có thể hủy.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hủy đơn").assertDoesNotExist()
        compose.runOnIdle { assertEquals("CONFIRMED", orders.state.value.orders.single().status) }
        assertEquals(29, runBlocking { CatalogRepository(helper).load(user.id).products.first { it.id == "cleanser" }.stock })
    }

    private fun openPlacedOrder() {
        openConfirmation()
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().performClick()
        awaitCart()
        compose.onNodeWithText("Xem đơn hàng của tôi").performScrollTo().performClick()
        awaitCart()
        compose.waitUntil(10_000) { !orders.state.value.loading }
        compose.onNodeWithText("BB-000001").performClick()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(250)
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        File(directory).mkdirs()
        File(directory, name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    private companion object { const val DATABASE = "cart_ui_test.db"; const val PASSWORD = "password123" }
}
