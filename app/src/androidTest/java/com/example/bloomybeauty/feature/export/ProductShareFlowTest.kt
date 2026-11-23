package com.example.bloomybeauty.feature.export

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import android.accessibilityservice.AccessibilityService
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.admin.AdminRepository
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.CartRepository
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.data.profile.ProfileRepository
import com.example.bloomybeauty.export.ProductImageExporter
import com.example.bloomybeauty.feature.app.*
import com.example.bloomybeauty.feature.auth.AuthViewModel
import com.example.bloomybeauty.feature.home.HomeViewModel
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ProductShareFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var exporter: ProductImageExporter
    private lateinit var auth: AuthViewModel
    private lateinit var repositories: ShopRepositories
    private val store = ViewModelStore()
    private var userId = 0L
    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB); helper = AuthDatabaseHelper(context, DB)
        exporter = ProductImageExporter(context, helper); exporter.clear()
        val accounts = AuthRepository(helper, onSessionChanged = { exporter.clear() })
        userId = (accounts.register("Khách chia sẻ", "share-flow@test.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
        repositories = ShopRepositories(CatalogRepository(helper), CartRepository(helper), OrderRepository(helper), ProfileRepository(helper), AdminRepository(helper), productImageExporter = exporter)
        compose.runOnUiThread { auth = AuthViewModel(accounts); store.put("auth", auth) }
        compose.activityRule.scenario.onActivity(::install)
        compose.waitUntil(10000) { auth.state.value.user?.id == userId }
        lateinit var home: HomeViewModel
        compose.runOnUiThread { home = ViewModelProvider(auth, HomeViewModel.Factory(repositories.catalog, userId))["catalog-$userId", HomeViewModel::class.java] }
        compose.waitUntil(10000) { !home.state.value.loading }
    }
    @After fun cleanup() { compose.runOnUiThread { store.clear() }; exporter.clear(); helper.close(); context.deleteDatabase(DB) }
    private fun install(activity: ComponentActivity) {
        activity.enableEdgeToEdge()
        activity.setContent { BloomyBeautyTheme { BloomyApp(auth, repositories) } }
    }
    private fun openProduct() {
        compose.onNodeWithTag("product_grid").performScrollToNode(hasText("Gel bí đao rửa mặt"))
        compose.onNodeWithText("Gel bí đao rửa mặt").performClick()
        compose.onNodeWithTag("product-share-create").performScrollTo().assertIsDisplayed()
    }
    private fun preview() {
        compose.onNodeWithTag("product-share-create").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("product-share-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("product-share-confirm").assertIsEnabled()
    }
    private fun files() = File(context.cacheDir, "product-images").listFiles().orEmpty().filter { it.isFile }
    private fun capture(name: String) {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(context.getExternalFilesDir(null), "pixel6-product-share").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun previewThenNativeSharesheetCanBeOpenedAndCancelledWithoutChangingCommerce() = runBlocking {
        openProduct(); capture("04-detail-action"); preview(); capture("05-preview")
        assertEquals(1, files().size)
        compose.onNodeWithTag("product-share-confirm").performClick()
        compose.waitUntil(15000) {
            val owner = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString().orEmpty()
            owner == "com.android.intentresolver" || owner == "android"
        }
        instrumentation.uiAutomation.waitForIdle(500, 10000)
        capture("06-android-sharesheet")
        assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(10000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName }
        compose.onNodeWithTag("product-share-create").performScrollTo().assertIsDisplayed()
        assertEquals(1, files().size) // Keep the granted file for asynchronous receivers.
        assertTrue(repositories.cart.load(userId).items.isEmpty())
        assertTrue(repositories.orders.list(userId).isEmpty())
    }
    @Test fun cancellingPreviewDeletesTemporaryImageAndAllowsCreatingAgain() {
        openProduct(); preview(); compose.onNodeWithText("Hủy").performClick()
        assertTrue(files().isEmpty())
        preview(); assertEquals(1, files().size)
        compose.onNodeWithText("Hủy").performClick(); assertTrue(files().isEmpty())
    }
    @Test fun changedPriceWhilePreviewingRequiresNewImageAndDoesNotOpenChooser() {
        openProduct(); preview()
        helper.writableDatabase.execSQL("UPDATE products SET price_vnd=200000,revision=revision+1 WHERE id='cleanser'")
        compose.onNodeWithTag("product-share-confirm").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("product-share-preview").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Thông tin sản phẩm đã thay đổi. Hãy tạo lại ảnh để chia sẻ.").performScrollTo().assertIsDisplayed()
        assertTrue(files().isEmpty()); capture("07-changed-product")
    }
    @Test fun recreationClosesUnsharedPreviewWithoutAutomaticallySharing() {
        openProduct(); preview()
        compose.activityRule.scenario.recreate(); compose.activityRule.scenario.onActivity(::install)
        compose.onNodeWithTag("product-share-preview").assertDoesNotExist()
        compose.onNodeWithTag("product-share-create").performScrollTo().assertIsDisplayed()
        assertTrue(files().isEmpty())
    }
    companion object { private const val DB = "product-share-flow-test.db"; private const val PASSWORD = "ProductShareFlow123!" }
}
