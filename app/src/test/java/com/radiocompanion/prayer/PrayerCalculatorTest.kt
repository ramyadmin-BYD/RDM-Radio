package com.radiocompanion.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Reference times come from the adhan library (batoulapps/adhan-js 4.4.3), an
 * independent implementation based on Jean Meeus' algorithms.
 */
class PrayerCalculatorTest {

    private fun check(city: String, date: String, expected: String, method: CalcMethod? = null, ramadan: Boolean = false) {
        val c = Cities.byId(city)!!
        val day = PrayerCalculator.compute(
            LocalDate.parse(date), c.latitude, c.longitude, c.zone,
            method ?: CalcMethod.forCountry(c.country), ramadan = ramadan,
        )
        expected.split(' ').map(LocalTime::parse).zip(Prayer.entries).forEach { (want, prayer) ->
            val got = day[prayer].toLocalTime()
            val diff = abs(Duration.between(want, got).toMinutes())
            assertTrue("$city $date $prayer: expected $want, got $got", diff <= 1)
        }
    }

    @Test fun cairoWinter() = check("cairo", "2026-11-06", "04:44 06:13 11:40 14:42 17:04 18:24")

    @Test fun cairoSummerUsesDaylightSavingTime() = check("cairo", "2027-06-21", "04:08 05:54 12:58 16:32 19:59 21:33")

    @Test fun makkahUmmAlQura() = check("makkah", "2027-02-08", "05:37 06:55 12:35 15:50 18:14 19:44")

    @Test fun makkahIshaIsTwoHoursAfterMaghribInRamadan() {
        val c = Cities.byId("makkah")!!
        val day = PrayerCalculator.compute(LocalDate.of(2027, 2, 8), c.latitude, c.longitude, c.zone, CalcMethod.UMM_AL_QURA, ramadan = true)
        assertEquals(120, Duration.between(day[Prayer.MAGHRIB], day[Prayer.ISHA]).toMinutes())
    }

    @Test fun londonMidsummerHighLatitude() = check("london", "2027-06-21", "02:31 04:43 13:03 17:25 21:22 23:27")

    @Test fun newYorkIsna() = check("new_york", "2027-12-21", "05:54 07:16 11:55 14:14 16:32 17:54")

    @Test fun timesAreInOrderForEveryCity() {
        for (c in Cities.all) for (date in listOf("2027-02-08", "2027-06-21", "2027-12-21")) {
            val day = PrayerCalculator.compute(LocalDate.parse(date), c.latitude, c.longitude, c.zone, CalcMethod.forCountry(c.country))
            Prayer.entries.zipWithNext().forEach { (a, b) ->
                assertTrue("${c.id} $date: $a ${day[a]} must be before $b ${day[b]}", day[a].isBefore(day[b]))
            }
        }
    }

    @Test fun nextAzanSkipsSunriseAndRollsOverToTomorrow() {
        val c = Cities.byId("cairo")!!
        val dayAt = { d: LocalDate -> PrayerCalculator.compute(d, c.latitude, c.longitude, c.zone, CalcMethod.EGYPTIAN) }
        val zone = ZoneId.of("Africa/Cairo")

        // 05:00 on 6 Nov 2026: Fajr (04:44) has passed, sunrise has no Azan, so next is Dhuhr.
        val morning = ZonedDateTime.of(2026, 11, 6, 5, 0, 0, 0, zone).toInstant()
        assertEquals(Prayer.DHUHR, PrayerCalculator.nextAzan(morning, Prayer.entries.toSet(), dayAt, zone)!!.first)

        // After Isha: tomorrow's Fajr.
        val night = ZonedDateTime.of(2026, 11, 6, 23, 0, 0, 0, zone).toInstant()
        val next = PrayerCalculator.nextAzan(night, Prayer.entries.toSet(), dayAt, zone)!!
        assertEquals(Prayer.FAJR, next.first)
        assertEquals(LocalDate.of(2026, 11, 7), next.second.toLocalDate())

        // Only Maghrib enabled.
        assertEquals(Prayer.MAGHRIB, PrayerCalculator.nextAzan(morning, setOf(Prayer.MAGHRIB), dayAt, zone)!!.first)
        assertNull(PrayerCalculator.nextAzan(morning, emptySet(), dayAt, zone))
    }

    @Test fun everyCountryHasADefaultCity() {
        for (code in listOf("EG", "SA", "AE", "KW", "QA", "BH", "OM", "JO", "LB", "IQ", "PS", "SY", "YE",
            "MA", "DZ", "TN", "LY", "SD", "TR", "GB", "US", "DE", "FR", "IT", "ES", "CN")) {
            assertEquals(code, Cities.defaultFor(code).country)
        }
        assertEquals("cairo", Cities.nearest(30.06, 31.25).first.id)
    }
}
