package com.example.bloomybeauty.notifications

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
import androidx.core.content.ContextCompat
import com.example.bloomybeauty.MainActivity
import com.example.bloomybeauty.R
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.SessionAccess
import com.example.bloomybeauty.events.OrderChange

class OrderNotifications(private val context: Context, private val helper: AuthDatabaseHelper) {
    /** Best effort, after business commit. Durable history already exists even if permission is denied. */
    fun show(change: OrderChange) {
        val db = helper.readableDatabase
        if(!SessionAccess.matches(db,change.ownerUserId)) return
        val id = db.rawQuery("SELECT _id FROM user_notifications WHERE user_id=? AND type='ORDER' AND entity_id=? ORDER BY _id DESC LIMIT 1",
            arrayOf("${change.ownerUserId}","${change.orderId}")).use {if(it.moveToFirst()) it.getLong(0) else return}
        if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Cập nhật đơn hàng",NotificationManager.IMPORTANCE_DEFAULT))
        if(!manager.areNotificationsEnabled()) return
        val intent = Intent(context,MainActivity::class.java).apply {
            action = context.packageName+".action.OPEN_INBOX"
            data = Uri.parse("bloomy-inbox://${context.packageName}/${change.ownerUserId}/$id")
            putExtra(OWNER_ID,change.ownerUserId);putExtra(NOTIFICATION_ID,id)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Bloomy Beauty — cập nhật đơn hàng")
            .setContentText("Có cập nhật mới. Mở hộp thông báo để xem.")
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).setAutoCancel(true).setContentIntent(pending).build()
        manager.notify("order-inbox:$id",0,notification)
    }
    fun clear() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter {it.tag?.startsWith("order-inbox:") == true}.forEach {manager.cancel(it.tag,it.id)}
    }
    companion object {
        const val CHANNEL = "bloomy_order_inbox"
        const val OWNER_ID = "inbox_owner_id"
        const val NOTIFICATION_ID = "inbox_notification_id"
    }
}
