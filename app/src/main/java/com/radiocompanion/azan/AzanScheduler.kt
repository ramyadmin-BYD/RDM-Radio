package com.radiocompanion.azan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.Instant

/**
 * Keeps exactly one alarm set: the next enabled Azan. Rescheduled when settings change,
 * after every Azan, after a reboot (car start) and when the clock or time zone changes.
 */
class AzanScheduler(private val context: Context, private val repo: PrayerRepository) {

    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    suspend fun reschedule(after: Instant = Instant.now()) {
        val place = repo.currentPlace()
        val pi = alarmIntent()
        alarms?.cancel(pi)
        val (prayer, time) = repo.nextAzan(place, after) ?: return
        val at = time.toInstant().toEpochMilli()
        val intent = alarmIntent(prayer.name, at)
        val am = alarms ?: return
        // Exact alarms are pre-granted to this app (USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM);
        // fall back to a one-minute window if a ROM revoked them.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, intent)
        }
        Log.i(TAG, "Next Azan: $prayer at $time")
    }

    fun cancel() { alarms?.cancel(alarmIntent()) }

    private fun alarmIntent(prayer: String? = null, at: Long = 0L): PendingIntent {
        val intent = Intent(context, AzanReceiver::class.java).setAction(AzanReceiver.ACTION_AZAN)
        if (prayer != null) intent.putExtra(AzanReceiver.EXTRA_PRAYER, prayer).putExtra(AzanReceiver.EXTRA_AT, at)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object { const val TAG = "AzanScheduler" }
}
