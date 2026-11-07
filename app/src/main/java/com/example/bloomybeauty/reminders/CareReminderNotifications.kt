package com.example.bloomybeauty.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bloomybeauty.MainActivity
import com.example.bloomybeauty.R
import com.example.bloomybeauty.data.care.CareRoutine

class CareReminderNotifications(private val context: Context) {
    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Nhắc chăm sóc da", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nhắc chăm sóc theo lịch cá nhân của bạn"
            })
    }

    fun allowed(): Boolean {
        createChannel()
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(routine: CareRoutine) {
        if (!allowed()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = context.packageName + ".action.OPEN_CARE"
            data = Uri.parse("bloomy-care://${context.packageName}/${routine.userId}/${routine.id}")
            putExtra(ROUTINE_ID, routine.id); putExtra(OWNER_ID, routine.userId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(routine.title)
            .setContentText("Đến giờ chăm sóc da. Chạm để xem lịch của bạn.")
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_SECRET).build()
        try { NotificationManagerCompat.from(context).notify(tag(routine.id), 0, notification) }
        catch (_: SecurityException) { /* The system permission may change after allowed(). */ }
    }

    fun cancel(id: Long) = NotificationManagerCompat.from(context).cancel(tag(id), 0)
    private fun tag(id: Long) = "care:$id"

    companion object {
        const val CHANNEL = "bloomy_care_reminders"
        const val ROUTINE_ID = "care_routine_id"
        const val OWNER_ID = "care_owner_id"
    }
}
