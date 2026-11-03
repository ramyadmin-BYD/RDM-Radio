package com.radiocompanion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FmClassifierTest {

    @Test fun extractsFrequencyFromName() {
        assertEquals("100.6", FmClassifier.extractFrequency("Nogoum FM 100.6"))
        assertEquals("88.2", FmClassifier.extractFrequency("Radio Hits 88,2 Cairo"))
        assertEquals("92.7", FmClassifier.extractFrequency("Mega FM (92.7)"))
    }

    @Test fun ignoresNumbersThatAreNotFmFrequencies() {
        assertNull(FmClassifier.extractFrequency("Lounge 128.0 kbps"))
        assertNull(FmClassifier.extractFrequency("Channel 1.5"))
        assertNull(FmClassifier.extractFrequency("Best of 2010.1"))
        assertNull(FmClassifier.extractFrequency("Radio 120.5"))
    }

    @Test fun detectsKnownEgyptianBrands() {
        assertTrue(FmClassifier.looksOfficial("Nogoum FM", ""))
        assertTrue(FmClassifier.looksOfficial("نجوم إف إم", ""))
        assertTrue(FmClassifier.looksOfficial("Some Station", "egypt,100.6"))
    }

    @Test fun webOnlyStationsAreNotOfficial() {
        assertFalse(FmClassifier.looksOfficial("Chillhop Radio", "lofi,chill"))
    }
}
