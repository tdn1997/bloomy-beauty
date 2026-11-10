package com.example.bloomybeauty.feature.home

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
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.cart.CartRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.feature.cart.CartViewModel
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File

class FavoritesFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var home: HomeViewModel
    private lateinit var cart: CartViewModel
    private val store = ViewModelStore()
    private lateinit var user: AuthUser

    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB)
        helper = AuthDatabaseHelper(context, DB)
        user = (AuthRepository(helper).register("Nguyễn Thị Mỹ", "favorites-ui@bloomy.test", "password123", "password123") as AuthResult.Success).user
        helper.writableDatabase.execSQL("UPDATE products SET stock=0 WHERE id='serum'")
        val catalog = CatalogRepository(helper)
        catalog.toggleFavorite(user.id, "cleanser")
        catalog.toggleFavorite(user.id, "serum")
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            home = HomeViewModel(catalog, user.id)
            cart = CartViewModel(CartRepository(helper), user.id, user.name, OrderRepository(helper))
            store.put("home", home); store.put("cart", cart)
        }
        compose.setContent {
            val state by home.state.collectAsState()
            val cartState by cart.state.collectAsState()
            BloomyBeautyTheme {
                HomeScreen(user, state, home::setQuery, home::setCategory, home::setSort,
                    home::toggleFavorite, home::reload, {}, cartCount=cartState.snapshot.quantity,
                    cartBusy=cartState.busy, onAddToCart=cart::add)
            }
        }
        compose.waitUntil(10_000) { !home.state.value.loading && !cart.state.value.loading }
    }
    @After fun cleanup() {
        compose.runOnUiThread { store.clear() }; helper.close(); context.deleteDatabase(DB)
    }
    private fun scroll(text: String) {
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText(text))
    }
    private fun capture(name: String) {
        compose.waitForIdle(); SystemClock.sleep(350)
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(dir).mkdirs()
        File(dir, name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
    @Test fun accountEntryIndependentFiltersQuickCartAndDetailSync() {
        compose.onNodeWithText("Tìm sản phẩm, thương hiệu…").performTextInput("khong tim thay")
        compose.onNodeWithText("khong tim thay").performImeAction()
        compose.onNodeWithText("Tài khoản").performClick()
        compose.onNodeWithText("Sản phẩm yêu thích").performScrollTo().assertIsDisplayed()
        capture("01-tai-khoan.png")
        compose.onNodeWithText("Sản phẩm yêu thích").performClick()
        scroll("Gel bí đao rửa mặt")
        compose.onNodeWithText("Gel bí đao rửa mặt").assertIsDisplayed()
        compose.onNodeWithTag("favorite_add_cleanser").performClick()
        compose.waitUntil(10_000) { cart.state.value.snapshot.quantity == 1 && !cart.state.value.busy }
        Assert.assertTrue("cleanser" in home.state.value.favoriteIds)
        capture("02-yeu-thich-them-gio.png")
        scroll("Tinh chất bí đao N7")
        compose.onNodeWithTag("favorite_add_serum").assertIsNotEnabled()
        compose.onNodeWithText("Hết hàng").assertIsDisplayed()
        capture("03-het-hang.png")
        scroll("Gel bí đao rửa mặt")
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
        compose.onNodeWithContentDescription("Bỏ Gel bí đao rửa mặt khỏi yêu thích").assertIsDisplayed()
        capture("04-chi-tiet-da-luu.png")
        compose.onNodeWithContentDescription("Bỏ Gel bí đao rửa mặt khỏi yêu thích").performClick()
        compose.waitUntil(10_000) { "cleanser" !in home.state.value.favoriteIds && !home.state.value.savingFavorite }
        capture("05-chi-tiet-bo-luu.png")
        compose.onNodeWithContentDescription("Quay lại").performClick()
        scroll("Tinh chất bí đao N7")
        compose.onNodeWithContentDescription("Bỏ Tinh chất bí đao N7 khỏi yêu thích").performClick()
        compose.waitUntil(10_000) { home.state.value.favoriteIds.isEmpty() && !home.state.value.savingFavorite }
        scroll("Khám phá sản phẩm")
        capture("06-danh-sach-rong.png")
        compose.onNodeWithText("Khám phá sản phẩm").performClick()
        scroll("8 sản phẩm")
        compose.onNodeWithText("8 sản phẩm").assertIsDisplayed()
        Assert.assertEquals(1, runBlocking { CartRepository(helper).load(user.id).quantity })
    }
    @Test fun favoriteSearchCanResetWithoutLosingSavedItems() {
        compose.onNodeWithText("Yêu thích").performClick()
        compose.onNodeWithText("Tìm sản phẩm, thương hiệu…").performTextInput("khong khop")
        compose.onNodeWithText("khong khop").performImeAction()
        scroll("Không tìm thấy sản phẩm phù hợp.")
        capture("07-bo-loc-khong-khop.png")
        compose.onNodeWithText("Xóa bộ lọc và tìm kiếm").performClick()
        scroll("2 sản phẩm")
        compose.onNodeWithText("2 sản phẩm").assertIsDisplayed()
        Assert.assertEquals(2, home.state.value.favoriteIds.size)
        capture("08-khoi-phuc-danh-sach.png")
    }
    @Test fun saveFromProductCardAndDetailUpdatesFavoriteList() {
        runBlocking {
            val catalog = CatalogRepository(helper)
            catalog.toggleFavorite(user.id, "cleanser")
            catalog.toggleFavorite(user.id, "serum")
        }
        compose.runOnUiThread { home.reload() }
        compose.waitUntil(10_000) { !home.state.value.loading && home.state.value.favoriteIds.isEmpty() }
        scroll("Gel bí đao rửa mặt")
        compose.onNodeWithContentDescription("Thêm Gel bí đao rửa mặt vào yêu thích").performClick()
        compose.waitUntil(10_000) { !home.state.value.savingFavorite && "cleanser" in home.state.value.favoriteIds }
        capture("09-luu-tu-the-san-pham.png")
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
        compose.onNodeWithContentDescription("Bỏ Gel bí đao rửa mặt khỏi yêu thích").performClick()
        compose.waitUntil(10_000) { !home.state.value.savingFavorite && home.state.value.favoriteIds.isEmpty() }
        compose.onNodeWithContentDescription("Thêm Gel bí đao rửa mặt vào yêu thích").performClick()
        compose.waitUntil(10_000) { !home.state.value.savingFavorite && "cleanser" in home.state.value.favoriteIds }
        capture("10-luu-tu-chi-tiet.png")
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithText("Yêu thích").performClick()
        scroll("1 sản phẩm")
        compose.onNodeWithText("1 sản phẩm").assertIsDisplayed()
        capture("11-dong-bo-tab-yeu-thich.png")
    }
    companion object { const val DB = "favorites_flow_test.db" }
}
