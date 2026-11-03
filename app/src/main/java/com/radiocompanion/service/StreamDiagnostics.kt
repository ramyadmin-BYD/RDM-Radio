package com.radiocompanion.service

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

data class StreamLogEntry(
    val timeMs: Long,
    val station: String,
    val url: String?,
    val message: String,
    val isError: Boolean,
)

/**
 * In-app log of stream problems, readable on the car without a computer
 * (⋮ → Stream diagnostics). Also written to Logcat under the tag "RdmStream".
 * The service and the UI run in the same process, so a shared object is enough.
 */
object StreamDiagnostics {

    private const val TAG = "RdmStream"
    private const val MAX_ENTRIES = 60

    private val _entries = MutableStateFlow<List<StreamLogEntry>>(emptyList())
    val entries: StateFlow<List<StreamLogEntry>> = _entries.asStateFlow()

    fun info(station: String, url: String?, message: String) = add(station, url, message, isError = false)

    fun error(station: String, url: String?, message: String) = add(station, url, message, isError = true)

    fun clear() { _entries.value = emptyList() }

    private fun add(station: String, url: String?, message: String, isError: Boolean) {
        if (isError) Log.w(TAG, "$station | $message | $url") else Log.i(TAG, "$station | $message | $url")
        val entry = StreamLogEntry(System.currentTimeMillis(), station, url, message, isError)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    /** Plain-language reason for a playback error, from the exception's cause chain. */
    @OptIn(UnstableApi::class)
    fun describe(error: PlaybackException): String {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(10).toList()
        causes.firstNotNullOfOrNull { it as? HttpDataSource.InvalidResponseCodeException }?.let {
            return when (it.responseCode) {
                401, 403 -> "Server refused the request (HTTP ${it.responseCode})"
                404, 410 -> "Stream address no longer exists (HTTP ${it.responseCode})"
                in 500..599 -> "Station server error (HTTP ${it.responseCode})"
                else -> "HTTP error ${it.responseCode}"
            }
        }
        if (causes.any { it is HttpDataSource.CleartextNotPermittedException }) return "Plain HTTP blocked by security settings"
        if (causes.any { it is SSLException || it is CertificateException }) return "Secure connection (SSL) failed"
        if (causes.any { it is UnknownHostException }) return "Server not found (DNS) or no internet"
        if (causes.any { it is SocketTimeoutException }) return "Connection timed out"
        if (causes.any { it is FileNotFoundException }) return "Stream address no longer exists"
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Couldn't connect to the station"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connection timed out"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Address isn't an audio stream (maybe a web page)"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Audio format not supported by this head unit"
            else -> error.errorCodeName
        }
    }
}
