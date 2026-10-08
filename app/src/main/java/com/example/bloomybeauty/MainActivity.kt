package com.example.bloomybeauty

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
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory((application as BloomyBeautyApplication).authRepository)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BloomyBeautyApplication
        val repositories = ShopRepositories(app.catalogRepository, app.cartRepository, app.orderRepository,
            app.profileRepository, app.adminRepository)
        setContent { BloomyBeautyTheme { BloomyApp(authViewModel, repositories) } }
    }
}
