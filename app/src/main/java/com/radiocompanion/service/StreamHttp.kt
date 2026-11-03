package com.radiocompanion.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource

/**
 * HTTP setup for radio streams. Many Egyptian/Arab stream servers (and their CDNs)
 * reject requests that don't look like a web browser, and many redirect between
 * http:// and https://, which ExoPlayer refuses to follow by default.
 */
@OptIn(UnstableApi::class)
object StreamHttp {

    /** A current mobile Chrome User-Agent. Update it once a year or so. */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    fun dataSourceFactory(context: Context): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(BROWSER_USER_AGENT)
            // http → https (and back) redirects are common when a station moves servers.
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Accept-Language" to "ar-EG,ar;q=0.9,en-US;q=0.8,en;q=0.7",
                )
            )
        // DefaultDataSource adds support for local files/assets/content:// on top of HTTP.
        return DefaultDataSource.Factory(context, http)
    }

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
}
