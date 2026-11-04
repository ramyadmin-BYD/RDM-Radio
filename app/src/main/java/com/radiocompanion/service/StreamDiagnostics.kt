package com.radiocompanion.service

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.radiocompanion.R
import com.radiocompanion.model.UiText
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
    val message: UiText,
    val isError: Boolean,
)

/**
 * In-app log of stream problems, readable on the car without a computer
 * (☰ → Stream diagnostics). Also written to Logcat under the tag "RdmStream".
 * The service and the UI run in the same process, so a shared object is enough.
 * Messages are [UiText], so they show in the screen's language.
 */
object StreamDiagnostics {

    private const val TAG = "RdmStream"
    private const val MAX_ENTRIES = 60

    /** Set by the Application; only used to write readable Logcat lines. */
    @Volatile var logContext: Context? = null

    private val _entries = MutableStateFlow<List<StreamLogEntry>>(emptyList())
    val entries: StateFlow<List<StreamLogEntry>> = _entries.asStateFlow()

    fun info(station: String, url: String?, message: UiText) = add(station, url, message, isError = false)

    fun error(station: String, url: String?, message: UiText) = add(station, url, message, isError = true)

    fun clear() { _entries.value = emptyList() }

    private fun add(station: String, url: String?, message: UiText, isError: Boolean) {
        val text = logContext?.let { message.resolve(it) } ?: "msg#${message.id}"
        if (isError) Log.w(TAG, "$station | $text | $url") else Log.i(TAG, "$station | $text | $url")
        val entry = StreamLogEntry(System.currentTimeMillis(), station, url, message, isError)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    /** Plain-language reason for a playback error, from the exception's cause chain. */
    @OptIn(UnstableApi::class)
    fun describe(error: PlaybackException): UiText {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(10).toList()
        causes.firstNotNullOfOrNull { it as? HttpDataSource.InvalidResponseCodeException }?.let {
            val code = it.responseCode
            return when (code) {
                401, 403 -> UiText(R.string.diag_http_refused, code)
                404, 410 -> UiText(R.string.diag_http_gone, code)
                in 500..599 -> UiText(R.string.diag_http_server, code)
                else -> UiText(R.string.diag_http_other, code)
            }
        }
        if (causes.any { it is HttpDataSource.CleartextNotPermittedException }) return UiText(R.string.diag_cleartext)
        if (causes.any { it is SSLException || it is CertificateException }) return UiText(R.string.diag_ssl)
        if (causes.any { it is UnknownHostException }) return UiText(R.string.diag_dns)
        if (causes.any { it is SocketTimeoutException }) return UiText(R.string.diag_timeout)
        if (causes.any { it is FileNotFoundException }) return UiText(R.string.diag_gone)
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> UiText(R.string.diag_connect_failed)
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> UiText(R.string.diag_timeout)
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> UiText(R.string.diag_not_audio)
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> UiText(R.string.diag_format)
            else -> UiText(R.string.diag_other, error.errorCodeName)
        }
    }
}
