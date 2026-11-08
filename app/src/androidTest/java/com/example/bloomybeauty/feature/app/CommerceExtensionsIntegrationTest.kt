package com.example.bloomybeauty.feature.app

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.address.AddressRepository
import com.example.bloomybeauty.data.admin.AdminRepository
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.notification.NotificationRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.data.profile.ProfileRepository
import com.example.bloomybeauty.data.review.ReviewRepository
import com.example.bloomybeauty.data.statistics.StatisticsRepository
import com.example.bloomybeauty.export.OrderPdfExporter
import com.example.bloomybeauty.feature.auth.AuthViewModel
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class CommerceExtensionsIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var auth: AuthViewModel
    private lateinit var repositories: ShopRepositories
    private val store=ViewModelStore()
    private var customer=0L
    private var admin=0L
    private var code=""
    private var inboxRequest by mutableStateOf<InboxOpenRequest?>(null)
    @Before fun setup(): Unit=runBlocking {
        context.deleteDatabase(DB);helper=AuthDatabaseHelper(context,DB)
        val accounts=AuthRepository(helper)
        admin=(accounts.register("Quản trị mở rộng","admin@commerce.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        helper.writableDatabase.execSQL("UPDATE users SET role='ADMIN' WHERE _id=?",arrayOf(admin))
        customer=(accounts.register("Nguyễn Ánh Hương","customer@commerce.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        val cart=CartRepository(helper);val orders=OrderRepository(helper)
        cart.add(customer,"cleanser");cart.saveShipping(customer,ShippingDetails("Nguyễn Ánh Hương","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
        val order=orders.create(customer,orders.prepare(customer));code=order.code
        accounts.login("admin@commerce.test",PASSWORD)
        val adminRepo=AdminRepository(helper)
        adminRepo.transition(admin,order.id,"PENDING","CONFIRMED");adminRepo.transition(admin,order.id,"CONFIRMED","SHIPPING");adminRepo.transition(admin,order.id,"SHIPPING","DELIVERED")
        accounts.login("customer@commerce.test",PASSWORD)
        repositories=ShopRepositories(CatalogRepository(helper),cart,orders,ProfileRepository(helper),adminRepo,
            reviews=ReviewRepository(helper),addresses=AddressRepository(helper),statistics=StatisticsRepository(helper),
            pdfExporter=OrderPdfExporter(context,helper),inbox=NotificationRepository(helper))
        compose.runOnUiThread {compose.activity.enableEdgeToEdge();auth=AuthViewModel(accounts);store.put("auth",auth)}
        compose.setContent {BloomyBeautyTheme {BloomyApp(auth,repositories,inboxOpenRequest=inboxRequest,onInboxOpenHandled={inboxRequest=null})}}
        compose.waitUntil(30000) {auth.state.value.user?.id==customer}
    }
    @After fun cleanup() {compose.runOnUiThread {store.clear()};repositories.pdfExporter?.clear();helper.close();context.deleteDatabase(DB)}
    private fun account() {compose.onAllNodesWithText("Tài khoản").filter(hasClickAction()).onFirst().performClick()}
    private fun capture(name: String) {
        compose.waitForIdle();android.os.SystemClock.sleep(500)
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder=File(context.getExternalFilesDir(null),"pixel6-commerce-integration-qa").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun homeBadgeInboxOrderPdfReviewAndAddressRoutesAreConnected() {
        compose.waitUntil(30000) {compose.onAllNodesWithContentDescription("Mở thông báo, 4 chưa đọc").fetchSemanticsNodes().isNotEmpty()}
        capture("01-home-badge")
        compose.onNodeWithContentDescription("Mở thông báo, 4 chưa đọc").performClick()
        compose.onNodeWithText("Thông báo (4)").assertIsDisplayed()
        compose.onAllNodesWithText("Đơn $code")[0].performClick()
        compose.waitUntil(30000) {compose.onAllNodesWithText("Trạng thái: Đã giao").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Chia sẻ PDF").performScrollTo().assertIsDisplayed()
        capture("02-inbox-to-order-pdf")
        compose.onNodeWithText("Đánh giá / sửa: Gel bí đao rửa mặt").performScrollTo().performClick()
        compose.onNodeWithTag("review-comment").assertExists()
        compose.onNodeWithText("Đóng").performClick()
        compose.onNodeWithContentDescription("Quay lại").performClick();compose.onNodeWithContentDescription("Quay lại").performClick()
        account();compose.onNodeWithText("Sổ địa chỉ").performScrollTo().performClick()
        compose.onNodeWithText("Sổ địa chỉ").assertIsDisplayed();capture("03-address-route")
    }
    @Test fun switchedAccountRejectsOldNotificationLinkAndAdminCanOpenStatistics(): Unit=runBlocking {
        val old=NotificationRepository(helper).page(customer).items.first()
        compose.runOnUiThread {auth.logout()};compose.waitUntil(30000) {auth.state.value.user==null && !auth.state.value.busy}
        compose.runOnUiThread {auth.submit("","admin@commerce.test",PASSWORD,"")}
        compose.waitUntil(30000) {auth.state.value.user?.id==admin && !auth.state.value.busy}
        compose.runOnUiThread {inboxRequest=InboxOpenRequest(customer,old.id)}
        compose.waitUntil(30000) {inboxRequest==null}
        assertTrue(NotificationRepository(helper).page(admin).items.isEmpty())
        account();compose.onNodeWithText("Quản trị cửa hàng").performScrollTo().performClick()
        compose.waitUntil(30000) {compose.onAllNodesWithText("Thống kê bán hàng").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Thống kê bán hàng").performClick()
        compose.waitUntil(30000) {compose.onAllNodesWithText("Doanh thu tiền hàng sau giảm giá").fetchSemanticsNodes().isNotEmpty()}
        capture("04-admin-statistics-route")
    }
    companion object {private const val DB="commerce-integration-test.db";private const val PASSWORD="CommerceTest123!"}
}
