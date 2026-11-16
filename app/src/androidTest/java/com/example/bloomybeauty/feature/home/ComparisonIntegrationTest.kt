package com.example.bloomybeauty.feature.home

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.admin.AdminRepository
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.CartRepository
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.data.profile.ProfileRepository
import com.example.bloomybeauty.feature.app.*
import com.example.bloomybeauty.feature.auth.AuthViewModel
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ComparisonIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var auth: AuthViewModel
    private lateinit var repositories: ShopRepositories
    private val store = ViewModelStore()
    private var userId = 0L
    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB); helper = AuthDatabaseHelper(context, DB)
        val accounts = AuthRepository(helper)
        userId = (accounts.register("Khách so sánh", "compare-integration@test.com", "CompareTest123!", "CompareTest123!") as AuthResult.Success).user.id
        repositories = ShopRepositories(CatalogRepository(helper), CartRepository(helper), OrderRepository(helper), ProfileRepository(helper), AdminRepository(helper))
        compose.runOnUiThread { compose.activity.enableEdgeToEdge(); auth = AuthViewModel(accounts); store.put("auth", auth) }
        compose.setContent { BloomyBeautyTheme { BloomyApp(auth, repositories) } }
        compose.waitUntil(10000) { auth.state.value.user?.id == userId }
        lateinit var home: HomeViewModel
        compose.runOnUiThread { home = ViewModelProvider(auth, HomeViewModel.Factory(repositories.catalog, userId))["catalog-$userId", HomeViewModel::class.java] }
        compose.waitUntil(10000) { !home.state.value.loading }
    }
    @After fun cleanup() { compose.runOnUiThread { store.clear() }; helper.close(); context.deleteDatabase(DB) }
    private fun choose(id: String) {
        compose.onNodeWithTag("product_grid").performScrollToNode(hasTestTag("compare-select-$id"))
        compose.onNodeWithTag("compare-select-$id").performClick()
    }
    @Test fun comparisonAddsToRealCartAndSelectionSurvivesRootNavigation() = runBlocking {
        choose("cleanser"); choose("micellar")
        compose.onNodeWithTag("compare-open").performClick()
        compose.onNodeWithTag("compare-add-cleanser").performScrollTo().performClick()
        compose.waitUntil(10000) { runBlocking { repositories.cart.load(userId).quantity } == 1 }
        assertEquals("cleanser", repositories.cart.load(userId).items.single().product.id)
        assertTrue(repositories.orders.list(userId).isEmpty())
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithTag("product_grid").performScrollToNode(hasContentDescription("Mở giỏ hàng"))
        compose.onNodeWithContentDescription("Mở giỏ hàng").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("quantity_cleanser").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("quantity_cleanser").assertTextEquals("1")
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithTag("compare-open").assertIsEnabled().performClick()
        compose.onNodeWithText("So sánh sản phẩm (2/3)").assertIsDisplayed()
        assertEquals(1, repositories.cart.load(userId).quantity)
    }
    companion object { private const val DB = "comparison-integration-test.db" }
}
