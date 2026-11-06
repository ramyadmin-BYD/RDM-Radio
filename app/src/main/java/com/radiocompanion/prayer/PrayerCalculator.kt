package com.radiocompanion.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** The six daily times. SUNRISE has no Azan; it marks the end of Fajr. */
enum class Prayer { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA;
    val hasAzan: Boolean get() = this != SUNRISE
}

/** Asr shadow length: STANDARD (Shafi'i, Maliki, Hanbali) = 1, HANAFI = 2. */
enum class AsrSchool(val shadowFactor: Int) { STANDARD(1), HANAFI(2) }

/**
 * Calculation conventions used by the official bodies of each region.
 * Isha is either a sun angle or a fixed number of minutes after Maghrib.
 * The *Minutes values are the safety margins each authority adds to the astronomical time.
 */
enum class CalcMethod(
    val fajrAngle: Double,
    val ishaAngle: Double? = null,
    val ishaMinutes: Int? = null,
    val dhuhrMinutes: Int = 0,
    val maghribMinutes: Int = 0,
    val sunriseMinutes: Int = 0,
    val asrMinutes: Int = 0,
) {
    /** Egyptian General Authority of Survey (also Sudan, Libya, Levant, Iraq). */
    EGYPTIAN(19.5, ishaAngle = 17.5, dhuhrMinutes = 1),
    /** Umm al-Qura, Makkah. Isha is 120 min after Maghrib during Ramadan. */
    UMM_AL_QURA(18.5, ishaMinutes = 90),
    /** Muslim World League. */
    MWL(18.0, ishaAngle = 17.0, dhuhrMinutes = 1),
    /** Islamic Society of North America. */
    ISNA(15.0, ishaAngle = 15.0),
    /** University of Islamic Sciences, Karachi. */
    KARACHI(18.0, ishaAngle = 18.0, dhuhrMinutes = 1),
    /** UAE General Authority of Islamic Affairs. */
    DUBAI(18.2, ishaAngle = 18.2, dhuhrMinutes = 3, maghribMinutes = 3, sunriseMinutes = -3, asrMinutes = 3),
    KUWAIT(18.0, ishaAngle = 17.5),
    QATAR(18.0, ishaMinutes = 90),
    /** Morocco, Ministry of Habous and Islamic Affairs. */
    MOROCCO(19.0, ishaAngle = 17.0, dhuhrMinutes = 5, maghribMinutes = 5),
    /** Diyanet, Turkey (angle approximation). */
    TURKEY(18.0, ishaAngle = 17.0, dhuhrMinutes = 5, maghribMinutes = 7, sunriseMinutes = -7, asrMinutes = 4),
    ;

    companion object {
        /** Method of the official authority for a country (ISO 3166-1 alpha-2). */
        fun forCountry(code: String): CalcMethod = when (code.uppercase()) {
            "EG", "SD", "LY", "SY", "LB", "PS", "IQ", "JO" -> EGYPTIAN
            "SA", "YE", "BH", "OM" -> UMM_AL_QURA
            "AE" -> DUBAI
            "KW" -> KUWAIT
            "QA" -> QATAR
            "MA" -> MOROCCO
            "TR" -> TURKEY
            "US", "CA" -> ISNA
            "PK", "IN", "BD", "AF" -> KARACHI
            else -> MWL
        }
    }
}

/** One day's prayer times at a place, rounded to the minute, in the place's time zone. */
data class PrayerDay(val date: LocalDate, val times: Map<Prayer, ZonedDateTime>) {
    operator fun get(prayer: Prayer): ZonedDateTime = times.getValue(prayer)
}

/**
 * Offline prayer-time calculation from latitude/longitude, the standard astronomical
 * method used by PrayTimes.org and most Islamic calendars: sun declination and the
 * equation of time for the date, then the hour angle at which the sun reaches each
 * prayer's depression angle (or Asr's shadow ratio).
 *
 * Accurate to about a minute for inhabited latitudes; cross-checked in unit tests.
 * Pure Kotlin (no Android types) so it runs in JVM tests and in the build tools.
 */
object PrayerCalculator {

    /** Imsak (start of the fast) shown in the Imsakiyya: this many minutes before Fajr. */
    const val IMSAK_MINUTES_BEFORE_FAJR = 10

