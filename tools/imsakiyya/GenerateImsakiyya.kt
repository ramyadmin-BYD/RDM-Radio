import com.radiocompanion.prayer.CalcMethod
import com.radiocompanion.prayer.Cities
import com.radiocompanion.prayer.Imsakiyya
import com.radiocompanion.prayer.ImsakiyyaFile
import com.radiocompanion.prayer.Prayer
import com.radiocompanion.prayer.PrayerCalculator
import com.radiocompanion.prayer.RamadanSpan
import java.io.File
import java.time.LocalDate

/**
 * Writes app/src/main/assets/ramadan_1448.json: the Ramadan 2027 Imsakiyya for every
 * built-in city, using each country's official calculation method.
 *
 * Ramadan 1448 starts on 8 February 2027 (Umm al-Qura; the new moon of 6 February is
 * easily visible on the evening of the 7th across the region). The month is 29 days
 * by Umm al-Qura; Morocco, which requires a sighting, is expected to complete 30.
 * The table runs from 7 Feb to 10 Mar so a one-day shift needs only the "starts" entry
 * changed. Replace rows with an authority's published figures if you have them.
 *
 * Run from the repository root (see tools/imsakiyya/README.md).
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "app/src/main/assets/${Imsakiyya.ASSET}")
    val tableStart = LocalDate.of(2027, 2, 7)
    val days = 32
    val imsak = PrayerCalculator.IMSAK_MINUTES_BEFORE_FAJR

    val cities = Cities.all.associate { city ->
        val method = CalcMethod.forCountry(city.country)
        city.id to (0 until days).map { i ->
            val day = PrayerCalculator.compute(
                tableStart.plusDays(i.toLong()), city.latitude, city.longitude, city.zone, method, ramadan = true,
            )
            fun t(p: Prayer) = day[p].toLocalTime().toString()
            listOf(
                day[Prayer.FAJR].minusMinutes(imsak.toLong()).toLocalTime().toString(),
                t(Prayer.FAJR), t(Prayer.SUNRISE), t(Prayer.DHUHR), t(Prayer.ASR), t(Prayer.MAGHRIB), t(Prayer.ISHA),
            ).joinToString(" ")
        }
    }
    val file = ImsakiyyaFile(
        hijriYear = 1448,
        gregorianYear = 2027,
        imsakMinutesBeforeFajr = imsak,
        starts = mapOf(
            Imsakiyya.DEFAULT to RamadanSpan("2027-02-08", 29),
            "MA" to RamadanSpan("2027-02-08", 30),
        ),
        tableStart = tableStart.toString(),
        cities = cities,
    )
    // One city per line keeps diffs readable.
    val json = Imsakiyya.encode(file).replace("],\"", "],\n\"").replace("\"cities\":{", "\"cities\":{\n")
    out.writeText(json + "\n")
    println("Wrote ${out.path}: ${cities.size} cities x $days days")
}
