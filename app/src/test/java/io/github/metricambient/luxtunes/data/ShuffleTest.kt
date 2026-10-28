package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ShuffleTest {
    private fun song(n: Int, artist: String, album: String, extra: Map<String, Any?> = emptyMap()): Track =
        trackOf(mapOf("id" to "%016X".format(n), "path" to "x/$n.mp3", "name" to "Song $n",
            "artist" to artist, "album" to album) + extra)!!

    /** 40 artists with 3 albums of 10 songs each. */
    private val library = (0 until 1200).map { n -> song(n, "Artist ${n / 30}", "Album ${n / 10}") }

    @Test
    fun everySongOnce() {
        val order = Shuffle.order(library, recent = { false })
        assertEquals(library.size, order.size)
        assertEquals(library.map { it.id }.toSet(), order.map { it.id }.toSet())
    }

    @Test
    fun neighboursAreSpreadApart() {
        val order = Shuffle.order(library, recent = { false }, random = Random(7))
        // The last few songs are whatever's left, so they can't always be spread.
        for (i in 1 until order.size - 20) {
            for (back in 1..minOf(i, Shuffle.ALBUM_GAP)) {
                val a = order[i]
                val b = order[i - back]
                if (back <= Shuffle.ARTIST_GAP) assertTrue("artist at $i", Shuffle.artistKey(a) != Shuffle.artistKey(b))
                assertTrue("album at $i", Shuffle.albumKey(a) != Shuffle.albumKey(b))
            }
        }
    }

    @Test
    fun recentSongsGoToTheBack() {
        val heard = library.take(600).map { it.id }.toSet()
        val order = Shuffle.order(library, recent = { it.id in heard }, random = Random(3))
        // The first songs are all ones not heard (spreading only moves songs a little).
        assertTrue(order.take(500).none { it.id in heard })
    }

    @Test
    fun skipWhenShufflingIsLeftOut() {
        val songs = library.take(20) + song(9999, "X", "Y", mapOf("skip_when_shuffling" to 1L))
        assertTrue(Shuffle.order(songs, recent = { false }).none { it.id == "%016X".format(9999) })
    }

    @Test
    fun noArtistOrAlbumNeverClashes() {
        val songs = (0 until 30).map { song(it, "", "") }
        assertEquals("", Shuffle.artistKey(songs[0]))
        assertEquals("", Shuffle.albumKey(songs[0]))
        assertEquals(30, Shuffle.order(songs, recent = { false }).size)
    }

    @Test
    fun fiveThousandSongsAreQuick() {
        val big = (0 until 5000).map { n -> song(n, "Artist ${n % 50}", "Album ${n % 400}") }
        val start = System.nanoTime()
        Shuffle.order(big, recent = { false })
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000)
    }
}
