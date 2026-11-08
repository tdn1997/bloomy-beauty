package com.example.bloomybeauty.export

import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.feature.export.OrderPdfActions
import com.example.bloomybeauty.fixtures.StatisticsFixture
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class OrderPdfScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var exporter: OrderPdfExporter
    private var owner = 0L
    private var id = 0L
    @Before fun setup(): Unit = runBlocking {
        context.deleteDatabase(DB);helper = AuthDatabaseHelper(context,DB)
        owner = (AuthRepository(helper).register("Nguyễn Ánh Hương","reader@pdf.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        id = StatisticsFixture.order(helper.writableDatabase,owner,"PENDING",1794106800000,null,200_000,20_000,30_000)
        exporter = OrderPdfExporter(context,helper);exporter.clear()
        compose.runOnUiThread {compose.activity.enableEdgeToEdge()}
        compose.setContent {BloomyBeautyTheme {Surface {Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            Text("Đơn BB-${id.toString().padStart(6,'0')}")
            Text("Trạng thái: Chờ xác nhận")
            OrderPdfActions(exporter,owner,id)
        }}}}
    }
    @After fun cleanup() {exporter.clear();helper.close();context.deleteDatabase(DB)}
    private fun capture(name: String) {
        android.os.SystemClock.sleep(700)
        var bitmap = instrumentation.uiAutomation.takeScreenshot()
        await {if(bitmap == null) bitmap=instrumentation.uiAutomation.takeScreenshot();bitmap != null}
        val captured = checkNotNull(bitmap)
        val folder = File(context.getExternalFilesDir(null),"pixel6-pdf-qa").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use {captured.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};captured.recycle()
    }
    private fun await(timeout: Long = 20_000, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis()+timeout
        while(!condition()) {if(android.os.SystemClock.uptimeMillis() > deadline) throw AssertionError("External PDF UI timeout");android.os.SystemClock.sleep(200)}
    }
    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if(node == null) return null
        if(predicate(node)) return node
        for(i in 0 until node.childCount) find(node.getChild(i),predicate)?.let {return it}
        return null
    }
    private fun back() {
        val now = android.os.SystemClock.uptimeMillis()
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0),true)
        instrumentation.uiAutomation.injectInputEvent(KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0),true)
    }
    @Test fun chooserCancellationDoesNotChangeOrderAndExternalReaderOpensContentUri(): Unit = runBlocking {
        capture("04-export-actions")
        compose.onNodeWithText("Chia sẻ PDF").performClick()
        await {instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.intentresolver"}
        await {find(instrumentation.uiAutomation.rootInActiveWindow) {it.text?.toString()?.contains("Sharing",ignoreCase=true) == true || it.text?.toString()?.contains("Share",ignoreCase=true) == true} != null}
        capture("05-share-chooser-no-recipient-selected")
        back()
        await {instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName}
        assertEquals("PENDING",OrderRepository(helper).detail(owner,id)!!.status)
        compose.onNodeWithText("Xem PDF").performClick()
        await {instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() in setOf("com.android.intentresolver","com.google.android.apps.docs")}
        // Use only the installed local viewer. No messages or uploads are sent.
        var reader: AccessibilityNodeInfo? = null
        await {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if(root?.packageName?.toString() == "com.google.android.apps.docs") true
            else {reader = find(root) {it.text?.toString()?.contains("Drive PDF",ignoreCase=true) == true || it.text?.toString() == "PDF Viewer"};reader != null}
        }
        reader?.let {node ->
            var target = node
            while(!target.isClickable && target.parent != null) target = target.parent
            assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        await {instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.google.android.apps.docs"}
        android.os.SystemClock.sleep(2000);capture("06-external-pdf-reader")
        back()
        assertEquals("PENDING",OrderRepository(helper).detail(owner,id)!!.status)
    }
    companion object {private const val DB = "pdf-screen-test.db";private const val PASSWORD = "PdfTest123!"}
}
