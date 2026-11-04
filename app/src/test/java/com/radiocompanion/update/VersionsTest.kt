package com.radiocompanion.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {

    @Test fun parsesReleaseVersions() {
        assertEquals(listOf(1, 0, 2), Versions.parse("v1.0.2"))
        assertEquals(listOf(1, 1), Versions.parse("1.1"))
        assertEquals(listOf(2, 0, 0), Versions.parse("2.0.0-beta1"))
        assertNull(Versions.parse("dev-12"))
        assertNull(Versions.parse(""))
    }

    @Test fun comparesNumerically() {
        assertTrue(Versions.isNewer("v1.1.0", "1.0.2"))
        assertTrue(Versions.isNewer("v1.0.10", "1.0.9"))      // not string order
        assertTrue(Versions.isNewer("2", "1.9.9"))
        assertFalse(Versions.isNewer("v1.0.2", "1.0.2"))
        assertFalse(Versions.isNewer("1.0", "1.0.0"))
        assertFalse(Versions.isNewer("v1.0.1", "1.0.2"))
        assertFalse(Versions.isNewer("v1.1.0", "dev-5"))      // dev builds never update
    }

    @Test fun picksReleaseApkNotDebug() {
        assertEquals("RDMRadio-1.1.0.apk", Versions.pickApk(listOf("RDMRadio-debug.apk", "RDMRadio-1.1.0.apk")))
        assertNull(Versions.pickApk(listOf("notes.txt", "RDMRadio-debug.apk")))
    }

    @Test fun releaseNotesBecomePlainText() {
        val notes = Versions.plainNotes("## What's new\n- **10-band** equalizer\n* Arabic")
        assertEquals("What's new\n• 10-band equalizer\n• Arabic", notes)
        assertTrue(Versions.plainNotes("x".repeat(900)).length <= 601)
    }
}
