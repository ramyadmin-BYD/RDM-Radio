package com.radiocompanion.data

import com.radiocompanion.model.Station
import com.radiocompanion.model.StationBackup
import com.radiocompanion.model.StationSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StationJsonTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun backupRoundTrip() {
        val backup = StationBackup(
            exportedAtEpochMs = 123L,
            stations = listOf(
                Station(
                    id = "custom:1", name = "Test", streamUrl = "https://example.com/live.m3u8",
                    backupStreamUrls = listOf("http://example.com/live"), countryCode = "EG",
                    isHls = true, isOfficialFm = true, isFavorite = true, source = StationSource.CUSTOM,
                ),
            ),
        )
        val text = json.encodeToString(StationBackup.serializer(), backup)
        assertTrue(text.contains("\"country\":\"EG\""))
        assertEquals(backup, json.decodeFromString(StationBackup.serializer(), text))
    }

    @Test fun oldFilesWithFrequencyStillImport() {
        val text = """{"exportedAtEpochMs":0,"stations":[{"id":"a","name":"Nogoum FM","streamUrl":"http://a/x",
            "country":"EG","isOfficialFm":true,"frequency":"100.6","logoUrl":null}]}"""
        val s = json.decodeFromString(StationBackup.serializer(), text).stations.single()
        assertEquals("EG", s.countryCode)
        assertTrue(s.isOfficialFm)
    }

    @Test fun readsOldBackupsThatUsedCountryCode() {
        val text = """{"exportedAtEpochMs":0,"stations":[{"id":"a","name":"A","streamUrl":"http://a/x","countryCode":"SA","extra":1}]}"""
        val s = json.decodeFromString(StationBackup.serializer(), text).stations.single()
        assertEquals("SA", s.countryCode)
        assertEquals(StationSource.CUSTOM, s.source)
        assertEquals(false, s.isOfficialFm)
    }

    /** The bundled list must contain Nogoum FM under Egypt with the agreed stream URL. */
    @Test fun bundledStationsIncludeNogoumFm() {
        val file = listOf("src/main/assets/stations.json", "app/src/main/assets/stations.json")
            .map(::File).first { it.exists() }
        val stations = json.decodeFromString(StationBackup.serializer(), file.readText()).stations
        val nogoum = stations.single { it.id == "eg-nogoum-fm" }
        assertEquals("Nogoum FM 100.6", nogoum.name)
        assertEquals("https://audio.nrpstream.com/listen/nogoumfm/radio.mp3", nogoum.streamUrl)
        assertEquals("EG", nogoum.countryCode)
        assertTrue(nogoum.isFavorite)
    }
}
