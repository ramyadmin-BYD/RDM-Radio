package com.radiocompanion.update

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.radiocompanion.BuildConfig
import com.radiocompanion.R
import com.radiocompanion.data.settings.SettingsStore
import com.radiocompanion.model.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

/** A newer release found on GitHub. */
data class AvailableUpdate(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val percent: Int) : UpdateState
    data class NeedsPermission(val update: AvailableUpdate) : UpdateState
    data class Installing(val update: AvailableUpdate) : UpdateState
    data class Failed(val update: AvailableUpdate, val reason: UiText) : UpdateState
}

/**
 * In-app updates from GitHub Releases:
 * 1. On startup (at most every 12 h) asks GET /repos/{owner}/{repo}/releases/latest.
 * 2. If its tag is a higher version than this app, shows an update prompt.
 * 3. Downloads the release APK, checks it is RDM Radio at that version, and hands
 *    it to Android's PackageInstaller. Android shows its own confirmation, and only
 *    accepts the file if it is signed with the same key as the installed app.
 *
 * Debug builds never update (they are a separate app id).
 */
class UpdateManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val currentVersion = BuildConfig.VERSION_NAME
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    /** One-off results of a manual check ("up to date", "couldn't check"). */
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    /** Whether this build can update itself at all. */
    val isSupported: Boolean
        get() = !BuildConfig.DEBUG && Versions.parse(currentVersion) != null && BuildConfig.UPDATE_REPO.contains('/')

    // ------------------------------------------------------------------ check

    /** Quiet check on app start; only every 12 hours. */
    fun checkOnStartup() {
        if (!isSupported) return
        scope.launch {
            if (settings.isUpdateCheckDue()) check(manual = false)
        }
    }

    /** ☰ → Check for updates. Reports the result as a message. */
    fun checkNow() {
        scope.launch {
            if (!isSupported) {
                _messages.trySend(UiText(R.string.msg_up_to_date, currentVersion))
                return@launch
            }
            check(manual = true)
        }
    }

    private suspend fun check(manual: Boolean) {
        val release = try {
            fetchLatestRelease()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            if (manual) _messages.trySend(UiText(R.string.msg_update_check_failed))
            return
        }
        settings.markUpdateChecked()

        val apk = release?.assets?.firstOrNull { it.name == Versions.pickApk(release.assets.map { a -> a.name }) }
        if (release == null || release.draft || release.prerelease || apk == null ||
            !Versions.isNewer(release.tagName, currentVersion)
        ) {
            if (manual) _messages.trySend(UiText(R.string.msg_up_to_date, currentVersion))
            return
        }
        val update = AvailableUpdate(
            version = release.tagName.removePrefix("v"),
            notes = Versions.plainNotes(release.body),
            apkUrl = apk.downloadUrl,
            apkSize = apk.size,
        )
        // Don't interrupt a download/install already in progress.
        if (_state.value is UpdateState.Idle || _state.value is UpdateState.Available) {
            _state.value = UpdateState.Available(update)
        }
    }

    /** Null when the repository has no published release yet (HTTP 404). */
    private suspend fun fetchLatestRelease(): GhRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        http.newCall(request).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) throw IOException("GitHub HTTP ${resp.code}")
            json.decodeFromString(GhRelease.serializer(), resp.body!!.string())
        }
    }

    // ------------------------------------------------------------------ prompt actions

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    /** "Update now": checks the install permission, then downloads and installs. */
    fun startUpdate() {
        val update = currentUpdate() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(update)
            return
        }
        job?.cancel()
        job = scope.launch { downloadAndInstall(update) }
    }

    /** Opens Android's "Install unknown apps" page for RDM Radio. */
    fun openInstallPermissionSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        runCatching { activity.startActivity(intent) }
            .onFailure { fail(UiText(R.string.update_err_blocked)) }
    }

    private fun currentUpdate(): AvailableUpdate? = when (val s = _state.value) {
        is UpdateState.Available -> s.update
        is UpdateState.NeedsPermission -> s.update
        is UpdateState.Failed -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.Installing -> s.update
        UpdateState.Idle -> null
    }

    // ------------------------------------------------------------------ download + install

    private suspend fun downloadAndInstall(update: AvailableUpdate) {
        _state.value = UpdateState.Downloading(update, 0)
        val file = try {
            download(update)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update download failed", e)
            fail(UiText(R.string.update_err_download))
            return
        }
        if (!isValidUpdate(file, update)) {
            file.delete()
            fail(UiText(R.string.update_err_verify))
            return
        }
        _state.value = UpdateState.Installing(update)
        try {
            withContext(Dispatchers.IO) { install(file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "PackageInstaller failed", e)
            fail(UiText(R.string.update_err_blocked))
        }
    }

    private suspend fun download(update: AvailableUpdate): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // keep only the newest download
        val target = File(dir, "RDMRadio-${update.version}.apk")
        http.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: update.apkSize
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = UpdateState.Downloading(update, percent)
                            }
                        }
                    }
                }
            }
        }
        if (update.apkSize > 0 && target.length() != update.apkSize) {
            throw IOException("Size mismatch: ${target.length()} != ${update.apkSize}")
        }
        target
    }

    /** The file must be this app (same package) at the advertised version. */
    private fun isValidUpdate(file: File, update: AvailableUpdate): Boolean {
        val pm = context.packageManager
        val info: PackageInfo? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.path, 0)
            }
        }.getOrNull()
        return info != null && info.packageName == context.packageName && info.versionName == update.version
    }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
                flags,
            )
            session.commit(callback.intentSender)
        }
    }

    /** Called by [UpdateInstallReceiver] when the installer finishes without restarting us. */
    internal fun onInstallFinished(status: Int) {
        val update = currentUpdate() ?: return
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = UpdateState.Available(update)   // user cancelled
            PackageInstaller.STATUS_FAILURE_BLOCKED -> fail(UiText(R.string.update_err_blocked))
            else -> fail(UiText(R.string.update_err_install))
        }
    }

    private fun fail(reason: UiText) {
        val update = currentUpdate() ?: return
        _state.value = UpdateState.Failed(update, UiText(R.string.update_failed, reason))
    }

    private companion object {
        const val TAG = "RdmUpdate"
    }
}
