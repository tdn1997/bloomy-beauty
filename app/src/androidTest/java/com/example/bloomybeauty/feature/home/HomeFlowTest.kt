package com.example.bloomybeauty.feature.home

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.AuthRepository
import com.example.bloomybeauty.data.auth.AuthResult
import com.example.bloomybeauty.data.auth.AuthUser
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var viewModel: HomeViewModel
    private lateinit var user: AuthUser
    private val store = ViewModelStore()
    private var loggedOut = false

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(TEST_DATABASE)
        helper = AuthDatabaseHelper(context, TEST_DATABASE)
        user = (AuthRepository(helper).register("Nguyễn Thị Mỹ", "home@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            viewModel = HomeViewModel(CatalogRepository(helper), user.id)
            store.put("home", viewModel)
        }
        compose.setContent {
            val state by viewModel.state.collectAsState()
            BloomyBeautyTheme {
                HomeScreen(user, state, viewModel::setQuery, viewModel::setCategory, viewModel::setSort,
                    viewModel::toggleFavorite, viewModel::reload, { loggedOut = true })
            }
        }
        compose.waitUntil(10_000) { !viewModel.state.value.loading }
    }

    @After
    fun tearDown() {
        compose.runOnUiThread { store.clear() }
        helper.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun searchOpenDetailFavoriteAndNavigateToAccount() {
        capture("home-screen.png")
        compose.onNodeWithText("Tìm sản phẩm, thương hiệu…").performTextInput("gel bi dao")
        compose.onNodeWithText("gel bi dao").performImeAction()
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText("Gel bí đao rửa mặt"))
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
        compose.onNodeWithText("Chi tiết sản phẩm").assertIsDisplayed()
        capture("product-detail.png")
        compose.onNodeWithContentDescription("Thêm Gel bí đao rửa mặt vào yêu thích").performClick()
        compose.waitUntil(10_000) { "cleanser" in viewModel.state.value.favoriteIds && !viewModel.state.value.savingFavorite }
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithText("Yêu thích").performClick()
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText("Gel bí đao rửa mặt"))
        compose.onNodeWithText("Gel bí đao rửa mặt").assertIsDisplayed()
        compose.onNodeWithText("Tài khoản").performClick()
        compose.onNodeWithText("Nguyễn Thị Mỹ").assertIsDisplayed()
        compose.onNodeWithText("Đăng xuất").performClick()
        compose.runOnIdle { assertTrue(loggedOut) }
    }

    @Test
    fun searchEmptyStateCanClearFilters() {
        compose.onNodeWithText("Tìm sản phẩm, thương hiệu…").performTextInput("khong tim thay")
        compose.onNodeWithText("khong tim thay").performImeAction()
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText("Không tìm thấy sản phẩm phù hợp."))
        compose.onNodeWithText("Không tìm thấy sản phẩm phù hợp.").assertIsDisplayed()
        compose.onNodeWithText("Xóa bộ lọc và tìm kiếm").performClick()
        compose.waitUntil(5_000) { viewModel.state.value.query.isEmpty() }
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText("8 sản phẩm"))
        compose.onNodeWithText("8 sản phẩm").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Semantics can update before SurfaceFlinger presents the rendered frame.
        SystemClock.sleep(250)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(directory).mkdirs()
        File(directory, name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    private companion object {
        const val TEST_DATABASE = "home_flow_test.db"
        const val PASSWORD = "password123"
    }
}