    fun compute(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        method: CalcMethod,
        asr: AsrSchool = AsrSchool.STANDARD,
        ramadan: Boolean = false,
    ): PrayerDay {
        // Offset in effect around midday (DST switches happen at night).
        val offsetHours = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jDate = julianDay(date) - longitude / (15.0 * 24.0)
        val lat = latitude

        // Day-portion estimates, refined once (PrayTimes uses a single iteration).
        var fajr = 5.0; var sunrise = 6.0; var dhuhr = 12.0; var asrT = 13.0
        var sunset = 18.0; var isha = 18.0
        repeat(2) {
            fajr = sunAngleTime(jDate, lat, method.fajrAngle, fajr / 24, ccw = true)
            sunrise = sunAngleTime(jDate, lat, SUNRISE_ANGLE, sunrise / 24, ccw = true)
            dhuhr = midDay(jDate, dhuhr / 24)
            asrT = asrTime(jDate, lat, asr.shadowFactor, asrT / 24)
            sunset = sunAngleTime(jDate, lat, SUNRISE_ANGLE, sunset / 24, ccw = false)
            isha = method.ishaAngle?.let { sunAngleTime(jDate, lat, it, isha / 24, ccw = false) } ?: sunset
        }

        val shift = offsetHours - longitude / 15.0
        fajr += shift; sunrise += shift; dhuhr += shift; asrT += shift; sunset += shift; isha += shift

        // High latitudes (UK/Germany in summer): the sun may never reach the angle.
        // Fall back to the "angle-based" portion of the night, as PrayTimes does.
        val night = 24 - (sunset - sunrise)
        if (fajr.isNaN() || sunrise - fajr > method.fajrAngle / 60.0 * night) {
            fajr = sunrise - method.fajrAngle / 60.0 * night
        }
        method.ishaAngle?.let { angle ->
            if (isha.isNaN() || isha - sunset > angle / 60.0 * night) isha = sunset + angle / 60.0 * night
        }

        val maghrib = sunset + method.maghribMinutes / 60.0
        method.ishaMinutes?.let { minutes ->
            val m = if (ramadan && method == CalcMethod.UMM_AL_QURA) 120 else minutes
            isha = maghrib + m / 60.0
        }
        dhuhr += method.dhuhrMinutes / 60.0
        sunrise += method.sunriseMinutes / 60.0
        asrT += method.asrMinutes / 60.0

        val offset = zone.rules.getOffset(date.atTime(12, 0))
        fun at(hours: Double): ZonedDateTime {
            // Round to the nearest minute, counted from local midnight at the midday offset.
            val local = date.atStartOfDay().plusMinutes((hours * 60).roundToLong())
            return local.atOffset(offset).atZoneSameInstant(zone)
        }
        return PrayerDay(
            date,
            mapOf(
                Prayer.FAJR to at(fajr),
                Prayer.SUNRISE to at(sunrise),
                Prayer.DHUHR to at(dhuhr),
                Prayer.ASR to at(asrT),
                Prayer.MAGHRIB to at(maghrib),
                Prayer.ISHA to at(isha),
            ),
        )
    }

    /**
     * The next Azan strictly after [now] among [enabled] prayers, looking up to two days
     * ahead. Null if nothing is enabled.
     */
    fun nextAzan(
        now: Instant,
        enabled: Set<Prayer>,
        dayAt: (LocalDate) -> PrayerDay,
        zone: ZoneId,
    ): Pair<Prayer, ZonedDateTime>? {
        val wanted = enabled.filter { it.hasAzan }
        if (wanted.isEmpty()) return null
        val today = now.atZone(zone).toLocalDate()
        for (offset in 0L..2L) {
            val day = dayAt(today.plusDays(offset))
            wanted.map { it to day[it] }
                .filter { it.second.toInstant().isAfter(now) }
                .minByOrNull { it.second }
                ?.let { return it }
        }
        return null
    }

    // ------------------------------------------------------------- astronomy

    private const val SUNRISE_ANGLE = 0.833   // refraction + sun's radius

    private fun julianDay(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + date.dayOfMonth + b - 1524.5
    }

    /** Sun declination (degrees) and equation of time (hours) for a Julian day. */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    private fun midDay(jDate: Double, dayPortion: Double): Double {
        val eqt = sunPosition(jDate + dayPortion).second
        return fixHour(12 - eqt)
    }

    /** Time at which the sun is [angle] degrees below the horizon (before noon if [ccw]). */
    private fun sunAngleTime(jDate: Double, lat: Double, angle: Double, dayPortion: Double, ccw: Boolean): Double {
        val decl = sunPosition(jDate + dayPortion).first
        val noon = midDay(jDate, dayPortion)
        val cosH = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
        if (cosH < -1 || cosH > 1) return Double.NaN
        val t = darccos(cosH) / 15.0
        return noon + if (ccw) -t else t
    }

    private fun asrTime(jDate: Double, lat: Double, factor: Int, dayPortion: Double): Double {
        val decl = sunPosition(jDate + dayPortion).first
        val angle = -darccot(factor + dtan(abs(lat - decl)))
        return sunAngleTime(jDate, lat, angle, dayPortion, ccw = false)
    }

    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun darccot(x: Double) = Math.toDegrees(atan(1 / x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double) = a - 24.0 * floor(a / 24.0)
}
