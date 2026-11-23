package com.example.bloomybeauty.feature.home

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.AuthRepository
import com.example.bloomybeauty.data.auth.AuthResult
import com.example.bloomybeauty.data.auth.AuthUser
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.catalog.SearchAiRanker
import com.example.bloomybeauty.data.catalog.SearchCandidate
import com.example.bloomybeauty.data.catalog.SearchGoal
import com.example.bloomybeauty.data.catalog.SearchReason
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
    private var searchAiRanker by mutableStateOf<SearchAiRanker?>(null)

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
                    viewModel::toggleFavorite, viewModel::reload, { loggedOut = true },
                    searchAiRanker = searchAiRanker)
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
        compose.onNodeWithText("Đăng xuất").performScrollTo().performClick()
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

    @Test
    fun recommendationsUpdateAfterSavingFavoriteAndRelatedDetailsReturnToTheirSource() {
        val grid = compose.onNodeWithTag("product_grid")
        grid.performScrollToNode(hasText("Gợi ý cho bạn"))
        compose.onNodeWithText("Gợi ý cho bạn").performClick()
        compose.onNodeWithText("Lưu sản phẩm yêu thích để nhận gợi ý phù hợp hơn.").assertIsDisplayed()
        compose.onNodeWithTag("recommendation-cleanser").performClick()
        compose.onNodeWithContentDescription("Thêm Gel bí đao rửa mặt vào yêu thích").performClick()
        compose.waitUntil(10_000) {
            "cleanser" in viewModel.state.value.favoriteIds && !viewModel.state.value.savingFavorite
        }

        compose.onNodeWithText("Sản phẩm tương tự").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recommendation-micellar").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Thêm Nước tẩy trang bí đao vào yêu thích").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithContentDescription("Bỏ Gel bí đao rửa mặt khỏi yêu thích").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quay lại").performClick()

        compose.onNodeWithText("Gợi ý cho bạn").assertIsDisplayed()
        compose.onNodeWithTag("recommendation-cleanser").assertDoesNotExist()
        compose.onNodeWithTag("recommendation-micellar")
            .assertTextContains("Dựa trên mục yêu thích: Gel bí đao rửa mặt")
        compose.onNodeWithContentDescription("Quay lại").performClick()
        grid.assertIsDisplayed()
    }

    @Test
    fun searchShowsOfflineSuggestionsAndOpensProductWhileTyping() {
        compose.onNodeWithText("Tìm sản phẩm, thương hiệu…").performTextInput("duong moi")
        compose.onNodeWithTag("search-suggestion-lip_balm").assertIsDisplayed().performClick()
        compose.onNodeWithText("Chi tiết sản phẩm").assertIsDisplayed()
        compose.onNodeWithText("Son dưỡng dầu dừa Bến Tre").assertExists()
    }

    @Test
    fun aiInSearchRunsOnDemandAndFallsBackWhenProviderFails() {
        val calls = mutableListOf<SearchGoal>()
        compose.runOnIdle {
            searchAiRanker = SearchAiRanker { goal, candidates ->
                calls.add(goal)
                check(candidates.all { it.productId in setOf("cleanser", "micellar") })
                if (calls.size == 1) throw IllegalStateException("quota")
                listOf(SearchCandidate("micellar", SearchReason.MAKEUP_REMOVER))
            }
        }
        compose.onNodeWithText("Tìm sản phẩm, thương hiệu…").performTextInput("lam sach da mat")
        compose.onNodeWithTag("search-suggestion-cleanser").assertIsDisplayed()
        compose.runOnIdle { assertTrue(calls.isEmpty()) }
        compose.onNodeWithText("Gợi ý bằng AI").performClick()
        compose.onNodeWithText("AI chưa khả dụng. Đang hiển thị gợi ý ngoại tuyến.").assertIsDisplayed()
        compose.onNodeWithTag("search-suggestion-cleanser").assertExists()
        compose.onNodeWithText("Gợi ý bằng AI").performClick()
        compose.onNodeWithText("Gợi ý từ AI").assertIsDisplayed()
        compose.onNodeWithTag("search-suggestion-micellar").assertTextContains("Nước tẩy trang")
        compose.onNodeWithTag("search-suggestion-cleanser").assertDoesNotExist()
        compose.runOnIdle { assertEquals(List(2) { SearchGoal.FACE_CLEANSING }, calls) }
    }

    @Test
    fun changingQueryDiscardsAnInFlightAiResponse() {
        val response = CompletableDeferred<List<SearchCandidate>>()
        compose.runOnIdle { searchAiRanker = SearchAiRanker { _, _ -> response.await() } }
        compose.onNodeWithText("Tìm sản phẩm, thương hiệu…").performTextInput("lam sach da mat")
        compose.onNodeWithText("Gợi ý bằng AI").performClick()
        compose.onNodeWithText("lam sach da mat").performTextReplacement("duong moi")
        response.complete(listOf(SearchCandidate("micellar", SearchReason.MAKEUP_REMOVER)))
        compose.onNodeWithTag("search-suggestion-lip_balm").assertIsDisplayed()
        compose.onNodeWithTag("search-suggestion-micellar").assertDoesNotExist()
        compose.onNodeWithText("Gợi ý từ AI").assertDoesNotExist()
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
