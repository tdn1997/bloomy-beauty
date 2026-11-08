package com.example.bloomybeauty.feature.review

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.data.review.*
import com.example.bloomybeauty.feature.order.*
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*

class ReviewScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var repository: ReviewRepository
    private lateinit var order: CustomerOrder
    private var userId = 0L
    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB); helper = AuthDatabaseHelper(context,DB); repository = ReviewRepository(helper)
        userId = (AuthRepository(helper).register("Khách đánh giá","ui@review.test","StrongPassword123","StrongPassword123") as AuthResult.Success).user.id
        val cart = CartRepository(helper); cart.add(userId,"cleanser")
        cart.saveShipping(userId,ShippingDetails("Người mua","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
        val orders = OrderRepository(helper); order = orders.create(userId,orders.prepare(userId))
        helper.writableDatabase.execSQL("UPDATE orders SET status='DELIVERED' WHERE _id=?",arrayOf(order.id))
        order = checkNotNull(orders.detail(userId,order.id))
    }
    @After fun cleanup() {helper.close();context.deleteDatabase(DB)}
    private fun count() = runBlocking {repository.list(userId,"cleanser").count}
    private fun capture(name: String) {
        compose.runOnUiThread {compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}
        compose.waitForIdle(); android.os.SystemClock.sleep(500)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = java.io.File(context.getExternalFilesDir(null),"pixel6-review-qa").apply {mkdirs()}
        java.io.File(directory,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun deliveredOrderToEditorThenProductReviewsAndDelete() {
        compose.runOnUiThread {compose.activity.enableEdgeToEdge()}
        compose.setContent {
            var product by remember {mutableStateOf(false)}
            BloomyBeautyTheme {Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TextButton({product = !product}) {Text(if(product) "Về đơn" else "Xem đánh giá sản phẩm")}
                if(product) Column(Modifier.padding(24.dp)) { ProductReviews(repository,userId,"cleanser") }
                else OrdersScreen(OrdersUiState(loading=false,orders=listOf(order)), {}, {}, reviews=repository,userId=userId)
            }}
        }
        compose.onNodeWithText(order.code).performClick()
        compose.onNodeWithText("Đánh giá / sửa:",substring=true).performScrollTo().performClick()
        capture("00-editor-open")
        try { compose.waitUntil(10000) {compose.onAllNodesWithTag("review-comment").fetchSemanticsNodes().any {it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()}} }
        catch(e: Exception) {
            capture("failure-load")
            compose.onAllNodes(isRoot(),useUnmergedTree=true).fetchSemanticsNodes().indices.forEach { i -> android.util.Log.i("ReviewUiTree",compose.onAllNodes(isRoot(),useUnmergedTree=true)[i].printToString()) }
            throw e
        }
        compose.onNodeWithContentDescription("4 sao").performClick()
        compose.onNodeWithTag("review-comment").performTextInput("Sản phẩm dịu nhẹ, dễ sử dụng")
        capture("01-viet-danh-gia")
        compose.onNodeWithText("Lưu đánh giá").performClick()
        compose.waitUntil(10000) {count() == 1}
        Assert.assertEquals(4,runBlocking {repository.own(userId,order.id,"cleanser")!!.rating})
        compose.waitUntil(10000) {compose.onAllNodesWithText("Lưu đánh giá").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithText("Xem đánh giá sản phẩm").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Sản phẩm dịu nhẹ, dễ sử dụng").fetchSemanticsNodes().isNotEmpty()}
        capture("02-danh-gia-san-pham")
        compose.onNodeWithText("Về đơn").performClick()
        compose.onNodeWithText(order.code).performClick()
        compose.onNodeWithText("Đánh giá / sửa:",substring=true).performScrollTo().performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Xóa đánh giá").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("review-comment").performTextReplacement("Đánh giá sau khi dùng lâu hơn")
        capture("03-sua-danh-gia")
        compose.onNodeWithText("Lưu đánh giá").performClick()
        try { compose.waitUntil(10000) {runBlocking {repository.own(userId,order.id,"cleanser")?.revision == 1L}} }
        catch (e: Exception) { capture("failure-save"); throw e }
        compose.waitUntil(10000) {compose.onAllNodesWithText("Lưu đánh giá").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithText("Đánh giá / sửa:",substring=true).performScrollTo().performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Xóa đánh giá").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Xóa đánh giá").performClick()
        capture("04-xac-nhan-xoa")
        compose.onNodeWithText("Xóa",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) {count() == 0}
    }
    @Test fun draftSurvivesActivityStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BloomyBeautyTheme { ReviewEditor(repository,userId,order.id,"cleanser",{}) } }
        compose.waitUntil(10000) {compose.onAllNodesWithTag("review-comment").fetchSemanticsNodes().any {it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()}}
        compose.onNodeWithTag("review-comment").performTextInput("Bản nháp giữ sau xoay màn hình")
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("review-comment").fetchSemanticsNodes().any {it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()}}
        compose.onNodeWithTag("review-comment").assertTextContains("Bản nháp giữ sau xoay màn hình")
        compose.onNodeWithText("Lưu đánh giá").performClick()
        compose.waitUntil(10000) {count() == 1}
        Assert.assertEquals("Bản nháp giữ sau xoay màn hình", runBlocking {repository.own(userId,order.id,"cleanser")!!.comment})
    }
    @Test fun staleRestoredDraftCannotDeleteNewerReview() {
        runBlocking { repository.save(userId,order.id,"cleanser",5,"Nhận xét ban đầu",null) }
        val initial = runBlocking {repository.own(userId,order.id,"cleanser")!!}
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BloomyBeautyTheme { ReviewEditor(repository,userId,order.id,"cleanser",{}) } }
        compose.waitUntil(10000) {compose.onAllNodesWithTag("review-comment").fetchSemanticsNodes().any {it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()}}
        compose.onNodeWithTag("review-comment").performTextReplacement("Bản nháp còn giữ")
        runBlocking {repository.save(userId,order.id,"cleanser",4,"Nội dung mới hơn",initial.token)}
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("review-comment").fetchSemanticsNodes().any {it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()}}
        compose.onNodeWithTag("review-comment").assertTextContains("Bản nháp còn giữ")
        compose.onNodeWithText("Xóa đánh giá").performClick()
        compose.onNodeWithText("Xóa",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Đánh giá đã thay đổi hoặc không thuộc tài khoản này.").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(1,count())
        Assert.assertEquals("Nội dung mới hơn",runBlocking {repository.own(userId,order.id,"cleanser")!!.comment})
        capture("05-chan-xoa-ban-nhap-cu")
    }
    companion object {private const val DB="review-ui-test.db"}
}
