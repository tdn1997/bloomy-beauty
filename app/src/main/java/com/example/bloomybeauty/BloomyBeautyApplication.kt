package com.example.bloomybeauty

import android.app.Application
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.AuthRepository
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.cart.CartRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.data.profile.ProfileRepository

class BloomyBeautyApplication : Application() {
    val promotionsRepository: com.example.bloomybeauty.data.promotions.PromotionsRepository by lazy {
        if (BuildConfig.FIREBASE_ENABLED) {
            com.example.bloomybeauty.data.promotions.FirestorePromotionsRepository(
                com.google.firebase.firestore.FirebaseFirestore.getInstance())
        } else com.example.bloomybeauty.data.promotions.DisabledPromotionsRepository
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FIREBASE_ENABLED) {
            com.example.bloomybeauty.notifications.PushNotifications(this).initialize()
        }
    }

    // Process-owned helper: Activity recreation must not close an active database.
    private val databaseHelper: AuthDatabaseHelper by lazy { AuthDatabaseHelper(this) }
    val adminRepository by lazy { com.example.bloomybeauty.data.admin.AdminRepository(databaseHelper) }
    val authRepository: AuthRepository by lazy { AuthRepository(databaseHelper) }
    val catalogRepository: CatalogRepository by lazy { CatalogRepository(databaseHelper) }
    val cartRepository: CartRepository by lazy { CartRepository(databaseHelper) }
    val orderRepository: OrderRepository by lazy { OrderRepository(databaseHelper) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(databaseHelper) }
}
