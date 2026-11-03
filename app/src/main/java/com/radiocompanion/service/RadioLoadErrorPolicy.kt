package com.radiocompanion.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/**
 * Retries inside the player for short network drops. Errors that won't fix
 * themselves (4xx, SSL, blocked cleartext) fail at once, so the service can move
 * on to the station's backup address instead of spinning.
 */
@OptIn(UnstableApi::class)
class RadioLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 3) {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val causes = generateSequence<Throwable>(loadErrorInfo.exception) { it.cause }.take(10).toList()
        val permanent = causes.any { e ->
            (e is HttpDataSource.InvalidResponseCodeException && e.responseCode in 400..499) ||
                e is HttpDataSource.CleartextNotPermittedException ||
                e is SSLException || e is CertificateException
        }
        if (permanent) return C.TIME_UNSET
        return minOf(1_000L * loadErrorInfo.errorCount, 4_000L)
    }
}
