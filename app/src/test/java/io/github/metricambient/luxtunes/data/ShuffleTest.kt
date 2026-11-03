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
        val order = Shuffle.order(library, heardAt = { null })
        assertEquals(library.size, order.size)
        assertEquals(library.map { it.id }.toSet(), order.map { it.id }.toSet())
    }

    /** The distance to the previous song with the same key, for each song that has one. */
    private fun gaps(order: List<Track>, key: (Track) -> String, upTo: Int): List<Int> {
        val last = HashMap<String, Int>()
        val out = ArrayList<Int>()
        for ((p, t) in order.take(upTo).withIndex()) {
            val k = key(t)
            if (k.isEmpty()) continue
            last[k]?.let { out += p - it }
            last[k] = p
        }
        return out
    }

    @Test
    fun albumsAndArtistsKeptApart() {
        // 600 ten-song albums by 200 artists: albums 150 songs apart, artists 33, through the first half.
        val big = (0 until 6000).map { n -> song(n, "Artist ${n / 30}", "Album ${n / 10}") }
        val order = Shuffle.order(big, heardAt = { null }, random = Random(7))
        assertTrue(gaps(order, Shuffle::albumKey, 3000).all { it > 150 })
        assertTrue(gaps(order, Shuffle::artistKey, 3000).all { it > 33 })
    }

    @Test
    fun aBigAlbumIsSpreadEvenly() {
        // One album is a tenth of the list: about a tenth of any stretch, never two within 5.
        val songs = (0 until 3000).map { n ->
            if (n < 300) song(n, "Big", "Big Album") else song(n, "Artist ${n % 97}", "Album ${n % 250}")
        }
        val order = Shuffle.order(songs, heardAt = { null }, random = Random(11))
        // (The very end is whatever's left, so it's not checked.)
        val positions = order.withIndex().filter { it.value.raw["album"] == "Big Album" && it.index < 2700 }.map { it.index }
        val bigGaps = positions.zipWithNext { a, b -> b - a }
        assertTrue(bigGaps.all { it > 5 })
        val early = order.take(300).count { it.raw["album"] == "Big Album" }
        assertTrue(early in 15..45)
    }

    private val now = 1_793_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun albumTiers() {
        // Album 0: one song played 5 days ago. Album 1: one song played an hour ago. Album 2: 20 days ago.
        val songs = library.take(30)
        val heard = mapOf(songs[0].id to now - 5 * day, songs[10].id to now - day / 24, songs[20].id to now - 20 * day)
        val tier = Shuffle.tiers(songs, songs, { heard[it.id] }, now)
        assertEquals(1, tier[songs[0].id])          // its album was heard lately (itself, 5 days ago)
        assertEquals(1, tier[songs[1].id])
        assertEquals(2, tier[songs[10].id])         // heard in the last 2 days
        assertEquals(1, tier[songs[11].id])
        assertEquals(0, tier[songs[21].id])         // 20 days is long enough
        // A song without an album only counts itself.
        val loose = song(5000, "Solo", "")
        assertEquals(0, Shuffle.tiers(listOf(loose), listOf(loose) + songs, { null }, now)[loose.id])
    }

    @Test
    fun recentSongsWaitUntilLast() {
        val heard = library.take(600).map { it.id }.toSet()
        val order = Shuffle.order(library, heardAt = { if (it.id in heard) now else null }, random = Random(3), now = now)
        // Songs heard lately wait until everything else has played.
        assertTrue(order.take(600).none { it.id in heard })
        assertTrue(order.drop(600).all { it.id in heard })
    }

    @Test
    fun albumsHeardLatelyStillTurnUp() {
        // Half the albums were heard 5 days ago (tier 1): fewer of them early, but not none.
        val heardAlbums = library.filter { (it.raw["album"] as String).removePrefix("Album ").toInt() % 2 == 0 }.map { it.id }.toSet()
        val order = Shuffle.order(library, heardAt = { if (it.id in heardAlbums) now - 5 * day else null }, random = Random(5), now = now)
        val early = order.take(100).count { it.id in heardAlbums }
        assertTrue(early in 1..40)
    }

    @Test
    fun skipWhenShufflingIsLeftOut() {
        val songs = library.take(20) + song(9999, "X", "Y", mapOf("skip_when_shuffling" to 1L))
        assertTrue(Shuffle.order(songs, heardAt = { null }).none { it.id == "%016X".format(9999) })
    }

    @Test
    fun noArtistOrAlbumNeverClashes() {
        val songs = (0 until 30).map { song(it, "", "") }
        assertEquals("", Shuffle.artistKey(songs[0]))
        assertEquals("", Shuffle.albumKey(songs[0]))
        assertEquals(30, Shuffle.order(songs, heardAt = { null }).size)
    }

    @Test
    fun manySongsAreQuick() {
        val big = (0 until 7600).map { n -> song(n, "Artist ${n % 50}", "Album ${n % 400}") }
        val start = System.nanoTime()
        Shuffle.order(big, heardAt = { null })
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000)
    }
}
