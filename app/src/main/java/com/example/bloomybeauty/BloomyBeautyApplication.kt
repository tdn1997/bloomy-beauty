package com.example.bloomybeauty

import android.app.Application
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.AuthRepository
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.cart.CartRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.data.profile.ProfileRepository
import com.example.bloomybeauty.data.care.CareRoutineRepository
import com.example.bloomybeauty.data.care.CareRoutineStore
import com.example.bloomybeauty.data.care.CareRoutineContract
import com.example.bloomybeauty.reminders.CareReminderScheduler
import java.util.concurrent.Executors

class BloomyBeautyApplication : Application() {
    val promotionsRepository: com.example.bloomybeauty.data.promotions.PromotionsRepository by lazy {
        if (BuildConfig.FIREBASE_ENABLED) {
            com.example.bloomybeauty.data.promotions.FirestorePromotionsRepository(
                com.google.firebase.firestore.FirebaseFirestore.getInstance())
        } else com.example.bloomybeauty.data.promotions.DisabledPromotionsRepository
    }

    override fun onCreate() {
        super.onCreate()
        // Activity.onResume and the restore receiver reconcile schedules. Do not do it
        // here: an alarm can cold-start this process and must consume its saved occurrence first.
        if (BuildConfig.FIREBASE_ENABLED) {
            com.example.bloomybeauty.notifications.PushNotifications(this).initialize()
        }
    }

    // Process-owned helper: Activity recreation must not close an active database.
    val databaseHelper: AuthDatabaseHelper by lazy { AuthDatabaseHelper(this) }
    val careStore by lazy { CareRoutineStore(databaseHelper) }
    val careRepository by lazy { CareRoutineRepository(contentResolver) }
    val careReminders by lazy { CareReminderScheduler(this, careStore) }
    private val careExecutor = Executors.newSingleThreadExecutor()
    fun runCareTask(task: () -> Unit) {
        careExecutor.execute {
            try { task() }
            catch (exception: Exception) { android.util.Log.e("CareReminders", "Unable to reconcile care reminders", exception) }
        }
    }
    fun reconcileCareReminders() = runCareTask { careReminders.reconcile() }
    private fun onSessionChanged() {
        // Auth methods call this from IO after commit, before updating the UI session.
        try {
            contentResolver.notifyChange(CareRoutineContract.CONTENT_URI, null)
            careReminders.reconcile()
        }
        catch (exception: Exception) { android.util.Log.e("CareReminders", "Unable to update session reminders", exception) }
    }
    private val orderChanges by lazy { com.example.bloomybeauty.events.BroadcastOrderChangePublisher(this) }
    val adminRepository by lazy { com.example.bloomybeauty.data.admin.AdminRepository(databaseHelper, orderChanges) }
    val authRepository: AuthRepository by lazy { AuthRepository(databaseHelper, onSessionChanged = ::onSessionChanged) }
    val catalogRepository: CatalogRepository by lazy { CatalogRepository(databaseHelper) }
    val cartRepository: CartRepository by lazy { CartRepository(databaseHelper) }
    val orderRepository: OrderRepository by lazy { OrderRepository(databaseHelper, orderChanges) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(databaseHelper) }
}
