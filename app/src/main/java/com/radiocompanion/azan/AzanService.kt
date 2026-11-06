package com.radiocompanion.azan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.radiocompanion.R
import com.radiocompanion.RadioCompanionApp
import com.radiocompanion.prayer.Prayer
import com.radiocompanion.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Plays the Azan, interrupting whatever is playing and handing back afterwards.
 *
 * 1. Shows the Azan notification (foreground service, so it can't be killed mid-Azan).
 * 2. Fades RDM Radio out and stops it, if it is playing ([AzanInterruptible]).
 * 3. Takes *transient* audio focus: other apps (Bluetooth music, the car's own radio)
 *    pause and resume by themselves when we let go.
 * 4. Plays the recording: the user's chosen file/link, else assets/azan/azan.mp3 if
 *    bundled, else the system alarm sound.
 * 5. Gives up focus and restarts the station on a fresh live connection.
 *
 * Pressing play on the radio (screen or steering wheel) during the Azan takes the
 * audio focus back, which ends the Azan; the radio is then already playing.
 */
@OptIn(UnstableApi::class)
class AzanService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as RadioCompanionApp
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val main = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var running = false
    private var radioPaused = false
    private var pausedByFocusLoss = false
    private var sources: List<String> = emptyList()
    private var sourceIndex = 0
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> begin(intent.getStringExtra(EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() })
            ACTION_STOP -> finish(resumeRadio = true)
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running) finish(resumeRadio = true)
        main.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ flow

    private fun begin(prayer: Prayer?) {
        // Always promote right away: startForegroundService() demands it within seconds.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(prayer), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (running) return
        running = true
        app.azanCoordinator.set(AzanState.Playing(prayer, radioWillResume = false))

        job = scope.launch {
            radioPaused = runCatching { app.azanCoordinator.radio?.pauseForAzan() }.getOrNull() ?: false
            app.azanCoordinator.set(AzanState.Playing(prayer, radioWillResume = radioPaused))

            if (!requestFocus()) {
                // A phone call holds the audio: no sound, but leave a visible reminder.
                postReminder(this@AzanService, prayer)
                finish(resumeRadio = true)
                return@launch
            }
            sources = soundSources(prayer)
            sourceIndex = 0
            playCurrentSource()

            delay(MAX_AZAN_MS)   // safety net for a link that streams forever
            finish(resumeRadio = true)
        }
    }

    private fun finish(resumeRadio: Boolean) {
        if (!running) {
            stopSelf()
            return
        }
        running = false
        job?.cancel()
        job = null
        player?.release()
        player = null
        focusRequest?.let { audio?.abandonAudioFocusRequest(it) }
        focusRequest = null
        if (radioPaused && resumeRadio) app.azanCoordinator.radio?.resumeAfterAzan()
        radioPaused = false
        app.azanCoordinator.set(AzanState.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------------ playback

    private fun playCurrentSource() {
        val uri = sources.getOrNull(sourceIndex) ?: return finish(resumeRadio = true)
        val p = player ?: ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ false,   // we hold transient focus ourselves
            )
            .build()
            .also { created ->
                player = created
                created.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        // Not from inside the player's own callback: it releases the player.
                        if (state == Player.STATE_ENDED) main.post { finish(resumeRadio = true) }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "Azan source failed: ${sources.getOrNull(sourceIndex)}", error)
                        sourceIndex++
                        main.post { if (running) playCurrentSource() }
                    }
                })
            }
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.play()
    }

    /** User's choice first, then the bundled recording, then the system alarm sound. */
    private suspend fun soundSources(prayer: Prayer?): List<String> {
        val chosen = app.settings.prayer.first().azanSoundUri
        val bundled = runCatching { assets.list("azan")?.toList() }.getOrNull().orEmpty()
        val fajr = "azan_fajr.mp3".takeIf { prayer == Prayer.FAJR && it in bundled }
        val regular = "azan.mp3".takeIf { it in bundled }
        val system = (RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.toString()
        return listOfNotNull(chosen, (fajr ?: regular)?.let { "asset:///azan/$it" }, system).distinct()
    }

    // ------------------------------------------------------------------ audio focus

    private fun requestFocus(): Boolean {
        val am = audio ?: return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(::onFocusChange, main)
            .build()
        focusRequest = request
        return am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun onFocusChange(change: Int) {
        val p = player
        when (change) {
            // Someone else started playing for good (often: the user pressed play on the radio).
            AudioManager.AUDIOFOCUS_LOSS -> finish(resumeRadio = false)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { pausedByFocusLoss = true; p?.pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> p?.volume = DUCK_VOLUME
            AudioManager.AUDIOFOCUS_GAIN -> {
                p?.volume = 1f
                if (pausedByFocusLoss) { pausedByFocusLoss = false; p?.play() }
            }
        }
    }

    // ------------------------------------------------------------------ notification

    private fun notification(prayer: Prayer?) =
        NotificationCompat.Builder(this, ensureChannel(this))
            .setSmallIcon(R.drawable.ic_stat_crescent)
            .setContentTitle(title(this, prayer))
            .setContentText(getString(R.string.azan_notification_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setSilent(true)   // the Azan itself is the sound
            .setContentIntent(openApp(this))
            .addAction(
                0, getString(R.string.azan_stop),
                PendingIntent.getService(
                    this, 1, Intent(this, AzanService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    companion object {
        private const val TAG = "AzanService"
        const val ACTION_PLAY = "com.radiocompanion.action.PLAY_AZAN"
        const val ACTION_STOP = "com.radiocompanion.action.STOP_AZAN"
        const val EXTRA_PRAYER = "prayer"
        private const val CHANNEL_ID = "azan"
        private const val NOTIFICATION_ID = 7001
        private const val REMINDER_ID = 7002
        private const val MAX_AZAN_MS = 8 * 60_000L
        private const val DUCK_VOLUME = 0.3f

        /** [prayer] null = test from the settings screen. */
        fun start(context: Context, prayer: Prayer?) {
            val intent = Intent(context, AzanService::class.java).setAction(ACTION_PLAY)
            prayer?.let { intent.putExtra(EXTRA_PRAYER, it.name) }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android 12+ can refuse a background start (alarm not exact): notify instead.
                Log.w(TAG, "Could not start the Azan", e)
                postReminder(context, prayer)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AzanService::class.java).setAction(ACTION_STOP))
        }

        private fun title(context: Context, prayer: Prayer?): String =
            if (prayer == null) context.getString(R.string.azan_test_title)
            else context.getString(R.string.azan_title, context.getString(prayer.labelRes()))

        private fun openApp(context: Context) = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Plain "time for prayer" notification, used when the Azan can't sound. */
        fun postReminder(context: Context, prayer: Prayer?) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val n = NotificationCompat.Builder(context, ensureChannel(context))
                .setSmallIcon(R.drawable.ic_stat_crescent)
                .setContentTitle(title(context, prayer))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(30 * 60_000L)
                .setContentIntent(openApp(context))
                .build()
            runCatching { nm.notify(REMINDER_ID, n) }   // no-op without notification permission
        }

        private fun ensureChannel(context: Context): String {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.azan_channel), NotificationManager.IMPORTANCE_HIGH)
                        .apply {
                            setSound(null, null)
                            enableVibration(false)
                        }
                )
            }
            return CHANNEL_ID
        }
    }
}

/** String resource for a prayer's name. */
fun Prayer.labelRes(): Int = when (this) {
    Prayer.FAJR -> R.string.prayer_fajr
    Prayer.SUNRISE -> R.string.prayer_sunrise
    Prayer.DHUHR -> R.string.prayer_dhuhr
    Prayer.ASR -> R.string.prayer_asr
    Prayer.MAGHRIB -> R.string.prayer_maghrib
    Prayer.ISHA -> R.string.prayer_isha
}
