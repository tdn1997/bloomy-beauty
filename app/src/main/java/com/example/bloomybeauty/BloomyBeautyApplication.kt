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
    val commerceSource get() = if(BuildConfig.CLOUD_EMULATOR) com.example.bloomybeauty.data.cloud.CommerceSource.CLOUD_EMULATOR
        else com.example.bloomybeauty.data.cloud.CommerceSource.LOCAL_DEMO
    val cloudServices by lazy { com.example.bloomybeauty.data.cloud.CloudServices.emulator(this) }
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
    val notificationRepository by lazy { com.example.bloomybeauty.data.notification.NotificationRepository(databaseHelper) }
    val orderNotifications by lazy { com.example.bloomybeauty.notifications.OrderNotifications(this,databaseHelper) }
    val orderPdfExporter by lazy { com.example.bloomybeauty.export.OrderPdfExporter(this,databaseHelper) }
    val statisticsRepository by lazy { com.example.bloomybeauty.data.statistics.StatisticsRepository(databaseHelper) }
    val addressRepository by lazy { com.example.bloomybeauty.data.address.AddressRepository(databaseHelper) }
    val reviewRepository by lazy { com.example.bloomybeauty.data.review.ReviewRepository(databaseHelper) }
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
    fun reconcileCareReminders() {
        if(commerceSource==com.example.bloomybeauty.data.cloud.CommerceSource.LOCAL_DEMO) runCareTask { careReminders.reconcile() }
    }
    private fun onSessionChanged() {
        // Auth methods call this from IO after commit, before updating the UI session.
        try { orderNotifications.clear() }
        catch (exception: Exception) { android.util.Log.e("OrderInbox", "Unable to clear notifications", exception) }
        try { orderPdfExporter.clear() }
        catch (exception: Exception) { android.util.Log.e("OrderPDF", "Unable to clear temporary PDFs", exception) }
        try {
            contentResolver.notifyChange(CareRoutineContract.CONTENT_URI, null)
            careReminders.reconcile()
        }
        catch (exception: Exception) { android.util.Log.e("CareReminders", "Unable to update session reminders", exception) }
    }
    private val orderChanges by lazy {
        val broadcast = com.example.bloomybeauty.events.BroadcastOrderChangePublisher(this)
        com.example.bloomybeauty.events.OrderChangePublisher { change ->
            broadcast.publish(change)
            try { orderNotifications.show(change) }
            catch (exception: Exception) { android.util.Log.e("OrderInbox", "Unable to show order notification", exception) }
        }
    }
    val adminRepository by lazy { com.example.bloomybeauty.data.admin.AdminRepository(databaseHelper, orderChanges) }
    val authRepository: AuthRepository by lazy { AuthRepository(databaseHelper, onSessionChanged = ::onSessionChanged) }
    val catalogRepository: CatalogRepository by lazy { CatalogRepository(databaseHelper) }
    val cartRepository: CartRepository by lazy { CartRepository(databaseHelper) }
    val orderRepository: OrderRepository by lazy { OrderRepository(databaseHelper, orderChanges) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(databaseHelper) }
}
