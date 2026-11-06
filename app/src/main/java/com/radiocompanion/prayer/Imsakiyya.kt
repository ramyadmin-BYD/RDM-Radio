package com.radiocompanion.prayer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

/**
 * On-disk format of assets/ramadan_1448.json (Ramadan 2027).
 *
 * - [starts]: expected first day and length per country ("default" for the rest).
 *   Official moon-sighting announcements can move these by a day; edit the file and
 *   ship an update if that happens.
 * - [cities]: one row per date from [tableStart], keyed by [City.id]. Each row is
 *   "imsak fajr sunrise dhuhr asr maghrib isha" in local 24-hour time.
 */
@Serializable
data class ImsakiyyaFile(
    val hijriYear: Int,
    val gregorianYear: Int,
    val imsakMinutesBeforeFajr: Int,
    val starts: Map<String, RamadanSpan>,
    val tableStart: String,
    val cities: Map<String, List<String>>,
)

@Serializable
data class RamadanSpan(val first: String, val days: Int)

/** The month of Ramadan in one country. */
data class RamadanPeriod(val hijriYear: Int, val first: LocalDate, val days: Int) {
    val last: LocalDate get() = first.plusDays(days - 1L)
    val eid: LocalDate get() = first.plusDays(days.toLong())
    operator fun contains(date: LocalDate) = !date.isBefore(first) && !date.isAfter(last)
    /** 1-based day of Ramadan, or null outside the month. */
    fun dayNumber(date: LocalDate): Int? = if (date in this) (date.toEpochDay() - first.toEpochDay() + 1).toInt() else null
}

/** One line of the Imsakiyya. */
data class ImsakiyyaRow(
    val day: Int,
    val date: LocalDate,
    val imsak: LocalTime,
    val fajr: LocalTime,
    val sunrise: LocalTime,
    val dhuhr: LocalTime,
    val asr: LocalTime,
    val maghrib: LocalTime,
    val isha: LocalTime,
)

class Imsakiyya(private val file: ImsakiyyaFile) {

    val hijriYear: Int get() = file.hijriYear
    val imsakMinutesBeforeFajr: Int get() = file.imsakMinutesBeforeFajr
    private val tableStart: LocalDate = LocalDate.parse(file.tableStart)

    fun period(country: String): RamadanPeriod {
        val span = file.starts[country.uppercase()] ?: file.starts.getValue(DEFAULT)
        return RamadanPeriod(file.hijriYear, LocalDate.parse(span.first), span.days)
    }

    fun hasCity(cityId: String) = cityId in file.cities

    /** The whole month for a built-in city, or null if the city has no table. */
    fun rows(cityId: String, country: String): List<ImsakiyyaRow>? {
        val table = file.cities[cityId] ?: return null
        val p = period(country)
        return (1..p.days).mapNotNull { day ->
            val date = p.first.plusDays(day - 1L)
            val index = (date.toEpochDay() - tableStart.toEpochDay()).toInt()
            table.getOrNull(index)?.let { parseRow(day, date, it) }
        }
    }

    companion object {
        const val ASSET = "ramadan_1448.json"
        const val DEFAULT = "default"

        private val json = Json { ignoreUnknownKeys = true }
        private val compactJson = Json { prettyPrint = false }

        fun parse(text: String): Imsakiyya = Imsakiyya(json.decodeFromString(ImsakiyyaFile.serializer(), text))

        fun encode(file: ImsakiyyaFile): String =
            compactJson.encodeToString(ImsakiyyaFile.serializer(), file)

        private fun parseRow(day: Int, date: LocalDate, row: String): ImsakiyyaRow {
            val t = row.trim().split(' ').map(LocalTime::parse)
            require(t.size == 7) { "Imsakiyya row for $date needs 7 times: $row" }
            return ImsakiyyaRow(day, date, t[0], t[1], t[2], t[3], t[4], t[5], t[6])
        }

        /** Row for a place that isn't a built-in city (car GPS), computed on the fly. */
        fun computedRow(day: Int, times: PrayerDay, imsakMinutes: Int): ImsakiyyaRow {
            fun t(p: Prayer) = times[p].toLocalTime()
            return ImsakiyyaRow(
                day, times.date, t(Prayer.FAJR).minusMinutes(imsakMinutes.toLong()), t(Prayer.FAJR),
                t(Prayer.SUNRISE), t(Prayer.DHUHR), t(Prayer.ASR), t(Prayer.MAGHRIB), t(Prayer.ISHA),
            )
        }
    }
}
