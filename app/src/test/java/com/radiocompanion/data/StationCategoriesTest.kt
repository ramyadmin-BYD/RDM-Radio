package com.radiocompanion.data

import com.radiocompanion.model.Station
import com.radiocompanion.model.StationRegion
import com.radiocompanion.model.region
import com.radiocompanion.model.streamCandidates
import org.junit.Assert.assertEquals
import org.junit.Test

class StationCategoriesTest {

    private fun station(country: String = "", url: String = "https://a.example/live", backups: List<String> = emptyList()) =
        Station(id = "x", name = "X", streamUrl = url, backupStreamUrls = backups, countryCode = country)

    @Test fun regionRelativeToHomeCountry() {
        assertEquals(StationRegion.LOCAL, station("EG").region("EG"))
        assertEquals(StationRegion.LOCAL, station("").region("EG"))
        assertEquals(StationRegion.REGIONAL, station("SA").region("EG"))
        assertEquals(StationRegion.INTERNATIONAL, station("GB").region("EG"))
        assertEquals(StationRegion.REGIONAL, station("EG").region("AE"))
    }

    @Test fun candidatesTryBackupsThenOtherProtocol() {
        val s = station(url = "https://a.example/live", backups = listOf("http://b.example/live", "not a url"))
        assertEquals(
            listOf(
                "https://a.example/live",
                "http://b.example/live",
                "http://a.example/live",
                "https://b.example/live",
            ),
            s.streamCandidates(),
        )
    }
}
