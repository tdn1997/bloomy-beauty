package com.example.bloomybeauty.feature.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.AuthUser
import com.example.bloomybeauty.data.catalog.CatalogSeed
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ComparisonFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(HomeUiState(loading = false, categories = CatalogSeed.categories, products = CatalogSeed.products))
    private var user by mutableStateOf(AuthUser(1, "Khách so sánh", "compare@test.com"))
    private val added = mutableListOf<String>()
    private var retries = 0
    @Before fun setup() { compose.activityRule.scenario.onActivity(::install) }
    private fun install(activity: ComponentActivity) {
        activity.enableEdgeToEdge()
        activity.setContent { BloomyBeautyTheme {
            HomeScreen(user, state, { state = state.copy(query = it) }, {}, {}, {}, { retries++ }, {}, onAddToCart = { added.add(it) })
        } }
    }
    private fun choose(id: String) {
        compose.onNodeWithTag("product_grid").performScrollToNode(hasTestTag("compare-select-$id"))
        compose.onNodeWithTag("compare-select-$id").performClick()
    }
    private fun open() { compose.onNodeWithTag("compare-open").assertIsEnabled().performClick() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "pixel6-comparison").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun twoProductsShowTableAndCartCallbackAndDetailReturnWork() {
        choose("cleanser"); choose("micellar"); capture("01-selection"); open()
        compose.onNodeWithText("So sánh sản phẩm (2/3)").assertIsDisplayed()
        compose.onNodeWithText("192.000 ₫").assertExists()
        capture("02-table-two")
        compose.onNodeWithTag("compare-add-cleanser").performScrollTo().performClick()
        assertEquals(listOf("cleanser"), added)
        compose.onNodeWithTag("compare-detail-cleanser").performScrollTo().performClick()
        compose.onNodeWithTag("compare-detail-select").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithText("So sánh sản phẩm (2/3)").assertIsDisplayed()
    }
    @Test fun threeIsMaximumRemovingAndClearingLeaveUsableEmptyScreen() {
        choose("cleanser"); choose("micellar"); choose("serum")
        compose.onNodeWithTag("product_grid").performScrollToNode(hasTestTag("compare-select-toner"))
        compose.onNodeWithTag("compare-select-toner").assertIsNotEnabled()
        open()
        compose.onNodeWithTag("comparison-table").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("compare-remove-serum").assertIsDisplayed()
        capture("03-three-products")
        compose.onNodeWithTag("compare-remove-cleanser").performScrollTo().performClick()
        compose.onNodeWithText("So sánh sản phẩm (2/3)").assertExists()
        compose.onNodeWithText("Xóa tất cả").performClick()
        compose.onNodeWithText("Chưa có sản phẩm để so sánh.").assertIsDisplayed(); capture("04-empty")
        compose.onNodeWithText("Chọn sản phẩm").performClick()
        compose.onNodeWithTag("compare-open").assertDoesNotExist()
    }
    @Test fun selectionSurvivesSearchAndActivityRecreationButNotAccountSwitch() {
        choose("cleanser"); choose("micellar")
        compose.runOnUiThread { state = state.copy(query = "khong khop") }
        open()
        compose.onNodeWithText("So sánh sản phẩm (2/3)").assertExists()
        compose.activityRule.scenario.recreate(); compose.activityRule.scenario.onActivity(::install)
        compose.onNodeWithText("So sánh sản phẩm (2/3)").assertIsDisplayed()
        compose.runOnUiThread { user = AuthUser(2, "Khách khác", "other@test.com"); state = state.copy(query = "") }
        compose.onNodeWithTag("compare-open").assertDoesNotExist()
        compose.onNodeWithText("So sánh sản phẩm (2/3)").assertDoesNotExist()
    }
    @Test fun catalogRefreshUpdatesPriceAndRemovesInactiveWhileSoldOutCannotBeAdded() {
        choose("cleanser"); choose("micellar"); open()
        compose.runOnUiThread { state = state.copy(products = state.products.map { if (it.id == "cleanser") it.copy(stock = 0, priceVnd = 200000) else it }) }
        compose.onNodeWithText("200.000 ₫").assertExists()
        compose.onNodeWithTag("compare-add-cleanser").assertIsNotEnabled(); capture("05-current-price-sold-out")
        compose.runOnUiThread { state = state.copy(products = state.products.filter { it.id != "micellar" }) }
        compose.onNodeWithText("So sánh sản phẩm (1/3)").assertExists()
        compose.onNodeWithTag("compare-remove-micellar").assertDoesNotExist()
    }
    @Test fun detailSelectionIsSharedWithCardsAndNeedsTwoBeforeOpening() {
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText("Gel bí đao rửa mặt"))
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
        compose.onNodeWithTag("compare-detail-select").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithTag("compare-open").assertIsNotEnabled()
        choose("micellar"); open()
        compose.onNodeWithText("So sánh sản phẩm (2/3)").assertIsDisplayed()
    }
    @Test fun loadingAndErrorHideTableAndRetryIsAvailable() {
        choose("cleanser"); choose("micellar"); open()
        compose.onNodeWithContentDescription("Tải lại dữ liệu so sánh").performClick()
        assertEquals(1, retries)
        compose.runOnUiThread { state = state.copy(loading = true) }
        compose.onNodeWithTag("comparison-table").assertDoesNotExist()
        compose.runOnUiThread { state = state.copy(loading = false, error = true) }
        compose.onNodeWithText("Không thể tải dữ liệu so sánh.").assertIsDisplayed()
        compose.onNodeWithTag("comparison-table").assertDoesNotExist()
        compose.onNodeWithText("Thử lại").performClick(); assertEquals(2, retries)
    }
}
