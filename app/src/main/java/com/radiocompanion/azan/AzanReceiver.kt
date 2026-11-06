package com.radiocompanion.azan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.radiocompanion.RadioCompanionApp
import com.radiocompanion.prayer.Prayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/** Azan alarm, plus boot / clock-change events that need the next alarm set again. */
class AzanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as RadioCompanionApp
        val pending = goAsync()
        scope.launch {
            try {
                var after = Instant.now()
                if (intent.action == ACTION_AZAN) {
                    val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
                    val at = intent.getLongExtra(EXTRA_AT, 0L)
                    // Skip an Azan that is badly late (device was asleep with alarms deferred).
                    val lateMs = System.currentTimeMillis() - at
                    if (prayer != null && lateMs < MAX_LATE_MS) AzanService.start(context, prayer)
                    // Never pick the same prayer again if the alarm fired a moment early.
                    if (at > 0) after = maxOf(after, Instant.ofEpochMilli(at + 1_000))
                }
                app.azanScheduler.reschedule(after)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_AZAN = "com.radiocompanion.action.AZAN"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_AT = "at"
        private const val MAX_LATE_MS = 10 * 60_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
