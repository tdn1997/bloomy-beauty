package com.example.bloomybeauty.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.bloomybeauty.BloomyBeautyApplication

class CareReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != context.packageName + ".action.CARE_REMINDER") return
        val result = goAsync()
        val app = context.applicationContext as BloomyBeautyApplication
        app.runCareTask { try { app.careReminders.receive(intent) } finally { result.finish() } }
    }
}

class CareScheduleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val result = goAsync()
        val app = context.applicationContext as BloomyBeautyApplication
        app.runCareTask { try { app.careReminders.reconcile() } finally { result.finish() } }
    }
}
