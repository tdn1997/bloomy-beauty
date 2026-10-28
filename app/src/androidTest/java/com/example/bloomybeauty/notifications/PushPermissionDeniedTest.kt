package com.example.bloomybeauty.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run separately after revoking POST_NOTIFICATIONS from the target package. */
@RunWith(AndroidJUnit4::class)
class PushPermissionDeniedTest {
    @Test fun deniedPermissionSkipsNotificationWithoutCrashing() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Run separately after revoking POST_NOTIFICATIONS",
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_DENIED)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        assertFalse(PushNotifications(context).show(PushMessage("Tin mới", "Nội dung")))
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
