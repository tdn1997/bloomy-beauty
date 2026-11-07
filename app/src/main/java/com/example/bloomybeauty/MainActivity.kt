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
    private var careOpenRequest by mutableStateOf<CareOpenRequest?>(null)
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Users can enable notifications later in system Settings. */ }

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory((application as BloomyBeautyApplication).authRepository)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BloomyBeautyApplication
        if (savedInstanceState?.containsKey("pending_care_owner") == true) {
            careOpenRequest = CareOpenRequest(savedInstanceState.getLong("pending_care_owner"), savedInstanceState.getLong("pending_care_id"))
        } else readCareIntent(intent)
        val repositories = ShopRepositories(app.catalogRepository, app.cartRepository, app.orderRepository,
            app.profileRepository, app.adminRepository, app.promotionsRepository, app.careRepository)
        setContent { BloomyBeautyTheme { BloomyApp(authViewModel, repositories, careOpenRequest) {
            careOpenRequest = null
            intent.removeExtra(CareReminderNotifications.ROUTINE_ID)
            intent.removeExtra(CareReminderNotifications.OWNER_ID)
        } } }
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
        readCareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        (application as BloomyBeautyApplication).reconcileCareReminders()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        careOpenRequest?.let { outState.putLong("pending_care_owner", it.userId); outState.putLong("pending_care_id", it.routineId) }
        super.onSaveInstanceState(outState)
    }

    private fun readCareIntent(intent: Intent) {
        if (intent.action != packageName + ".action.OPEN_CARE") return
        val owner = intent.getLongExtra(CareReminderNotifications.OWNER_ID, -1)
        val id = intent.getLongExtra(CareReminderNotifications.ROUTINE_ID, -1)
        if (owner > 0 && id > 0) careOpenRequest = CareOpenRequest(owner, id)
    }
}
