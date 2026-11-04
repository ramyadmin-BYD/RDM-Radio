package com.radiocompanion.service

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.radiocompanion.R
import com.radiocompanion.RadioCompanionApp
import com.radiocompanion.model.UiText
import com.radiocompanion.model.streamCandidates
import com.radiocompanion.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * Radio playback service.
 *
 * - Foreground service of type mediaPlayback (Media3 promotes it while playing).
 * - Audio focus handled by ExoPlayer: pauses for calls, ducks under navigation prompts.
 * - Browser-like HTTP requests (see [StreamHttp]).
 * - When a stream fails it tries, in order: the same address again (short network
 *   drops), the station's backup addresses, then the http/https variant of each.
 *   A backup that works becomes the station's main address.
 * - Every failure and switch is written to [StreamDiagnostics].
 * - Exposes stations as a browse tree for the system media center and restores
 *   the last station after the process was killed.
 */
@OptIn(UnstableApi::class)
class RadioPlaybackService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val app get() = application as RadioCompanionApp
    private val dao by lazy { app.database.stationDao() }

    private lateinit var player: ExoPlayer
    private lateinit var lastStation: LastStationStore
    private var session: MediaLibrarySession? = null

    // ---- Fallback state for the station currently loaded
    private var stationId: String? = null
    private var candidates: List<String> = emptyList()
    private var candidateIndex = 0
    private var retriesOnThisUrl = 0
    private var fullCycles = 0
    private var backoffStep = 0
    private var readyOnThisUrl = false
    private var pendingAction: Runnable? = null
    private var internalSwitchUrl: String? = null   // set while we swap in a backup URL

    private val bufferingWatchdog = Runnable {
        if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady && !readyOnThisUrl && isOnline()) {
            StreamDiagnostics.error(stationName(), currentUrl(), UiText(R.string.svc_no_audio, (STARTUP_TIMEOUT_MS / 1000).toInt()))
            moveToNextCandidate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        lastStation = LastStationStore(this)

        val mediaSourceFactory = DefaultMediaSourceFactory(StreamHttp.dataSourceFactory(this))
            .setLoadErrorHandlingPolicy(RadioLoadErrorPolicy())

        // Bigger buffer than the default: rides through cell handoffs and short tunnels.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 2_000,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL   // "next" on the last station wraps to the first
                addListener(playerListener)
            }

        // Fixed audio session so the equalizer effects attach once and stay attached.
        val audioSessionId = getSystemService(AudioManager::class.java)?.generateAudioSessionId() ?: 0
        if (audioSessionId > 0) {
            player.setAudioSessionId(audioSessionId)
            app.audioEffects.attach(audioSessionId)
        } else {
            StreamDiagnostics.error(getString(R.string.eq_title), null, UiText(R.string.svc_no_session))
        }

        // Load (but don't play) the last station so the play button works right away.
        lastStation.load()?.let { player.setMediaItem(it) }

        val openUi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openUi)
            .build()

        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Keep playing when swiped away from recents. It's a car radio. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
        }
        main.removeCallbacksAndMessages(null)
        scope.cancel()
        app.audioEffects.detach()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- player events

    private val playerListener = object : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem ?: return
            val url = mediaItem.localConfiguration?.uri?.toString()
            val isOurBackupSwap = url != null && url == internalSwitchUrl
            internalSwitchUrl = null
            // A new station, or the user picked the same station again from the list.
            if (mediaItem.mediaId != stationId ||
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && !isOurBackupSwap)
            ) {
                startStation(mediaItem)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // The user pressed play again after we gave up: start over with fresh counters.
            if (playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                fullCycles = 0
                retriesOnThisUrl = 0
                backoffStep = 0
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> if (!readyOnThisUrl && player.playWhenReady) {
                    main.removeCallbacks(bufferingWatchdog)
                    main.postDelayed(bufferingWatchdog, STARTUP_TIMEOUT_MS)
                }
                Player.STATE_READY -> {
                    main.removeCallbacks(bufferingWatchdog)
                    if (!readyOnThisUrl) onUrlWorks()
                }
                else -> main.removeCallbacks(bufferingWatchdog)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            main.removeCallbacks(bufferingWatchdog)
            val reason = StreamDiagnostics.describe(error)
            StreamDiagnostics.error(stationName(), currentUrl(), reason)

            when {
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    player.seekToDefaultPosition()
                    player.prepare()
                }
                !player.playWhenReady -> Unit
                // Car is offline (tunnel, garage): keep this URL; the network callback retries.
                !isOnline() -> retrySameUrl(countAgainstUrl = false)
                isTransient(error) && retriesOnThisUrl < RETRIES_PER_URL -> retrySameUrl(countAgainstUrl = true)
                else -> moveToNextCandidate()
            }
        }
    }

    /** A new station was selected: build its list of addresses to try. */
    private fun startStation(item: MediaItem) {
        cancelPending()
        stationId = item.mediaId
        val first = item.localConfiguration?.uri?.toString()
        candidates = listOfNotNull(first)
        candidateIndex = 0
        retriesOnThisUrl = 0
        fullCycles = 0
        backoffStep = 0
        readyOnThisUrl = false
        scope.launch {
            val station = dao.getById(item.mediaId) ?: return@launch
            if (stationId != item.mediaId) return@launch   // user already switched again
            candidates = (listOfNotNull(first) + station.streamCandidates()).distinct()
        }
    }

    private fun onUrlWorks() {
        readyOnThisUrl = true
        // Remember the last station that actually played (with the address that worked),
        // so the app can resume it automatically on the next start.
        player.currentMediaItem?.let(lastStation::save)
        retriesOnThisUrl = 0
        backoffStep = 0
        fullCycles = 0
        val url = currentUrl()
        if (candidateIndex > 0 && url != null) {
            StreamDiagnostics.info(stationName(), url, UiText(R.string.svc_backup_works))
            val id = stationId ?: return
            scope.launch { runCatching { app.repository.promoteWorkingUrl(id, url) } }
        }
    }

    private fun retrySameUrl(countAgainstUrl: Boolean) {
        if (countAgainstUrl) retriesOnThisUrl++
        postAction(nextBackoffMs()) {
            if (player.playerError != null && player.playWhenReady) player.prepare()
        }
    }

    /** Next backup address; after the last one, start over a few times, then give up. */
    private fun moveToNextCandidate() {
        if (candidateIndex + 1 < candidates.size) {
            switchToCandidate(candidateIndex + 1, delayMs = 0)
            return
        }
        if (fullCycles < MAX_FULL_CYCLES && candidates.isNotEmpty()) {
            fullCycles++
            switchToCandidate(0, delayMs = nextBackoffMs())
            return
        }
        StreamDiagnostics.error(stationName(), currentUrl(), UiText(R.string.svc_all_failed, candidates.size))
        cancelPending()
        player.pause()
    }

    private fun switchToCandidate(index: Int, delayMs: Long) {
        val url = candidates.getOrNull(index) ?: return
        postAction(delayMs) {
            val item = player.currentMediaItem ?: return@postAction
            if (item.mediaId != stationId || !player.playWhenReady) return@postAction
            candidateIndex = index
            retriesOnThisUrl = 0
            readyOnThisUrl = false
            if (index > 0) StreamDiagnostics.info(stationName(), url, UiText(R.string.svc_trying, index + 1, candidates.size))
            val replacement = item.buildUpon()
                .setUri(url)
                .setMimeType(if (isHlsUrl(url)) MimeTypes.APPLICATION_M3U8 else null)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(url.toUri()).build())
                .build()
            internalSwitchUrl = url
            player.replaceMediaItem(player.currentMediaItemIndex, replacement)
            player.prepare()
        }
    }

    private fun postAction(delayMs: Long, block: () -> Unit) {
        cancelPending()
        val r = Runnable { pendingAction = null; block() }
        pendingAction = r
        main.postDelayed(r, delayMs)
    }

    private fun cancelPending() {
        pendingAction?.let(main::removeCallbacks)
        pendingAction = null
    }

    /** 1s, 2s, 4s … capped at 30s. */
    private fun nextBackoffMs(): Long =
        (1_000L shl backoffStep.coerceAtMost(5)).coerceAtMost(30_000L).also { backoffStep++ }

    private fun isTransient(e: PlaybackException): Boolean = e.errorCode in setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_TIMEOUT,
    )

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun currentUrl(): String? = player.currentMediaItem?.localConfiguration?.uri?.toString()

    private fun stationName(): String =
        player.currentMediaItem?.mediaMetadata?.let { it.station ?: it.title }?.toString() ?: "?"

    /** Leaving a tunnel or parking garage: retry now instead of waiting for the backoff. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post {
                if (player.playerError != null && player.playWhenReady) {
                    cancelPending()
                    backoffStep = 0
                    player.prepare()
                }
            }
        }
    }

    // ---------------------------------------------------------------- session callback

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            if (parentId != ROOT_ID) {
                return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
            // Favorites first; fall back to everything if nothing is favorited yet.
            val stations = dao.getFavorites().ifEmpty { dao.getAll() }
            val paged = if (pageSize in 1 until Int.MAX_VALUE) {
                stations.drop(page * pageSize).take(pageSize)
            } else stations
            LibraryResult.ofItemList(paged.map { it.toMediaItem() }, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val station = dao.getById(mediaId)
            if (station != null) LibraryResult.ofItem(station.toMediaItem(), null)
            else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        /**
         * Rebuilds playable items. Our own UI sends the full item; the system media
         * center may send only a mediaId, so look that up in the database.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.mapNotNull { item ->
                val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                if (uri != null) {
                    item.buildUpon()
                        .setUri(uri)
                        .apply {
                            if (isHlsUrl(uri.toString()) || item.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8) {
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                            }
                        }
                        .build()
                } else {
                    dao.getById(item.mediaId)?.toMediaItem()
                }
            }.toMutableList()
        }

        /** Steering-wheel "play" after the process was killed: bring back the last station. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val item = lastStation.load()
                ?: return Futures.immediateFailedFuture(IllegalStateException("No station played yet"))
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET)
            )
        }
    }

    private fun rootItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.svc_root_title))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .build()
            )
            .build()

    private companion object {
        const val ROOT_ID = "root"
        const val RETRIES_PER_URL = 2
        const val MAX_FULL_CYCLES = 2
        const val STARTUP_TIMEOUT_MS = 20_000L
    }
}
