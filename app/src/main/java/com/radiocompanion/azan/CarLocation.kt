package com.radiocompanion.azan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.radiocompanion.data.settings.GpsFix
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import kotlin.coroutines.resume

/**
 * One position from the car, for prayer times. City-level accuracy is plenty, so a
 * recent cached fix is used when there is one; otherwise one fresh fix (DiLink head
 * units usually have GPS but no Google location services).
 */
object CarLocation {

    fun hasPermission(context: Context) = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    suspend fun fetch(context: Context): GpsFix? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())

        val cached = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < MAX_CACHE_AGE_MS }
        val location = cached ?: providers
            .filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER || it == "fused" }
            .firstNotNullOfOrNull { provider -> withTimeoutOrNull(FIX_TIMEOUT_MS) { current(context, lm, provider) } }
        return location?.let { GpsFix(it.latitude, it.longitude, ZoneId.systemDefault().id) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(context: Context, lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(lm, provider, cancel, ContextCompat.getMainExecutor(context)) {
                    if (cont.isActive) cont.resume(it)
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }

    private const val MAX_CACHE_AGE_MS = 24 * 60 * 60_000L
    private const val FIX_TIMEOUT_MS = 20_000L
}
