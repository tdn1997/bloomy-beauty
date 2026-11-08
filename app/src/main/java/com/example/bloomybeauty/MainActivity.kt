package com.example.bloomybeauty

import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.bloomybeauty.feature.app.BloomyApp
import com.example.bloomybeauty.feature.app.ShopRepositories
import com.example.bloomybeauty.feature.app.CareOpenRequest
import com.example.bloomybeauty.reminders.CareReminderNotifications
import com.example.bloomybeauty.feature.auth.AuthViewModel
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme

class MainActivity : ComponentActivity() {
    private var inboxOpenRequest by mutableStateOf<com.example.bloomybeauty.feature.app.InboxOpenRequest?>(null)
    private var careOpenRequest by mutableStateOf<CareOpenRequest?>(null)
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Users can enable notifications later in system Settings. */ }

    private val cloudAccountViewModel: com.example.bloomybeauty.feature.cloud.CloudAccountViewModel by viewModels {
        val services=(application as BloomyBeautyApplication).cloudServices
        com.example.bloomybeauty.feature.cloud.CloudAccountViewModel.Factory(services.auth,services.accounts)
    }
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory((application as BloomyBeautyApplication).authRepository)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BloomyBeautyApplication
        if(app.commerceSource==com.example.bloomybeauty.data.cloud.CommerceSource.CLOUD_EMULATOR) {
            setContent {BloomyBeautyTheme {com.example.bloomybeauty.feature.cloud.CloudApp(cloudAccountViewModel)}}
            return
        }
        if (savedInstanceState?.containsKey("pending_care_owner") == true) {
            careOpenRequest = CareOpenRequest(savedInstanceState.getLong("pending_care_owner"), savedInstanceState.getLong("pending_care_id"))
        } else readCareIntent(intent)
        if(savedInstanceState?.containsKey("pending_inbox_owner") == true) {
            inboxOpenRequest=com.example.bloomybeauty.feature.app.InboxOpenRequest(savedInstanceState.getLong("pending_inbox_owner"),savedInstanceState.getLong("pending_inbox_id"))
        } else readInboxIntent(intent)
        val repositories = ShopRepositories(app.catalogRepository, app.cartRepository, app.orderRepository,
            app.profileRepository, app.adminRepository, app.promotionsRepository, app.careRepository, app.reviewRepository, app.addressRepository, app.statisticsRepository, app.orderPdfExporter, app.notificationRepository)
        setContent { BloomyBeautyTheme { BloomyApp(authViewModel,repositories,careOpenRequest,onCareOpenHandled={
            careOpenRequest=null
            intent.removeExtra(CareReminderNotifications.ROUTINE_ID);intent.removeExtra(CareReminderNotifications.OWNER_ID)
        },inboxOpenRequest=inboxOpenRequest,onInboxOpenHandled={
            inboxOpenRequest=null
            intent.removeExtra(com.example.bloomybeauty.notifications.OrderNotifications.OWNER_ID)
            intent.removeExtra(com.example.bloomybeauty.notifications.OrderNotifications.NOTIFICATION_ID)
        }) } }
        if (BuildConfig.FIREBASE_ENABLED && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val preferences = getSharedPreferences("push_settings", MODE_PRIVATE)
            if (!preferences.getBoolean("permission_requested", false)) {
                preferences.edit().putBoolean("permission_requested", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        careOpenRequest=null;inboxOpenRequest=null
        if(!BuildConfig.CLOUD_EMULATOR) {readCareIntent(intent);readInboxIntent(intent)}
    }

    override fun onResume() {
        super.onResume()
        if(!BuildConfig.CLOUD_EMULATOR) (application as BloomyBeautyApplication).reconcileCareReminders()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        careOpenRequest?.let { outState.putLong("pending_care_owner", it.userId); outState.putLong("pending_care_id", it.routineId) }
        inboxOpenRequest?.let {outState.putLong("pending_inbox_owner",it.userId);outState.putLong("pending_inbox_id",it.notificationId)}
        super.onSaveInstanceState(outState)
    }

    private fun readInboxIntent(intent: Intent) {
        if(intent.action != packageName+".action.OPEN_INBOX") return
        val owner=intent.getLongExtra(com.example.bloomybeauty.notifications.OrderNotifications.OWNER_ID,-1)
        val id=intent.getLongExtra(com.example.bloomybeauty.notifications.OrderNotifications.NOTIFICATION_ID,-1)
        if(owner>0 && id>0) inboxOpenRequest=com.example.bloomybeauty.feature.app.InboxOpenRequest(owner,id)
    }
    private fun readCareIntent(intent: Intent) {
        if (intent.action != packageName + ".action.OPEN_CARE") return
        val owner = intent.getLongExtra(CareReminderNotifications.OWNER_ID, -1)
        val id = intent.getLongExtra(CareReminderNotifications.ROUTINE_ID, -1)
        if (owner > 0 && id > 0) careOpenRequest = CareOpenRequest(owner, id)
    }
}
