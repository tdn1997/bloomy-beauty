package com.example.bloomybeauty.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bloomybeauty.MainActivity
import com.example.bloomybeauty.R
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.atomic.AtomicInteger

class PushNotifications(private val context: Context) {
    fun initialize() {
        createChannel()
        // Refresh on launch as well as onNewToken; never log registration tokens.
        FirebaseMessaging.getInstance().token.addOnSuccessListener { saveToken(it) }
    }

    fun saveToken(token: String) {
        context.getSharedPreferences("push_registration", Context.MODE_PRIVATE)
            .edit().putString("token", token).apply()
    }

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notification_channel_description)
            }
        )
    }

    fun show(message: PushMessage): Boolean {
        createChannel()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID).importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.title).setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setContentIntent(pendingIntent).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        return try {
            manager.notify(nextId.incrementAndGet(), notification)
            true
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and posting.
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "bloomy_updates"
        private val nextId = AtomicInteger()
    }
}
