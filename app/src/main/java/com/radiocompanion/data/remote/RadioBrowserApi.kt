package com.radiocompanion.data.remote

import com.radiocompanion.data.FmClassifier
import com.radiocompanion.model.Station
import com.radiocompanion.model.StationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class RbServer(val name: String)

@Serializable
data class RbStation(
    @SerialName("stationuuid") val uuid: String,
    val name: String = "",
    val url: String = "",
    @SerialName("url_resolved") val urlResolved: String = "",
    val favicon: String = "",
    val tags: String = "",
    @SerialName("countrycode") val countryCode: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Int = 0,
) {
    val playableUrl: String get() = urlResolved.ifBlank { url }
}

fun RbStation.toStation(sortOrder: Int): Station {
    val stream = playableUrl.trim()
    val cleanName = name.trim().ifBlank { stream }
    val backups = listOf(url.trim()).filter { it.isNotBlank() && it != stream }
    return Station(
        id = "rb:$uuid",
        name = cleanName,
        streamUrl = stream,
        backupStreamUrls = backups,
        logoUrl = favicon.trim().ifBlank { null },
        tags = tags,
        countryCode = countryCode.uppercase(),
        codec = codec,
        bitrate = bitrate,
        isHls = hls == 1 || stream.substringBefore('?').lowercase().endsWith(".m3u8"),
        isOfficialFm = FmClassifier.looksOfficial(cleanName, tags),
        source = StationSource.RADIO_BROWSER,
        sortOrder = sortOrder,
    )
}

/**
 * Client for the free, community-run radio-browser.info directory.
 * The project asks clients to pick a mirror at runtime instead of hard-coding one.
 */
class RadioBrowserApi(private val http: OkHttpClient, private val json: Json) {

    @Volatile private var cachedHosts: List<String>? = null

    /** Most-listened stations in one country (e.g. "EG"). */
    suspend fun byCountry(countryCode: String, limit: Int = 200): List<RbStation> =
        search("countrycode" to countryCode.uppercase(), limit = limit)

    /** Most-listened stations broadcasting in a language (e.g. "arabic"): the regional list. */
    suspend fun byLanguage(language: String, limit: Int = 200): List<RbStation> =
        search("language" to language, limit = limit)

    /** Most-listened stations worldwide: the international list. */
    suspend fun topWorldwide(limit: Int = 150): List<RbStation> = search(limit = limit)

    private suspend fun search(vararg filters: Pair<String, String>, limit: Int): List<RbStation> =
        withContext(Dispatchers.IO) {
            withAnyHost { host ->
                val url = "https://$host/json/stations/search".toHttpUrl().newBuilder()
                    .apply { filters.forEach { (k, v) -> addQueryParameter(k, v) } }
                    .addQueryParameter("hidebroken", "true")
                    .addQueryParameter("order", "clickcount")
                    .addQueryParameter("reverse", "true")
                    .addQueryParameter("limit", limit.toString())
                    .build()
                http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("radio-browser HTTP ${resp.code}")
                    json.decodeFromString(ListSerializer(RbStation.serializer()), resp.body!!.string())
                }
            }
        }

    /** Tries each mirror in turn so one dead server doesn't break the app. */
    private fun <T> withAnyHost(block: (String) -> T): T {
        var last: Exception? = null
        for (host in hosts()) {
            try {
                return block(host)
            } catch (e: Exception) {
                last = e
            }
        }
        cachedHosts = null   // re-discover next time
        throw last ?: IOException("No radio-browser server reachable")
    }

    private fun hosts(): List<String> = cachedHosts ?: run {
        val discovered = runCatching {
            val req = Request.Builder().url("https://all.api.radio-browser.info/json/servers").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) emptyList()
                else json.decodeFromString(ListSerializer(RbServer.serializer()), resp.body!!.string())
                    .map { it.name }
            }
        }.getOrDefault(emptyList())
        (discovered.distinct().shuffled() + FALLBACK_HOSTS).distinct().also { cachedHosts = it }
    }

    private companion object {
        val FALLBACK_HOSTS = listOf(
            "de1.api.radio-browser.info",
            "nl1.api.radio-browser.info",
            "at1.api.radio-browser.info",
        )
    }
}
