package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingChangesTest {
    private val id = "1A2B3C4D5E6F7081"
    private val t1 = 1_792_000_000_000L      // fake times (Unix ms)
    private val t2 = t1 + 60_000

    @Test
    fun playsSkipsAndRatingsAddUp() {
        val e = PendingEntry(id).played(t1).played(t2).skipped(t1).rated(80)
        assertEquals(2, e.plays)
        assertEquals(t2, e.lastPlayed)
        assertEquals(1, e.skips)
        assertEquals(80, e.rating)
        assertFalse(e.isEmpty)
    }

    @Test
    fun deliveredClearsWhatWasSent() {
        val sent = PendingEntry(id).played(t1).rated(60).markSent()
        val after = sent.delivered()
        assertTrue(after.isEmpty)
        assertEquals(0, after.sentPlays)
    }

    @Test
    fun changesAfterWritingSurviveDelivery() {
        // Written with one play, then another play and a new rating before LuxTunes emptied the file.
        val e = PendingEntry(id).played(t1).rated(60).markSent().played(t2).rated(100)
        val after = e.delivered()
        assertEquals(1, after.plays)
        assertEquals(t2, after.lastPlayed)
        assertEquals(100, after.rating)
    }

    @Test
    fun sameRatingIsNotSentTwice() {
        val after = PendingEntry(id).rated(40).markSent().delivered()
        assertNull(after.rating)
    }

    @Test
    fun ratingZeroIsStillARating() {
        val e = PendingEntry(id).rated(0)
        assertFalse(e.isEmpty)
        assertTrue(ChangesJson.build(listOf(e), null).contains("\"rating\": 0"))
    }

    @Test
    fun timesUseLuxTunesFormat() {
        assertEquals("2026-10-18T21:04:00Z", ChangesJson.isoTime(1_792_357_440_000L))
    }

    @Test
    fun fileHasTheV1Shape() {
        val e = PendingEntry(id).played(1_792_357_440_000L).skipped(1_792_357_440_000L).rated(80)
        val text = ChangesJson.build(listOf(e, PendingEntry("00000000000000FF")), "ABCDEF0123456789")
        assertTrue(text.startsWith("{\"version\": 1, \"library_id\": \"ABCDEF0123456789\", \"tracks\": ["))
        assertTrue(text.contains(
            "{\"id\": \"$id\", \"plays\": 1, \"last_played\": \"2026-10-18T21:04:00Z\", " +
                "\"skips\": 1, \"last_skipped\": \"2026-10-18T21:04:00Z\", \"rating\": 80}"))
        assertFalse(text.contains("00000000000000FF"))      // nothing to report for it
    }

    @Test
    fun emptyFileDetection() {
        assertTrue(ChangesJson.isEmptied("{\"version\": 1, \"tracks\": []}"))
        assertTrue(ChangesJson.isEmptied("{\"version\": 2, \"tracks\": [], \"playlists\": []}"))
        assertTrue(ChangesJson.isEmptied(ChangesJson.build(emptyList(), null)))
        assertFalse(ChangesJson.isEmptied(ChangesJson.build(listOf(PendingEntry(id).played(t1)), null)))
        assertFalse(ChangesJson.isEmptied(""))
    }

    @Test
    fun remainderLeftByLuxTunesStaysPending() {
        // Written with 3 plays; LuxTunes merged 2 of them and left 1 in the file.
        val e = PendingEntry(id).played(t1).played(t1).played(t2).rated(80).markSent()
        val after = e.delivered(keptPlays = 1, keptSkips = 0, ratingKept = true)
        assertEquals(1, after.plays)
        assertEquals(80, after.rating)
    }

    @Test
    fun version2HasPlaylists() {
        val create = PlaylistEdit("9F8E7D6C5B4A3921", "create", "Road \"Trip\"", false, listOf("1A2B3C4D5E6F7081"))
        val delete = PlaylistEdit("1234123412341234", "delete")
        val smart = PlaylistEdit("77AA77AA77AA77AA", "update", "Top", true,
            smartInfo = byteArrayOf(1, 2), smartCriteria = byteArrayOf(3))
        val text = ChangesJson.build(emptyList(), "ABCDEF0123456789",
            listOf(create, delete, smart).map { ChangesJson.playlistEntry(it) })
        assertTrue(text.startsWith("{\"version\": 2,"))
        assertTrue(text.contains("{\"id\": \"9F8E7D6C5B4A3921\", \"action\": \"create\", \"name\": \"Road \\\"Trip\\\"\", " +
            "\"is_smart\": 0, \"items\": [\"1A2B3C4D5E6F7081\"]}"))
        assertTrue(text.contains("{\"id\": \"1234123412341234\", \"action\": \"delete\"}"))
        assertTrue(text.contains("\"smart_info\": \"AQI=\", \"smart_criteria\": \"Aw==\""))
        assertTrue(ChangesJson.isEmptied("{\"version\": 2, \"tracks\": [], \"playlists\": []}"))
    }

    @Test
    fun spreadShuffleKeepsEverySong() {
        val songs = (1..50).map { "artist${it % 5}" to it }
        val shuffled = spreadShuffle(songs, kotlin.random.Random(7)) { it.first }
        assertEquals(songs.toSet(), shuffled.toSet())
        assertEquals(songs.size, shuffled.size)
    }
}
