package com.example.bloomybeauty.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.bloomybeauty.data.care.CareRoutineRules
import com.example.bloomybeauty.data.care.CareRoutineStore
import java.time.Instant
import java.time.ZoneId

class CareReminderScheduler(private val context: Context, private val store: CareRoutineStore) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences("care_alarms", Context.MODE_PRIVATE)
    private val notifications = CareReminderNotifications(context)

    @Synchronized fun reconcile() {
        val active = store.enabledForCurrentUser()
        val previous = preferences.getStringSet("scheduled", emptySet()).orEmpty().toSet()
        previous.forEach { entry ->
            val fields = entry.split(':').mapNotNull { it.toLongOrNull() }
            if (fields.size != 4) return@forEach
            pending(fields[0], fields[1], fields[2], fields[3], PendingIntent.FLAG_NO_CREATE)?.let {
                alarms.cancel(it); it.cancel()
            }
            if (active.none { it.id == fields[0] && it.userId == fields[1] && it.revision == fields[2] }) notifications.cancel(fields[0])
        }
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val scheduled = active.map { routine ->
            val occurrence = CareRoutineRules.nextOccurrence(routine.minuteOfDay, routine.daysMask, now, zone).toEpochMilli()
            "${routine.id}:${routine.userId}:${routine.revision}:$occurrence"
        }
        // Persist identities before scheduling so a later launch can cancel partial work.
        check(preferences.edit().putStringSet("scheduled", scheduled.toSet()).commit())
        scheduled.forEach(::scheduleEntry)
    }

    @Synchronized fun receive(intent: Intent) {
        val id = intent.getLongExtra(CareReminderNotifications.ROUTINE_ID, -1)
        val owner = intent.getLongExtra(CareReminderNotifications.OWNER_ID, -1)
        val revision = intent.getLongExtra("revision", -1)
        val occurrence = intent.getLongExtra("occurrence", -1)
        val key = "$id:$owner:$revision:$occurrence"
        val registered = preferences.getStringSet("scheduled", emptySet()).orEmpty().contains(key)
        if (!registered) return // A stale or duplicate broadcast must not reschedule other alarms.
        val elapsed = System.currentTimeMillis() - occurrence
        if (registered && id > 0 && owner > 0 && elapsed in 0..86_400_000L && store.currentUserId() == owner) {
            store.claimOccurrence(id, owner, revision, occurrence, notifications::show)
        }
        // Reschedule only this routine. Other alarms may be due at the exact same time.
        val active = store.enabledForCurrentUser().find { it.id == id }
        val remaining = preferences.getStringSet("scheduled", emptySet()).orEmpty().toMutableSet().apply { remove(key) }
        val next = active?.let {
            val time = CareRoutineRules.nextOccurrence(it.minuteOfDay, it.daysMask, Instant.now(), ZoneId.systemDefault()).toEpochMilli()
            "${it.id}:${it.userId}:${it.revision}:$time"
        }
        if (next != null) remaining.add(next) else notifications.cancel(id)
        check(preferences.edit().putStringSet("scheduled", remaining).commit())
        if (next != null) scheduleEntry(next)
    }

    private fun scheduleEntry(entry: String) {
        val fields = entry.split(':').map { it.toLong() }
        val intent = requireNotNull(pending(fields[0], fields[1], fields[2], fields[3], PendingIntent.FLAG_UPDATE_CURRENT))
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fields[3], intent)
    }

    private fun pending(id: Long, owner: Long, revision: Long, occurrence: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, Intent(context, CareReminderReceiver::class.java).apply {
            action = context.packageName + ".action.CARE_REMINDER"
            data = Uri.parse("bloomy-care-alarm://${context.packageName}/$id")
            putExtra(CareReminderNotifications.ROUTINE_ID, id); putExtra(CareReminderNotifications.OWNER_ID, owner)
            putExtra("revision", revision); putExtra("occurrence", occurrence)
        }, flags or PendingIntent.FLAG_IMMUTABLE)
}
