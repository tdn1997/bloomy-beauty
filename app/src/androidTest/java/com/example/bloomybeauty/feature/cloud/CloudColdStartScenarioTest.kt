package com.example.bloomybeauty.feature.cloud

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.BuildConfig
import com.example.bloomybeauty.MainActivity
import com.example.bloomybeauty.BloomyBeautyApplication
import com.example.bloomybeauty.data.auth.AuthContract
import com.example.bloomybeauty.data.auth.UserRole
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File

/** Host runs prepare, kills the process, launches MainActivity, then runs inspect. Keeps only the public emulator fixture signed in. */
class CloudColdStartScenarioTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun scenario(): Unit=runBlocking {
        assumeTrue(BuildConfig.CLOUD_EMULATOR)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val action=InstrumentationRegistry.getArguments().getString("cloudScenario")
        assumeTrue(action=="prepare" || action=="inspect")
        val context=instrumentation.targetContext
        val services=(context.applicationContext as BloomyBeautyApplication).cloudServices
        val preferences=context.getSharedPreferences("cloud_cold_start_qa",0)
        if(action=="prepare") {
            val prior=services.auth.session.value
            if(prior!=null) assertEquals("Refuse to replace a different user's session","customer@bloomy.cloud.test",services.accounts.current(prior).email)
            else services.auth.login("customer@bloomy.cloud.test","CloudDemo123!")
            val session=checkNotNull(services.auth.session.value)
            assertTrue(preferences.edit().putString("uid",session.uid).putString("nonce",session.nonce).commit())
        } else {
            val session=checkNotNull(services.auth.session.value)
            assertEquals(preferences.getString("uid",null),session.uid)
            assertNotEquals(preferences.getString("nonce",null),session.nonce)
        }
        val session=checkNotNull(services.auth.session.value)
        val account=services.accounts.current(session)
        assertEquals("customer@bloomy.cloud.test",account.email);assertEquals(UserRole.CUSTOMER,account.role)
        compose.waitUntil(30000) {compose.onAllNodesWithText("customer@bloomy.cloud.test").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Vai trò: Khách hàng").assertIsDisplayed()
        assertFalse("Cloud MainActivity must not initialize/import the local database",context.getDatabasePath(AuthContract.DATABASE_NAME).exists())
        val flags=android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
        for(type in listOf(com.example.bloomybeauty.reminders.CareReminderReceiver::class.java,com.example.bloomybeauty.reminders.CareScheduleRestoreReceiver::class.java)) {
            assertFalse(context.packageManager.getReceiverInfo(android.content.ComponentName(context,type),flags).enabled)
        }
        assertFalse(context.packageManager.getProviderInfo(android.content.ComponentName(context,com.example.bloomybeauty.data.care.CareRoutineProvider::class.java),flags).enabled)
        val folder=File(context.getExternalFilesDir(null),"pixel6-cloud-identity-qa").apply {mkdirs()}
        File(folder,"cold-$action.json").writeText(JSONObject().put("uid",session.uid).put("nonce",session.nonce).put("source","CLOUD_EMULATOR").put("role",account.role.name).put("localDatabaseCreated",false).toString(2))
        compose.waitForIdle();android.os.SystemClock.sleep(500)
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder,"06-cold-$action.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
