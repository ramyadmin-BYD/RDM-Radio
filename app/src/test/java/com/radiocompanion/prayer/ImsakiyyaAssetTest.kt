package com.radiocompanion.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs

/** Guards the embedded Ramadan 2027 timetable (assets/ramadan_1448.json). */
class ImsakiyyaAssetTest {

    private val imsakiyya: Imsakiyya by lazy {
        val file = listOf("src/main/assets/${Imsakiyya.ASSET}", "app/src/main/assets/${Imsakiyya.ASSET}")
            .map(::File).first { it.exists() }
        Imsakiyya.parse(file.readText())
    }

    @Test fun ramadanStartsOnEighthOfFebruary2027() {
        val p = imsakiyya.period("EG")
        assertEquals(LocalDate.of(2027, 2, 8), p.first)
        assertEquals(LocalDate.of(2027, 3, 8), p.last)
        assertEquals(LocalDate.of(2027, 3, 9), p.eid)
        assertEquals(1, p.dayNumber(LocalDate.of(2027, 2, 8)))
        assertEquals(29, p.dayNumber(LocalDate.of(2027, 3, 8)))
        assertNull(p.dayNumber(LocalDate.of(2027, 2, 7)))
        assertNull(p.dayNumber(LocalDate.of(2027, 3, 9)))
        assertEquals(30, imsakiyya.period("MA").days)
    }

    @Test fun everyCityHasACompleteOrderedMonth() {
        for (city in Cities.all) {
            val rows = imsakiyya.rows(city.id, city.country)
            assertNotNull("${city.id} missing", rows)
            assertEquals(city.id, imsakiyya.period(city.country).days, rows!!.size)
            rows.forEachIndexed { i, r ->
                assertEquals(i + 1, r.day)
                val order = listOf(r.imsak, r.fajr, r.sunrise, r.dhuhr, r.asr, r.maghrib, r.isha)
                assertTrue("${city.id} day ${r.day} out of order: $order", order.zipWithNext().all { (a, b) -> a < b })
                assertEquals(imsakiyya.imsakMinutesBeforeFajr.toLong(), Duration.between(r.imsak, r.fajr).toMinutes())
            }
        }
    }

    /** Hand-edited official figures are fine; a typo (wrong hour) is not. */
    @Test fun tableAgreesWithCalculationWithinFiveMinutes() {
        for (city in Cities.all) {
            for (r in imsakiyya.rows(city.id, city.country)!!) {
                val calc = PrayerCalculator.compute(
                    r.date, city.latitude, city.longitude, city.zone, CalcMethod.forCountry(city.country), ramadan = true,
                )
                listOf(
                    Prayer.FAJR to r.fajr, Prayer.SUNRISE to r.sunrise, Prayer.DHUHR to r.dhuhr,
                    Prayer.ASR to r.asr, Prayer.MAGHRIB to r.maghrib, Prayer.ISHA to r.isha,
                ).forEach { (p, t) ->
                    val diff = abs(Duration.between(calc[p].toLocalTime(), t).toMinutes())
                    assertTrue("${city.id} ${r.date} $p table=$t calc=${calc[p].toLocalTime()}", diff <= 5)
                }
            }
        }
    }
}
