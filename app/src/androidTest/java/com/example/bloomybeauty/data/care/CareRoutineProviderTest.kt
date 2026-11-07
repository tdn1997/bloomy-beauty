package com.example.bloomybeauty.data.care

import android.content.ContentValues
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.BloomyBeautyApplication
import com.example.bloomybeauty.data.auth.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.example.bloomybeauty.reminders.*

/** Uses the installed provider and a private test account on the dedicated test emulator. */
class CareRoutineProviderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as BloomyBeautyApplication
    private val repository = CareRoutineRepository(context.contentResolver)
    private val users = mutableListOf<Long>()
    private var originalUser: Long? = null
    private var userId = 0L
    private val auth get() = app.authRepository
    private val password = "CareTest123!"

    @Before fun setUp() = runBlocking {
        originalUser = app.careStore.currentUserId()
        userId = register()
    }
    private suspend fun register(): Long {
        val result = auth.register("Khách chăm sóc", "care-${java.util.UUID.randomUUID()}@example.com", password, password) as AuthResult.Success
        return result.user.id.also { users.add(it) }
    }
    @After fun tearDown() = runBlocking {
        auth.logout()
        val db = app.databaseHelper.writableDatabase
        users.forEach { db.delete("users", "_id=?", arrayOf(it.toString())) }
        originalUser?.let { id -> db.execSQL("INSERT OR REPLACE INTO auth_session(_id,user_id) VALUES(1,?)", arrayOf(id)) }
        app.careReminders.reconcile()
    }

    @Test fun resolverCrudMimeAndRevisionConflict() = runBlocking {
        repository.save(userId, null, null, CareRoutineDraft(title = " Serum ", productId = "serum"))
        val row = repository.load(userId).single()
        assertEquals("Serum", row.title)
        assertEquals(userId, row.userId)
        assertEquals("vnd.android.cursor.dir/vnd.${CareRoutineContract.AUTHORITY}.care_routine", context.contentResolver.getType(CareRoutineContract.CONTENT_URI))
        assertTrue(context.contentResolver.getType(CareRoutineContract.uri(userId, row.id))!!.contains("cursor.item"))
        repository.save(userId, row.id, row.revision, CareRoutineDraft(title = "Buổi tối", minuteOfDay = 1320, daysMask = 65))
        val updated = repository.load(userId).single()
        assertEquals("Buổi tối", updated.title)
        assertEquals(row.revision + 1, updated.revision)
        try { repository.delete(userId, row); fail("Stale revision accepted") } catch (_: IllegalStateException) { }
        repository.setEnabled(userId, updated, false)
        assertFalse(repository.load(userId).single().enabled)
        repository.delete(userId, repository.load(userId).single())
        assertTrue(repository.load(userId).isEmpty())
    }

    @Test fun anotherSessionCannotReadOrMutatePreviousUsersRows() = runBlocking {
        repository.save(userId, null, null, CareRoutineDraft(title = "Riêng tư"))
        val row = repository.load(userId).single()
        val other = register()
        assertTrue(repository.load(other).isEmpty())
        try { repository.load(userId); fail("Stale account read accepted") } catch (_: SessionException) { }
        val changed = context.contentResolver.update(CareRoutineContract.uri(other, row.id, row.revision), ContentValues().apply { put("enabled", 0) }, null, null)
        assertEquals(0, changed)
    }

    @Test fun rejectsOwnerInjectionInvalidFieldsAndUnknownPaths() = runBlocking {
        val resolver = context.contentResolver
        val uri = CareRoutineContract.uri(userId)
        val attempts = listOf(ContentValues().apply { put("user_id", userId) }, ContentValues().apply { put("title", ""); put("minute_of_day", 1440); put("days_mask", 0); put("enabled", 1) })
        attempts.forEach {
            try { resolver.insert(uri, it); fail("Invalid values accepted") } catch (_: IllegalArgumentException) { }
        }
        try { resolver.query(uri.buildUpon().appendPath("private").build(), null, null, null, null); fail("Unknown path accepted") } catch (_: IllegalArgumentException) { }
        assertTrue(repository.load(userId).isEmpty())
    }

    @Test fun writesNotifyContentObservers() = runBlocking {
        val changed = CountDownLatch(1)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { changed.countDown() }
        }
        context.contentResolver.registerContentObserver(CareRoutineContract.CONTENT_URI, true, observer)
        try {
            repository.save(userId, null, null, CareRoutineDraft(title = "Quan sát"))
            assertTrue(changed.await(5, TimeUnit.SECONDS))
        } finally { context.contentResolver.unregisterContentObserver(observer) }
    }

    @Test fun occurrenceClaimsRejectReplayAndStaleRevision() = runBlocking {
        repository.save(userId, null, null, CareRoutineDraft(title = "Nhắc thử"))
        val row = repository.load(userId).single()
        var shown = 0
        assertTrue(app.careStore.claimOccurrence(row.id, userId, row.revision, 1000) { shown++ })
        assertFalse(app.careStore.claimOccurrence(row.id, userId, row.revision, 1000) { shown++ })
        repository.setEnabled(userId, row, false)
        assertFalse(app.careStore.claimOccurrence(row.id, userId, row.revision, 2000) { shown++ })
        assertEquals(1, shown)
    }

    @Test fun staticReceiverPostsOnceAndLogoutCancelsNotificationAndAlarms() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        repository.save(userId, null, null, CareRoutineDraft(title = "Nhắc qua receiver"))
        val row = repository.load(userId).single()
        val drained = CountDownLatch(1)
        app.runCareTask { drained.countDown() }
        assertTrue(drained.await(5, TimeUnit.SECONDS))
        val preferences = context.getSharedPreferences("care_alarms", 0)
        val occurrence = System.currentTimeMillis() - 1000
        // Simulate delivery, not AlarmManager timing. A separate device demo checks real timing.
        preferences.edit().putStringSet("scheduled", setOf("${row.id}:$userId:${row.revision}:$occurrence")).commit()
        val intent = Intent(context, CareReminderReceiver::class.java).apply {
            action = context.packageName + ".action.CARE_REMINDER"
            putExtra(CareReminderNotifications.ROUTINE_ID, row.id); putExtra(CareReminderNotifications.OWNER_ID, userId)
            putExtra("revision", row.revision); putExtra("occurrence", occurrence)
        }
        context.sendBroadcast(intent)
        val deadline = SystemClock.uptimeMillis() + 5000
        while (manager.activeNotifications.none { it.tag == "care:${row.id}" } && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        val notification = manager.activeNotifications.single { it.tag == "care:${row.id}" }.notification
        assertEquals(CareReminderNotifications.CHANNEL, notification.channelId)
        assertNotNull(notification.contentIntent)
        context.sendBroadcast(intent)
        auth.logout()
        app.careReminders.reconcile()
        assertTrue(manager.activeNotifications.none { it.tag == "care:${row.id}" })
        assertTrue(preferences.getStringSet("scheduled", emptySet())!!.isEmpty())
    }

    @Test fun simultaneousOccurrencesDoNotCancelEachOther() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        repository.save(userId, null, null, CareRoutineDraft(title = "Lịch thứ nhất"))
        repository.save(userId, null, null, CareRoutineDraft(title = "Lịch thứ hai"))
        val rows = repository.load(userId)
        val drained = CountDownLatch(1)
        app.runCareTask { drained.countDown() }
        assertTrue(drained.await(5, TimeUnit.SECONDS))
        val occurrence = System.currentTimeMillis() - 1000
        val preferences = context.getSharedPreferences("care_alarms", 0)
        preferences.edit().putStringSet("scheduled", rows.map { "${it.id}:$userId:${it.revision}:$occurrence" }.toSet()).commit()
        rows.forEach { row ->
            app.careReminders.receive(Intent().apply {
                putExtra(CareReminderNotifications.ROUTINE_ID, row.id); putExtra(CareReminderNotifications.OWNER_ID, userId)
                putExtra("revision", row.revision); putExtra("occurrence", occurrence)
            })
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        // NotificationManager enqueues asynchronously on newer framework builds.
        val deadline = SystemClock.uptimeMillis() + 5000
        while (rows.any { row -> manager.activeNotifications.none { it.tag == "care:${row.id}" } } && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue(rows.all { row -> manager.activeNotifications.any { it.tag == "care:${row.id}" } })
    }

    @Test fun notificationPermissionDeniedStillKeepsRoutine() = runBlocking {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
        repository.save(userId, null, null, CareRoutineDraft(title = "Lịch chưa có quyền"))
        val row = repository.load(userId).single()
        CareReminderNotifications(context).show(row)
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.tag == "care:${row.id}" })
        assertEquals("Lịch chưa có quyền", repository.load(userId).single().title)
    }
}
