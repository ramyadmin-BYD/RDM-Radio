package com.radiocompanion.azan

import android.content.Context
import com.radiocompanion.data.settings.PrayerSettings
import com.radiocompanion.data.settings.SettingsStore
import com.radiocompanion.prayer.AsrSchool
import com.radiocompanion.prayer.CalcMethod
import com.radiocompanion.prayer.Cities
import com.radiocompanion.prayer.City
import com.radiocompanion.prayer.Imsakiyya
import com.radiocompanion.prayer.ImsakiyyaRow
import com.radiocompanion.prayer.Prayer
import com.radiocompanion.prayer.PrayerCalculator
import com.radiocompanion.prayer.PrayerDay
import com.radiocompanion.prayer.RamadanPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Where prayer times are calculated for, after applying the user's settings. */
data class PrayerPlace(
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
    val country: String,
    /** Built-in city, or the nearest one to the GPS position (for its name). */
    val city: City,
    val fromGps: Boolean,
    val method: CalcMethod,
    val settings: PrayerSettings,
)

/** Settings + home country, resolved into times. Shared by the UI, the scheduler and the receiver. */
class PrayerRepository(private val context: Context, private val settings: SettingsStore) {

    /** Ramadan 2027 timetable from assets (parsed once, ~100 KB). */
    val imsakiyya: Imsakiyya by lazy {
        Imsakiyya.parse(context.assets.open(Imsakiyya.ASSET).bufferedReader().use { it.readText() })
    }

    val place: Flow<PrayerPlace> =
        combine(settings.prayer, settings.countryCode) { s, home -> resolve(s, home) }.distinctUntilChanged()

    suspend fun currentPlace(): PrayerPlace = place.first()

    fun ramadan(place: PrayerPlace): RamadanPeriod = imsakiyya.period(place.country)

    fun day(place: PrayerPlace, date: LocalDate): PrayerDay = PrayerCalculator.compute(
        date, place.latitude, place.longitude, place.zone, place.method, place.settings.asrSchool,
        ramadan = date in ramadan(place),
    )

    /** Next enabled Azan after [now], or null when the Azan is off. */
    fun nextAzan(place: PrayerPlace, now: Instant): Pair<Prayer, ZonedDateTime>? {
        if (!place.settings.azanEnabled) return null
        return PrayerCalculator.nextAzan(now, place.settings.azanPrayers, { day(place, it) }, place.zone)
    }

    /** Next of the six times (sunrise included), for the "next prayer" display. */
    fun nextTime(place: PrayerPlace, now: Instant): Pair<Prayer, ZonedDateTime> =
        PrayerCalculator.nextAzan(now, Prayer.entries.toSet(), { day(place, it) }, place.zone)
            ?: error("every day has prayer times")

    /**
     * Imsakiyya for the place. Built-in cities with the default method use the embedded
     * table; a GPS position or a changed method/Asr school is calculated the same way.
     */
    fun imsakiyyaRows(place: PrayerPlace): List<ImsakiyyaRow> {
        val usesTable = !place.fromGps && place.settings.method == null &&
            place.settings.asrSchool == AsrSchool.STANDARD
        if (usesTable) imsakiyya.rows(place.city.id, place.country)?.let { return it }
        val period = ramadan(place)
        return (1..period.days).map { d ->
            Imsakiyya.computedRow(d, day(place, period.first.plusDays(d - 1L)), imsakiyya.imsakMinutesBeforeFajr)
        }
    }

    private fun resolve(s: PrayerSettings, homeCountry: String): PrayerPlace {
        val gps = s.gps
        if (s.useGps && gps != null) {
            val nearest = Cities.nearest(gps.latitude, gps.longitude).first
            val zone = runCatching { ZoneId.of(gps.zoneId) }.getOrDefault(ZoneId.systemDefault())
            return PrayerPlace(
                gps.latitude, gps.longitude, zone, nearest.country, nearest, fromGps = true,
                method = s.method ?: CalcMethod.forCountry(nearest.country), settings = s,
            )
        }
        val city = Cities.byId(s.cityId) ?: Cities.defaultFor(homeCountry)
        return PrayerPlace(
            city.latitude, city.longitude, city.zone, city.country, city, fromGps = false,
            method = s.method ?: CalcMethod.forCountry(city.country), settings = s,
        )
    }
}
