package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun weights() {
        // A 15-song album: one song heard 5 days ago, one an hour ago.
        val album = (0 until 15).map { song(it, "A", "Fifteen") }
        val heard = mapOf(album[0].id to now - 5 * day, album[1].id to now - day / 24)
        val w = Shuffle.weights(album, { heard[it.id] }, now)
        assertEquals(0.2, w[album[0].id]!!, 1e-9)                     // heard in the last 14 days
        assertNull(w[album[1].id])                                    // heard in the last 2 days: goes last
        assertEquals(1 - 0.8 * 2 / 15, w[album[2].id]!!, 1e-9)        // two of 15 heard lately
        // One song heard on its own album: the other 14 are about 0.95.
        val one = Shuffle.weights(album, { if (it.id == album[0].id) now - 5 * day else null }, now)
        assertEquals(1 - 0.8 / 15, one[album[3].id]!!, 1e-9)
        // Not heard lately (20 days), and a song without an album: 1.
        assertEquals(1.0, Shuffle.weights(album, { now - 20 * day }, now)[album[4].id]!!, 1e-9)
        val loose = song(5000, "Solo", "")
        assertEquals(1.0, Shuffle.weights(listOf(loose) + album, { if (it.id == album[0].id) now else null }, now)[loose.id]!!, 1e-9)
    }

    @Test
    fun fewAlbumsHeardDoesntCrowdTheStart() {
        // 50 ten-song albums; one song heard on 4 in 5 of them. The other albums' songs are
        // a fifth of the list, and about a fifth of the first 100 songs, not most of them.
        val songs = (0 until 500).map { n -> song(n, "Artist ${n / 20}", "Album ${n / 10}") }
        val heard = songs.filter { (it.raw["album"] as String).removePrefix("Album ").toInt() % 5 != 0 && it.id.toLong(16) % 10 == 0L }
            .map { it.id }.toSet()
        val order = Shuffle.order(songs, heardAt = { if (it.id in heard) now - 5 * day else null }, random = Random(9), now = now)
        val fresh = order.take(100).count { (it.raw["album"] as String).removePrefix("Album ").toInt() % 5 == 0 }
        assertTrue("fresh: $fresh", fresh in 8..40)
    }

    @Test
    fun oneArtistManyAlbums() {
        // LuxTunes 0.8.8 couldn't shuffle this (a wait of -1); every song must still come out once.
        val songs = (0 until 96).map { n -> song(n, "Only Artist", "Album ${n % 3}") }
        val order = Shuffle.order(songs, heardAt = { null })
        assertEquals(songs.map { it.id }.toSet(), order.map { it.id }.toSet())
        assertEquals(96, order.size)
    }

    @Test
    fun fringeCases() {
        assertTrue(Shuffle.order(emptyList(), heardAt = { null }).isEmpty())
        val one = listOf(song(1, "A", "B"))
        assertEquals(one, Shuffle.order(one, heardAt = { null }))
        // One album, one artist; every song heard this session; the same song twice in a playlist.
        val same = (0 until 40).map { song(it, "A", "B") }
        assertEquals(40, Shuffle.order(same, heardAt = { null }).size)
        assertEquals(40, Shuffle.order(same, heardAt = { now }, now = now).size)
        val twice = same.take(5) + same.take(5)
        assertEquals(10, Shuffle.order(twice, heardAt = { null }).size)
        // Everything marked "skip when shuffling": nothing to play (the player falls back to the list).
        val skipped = (0 until 5).map { song(it, "A", "B", mapOf("skip_when_shuffling" to 1L)) }
        assertTrue(Shuffle.order(skipped, heardAt = { null }).isEmpty())
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
