package com.example.bloomybeauty.feature.notification

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.admin.AdminSchema
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.notification.NotificationRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class NotificationScreenTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var vm: NotificationViewModel
    private lateinit var auth: AuthRepository
    private val store=ViewModelStore()
    private var owner=0L
    private var orderId=0L
    private var opened: Long?=null
    @Before fun setup(): Unit=runBlocking {
        context.deleteDatabase(DB);helper=AuthDatabaseHelper(context,DB);auth=AuthRepository(helper)
        owner=(auth.register("Khách hộp thông báo","ui@inbox.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        val cart=CartRepository(helper);val orders=OrderRepository(helper)
        cart.add(owner,"cleanser");cart.saveShipping(owner,ShippingDetails("Nguyễn Ánh Hương","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
        orderId=orders.create(owner,orders.prepare(owner)).id
        val db=helper.writableDatabase;db.beginTransaction()
        try {repeat(34) {AdminSchema.record(db,orderId,owner,"PENDING","CONFIRMED")};db.setTransactionSuccessful()} finally {db.endTransaction()}
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge();vm=NotificationViewModel(NotificationRepository(helper),owner);store.put("inbox",vm)
        }
        compose.setContent {val state by vm.state.collectAsState();BloomyBeautyTheme {
            NotificationScreen(state,{},vm::reload,vm::more,vm::readAll) {id -> vm.open(id) {opened=it}}
        }}
        await()
    }
    @After fun cleanup() {compose.runOnUiThread {store.clear()};helper.close();context.deleteDatabase(DB)}
    private fun await() {compose.waitUntil(30000) {!vm.state.value.busy}}
    private fun scroll(text: String) {compose.onNodeWithTag("inbox-list").performScrollToNode(hasText(text))}
    private fun capture(name: String) {
        compose.waitForIdle();android.os.SystemClock.sleep(500)
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder=File(context.getExternalFilesDir(null),"pixel6-inbox-qa").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun pageReadAllAndOpenOperateWithoutChangingOrder(): Unit=runBlocking {
        compose.onNodeWithText("Thông báo (35)").assertIsDisplayed();capture("01-unread-inbox")
        assertEquals(30,vm.state.value.items.size)
        scroll("Xem thêm");compose.onNodeWithText("Xem thêm").performClick();await()
        assertEquals(35,vm.state.value.items.size);assertNull(vm.state.value.next)
        scroll("Đánh dấu tất cả đã đọc");compose.onNodeWithText("Đánh dấu tất cả đã đọc").performClick();await()
        assertEquals(0L,vm.state.value.unread);compose.onNodeWithText("Thông báo (0)").assertIsDisplayed()
        capture("02-all-read")
        compose.onAllNodesWithText("Đơn BB-${orderId.toString().padStart(6,'0')}")[0].performClick();await()
        assertEquals(orderId,opened)
        assertEquals("PENDING",OrderRepository(helper).detail(owner,orderId)!!.status)
    }
    @Test fun missingOrderShowsErrorAndLogoutClearsPrivateRows(): Unit=runBlocking {
        helper.writableDatabase.execSQL("DELETE FROM order_events");helper.writableDatabase.execSQL("DELETE FROM orders")
        compose.onAllNodesWithText("Đơn BB-${orderId.toString().padStart(6,'0')}")[0].performClick();await()
        assertNull(opened);assertEquals(35L,vm.state.value.unread)
        scroll("Đơn không còn tồn tại hoặc không thuộc tài khoản này.")
        compose.onNodeWithText("Đơn không còn tồn tại hoặc không thuộc tài khoản này.").assertIsDisplayed();capture("03-missing-target")
        auth.logout();compose.runOnUiThread {vm.reload()};await()
        assertTrue(vm.state.value.items.isEmpty());assertEquals(0L,vm.state.value.unread)
        compose.onNodeWithText("Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại.").assertIsDisplayed();capture("04-session-cleared")
    }
    companion object {private const val DB="notification-screen-test.db";private const val PASSWORD="InboxTest123!"}
}
