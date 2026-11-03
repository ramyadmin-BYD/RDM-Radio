package com.radiocompanion.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.radiocompanion.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the UI shows about the current playback. */
data class NowPlaying(
    val stationId: String? = null,
    val stationName: String = "",
    val streamTitle: String? = null,   // ICY "StreamTitle" / HLS ID3 title
    val artworkUri: Uri? = null,
    val isPlaying: Boolean = false,
    val wantsToPlay: Boolean = false,  // playWhenReady: true while buffering/reconnecting too
    val isBuffering: Boolean = false,
    val hasError: Boolean = false,
    val errorDetail: String? = null,   // plain-language reason, from StreamDiagnostics
)

/** Connects the UI to [RadioPlaybackService] through a MediaController and exposes a StateFlow. */
class PlayerConnection(private val context: Context) {

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture !== future) return@addListener   // released meanwhile
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            publish(c)
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    /** Loads the whole visible list so steering-wheel next/previous moves through it. */
    fun play(stations: List<Station>, index: Int) {
        val c = controller ?: return
        if (index !in stations.indices) return
        c.setMediaItems(stations.map { it.toMediaItem() }, index, C.TIME_UNSET)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.playWhenReady) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    private fun publish(p: Player) {
        val item = p.currentMediaItem
        val station = (item?.mediaMetadata?.station ?: item?.mediaMetadata?.title)?.toString().orEmpty()
        val live = p.mediaMetadata.title?.toString()?.trim()
        _state.value = NowPlaying(
            stationId = item?.mediaId,
            stationName = station,
            streamTitle = live?.takeIf { it.isNotEmpty() && it != station },
            artworkUri = item?.mediaMetadata?.artworkUri,
            isPlaying = p.isPlaying,
            wantsToPlay = p.playWhenReady,
            isBuffering = p.playWhenReady && p.playbackState == Player.STATE_BUFFERING,
            hasError = p.playerError != null,
            errorDetail = if (p.playerError != null) {
                StreamDiagnostics.entries.value.firstOrNull { it.isError }?.message
            } else null,
        )
    }
}
