package com.radiocompanion.data

import com.radiocompanion.data.remote.PlaylistResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistResolverTest {

    @Test fun parsesPls() {
        val pls = """
            [playlist]
            NumberOfEntries=2
            File1=http://stream.example.com:8000/live
            Title1=Example FM
            File2=http://backup.example.com/live
        """.trimIndent()
        assertEquals("http://stream.example.com:8000/live", PlaylistResolver.parse(pls))
    }

    @Test fun parsesExtendedM3u() {
        val m3u = "#EXTM3U\n#EXTINF:-1,Example\nhttps://example.com/stream.aac\n"
        assertEquals("https://example.com/stream.aac", PlaylistResolver.parse(m3u))
    }

    @Test fun returnsNullWhenNoStream() {
        assertNull(PlaylistResolver.parse("[playlist]\nNumberOfEntries=0\n"))
    }

    @Test fun detectsPlaylistUrls() {
        assertTrue(PlaylistResolver.looksLikePlaylist("http://x.com/listen.pls?id=3"))
        assertTrue(PlaylistResolver.looksLikePlaylist("http://x.com/LIVE.M3U"))
        assertFalse(PlaylistResolver.looksLikePlaylist("http://x.com/live.m3u8"))
        assertFalse(PlaylistResolver.looksLikePlaylist("http://x.com:8000/stream"))
    }
}
