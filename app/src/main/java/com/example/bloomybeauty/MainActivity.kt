package com.example.bloomybeauty

import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.bloomybeauty.feature.app.BloomyApp
import com.example.bloomybeauty.feature.app.ShopRepositories
import com.example.bloomybeauty.feature.auth.AuthViewModel
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme

class MainActivity : ComponentActivity() {
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
        val repositories = ShopRepositories(app.catalogRepository, app.cartRepository, app.orderRepository,
            app.profileRepository, app.adminRepository, app.promotionsRepository)
        setContent { BloomyBeautyTheme { BloomyApp(authViewModel, repositories) } }
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
}
