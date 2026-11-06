package com.radiocompanion

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.radiocompanion.azan.AzanCoordinator
import com.radiocompanion.azan.AzanScheduler
import com.radiocompanion.azan.PrayerRepository
import com.radiocompanion.data.local.AppDatabase
import com.radiocompanion.data.remote.PlaylistResolver
import com.radiocompanion.data.remote.RadioBrowserApi
import com.radiocompanion.data.repository.StationRepository
import com.radiocompanion.data.settings.SettingsStore
import com.radiocompanion.service.StreamDiagnostics
import com.radiocompanion.service.audio.AudioEffectsController
import com.radiocompanion.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Application + a small manual DI container (swap for Hilt if the app grows). */
class RadioCompanionApp : Application(), ImageLoaderFactory {

    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true   // tolerate radio-browser / newer backup fields
            encodeDefaults = true
            prettyPrint = true         // human-readable JSON exports
        }
    }

    /** One shared HTTP client: API calls, station logos and the audio streams. */
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "RDMRadio/${BuildConfig.VERSION_NAME}")
                        .build()
                )
            }
            .build()
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    val settings: SettingsStore by lazy { SettingsStore(this) }

    /** In-app updates from GitHub Releases. */
    val updates: UpdateManager by lazy { UpdateManager(this, http, json, settings) }

    /** Equalizer / Loudness / makeup gain, shared by the playback service and the UI. */
    val audioEffects: AudioEffectsController by lazy { AudioEffectsController(this, settings) }

    /** Prayer times, Ramadan timetable and the Azan. */
    val prayers: PrayerRepository by lazy { PrayerRepository(this, settings) }
    val azanScheduler: AzanScheduler by lazy { AzanScheduler(this, prayers) }
    val azanCoordinator = AzanCoordinator()

    /** App-lifetime work (keeping the next Azan alarm in sync with the settings). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: StationRepository by lazy {
        StationRepository(
            context = this,
            db = database,
            api = RadioBrowserApi(http, json),
            resolver = PlaylistResolver(http),
            json = json,
        )
    }

    override fun onCreate() {
        super.onCreate()
        StreamDiagnostics.logContext = this
        // Any change of city, method or enabled prayers re-arms the next Azan.
        prayers.place.onEach { runCatching { azanScheduler.reschedule() } }.launchIn(appScope)
    }

    /** True once this app process has auto-started the last station (only once per launch). */
    @Volatile var autoPlayHandled = false

    /** Small image cache: head units have little RAM and BYD kills memory-hungry apps. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(http)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.10).build() }
            .crossfade(false)
            .build()
}
