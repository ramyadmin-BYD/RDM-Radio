package com.radiocompanion.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.radiocompanion.R
import com.radiocompanion.RadioCompanionApp
import com.radiocompanion.azan.CarLocation
import com.radiocompanion.data.settings.ThemeMode
import com.radiocompanion.model.UiText
import com.radiocompanion.service.PlayerConnection
import com.radiocompanion.ui.theme.CarRadioTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AppCompatActivity (not plain ComponentActivity) so the in-app language switch
 * (English / Arabic) also works on Android 10–12, which DiLink is based on.
 */
class MainActivity : AppCompatActivity() {

    private val vm: RadioViewModel by viewModels { RadioViewModel.Factory }
    private lateinit var player: PlayerConnection
    private val app get() = application as RadioCompanionApp

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) vm.exportTo(uri)
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importFrom(uri)
        }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) vm.useCarLocation() else vm.message(UiText(R.string.msg_location_denied))
        }

    /** Azan recording from a USB drive or the head unit's storage; kept readable across restarts. */
    private val azanSoundPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.setAzanSound(uri.toString())
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        player = PlayerConnection(applicationContext)

        val openedWithFile = intent?.action == Intent.ACTION_VIEW
        if (savedInstanceState == null) handleViewIntent(intent)
        if (savedInstanceState == null) {
            maybeAutoPlayLastStation(skip = openedWithFile)
            app.updates.checkOnStartup()
        }
        requestNotificationPermissionIfNeeded()
        maybeAskBatteryExemption()

        setContent {
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val ramadan by vm.ramadanThemeActive.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Status/navigation bar icons: light icons on the dark theme, dark icons on the light one.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose {}
            }
            CarRadioTheme(darkTheme = dark, ramadan = ramadan) {
                RadioScreen(
                    vm = vm,
                    player = player,
                    onImport = ::launchImport,
                    onExport = ::launchExport,
                    onRequestCarLocation = ::requestCarLocation,
                    onPickAzanSound = ::launchAzanSoundPicker,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player.connect()
    }

    override fun onStop() {
        player.release()   // the service keeps playing on its own
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /**
     * Resume the last station when the app opens in the car. Only once per app
     * process: not on rotation, language change or returning from another app.
     */
    private fun maybeAutoPlayLastStation(skip: Boolean) {
        if (app.autoPlayHandled) return
        app.autoPlayHandled = true
        if (skip) return
        lifecycleScope.launch {
            if (app.settings.autoPlayLastStation.first()) player.requestAutoPlay()
        }
    }

    /** "Open with" a playlist, stream link or JSON backup. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        vm.handleIncoming(data, intent.type)
    }

    private fun launchExport() {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        try {
            exportLauncher.launch("radio-stations-$stamp.json")
        } catch (e: ActivityNotFoundException) {
            // Some head units ship without the system file picker.
            vm.exportToDownloads()
        }
    }

    private fun launchImport() {
        try {
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        } catch (e: ActivityNotFoundException) {
            vm.message(UiText(R.string.msg_no_file_picker))
        }
    }

    private fun requestCarLocation() {
        if (CarLocation.hasPermission(this)) {
            vm.useCarLocation()
        } else {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    private fun launchAzanSoundPicker() {
        try {
            azanSoundPicker.launch(arrayOf("audio/*"))
        } catch (e: ActivityNotFoundException) {
            vm.message(UiText(R.string.msg_no_file_picker_azan))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Asks once to be excluded from battery/background cleanup. Custom ROMs may lack this screen. */
    @SuppressLint("BatteryLife")
    private fun maybeAskBatteryExemption() {
        lifecycleScope.launch {
            val settings = app.settings
            if (settings.batteryPromptShown()) return@launch
            settings.markBatteryPromptShown()
            val pm = getSystemService(PowerManager::class.java) ?: return@launch
            if (pm.isIgnoringBatteryOptimizations(packageName)) return@launch
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())
                )
            }
        }
    }
}
