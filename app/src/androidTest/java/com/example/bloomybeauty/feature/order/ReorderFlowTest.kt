package com.example.bloomybeauty.feature.order

import android.graphics.Bitmap
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
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.data.profile.ProfileRepository
import com.example.bloomybeauty.feature.app.*
import com.example.bloomybeauty.feature.auth.AuthViewModel
import com.example.bloomybeauty.feature.cart.CartViewModel
import com.example.bloomybeauty.feature.cart.CartStep
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ReorderFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var repositories: ShopRepositories
    private lateinit var auth: AuthViewModel
    private lateinit var factory: AuthViewModel.Factory
    private lateinit var old: CustomerOrder
    private var userId = 0L

    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB); helper = AuthDatabaseHelper(context, DB)
        val accounts = AuthRepository(helper)
        userId = (accounts.register("Khách mua lại", "reorder-ui@test.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
        val cart = CartRepository(helper); val orders = OrderRepository(helper)
        cart.add(userId, "cleanser"); cart.setQuantity(userId, "cleanser", 3)
        cart.add(userId, "lip_balm"); cart.saveShipping(userId, SHIPPING)
        val request = orders.prepare(userId)
        old = orders.cancel(userId, orders.create(userId, request).id)
        orders.acknowledge(userId, request.id)
        cart.add(userId, "toner")
        repositories = ShopRepositories(CatalogRepository(helper), cart, orders, ProfileRepository(helper), AdminRepository(helper))
        factory = AuthViewModel.Factory(accounts)
        compose.activityRule.scenario.onActivity(::install)
        compose.waitUntil(15000) { ::auth.isInitialized && auth.state.value.user?.id == userId }
        ready()
    }
    @After fun cleanup() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        helper.close(); context.deleteDatabase(DB)
    }
    private fun install(activity: ComponentActivity) {
        activity.enableEdgeToEdge()
        activity.setContent { val retained: AuthViewModel = viewModel(factory = factory); auth = retained
            BloomyBeautyTheme { BloomyApp(retained, repositories) }
        }
    }
    private fun cartVm(): CartViewModel {
        lateinit var model: CartViewModel
        compose.runOnUiThread { model = ViewModelProvider(auth, CartViewModel.Factory(repositories.cart, userId, "Khách mua lại", repositories.orders))["cart-$userId", CartViewModel::class.java] }
        return model
    }
    private fun ready() { val model = cartVm(); compose.waitUntil(15000) { !model.state.value.loading && !model.state.value.busy }; compose.waitForIdle() }
    private fun openOrder() {
        compose.onAllNodesWithText("Tài khoản").filter(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Đơn hàng của tôi").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText(old.code).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(old.code).performClick()
        compose.onNodeWithTag("reorder-open").performScrollTo().assertIsEnabled()
    }
    private fun preview() {
        compose.onNodeWithTag("reorder-open").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("reorder-preview").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun confirm() { compose.onNodeWithTag("reorder-confirm").performClick(); ready() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(context.getExternalFilesDir(null), "pixel6-reorder").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    @Test fun fullRouteMergesCartAndPlacesNewOrderWithOriginalHistoryIntact() = runBlocking {
        openOrder(); capture("01-order-action"); preview(); capture("02-preview")
        confirm()
        compose.waitUntil(15000) { cartVm().state.value.snapshot.quantity == 5 }
        assertEquals(CartStep.ITEMS, cartVm().state.value.step)
        compose.onNodeWithTag("reorder-result").assert(hasText("Đã thêm 4 sản phẩm", substring = true))
        capture("03-cart-merged")
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().performClick(); ready()
        compose.onNodeWithText("Lưu và kiểm tra thông tin").performScrollTo().performClick(); ready()
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().performClick(); ready()
        assertEquals(CartStep.SUCCESS, cartVm().state.value.step)
        assertEquals(2, repositories.orders.list(userId).size)
        assertEquals(old, repositories.orders.detail(userId, old.id))
        assertEquals(5, cartVm().state.value.placedOrder!!.lines.sumOf { it.quantity })
        capture("04-new-order")
    }

    @Test fun partialStockChangedPriceAndInactiveProductAreShownBeforeMerge() = runBlocking {
        helper.writableDatabase.execSQL("UPDATE products SET stock=2,price_vnd=200000 WHERE id='cleanser'")
        helper.writableDatabase.execSQL("UPDATE products SET active=0 WHERE id='lip_balm'")
        openOrder(); preview()
        compose.onNodeWithText("Yêu cầu 3 · Thêm 2 · Giỏ hiện có 0").assertExists()
        compose.onNodeWithText("Giá cũ", substring = true).assertExists()
        compose.onNodeWithText("Sản phẩm đã ngừng bán.").performScrollTo().assertIsDisplayed()
        capture("05-partial-preview"); confirm()
        compose.waitUntil(15000) { cartVm().state.value.snapshot.quantity == 3 }
        compose.onNodeWithTag("reorder-result").assert(hasText("bỏ qua 1 mặt hàng", substring = true))
        assertEquals(200000L, repositories.cart.load(userId).items.first { it.product.id == "cleanser" }.acceptedPriceVnd)
        capture("06-partial-result")
    }

    @Test fun dismissAndActivityRecreationNeverAutomaticallyAdd() = runBlocking {
        openOrder(); preview()
        compose.onNodeWithText("Hủy").performClick()
        assertEquals(1, repositories.cart.load(userId).quantity)
        preview()
        val retainedAuth = auth
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity(::install)
        ready()
        assertSame(retainedAuth, auth)
        compose.onNodeWithTag("reorder-confirm").assertExists()
        assertEquals(1, repositories.cart.load(userId).quantity)
        compose.onNodeWithTag("reorder-confirm").assertIsEnabled().performClick(); ready()
        compose.waitUntil(15000) { cartVm().state.value.snapshot.quantity == 5 }
        compose.activityRule.scenario.recreate(); compose.activityRule.scenario.onActivity(::install); ready()
        assertEquals(5, repositories.cart.load(userId).quantity)
        assertEquals(CartStep.ITEMS, cartVm().state.value.step)
    }

    @Test fun stalePreviewUpdatesAndRequiresSecondConfirmation() = runBlocking {
        openOrder(); preview()
        repositories.cart.add(userId, "cleanser")
        compose.onNodeWithTag("reorder-confirm").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText(reorderErrorText(ReorderError.CHANGED)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, repositories.cart.load(userId).quantity)
        compose.onNodeWithText("Yêu cầu 3 · Thêm 3 · Giỏ hiện có 1").assertExists()
        capture("07-stale-preview")
        confirm(); compose.waitUntil(15000) { cartVm().state.value.snapshot.quantity == 6 }
        assertEquals(6, repositories.cart.load(userId).quantity)
    }

    @Test fun allUnavailableDisablesConfirmation() = runBlocking {
        helper.writableDatabase.execSQL("UPDATE products SET stock=0 WHERE id IN ('cleanser','lip_balm')")
        openOrder(); preview()
        compose.onNodeWithTag("reorder-confirm").assertIsNotEnabled()
        compose.onNodeWithText("Không có sản phẩm nào có thể thêm vào giỏ.").performScrollTo().assertIsDisplayed()
        capture("08-unavailable")
        compose.onNodeWithText("Hủy").performClick()
        assertEquals(1, repositories.cart.load(userId).quantity)
    }

    @Test fun cartReviewDraftAndCouponAreClearedAfterMergeButShippingDraftIsKept() = runBlocking {
        val model = cartVm()
        compose.runOnUiThread { model.editCoupon("DEMO20"); model.editDraft(SHIPPING.copy(recipient = "Người nhận mới")); model.continueToShipping() }; ready()
        compose.runOnUiThread { model.saveShipping() }; ready()
        assertEquals(CartStep.REVIEW, model.state.value.step)
        openOrder(); preview(); confirm()
        compose.waitUntil(15000) { model.state.value.snapshot.quantity == 5 }
        assertEquals(CartStep.ITEMS, model.state.value.step)
        assertEquals("", model.state.value.couponText); assertNull(model.state.value.request)
        assertEquals("Người nhận mới", model.state.value.draft.recipient)
        assertNull(repositories.orders.recover(userId))
    }

    @Test fun committedCheckoutMustBeFinishedBeforeReordering() = runBlocking {
        val request = repositories.orders.prepare(userId)
        val committed = repositories.orders.create(userId, request)
        openOrder()
        compose.onNodeWithTag("reorder-open").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText(reorderErrorText(ReorderError.CHECKOUT_PENDING)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Kiểm tra giỏ hàng").performScrollTo().performClick(); ready()
        assertEquals(CartStep.SUCCESS, cartVm().state.value.step)
        assertEquals(committed.id, cartVm().state.value.placedOrder?.id)
        assertTrue(repositories.cart.load(userId).items.isEmpty())
    }

    companion object {
        private const val DB = "reorder-flow-test.db"
        private const val PASSWORD = "ReorderUi123!"
        private val SHIPPING = ShippingDetails("Khách mua lại", "0912345678", "123 Nguyễn Huệ, Thành phố Hồ Chí Minh")
    }
}
