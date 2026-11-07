package com.example.bloomybeauty.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushNotificationsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val push = PushNotifications(context)

    @Before fun prepare() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        manager.cancelAll()
        push.createChannel()
    }

    @After fun cleanUp() {
        manager.cancelAll()
    }

    @Test fun channelIsCreatedWithoutOverwritingUserSettings() {
        val before = manager.getNotificationChannel(PushNotifications.CHANNEL_ID)
        push.createChannel()
        val after = manager.getNotificationChannel(PushNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, before.importance)
        assertEquals(before.importance, after.importance)
        assertTrue(after.name.isNotBlank())
    }

    @Test fun messagePostsNotificationWithTapAction() {
        assertTrue(push.show(PushMessage("Đơn hàng", "Đang giao")))
        val notification = awaitNotifications(1).single().notification
        assertEquals("Đơn hàng", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Đang giao", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(PushNotifications.CHANNEL_ID, notification.channelId)
        assertNotNull(notification.smallIcon)
        assertEquals(context.packageName, notification.contentIntent.creatorPackage)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test fun dataMessagePassesThroughFirebaseService() {
        val service = BloomyMessagingService()
        // Attach the real application context, as Android does when creating a service.
        val attach = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
        attach.isAccessible = true
        attach.invoke(service, context)
        service.onMessageReceived(RemoteMessage.Builder("test-sender")
            .setData(mapOf("title" to "Ưu đãi", "body" to "Tin mới")).build())
        if (!com.example.bloomybeauty.BuildConfig.FIREBASE_ENABLED) {
            assertTrue(manager.activeNotifications.isEmpty())
            return
        }
        assertEquals("Ưu đãi", awaitNotifications(1).single().notification.extras.getString(Notification.EXTRA_TITLE))
        service.onMessageReceived(RemoteMessage.Builder("test-sender").build())
        assertEquals(1, manager.activeNotifications.size)
    }

    private fun awaitNotifications(count: Int): Array<android.service.notification.StatusBarNotification> {
        val deadline = android.os.SystemClock.elapsedRealtime() + 3000
        while (manager.activeNotifications.size < count && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(50)
        }
        return manager.activeNotifications
    }

}
