package io.github.metricambient.luxtunes.data

import java.security.SecureRandom
import java.time.Instant
import java.util.Collections
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * LuxTunes' shuffle (as on the computer from 0.8.9):
 * 1. a fair Fisher–Yates shuffle with a fresh random generator;
 * 2. freshness as odds: a song heard in the last 14 days, or from an album much of which
 *    was, comes up early less often; songs heard in the last 2 days (or this session)
 *    wait until everything else has played (see [weights]);
 * 3. each album and artist waits a number of songs before it can come up again, scaled
 *    to the list (see [build]).
 */
object Shuffle {
    const val ARTIST_GAP = 2            // the least an artist waits…
    const val ARTIST_GAP_MAX = 40       // …and the most
    const val ALBUM_GAP = 8
    const val ALBUM_GAP_MAX = 150
    const val EVEN = 0.8                // a big album or artist waits at most 80% of its even spacing
    const val LOOK_AHEAD = 400
    const val MIN_WEIGHT = 0.2          // a song heard lately, against a fresh one
    private const val DAY_MS = 24L * 60 * 60 * 1000
    const val HOLD_DAYS = 2             // a song heard this recently goes last
    const val RECENT_DAYS = 14          // "heard lately", for a song and its album

    /** A new generator for every shuffle: the system's secure source mixed with the time. */
    fun freshRandom(): Random = Random(SecureRandom().nextLong() xor System.nanoTime())

    /**
     * Every song in `songs` once, shuffled. Songs marked "skip when shuffling" are
     * left out (set `keepSkipped` to keep them). `library` isn't used any more (from
     * LuxTunes 0.8.9 albums are counted within the list being shuffled); it stays so
     * the callers don't have to change.
     */
    fun order(
        songs: List<Track>,
        library: List<Track> = songs,
        heardAt: (Track) -> Long? = ::lastHeard,
        random: Random = freshRandom(),
        keepSkipped: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): List<Track> {
        val pool = if (keepSkipped) songs else songs.filter { !skipWhenShuffling(it) }
        // 1. Fair shuffle.
        val fair = pool.toMutableList()
        Collections.shuffle(fair, random)
        // 2. Weighted random order (Efraimidis–Spirakis): rank = u^(1/weight), highest first.
        //    Songs with no weight are held back, in their shuffled order.
        val weight = weights(pool, heardAt, now)
        val main = ArrayList<Pair<Double, Track>>()
        val held = ArrayList<Track>()
        for (t in fair) {
            val w = weight[t.id]
            if (w == null) held += t else main += random.nextDouble().pow(1 / w) to t
        }
        main.sortByDescending { it.first }
        // 3. Albums and artists kept apart.
        return build(main.map { it.second }, held)
    }

