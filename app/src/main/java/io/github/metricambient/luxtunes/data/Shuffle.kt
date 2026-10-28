package io.github.metricambient.luxtunes.data

import java.security.SecureRandom
import java.time.Instant
import java.util.Collections
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

/**
 * LuxTunes' shuffle (as on the computer from 0.8.3):
 * 1. a fair Fisher–Yates shuffle with a fresh random generator;
 * 2. songs heard recently go to the back;
 * 3. neighbours are spread apart: not the same artist within 2 songs, nor the
 *    same album within 8 (moving a later song forward, looking at most 300 ahead).
 */
object Shuffle {
    const val ARTIST_GAP = 2
    const val ALBUM_GAP = 8
    const val LOOK_AHEAD = 300
    private const val RECENT_MS = 2L * 24 * 60 * 60 * 1000

    /** A new generator for every shuffle: the system's secure source mixed with the time. */
    fun freshRandom(): Random = Random(SecureRandom().nextLong() xor System.nanoTime())

    /**
     * Every song in `songs` once, shuffled. Songs marked "skip when shuffling" are
     * left out (set `keepSkipped` to keep them).
     */
    fun order(
        songs: List<Track>,
        recent: (Track) -> Boolean = ::heardRecently,
        random: Random = freshRandom(),
        keepSkipped: Boolean = false,
    ): List<Track> {
        val pool = if (keepSkipped) songs else songs.filter { !skipWhenShuffling(it) }
        // 1. Fair shuffle.
        val fair = pool.toMutableList()
        Collections.shuffle(fair, random)
        // 2. Recently heard songs after the others, each part keeping its shuffled order.
        val (old, heard) = fair.partition { !recent(it) }
        val order = ArrayList<Track>(fair.size).apply { addAll(old); addAll(heard) }
        // 3. Spread neighbours apart. Keys are worked out once per song, as numbers.
        spread(order)
        return order
    }

    /** Step 3, in place. */
    internal fun spread(order: MutableList<Track>) {
        // Each song's artist and album as a number (-1: none), worked out once.
        val ids = HashMap<String, Int>()
        fun idOf(key: String): Int = if (key.isEmpty()) -1 else ids.getOrPut(key) { ids.size }
        val songs = order.toList()
        val artist = IntArray(songs.size) { idOf(artistKey(songs[it])) }
        val album = IntArray(songs.size) { idOf(albumKey(songs[it])) }
        val at = (songs.indices).toMutableList()      // the order, as indexes into `songs`

        fun clashes(i: Int, song: Int): Boolean {
            for (back in 1..maxOf(ARTIST_GAP, ALBUM_GAP)) {
                if (i - back < 0) break
                val before = at[i - back]
                if (back <= ARTIST_GAP && artist[song] >= 0 && artist[before] == artist[song]) return true
                if (back <= ALBUM_GAP && album[song] >= 0 && album[before] == album[song]) return true
            }
            return false
        }

        for (i in 1 until at.size) {
            if (!clashes(i, at[i])) continue
            val end = minOf(at.size, i + LOOK_AHEAD)
            for (j in i + 1 until end) {
                if (!clashes(i, at[j])) {
                    at.add(i, at.removeAt(j))      // the rest shift back by one
                    break
                }
            }
        }
        for (k in at.indices) order[k] = songs[at[k]]
    }

    /** The album artist, or the artist without one (lower case; "" if neither). */
    fun artistKey(t: Track): String {
        val albumArtist = t.raw["album_artist"]?.toString()?.trim().orEmpty()
        val artist = albumArtist.ifEmpty { t.raw["artist"]?.toString()?.trim().orEmpty() }
        return artist.lowercase()
    }

    /** The album with its artist, so two artists' "Greatest Hits" differ ("" without an album name). */
    fun albumKey(t: Track): String {
        val album = t.raw["album"]?.toString()?.trim().orEmpty()
        return if (album.isEmpty()) "" else album.lowercase() + "\u0001" + artistKey(t)
    }

    /** Started in this session, or last played less than 2 days ago. */
    fun heardRecently(t: Track): Boolean {
        if (t.id in HeardThisSession) return true
        val played = t.playDate ?: return false
        val at = runCatching { Instant.parse(played).toEpochMilli() }.getOrNull() ?: return false
        return System.currentTimeMillis() - at < RECENT_MS
    }

    fun skipWhenShuffling(t: Track): Boolean = when (val v = t.raw["skip_when_shuffling"]) {
        is Number -> v.toLong() != 0L
        is String -> v.trim().let { it.isNotEmpty() && it != "0" }
        else -> false
    }
}

/** Songs started since the app started (even ones skipped straight away). */
object HeardThisSession {
    private val ids: MutableSet<String> = ConcurrentHashMap.newKeySet()
    fun add(id: String) { ids += id }
    operator fun contains(id: String): Boolean = id in ids
}

/**
 * Groups (albums or artists) in random order, each group's songs kept in their
 * own order: "Shuffling by albums" / "Shuffling by artists".
 */
fun shuffleGroups(tracks: List<Track>, key: (Track) -> String): List<Track> =
    tracks.groupBy(key).values.shuffled(Shuffle.freshRandom()).flatten()
