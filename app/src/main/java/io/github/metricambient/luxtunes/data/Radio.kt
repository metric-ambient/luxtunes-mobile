package io.github.metricambient.luxtunes.data

import java.time.Instant
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.pow

/**
 * A radio: an endless mix of songs related to a song, an album or an artist, worked out
 * on the phone from the library (as LuxTunes does on the computer, core/radio.py).
 * `genres` are catalogue indexes in memory; they're saved as catalogue ids.
 */
data class Radio(
    val kind: String,                   // "song", "album" or "artist"
    val seedId: String,                 // the song it was started from
    val title: String,                  // "Harbor Lights Radio", "<album> Radio", "<artist> Radio"
    val genres: List<Int>,
    val trackIds: List<String>,
    val discover: Boolean = false,
) {
    companion object {
        val KINDS = listOf("song", "album", "artist")

        fun titleFor(kind: String, t: Track): String = when (kind) {
            "album" -> t.album.ifBlank { "Unknown Album" }
            "artist" -> t.artist.ifBlank { t.albumArtist }.ifBlank { "Unknown Artist" }
            else -> t.name.ifBlank { "Untitled" }
        } + " Radio"
    }
}

/** A like (1) or dislike (-1) given on a radio, with that radio's genres (indexes) and title. */
data class Opinion(val value: Int, val genres: List<Int>, val radio: String, val date: String = Instant.now().toString())

/**
 * Picking a radio's songs. Everything here works on what it's given (no storage), so it
 * can be tested on its own. The numbers are the computer's.
 */
object RadioMix {
    const val SIZE = 50
    const val TOP_UP = 25
    const val TOP_UP_WHEN = 10
    const val NEAR = 0.3                // a like or dislike from a radio this close counts in full
    private val SEED_ARTIST_SHARE = mapOf("song" to 0.2, "album" to 0.25, "artist" to 0.4)
    private const val OTHER_ARTIST_SHARE = 0.1

    /** How much each thing counts: normal, Discover. */
    private class Weights(private val discover: Boolean) {
        private fun w(normal: Double, disc: Double) = if (discover) disc else normal
        val genre = w(0.45, 0.60)
        val artist = w(0.15, 0.05)
        val playlist = w(0.20, -0.10)
        val plays = w(0.10, 0.0)
        val unheard = w(0.0, 0.20)
        val rating = w(0.05, 0.0)
        val year = w(0.05, 0.10)
        val liked = w(0.50, 0.30)
        val disliked = w(0.50, 0.50)
    }

    /** What a radio is worked out from. */
    class Input(
        val library: List<Track>,                   // every song (only music is used)
        val playlists: List<Playlist>,
        val opinions: Map<String, Opinion>,
        val catalogue: GenreCatalogue?,
    ) {
        val music: List<Track> = library.filter { it.mediaKind == "music" }
        val byId: Map<String, Track> = library.associateBy { it.id }
    }

    /** The songs a radio starts from: the song, its album, or every song by its artist. */
    fun seedTracks(music: List<Track>, kind: String, seed: Track): List<Track> {
        val found = when (kind) {
            "album" -> music.filter { it.albumKey == seed.albumKey }
            "artist" -> {
                // Everyone the song is by: Artist, Album Artist and each of its Artists
                // (a song by "Lümen; Grayfield" starts from both).
                val wanted = whoOf(seed)
                if (wanted.isEmpty()) emptyList() else music.filter { t -> whoOf(t).any { it in wanted } }
            }
            else -> listOf(seed)
        }
        return found.ifEmpty { listOf(seed) }
    }

    /** The five catalogue genres most common among the starting songs. */
    fun seedGenres(seeds: List<Track>, catalogue: GenreCatalogue?, most: Int = 5): List<Int> {
        if (catalogue == null) return emptyList()
        val counts = LinkedHashMap<Int, Int>()
        for (t in seeds) for (g in catalogue.genreIds(t.genre)) counts.merge(g, 1, Int::plus)
        return counts.entries.sortedByDescending { it.value }.take(most).map { it.key }
    }

