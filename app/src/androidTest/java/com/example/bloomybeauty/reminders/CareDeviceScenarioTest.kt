package com.example.bloomybeauty.reminders

import android.app.NotificationManager
import android.os.Bundle
import android.os.PowerManager
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.BloomyBeautyApplication
import com.example.bloomybeauty.data.auth.AuthResult
import com.example.bloomybeauty.data.care.CareRoutineContract
import com.example.bloomybeauty.data.care.CareRoutineDraft
import com.example.bloomybeauty.data.care.CareRoutineRules
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in fixture for scripts/qa-care-emulator.py. Never included in the application APK. */
class CareDeviceScenarioTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as BloomyBeautyApplication
    private val fixture get() = context.getSharedPreferences("care_device_qa", 0)

    @Test fun deviceScenario() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val action = arguments.getString("careScenario")
        assumeTrue(action != null)
        when (action) {
            "prepare" -> {
                check(!fixture.contains("user_id")) { "Clean up the previous care QA fixture first" }
                val original = app.careStore.currentUserId()
                val password = "CareDeviceQA123!"
                val user = (app.authRepository.register("Khách kiểm thử giả lập", "care-device-${UUID.randomUUID()}@example.com", password, password) as AuthResult.Success).user
                check(fixture.edit().putLong("user_id", user.id).putLong("original_user_id", original ?: -1).commit())
                val lead = arguments.getString("careLeadSeconds")?.toLong() ?: 120L
                require(lead in 30..3600)
                val desired = Instant.now().plusSeconds(lead).atZone(ZoneId.systemDefault())
                app.careRepository.save(user.id, null, null, CareRoutineDraft(
                    title = "Nhắc kiểm thử giả lập", minuteOfDay = desired.hour * 60 + desired.minute,
                ))
                val row = app.careRepository.load(user.id).single()
                check(fixture.edit().putLong("routine_id", row.id).commit())
                drain()
                app.careReminders.reconcile()
            }
            "inspect" -> check(fixture.contains("user_id")) { "No care QA fixture" }
            "cleanup" -> {
                if (fixture.contains("user_id")) {
                    val id = fixture.getLong("user_id", -1)
                    val original = fixture.getLong("original_user_id", -1)
                    app.authRepository.logout()
                    app.databaseHelper.writableDatabase.delete("users", "_id=?", arrayOf(id.toString()))
                    if (original > 0) {
                        app.databaseHelper.writableDatabase.execSQL(
                            "INSERT OR REPLACE INTO auth_session(_id,user_id) SELECT 1,_id FROM users WHERE _id=?", arrayOf(original))
                    }
                    fixture.edit().clear().commit()
                    context.contentResolver.notifyChange(CareRoutineContract.CONTENT_URI, null)
                    app.careReminders.reconcile()
                }
            }
            else -> error("Unknown care QA action")
        }
        val snapshot = snapshot()
        instrumentation.sendStatus(0, Bundle().apply { putString("care_snapshot", snapshot.toString()) })
    }

    private fun drain() {
        val completed = CountDownLatch(1)
        app.runCareTask { completed.countDown() }
        check(completed.await(10, TimeUnit.SECONDS)) { "Care executor did not drain" }
    }

    private fun snapshot(): JSONObject {
        val result = JSONObject()
            .put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("package", context.packageName)
            .put("now", System.currentTimeMillis())
            .put("zone", ZoneId.systemDefault().id)
            .put("idle", context.getSystemService(PowerManager::class.java).isDeviceIdleMode)
            .put("userId", fixture.getLong("user_id", -1))
            .put("routineId", fixture.getLong("routine_id", -1))
        val id = fixture.getLong("routine_id", -1)
        app.databaseHelper.readableDatabase.rawQuery("SELECT * FROM care_routines WHERE _id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) {
                val minute = c.getInt(c.getColumnIndexOrThrow("minute_of_day"))
                val days = c.getInt(c.getColumnIndexOrThrow("days_mask"))
                val last = c.getColumnIndexOrThrow("last_occurrence_at")
                result.put("minute", minute).put("days", days)
                    .put("lastOccurrence", if (c.isNull(last)) JSONObject.NULL else c.getLong(last))
                    .put("expectedNext", CareRoutineRules.nextOccurrence(minute, days, Instant.now(), ZoneId.systemDefault()).toEpochMilli())
            }
        }
        val entries = context.getSharedPreferences("care_alarms", 0).getStringSet("scheduled", emptySet()).orEmpty()
        result.put("scheduled", JSONArray(entries.filter { it.startsWith("$id:") }.sorted()))
        result.put("notified", context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.tag == "care:$id" })
        return result
    }
}