    /**
     * Step 3: take songs from the front of `main` (then `held`), each time the first of
     * the next LOOK_AHEAD whose artist and album have waited long enough; if none has,
     * the one whose artist and album were heard longest ago (for their wait).
     */
    internal fun build(main: List<Track>, held: List<Track>): List<Track> {
        val all = main + held
        val total = all.size
        if (total == 0) return all
        // Artists and albums as numbers (-1: none), worked out once.
        val ids = HashMap<String, Int>()
        val artistIds = HashSet<Int>()
        val albumIds = HashSet<Int>()
        fun idOf(key: String): Int = ids.getOrPut(key) { ids.size }
        val artistOf = HashMap<String, Int>(total * 2)
        val albumOf = HashMap<String, Int>(total * 2)
        for (t in all) {
            val a = artistKey(t).let { if (it.isEmpty()) -1 else idOf("a\u0000$it") }
            val b = albumKey(t).let { if (it.isEmpty()) -1 else idOf("b\u0000$it") }
            artistOf[t.id] = a
            albumOf[t.id] = b
            if (a >= 0) artistIds += a
            if (b >= 0) albumIds += b
        }
        val count = IntArray(ids.size)
        for (t in all) {
            artistOf.getValue(t.id).let { if (it >= 0) count[it]++ }
            albumOf.getValue(t.id).let { if (it >= 0) count[it]++ }
        }
        val artistGap = (artistIds.size / 6).coerceIn(ARTIST_GAP, ARTIST_GAP_MAX)
        val albumGap = (albumIds.size / 4).coerceIn(ALBUM_GAP, ALBUM_GAP_MAX)
        // How many songs each artist or album waits before it can come up again.
        val wait = IntArray(ids.size) { k ->
            val gap = if (k in artistIds) artistGap else albumGap
            // Never below 0: one artist with more than 80% of the songs would get -1, and the
            // score below divides by wait + 1 (LuxTunes 0.8.8 crashed on such a playlist).
            (minOf(gap, (total * EVEN / count[k]).toInt() - 1)).coerceAtLeast(0)
        }
        val last = IntArray(ids.size) { Int.MIN_VALUE }      // where each was last placed

        fun since(key: Int, position: Int): Double =
            if (key < 0 || last[key] == Int.MIN_VALUE) Double.POSITIVE_INFINITY else (position - last[key]).toDouble()

        val queues = listOf(ArrayList(main), ArrayList(held))
        val out = ArrayList<Track>(total)
        for (position in 0 until total) {
            val queue = queues.first { it.isNotEmpty() }
            var pick = -1
            var best = -1
            var bestScore = Double.NEGATIVE_INFINITY
            for (i in 0 until minOf(LOOK_AHEAD, queue.size)) {
                val t = queue[i]
                val a = artistOf.getValue(t.id)
                val b = albumOf.getValue(t.id)
                val sa = since(a, position)
                val sb = since(b, position)
                val wa = if (a >= 0) wait[a] else 0
                val wb = if (b >= 0) wait[b] else 0
                if (sa > wa && sb > wb) { pick = i; break }
                val score = minOf(sa / (wa + 1), sb / (wb + 1))
                if (score > bestScore) { bestScore = score; best = i }
            }
            val t = queue.removeAt(if (pick >= 0) pick else best)
            out += t
            artistOf.getValue(t.id).let { if (it >= 0) last[it] = position }
            albumOf.getValue(t.id).let { if (it >= 0) last[it] = position }
        }
        return out
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

    /**
     * Each song's weight in step 2 (by ID), or null when it goes last:
     * - null: the song itself was heard in the last 2 days, or this session;
     * - 0.2: the song itself was heard in the last 14 days;
     * - otherwise 1 - 0.8 × the share of its album (in `songs`) heard in the last 14 days
     *   (a song without an album name: 1).
     */
    fun weights(songs: List<Track>, heardAt: (Track) -> Long?, now: Long): Map<String, Double?> {
        fun lately(at: Long?, days: Int) = at != null && now - at < days * DAY_MS
        val heard = HashMap<String, Long?>(songs.size * 2)
        for (t in songs) heard[t.id] = heardAt(t)
        // Per album: its songs in the list, and how many of them were heard lately.
        val onAlbum = HashMap<String, Int>()
        val heardOnAlbum = HashMap<String, Int>()
        for (t in songs) {
            val key = albumKey(t)
            if (key.isEmpty()) continue
            onAlbum.merge(key, 1, Int::plus)
            if (lately(heard[t.id], RECENT_DAYS)) heardOnAlbum.merge(key, 1, Int::plus)
        }
        return songs.associate { t ->
            val own = heard[t.id]
            t.id to when {
                lately(own, HOLD_DAYS) -> null
                lately(own, RECENT_DAYS) -> MIN_WEIGHT
                else -> {
                    val key = albumKey(t)
                    val share = if (key.isEmpty()) 0.0 else (heardOnAlbum[key] ?: 0).toDouble() / onAlbum.getValue(key)
                    1 - (1 - MIN_WEIGHT) * share
                }
            }
        }
    }

    /** When a song was last heard: the later of its last play and last skip; now if it was started this session. */
    fun lastHeard(t: Track): Long? {
        if (t.id in HeardThisSession) return System.currentTimeMillis()
        fun parse(date: String?) = date?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        val played = parse(t.playDate)
        val skipped = parse(t.skipDate)
        return if (played == null) skipped else if (skipped == null) played else maxOf(played, skipped)
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