    /** A new radio from this song / its album / its artist (its songs picked and ordered). */
    fun start(input: Input, kind: String, seed: Track, discover: Boolean = false, random: Random = Shuffle.freshRandom()): Radio {
        val seeds = seedTracks(input.music, kind, seed)
        val radio = Radio(kind, seed.id, Radio.titleFor(kind, seed), seedGenres(seeds, input.catalogue), emptyList(), discover)
        val picked = if (kind == "song") listOf(seed.id) + pick(input, radio, seeds, SIZE - 1, setOf(seed.id), random)
        else pick(input, radio, seeds, SIZE, emptySet(), random)
        return radio.copy(trackIds = picked)
    }

    /** `count` more songs for the end of the radio (none it already has). */
    fun extend(input: Input, radio: Radio, count: Int = TOP_UP, random: Random = Shuffle.freshRandom()): List<String> {
        val seed = input.byId[radio.seedId] ?: return emptyList()
        val seeds = seedTracks(input.music, radio.kind, seed)
        return pick(input, radio, seeds, count, radio.trackIds.toSet(), random)
    }

    private fun eligible(t: Track): Boolean =
        t.mediaKind == "music" && enabled(t) && !Shuffle.skipWhenShuffling(t)

    /** Unchecked songs are left out ("enabled" 0). */
    fun enabled(t: Track): Boolean = when (val v = t.raw["enabled"]) {
        is Number -> v.toLong() != 0L
        is String -> v.trim() != "0"
        else -> true
    }

    private fun versionKey(t: Track) = t.name.trim().lowercase() to t.artist.trim().lowercase()

    /** How much each song shares your own playlists with the starting songs (0..1; big playlists count less). */
    private fun playlistCloseness(playlists: List<Playlist>, seeds: List<Track>): Map<String, Double> {
        val seedIds = seeds.map { it.id }.toSet()
        val score = HashMap<String, Double>()
        for (p in playlists) {
            if (p.isSmart) continue
            val songs = p.trackIds.toSet()
            if (songs.size > 1 && songs.any { it in seedIds }) {
                val weight = 1 / ln(songs.size + 1.0)
                for (id in songs) score.merge(id, weight, Double::plus)
            }
        }
        val top = score.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        return score.mapValues { it.value / top }
    }

    /** Every eligible song's score for this radio (higher: more likely); songs left out aren't in it. */
    fun scores(input: Input, radio: Radio, seeds: List<Track>): Map<String, Double> {
        val w = Weights(radio.discover)
        val catalogue = input.catalogue
        val near = if (catalogue != null && radio.genres.isNotEmpty()) catalogue.related(radio.genres) else emptyMap()
        val genreCache = HashMap<String, Double>()
        fun genreScore(field: String): Double {
            if (catalogue == null || near.isEmpty() || field.isBlank()) return 0.0
            return genreCache.getOrPut(field) { catalogue.genreIds(field).maxOfOrNull { near[it] ?: 0.0 } ?: 0.0 }
        }
        /** How much a like or dislike given on another radio applies here (0..1). */
        fun reach(genres: List<Int>): Double {
            if (genres.isEmpty() || near.isEmpty()) return 0.2
            val closeness = genres.maxOf { near[it] ?: 0.0 }
            return if (closeness >= NEAR) 1.0 else 0.2 + closeness
        }

        val seedIds = seeds.map { it.id }.toSet()
        val seedArtists = (seeds.map { Shuffle.artistKey(it) } + seeds.flatMap { whoOf(it) }).toMutableSet().apply { remove("") }
        val years = seeds.map { it.year }.filter { it > 0 }.sorted()
        val seedYear = if (years.isEmpty()) null else years[years.size / 2]
        val together = playlistCloseness(input.playlists, seeds)
        val playsScale = ln1p((input.music.maxOfOrNull { it.playCount } ?: 0).toDouble()).takeIf { it > 0 } ?: 1.0

        val result = HashMap<String, Double>()
        for (t in input.music) {
            if (!eligible(t)) continue
            val genre = genreScore(t.genre)
            var artist = if (Shuffle.artistKey(t) in seedArtists || whoOf(t).any { it in seedArtists }) 1.0 else 0.0
            val shared = together[t.id] ?: 0.0
            if (radio.kind == "artist" && t.id in seedIds) artist = 1.0
            // Not related at all (no genre link, artist or playlist): not on this radio.
            if (genre < 0.01 && artist == 0.0 && (shared <= 0 || radio.discover)) continue
            val plays = ln1p(t.playCount.toDouble()) / playsScale
            val rating = if (t.ratingComputed) 0.0 else t.rating / 100.0
            val year = if (seedYear != null && t.year > 0) exp(-abs(t.year - seedYear) / 10.0) else 0.0
            // Your listening counts only as far as the song is related at all, so a favourite
            // from another genre doesn't land on every radio.
            val related = maxOf(genre, artist, if (near.isEmpty()) 0.25 else 0.0)
            val personal = w.playlist * shared + w.plays * plays + w.unheard * (1 - plays) +
                w.rating * rating + w.year * year
            val artistWeight = w.artist * (if (radio.kind == "artist") 2.3 else 1.0)
            var score = w.genre * genre + artistWeight * artist + personal * related
            val opinion = input.opinions[t.id]
            if (opinion != null) {
                val r = reach(opinion.genres)
                if (opinion.value < 0) {
                    if (r >= 1.0) continue               // disliked on a radio like this one
                    score -= w.disliked * r
                } else {
                    score += w.liked * r
                }
            }
            if (score > 0) result[t.id] = score
        }
        return result
    }

