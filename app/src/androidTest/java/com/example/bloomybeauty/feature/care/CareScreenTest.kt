package com.example.bloomybeauty.feature.care

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.BloomyBeautyApplication
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class CareScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as BloomyBeautyApplication
    private lateinit var vm: CareRoutineViewModel
    private val store = ViewModelStore()
    private lateinit var restoration: StateRestorationTester
    private var userId = 0L
    private var originalUser: Long? = null

    @Before fun setUp() = runBlocking {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        originalUser = app.careStore.currentUserId()
        val password = "CareTest123!"
        userId = (app.authRepository.register("Khách giao diện", "care-ui-${java.util.UUID.randomUUID()}@example.com", password, password) as AuthResult.Success).user.id
        val products = app.catalogRepository.load(userId).products
        compose.runOnUiThread { compose.activity.enableEdgeToEdge(); vm = CareRoutineViewModel(app.careRepository, userId); store.put("care", vm) }
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            BloomyBeautyTheme {
                CareScreen(vm.state.collectAsStateWithLifecycle().value, products, null, null, {}, {}, vm::reload, vm::save, vm::toggle, vm::delete, vm::clearError)
            }
        }
        compose.waitUntil(10000) { !vm.state.value.loading }
    }

    @After fun tearDown() = runBlocking {
        compose.runOnUiThread { store.clear() }
        app.authRepository.logout()
        app.databaseHelper.writableDatabase.delete("users", "_id=?", arrayOf(userId.toString()))
        originalUser?.let { app.databaseHelper.writableDatabase.execSQL("INSERT OR REPLACE INTO auth_session(_id,user_id) VALUES(1,?)", arrayOf(it)) }
        app.careReminders.reconcile()
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("careScreenshots") != "true") return
        compose.runOnUiThread {
            compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
        // Window/IME animations are outside Compose's idling resource.
        android.os.SystemClock.sleep(500)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = java.io.File(context.getExternalFilesDir(null), "pixel6-care-qa").apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    @Test fun createEditToggleAndDeleteThroughUi() {
        compose.onNodeWithText("Thêm lịch chăm sóc").performClick()
        compose.onNodeWithText("Tên lịch").performTextInput("Serum buổi tối")
        capture("01-bieu-mau-them")
        compose.onNodeWithText("Lưu lịch").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.state.value.routines.size == 1 && !vm.state.value.busy }
        compose.onNodeWithText("Serum buổi tối").assertExists()
        capture("02-lich-da-luu")
        compose.onNodeWithText("Sửa lịch").performScrollTo().performClick()
        compose.onNodeWithText("Tên lịch").performTextReplacement("Chăm sóc buổi tối")
        capture("03-sua-lich")
        compose.onNodeWithText("Lưu lịch").performScrollTo().performClick()
        compose.waitUntil(10000) { vm.state.value.routines.singleOrNull()?.title == "Chăm sóc buổi tối" && !vm.state.value.busy }
        compose.onNode(isToggleable()).performClick()
        compose.waitUntil(10000) { vm.state.value.routines.singleOrNull()?.enabled == false && !vm.state.value.busy }
        capture("04-tat-nhac")
        compose.onNodeWithText("Xóa lịch").performScrollTo().performClick()
        capture("05-xac-nhan-xoa")
        compose.onNodeWithText("Xóa").performClick()
        compose.waitUntil(10000) { vm.state.value.routines.isEmpty() && !vm.state.value.busy }
        compose.onNodeWithText("Bạn chưa có lịch chăm sóc. Hãy thêm lịch đầu tiên.").assertExists()
        capture("06-da-xoa-lich")
    }

    @Test fun draftSurvivesStateRestorationAndEmptyDaysCannotBeSaved() {
        compose.onNodeWithText("Thêm lịch chăm sóc").performClick()
        compose.onNodeWithText("Tên lịch").performTextInput("Bản nháp còn giữ")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Tên lịch").assertTextContains("Bản nháp còn giữ")
        listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        compose.onNodeWithText("Lưu lịch").performScrollTo().assertIsNotEnabled()
        capture("07-khoi-phuc-nhap-va-loi-ngay")
        assertTrue(vm.state.value.routines.isEmpty())
    }
}
