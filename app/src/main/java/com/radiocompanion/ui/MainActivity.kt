package com.radiocompanion.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.radiocompanion.RadioCompanionApp
import com.radiocompanion.service.PlayerConnection
import com.radiocompanion.ui.theme.CarRadioTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val vm: RadioViewModel by viewModels { RadioViewModel.Factory }
    private lateinit var player: PlayerConnection

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) vm.exportTo(uri)
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importFrom(uri)
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        player = PlayerConnection(applicationContext)

        if (savedInstanceState == null) handleViewIntent(intent)
        requestNotificationPermissionIfNeeded()
        maybeAskBatteryExemption()

        setContent {
            CarRadioTheme {
                RadioScreen(
                    vm = vm,
                    player = player,
                    onImport = ::launchImport,
                    onExport = ::launchExport,
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
            vm.message("No file picker on this unit. Open the .json file from a file manager instead.")
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
            val settings = (application as RadioCompanionApp).settings
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