    /**
     * `count` songs drawn from the best-scoring ones (better scores more likely), without
     * songs in `exclude` or second copies of a song, each artist within its limit, then
     * ordered like a shuffle (artists and albums spread out, better scores earlier).
     */
    fun pick(input: Input, radio: Radio, seeds: List<Track>, count: Int, exclude: Set<String>, random: Random): List<String> {
        val byId = input.byId
        val takenVersions = exclude.mapNotNull { byId[it] }.map(::versionKey).toMutableSet()
        val scored = scores(input, radio, seeds)
        var ranked = scored.keys.filter { it !in exclude }.sortedByDescending { scored.getValue(it) }
        // Draw from the closest: those within half the best score, or further down only
        // when there aren't three times as many as needed.
        if (ranked.isNotEmpty()) {
            val best = scored.getValue(ranked[0])
            var close = ranked
            for (share in listOf(0.5, 0.35, 0.2, 0.0)) {
                close = ranked.filter { scored.getValue(it) >= best * share }
                if (close.size >= count * 3) break
            }
            ranked = close
        }
        // How many songs each artist may have, counting those already on the radio.
        val total = exclude.size + count
        val seedArtists = seeds.map { Shuffle.artistKey(it) }.toSet()
        val seedCap = maxOf(2, Math.rint(total * SEED_ARTIST_SHARE.getValue(radio.kind)).toInt())
        val otherCap = maxOf(2, Math.rint(total * OTHER_ARTIST_SHARE).toInt())
        val perArtist = HashMap<String, Int>()
        for (id in exclude) byId[id]?.let { perArtist.merge(Shuffle.artistKey(it), 1, Int::plus) }
        fun full(t: Track): Boolean {
            val who = Shuffle.artistKey(t)
            return (perArtist[who] ?: 0) >= (if (who in seedArtists) seedCap else otherCap)
        }

        val chosen = ArrayList<String>()
        val pool = ranked.toMutableList()
        while (pool.isNotEmpty() && chosen.size < count) {
            pool.removeAll { full(byId.getValue(it)) }        // artists already at their limit
            if (pool.isEmpty()) break
            // Weighted by score², without putting back.
            val weights = pool.map { scored.getValue(it).pow(2) }
            var at = random.nextDouble() * weights.sum()
            var index = weights.lastIndex
            for (i in weights.indices) { at -= weights[i]; if (at < 0) { index = i; break } }
            val id = pool.removeAt(index)
            val t = byId.getValue(id)
            val version = versionKey(t)
            if (version in takenVersions || full(t)) continue
            takenVersions += version
            perArtist.merge(Shuffle.artistKey(t), 1, Int::plus)
            chosen += id
        }
        return order(chosen.map { byId.getValue(it) }, scored, random).map { it.id }
    }

    /** Like a shuffle: a fair shuffle, then better scores tend earlier, then artists and albums spread out. */
    private fun order(songs: List<Track>, scored: Map<String, Double>, random: Random): List<Track> {
        val top = songs.maxOfOrNull { scored.getValue(it.id) }?.takeIf { it > 0 } ?: 1.0
        val fair = songs.toMutableList().apply { java.util.Collections.shuffle(this, random) }
        val ranked = fair.map { t ->
            val weight = maxOf(0.05, scored.getValue(t.id) / top)
            random.nextDouble().pow(1 / weight) to t
        }.sortedByDescending { it.first }.map { it.second }
        return Shuffle.build(ranked, emptyList())
    }
}
