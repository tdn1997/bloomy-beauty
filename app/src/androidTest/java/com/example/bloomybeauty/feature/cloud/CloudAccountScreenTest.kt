package com.example.bloomybeauty.feature.cloud

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.BuildConfig
import com.example.bloomybeauty.data.auth.UserRole
import com.example.bloomybeauty.data.cloud.*
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.UUID

class CloudAccountScreenTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private lateinit var services: CloudServices
    private lateinit var vm: CloudAccountViewModel
    private val store=ViewModelStore()
    private val users=mutableSetOf<String>()
    @Before fun setup() {
        assumeTrue(BuildConfig.CLOUD_EMULATOR)
        services=CloudServices.emulator(context,"cloud-ui-${UUID.randomUUID()}")
        compose.runOnUiThread {compose.activity.enableEdgeToEdge();vm=CloudAccountViewModel(services.auth,services.accounts);store.put("cloud",vm)}
        compose.setContent {BloomyBeautyTheme {CloudApp(vm)}}
        compose.waitUntil(30000) {!vm.state.value.initializing}
    }
    @After fun cleanup(): Unit=runBlocking {
        if(::services.isInitialized) {
            services.auth.session.value?.uid?.let {users.add(it)}
            compose.runOnUiThread {store.clear()}
            CloudEmulatorFixture.close(services,users)
        }
    }
    private fun awaitAccount() {
        compose.waitUntil(30000) {!vm.state.value.busy && vm.state.value.account!=null}
        users.add(checkNotNull(vm.state.value.account).uid)
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.waitForIdle();android.os.SystemClock.sleep(500)
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder=File(context.getExternalFilesDir(null),"pixel6-cloud-identity-qa").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun vietnameseRegistrationLogoutLoginAndLiveRoleRevocation(): Unit=runBlocking {
        val email="ui-${UUID.randomUUID()}@cloud.test"
        compose.onNodeWithText("Chưa có tài khoản? Đăng ký").performScrollTo().performClick()
        compose.onNodeWithText("Họ và tên").performTextInput("Nguyễn Thị Ánh Hương")
        compose.onNodeWithText("Email").performTextInput(email)
        compose.onNodeWithText("Mật khẩu").performTextInput(PASSWORD)
        compose.onNodeWithText("Nhập lại mật khẩu").performTextInput(PASSWORD)
        compose.onNodeWithText("Đăng ký").performScrollTo().performClick();awaitAccount()
        val uid=checkNotNull(vm.state.value.account).uid
        compose.onNodeWithText("Nguyễn Thị Ánh Hương").assertIsDisplayed()
        compose.onNodeWithText("Vai trò: Khách hàng").assertIsDisplayed();capture("01-registered-customer")
        compose.onNodeWithText("Đăng xuất").performClick()
        compose.waitUntil(30000) {vm.state.value.session==null && !vm.state.value.busy}
        compose.onNodeWithText("Nguyễn Thị Ánh Hương").assertDoesNotExist();capture("02-logout-clears-account")
        compose.onNodeWithText("Email").performTextInput(email)
        compose.onNodeWithText("Mật khẩu").performTextInput(PASSWORD)
        compose.onNodeWithText("Đăng nhập").performScrollTo().performClick();awaitAccount()
        assertEquals(uid,vm.state.value.account!!.uid)
        CloudEmulatorFixture.role(uid,"ADMIN")
        compose.waitUntil(30000) {vm.state.value.account?.role==UserRole.ADMIN && !vm.state.value.busy}
        compose.onNodeWithText("Vai trò: Quản trị viên").assertIsDisplayed();capture("03-server-admin-role")
        CloudEmulatorFixture.role(uid,"CUSTOMER")
        compose.waitUntil(30000) {vm.state.value.account?.role==UserRole.CUSTOMER && !vm.state.value.busy}
        compose.onNodeWithText("Vai trò: Quản trị viên").assertDoesNotExist();capture("04-revoked-admin-role")
        try {services.api.call(checkNotNull(services.auth.session.value),"verifyAdmin");fail("Revoked role authorized")}
        catch(e: CloudApiException) {assertEquals("PERMISSION_DENIED",e.code)}
    }
    @Test fun accountSwitchDiscardsPreviousSessionAndRole(): Unit=runBlocking {
        val email="first-${UUID.randomUUID()}@cloud.test"
        compose.runOnUiThread {vm.switchMode();vm.submit("Khách thứ nhất",email,PASSWORD,PASSWORD)};awaitAccount()
        val first=checkNotNull(services.auth.session.value)
        CloudEmulatorFixture.role(first.uid,"ADMIN")
        compose.waitUntil(30000) {vm.state.value.account?.role==UserRole.ADMIN && !vm.state.value.busy}
        compose.runOnUiThread {vm.logout()}
        compose.waitUntil(30000) {vm.state.value.session==null && !vm.state.value.busy}
        compose.runOnUiThread {vm.switchMode();vm.submit("Khách thứ hai","second-${UUID.randomUUID()}@cloud.test",PASSWORD,PASSWORD)};awaitAccount()
        assertNotEquals(first.uid,vm.state.value.account!!.uid)
        compose.onNodeWithText("Khách thứ nhất").assertDoesNotExist()
        compose.onNodeWithText("Vai trò: Quản trị viên").assertDoesNotExist()
        compose.onNodeWithText("Khách thứ hai").assertIsDisplayed();capture("05-switched-customer")
        try {services.accounts.current(first);fail("Old session survived") } catch(_: CloudSessionChanged) {}
    }
    @Test fun existingFirebasePasswordIsNotRejectedByLocalRegistrationPolicy(): Unit=runBlocking {
        val email="external-${UUID.randomUUID()}@cloud.test"
        val existingPassword="Abc123"
        services.auth.register("Tài khoản Firebase",email,existingPassword)
        users.add(checkNotNull(services.auth.session.value).uid)
        services.auth.logout()
        compose.waitUntil(30000) {vm.state.value.session==null && !vm.state.value.busy}
        compose.onNodeWithText("Email").performTextInput(email)
        compose.onNodeWithText("Mật khẩu").performTextInput(existingPassword)
        compose.onNodeWithText("Đăng nhập").performScrollTo().performClick();awaitAccount()
        assertEquals("Tài khoản Firebase",vm.state.value.account!!.name)
        assertEquals(email,vm.state.value.account!!.email)
    }
    companion object {private const val PASSWORD="CloudTest123!"}
}
