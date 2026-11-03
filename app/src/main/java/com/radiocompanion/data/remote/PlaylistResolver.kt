package com.radiocompanion.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * ExoPlayer plays HLS (.m3u8) and direct streams itself, but not .pls or plain
 * .m3u playlist files, which many station websites link to. This turns those
 * into the real stream URL.
 */
class PlaylistResolver(private val http: OkHttpClient) {

    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        if (!looksLikePlaylist(url)) return@withContext url
        val body = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return@withContext url
        parse(body) ?: url
    }

    companion object {
        fun looksLikePlaylist(url: String): Boolean {
            val path = url.substringBefore('#').substringBefore('?').lowercase()
            return path.endsWith(".pls") || path.endsWith(".m3u")
        }

        /** First http(s) stream in a .pls ("File1=http://…") or .m3u (bare line) body. */
        fun parse(body: String): String? =
            body.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("[") }
                .map { line ->
                    if (line.substringBefore('=').startsWith("File", ignoreCase = true)) {
                        line.substringAfter('=').trim()
                    } else line
                }
                .firstOrNull { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }
}
