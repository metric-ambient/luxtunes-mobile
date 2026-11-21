package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class RadioTest {
    private val cat get() = TestCatalogue.catalogue
    private var next = 0

    private fun song(artist: String, genre: String, extra: Map<String, Any?> = emptyMap(), name: String? = null): Track {
        val n = next++
        return trackOf(mapOf(
            "id" to "%016X".format(n), "path" to "x/$n.mp3", "name" to (name ?: "Song $n"),
            "artist" to artist, "album" to "$artist Album ${n % 3}", "genre" to genre,
            "play_count" to (n * 7 % 50), "year" to 2005 + n % 15,
        ) + extra)!!
    }

    // 30 heavy bands of 10 songs (metalcore and its neighbours), 50 country and 50 jazz songs.
    private val heavyGenres = listOf("Metalcore", "Deathcore", "Post-hardcore", "Metalcore, Post-hardcore", "Melodic death metal")
    private val heavy = (0 until 300).map { song("Band ${it / 10}", heavyGenres[(it / 10) % heavyGenres.size]) }
    private val country = (0 until 50).map { song("Cowboy ${it / 5}", "Country") }
    private val jazz = (0 until 50).map { song("Trio ${it / 5}", "Jazz") }
    private val unchecked = (0 until 10).map { song("Band X", "Metalcore", mapOf("enabled" to 0L)) }
    private val skipped = (0 until 10).map { song("Band Y", "Metalcore", mapOf("skip_when_shuffling" to 1L)) }
    private val podcasts = (0 until 10).map { song("Pod", "Metalcore", mapOf("media_kind" to "podcast")) }
    // Another copy (an AAC of an MP3) of each of Band 0's songs.
    private val copies = heavy.take(10).map { song(it.artist, it.genre, name = it.name) }
    private val library = heavy + country + jazz + unchecked + skipped + podcasts + copies
    private val seed = heavy[0]

    private fun input(opinions: Map<String, Opinion> = emptyMap()) = RadioMix.Input(library, emptyList(), opinions, cat)

    private fun songRadio(random: Random, opinions: Map<String, Opinion> = emptyMap(), discover: Boolean = false) =
        RadioMix.start(input(opinions), "song", seed, discover, random)

    @Test
    fun aSongRadioHas50SongsStartingWithTheSongAndNoCopies() {
        val radio = songRadio(Random(1))
        assertEquals(50, radio.trackIds.size)
        assertEquals(seed.id, radio.trackIds.first())
        assertEquals(50, radio.trackIds.toSet().size)
        val byId = library.associateBy { it.id }
        val versions = radio.trackIds.map { byId.getValue(it).let { t -> t.name.lowercase() to t.artist.lowercase() } }
        assertEquals(50, versions.toSet().size)
        assertEquals("Song 0 Radio", radio.title)
    }

    @Test
    fun uncheckedSkippedAndPodcastSongsNeverAppear() {
        val never = (unchecked + skipped + podcasts).map { it.id }.toSet()
        repeat(10) { assertTrue(songRadio(Random(it.toLong())).trackIds.none { it in never }) }
    }

    @Test
    fun noArtistGoesOverItsLimit() {
        val byId = library.associateBy { it.id }
        repeat(10) { r ->
            val radio = songRadio(Random(r.toLong()))
            val counts = radio.trackIds.groupingBy { Shuffle.artistKey(byId.getValue(it)) }.eachCount()
            assertTrue(counts.getValue("band 0") <= 10)                 // 20% of 50 for the song's artist
            assertTrue(counts.filterKeys { it != "band 0" }.values.all { it <= 5 })     // 10% for any other
        }
    }

    @Test
    fun unrelatedGenresStayOffAMetalcoreRadio() {
        val off = (country + jazz).map { it.id }.toSet()
        repeat(10) { assertTrue(songRadio(Random(it.toLong())).trackIds.none { it in off }) }
    }

    @Test
    fun growingAddsNewSongsAndKeepsTheOrder() {
        val radio = songRadio(Random(3))
        val added = RadioMix.extend(input(), radio, RadioMix.TOP_UP, Random(4))
        assertEquals(25, added.size)
        assertTrue(added.none { it in radio.trackIds })
        val grown = radio.copy(trackIds = radio.trackIds + added)
        assertEquals(radio.trackIds, grown.trackIds.take(50))
        assertEquals(75, grown.trackIds.toSet().size)
    }

    @Test
    fun aDislikedSongNeverComesBackOnMetalcoreRadios() {
        val first = songRadio(Random(5))
        val disliked = first.trackIds[3]
        val opinions = mapOf(disliked to Opinion(-1, first.genres, first.title))
        repeat(20) { assertFalse(disliked in songRadio(Random(100L + it), opinions).trackIds) }
        // Also from another metalcore song.
        val other = RadioMix.start(input(opinions), "song", heavy.drop(50).first { it.id != disliked && it.genre == "Metalcore" }, random = Random(9))
        assertFalse(disliked in other.trackIds)
    }

    @Test
    fun aLikedSongAppearsMoreOften() {
        val genres = songRadio(Random(1)).genres
        // A song from a less close genre, so it isn't on every radio anyway.
        val liked = heavy.first { it.genre == "Melodic death metal" }.id
        val opinions = mapOf(liked to Opinion(1, genres, "Song 0 Radio"))
        val without = (0 until 60).count { liked in songRadio(Random(200L + it)).trackIds }
        val with = (0 until 60).count { liked in songRadio(Random(200L + it), opinions).trackIds }
        assertTrue("with $with, without $without", with > without)
    }

    @Test
    fun discoverFavoursSongsYouRarelyPlay() {
        val byId = library.associateBy { it.id }
        fun average(discover: Boolean) = (0 until 10).flatMap { songRadio(Random(300L + it), discover = discover).trackIds.drop(1) }
            .map { byId.getValue(it).playCount }.average()
        assertTrue(average(true) < average(false))
    }

    @Test
    fun albumAndArtistRadios() {
        val album = RadioMix.start(input(), "album", seed, random = Random(6))
        assertEquals(50, album.trackIds.size)
        assertEquals("${seed.album} Radio", album.title)
        val artist = RadioMix.start(input(), "artist", seed, random = Random(7))
        assertEquals("Band 0 Radio", artist.title)
        val byId = library.associateBy { it.id }
        // An artist's radio is mostly (up to 40%) theirs.
        val own = artist.trackIds.count { byId.getValue(it).artist == "Band 0" }
        assertTrue(own in 5..10)          // its 10 songs (the copies left out)
    }

    @Test
    fun radioIsKeptPrivateAndNeverSynced() {
        // The radio and likes go only to the app's private files, never to the LuxTunes folder or changes.json.
        fun source(name: String) = listOf("src/main/java", "app/src/main/java").map { java.io.File(it, "io/github/metricambient/luxtunes/data/$name") }
            .first { it.exists() }.readText()
        val store = source("RadioStore.kt").lines().map { it.trim() }
            .filter { !it.startsWith("*") && !it.startsWith("/*") && !it.startsWith("//") }.joinToString("\n")      // code, not comments
        assertTrue("context.filesDir" in store)
        assertFalse("LuxFolder" in store || "changes" in store.lowercase())
        val sync = source("Repository.kt") + source("PendingChanges.kt")
        assertFalse(Regex("radio", RegexOption.IGNORE_CASE).containsMatchIn(sync))
    }
}
