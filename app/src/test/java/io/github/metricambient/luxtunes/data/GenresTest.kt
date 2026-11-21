package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The app's own genre catalogue (assets/genres.json), read once for all the tests. */
object TestCatalogue {
    val catalogue: GenreCatalogue by lazy {
        val file = listOf("src/main/assets/genres.json", "app/src/main/assets/genres.json").map(::File).first { it.exists() }
        GenreCatalogue(file.readText())
    }
}

class GenresTest {
    private val cat get() = TestCatalogue.catalogue

    private fun song(n: Int, genre: String): Track =
        trackOf(mapOf("id" to "%016X".format(n), "path" to "x/$n.mp3", "name" to "Song $n", "artist" to "A", "album" to "B", "genre" to genre))!!

    // ---- Several genres in one field ----

    @Test
    fun splitsAtCommasAndSemicolonsOnly() {
        assertEquals(listOf("Metalcore", "Post-hardcore"), splitGenres("Metalcore, Post-hardcore"))
        assertEquals(listOf("Rock", "Pop"), splitGenres(" Rock ;Pop "))
        assertEquals(listOf("R&B/Soul"), splitGenres("R&B/Soul"))
        assertEquals(emptyList<String>(), splitGenres(", ;"))
    }

    @Test
    fun emptyFieldIsUnknownGenre() {
        assertEquals(listOf(UNKNOWN_GENRE), genresOf(""))
        assertEquals(listOf(UNKNOWN_GENRE), genresOf(", ;"))
    }

    @Test
    fun aSongWithTwoGenresIsUnderBothAndCountedOnce() {
        val songs = listOf(song(1, "Metalcore, Post-hardcore"), song(2, "Metalcore"), song(3, ""))
        val groups = groupByNames(songs) { genresOf(it.genre) }.toMap()
        assertEquals(setOf("Metalcore", "Post-hardcore", UNKNOWN_GENRE), groups.keys)
        assertEquals(2, groups.getValue("Metalcore").size)
        assertEquals(1, groups.getValue("Post-hardcore").size)
        // "All" (the tab's Play) has each song once.
        assertEquals(3, groups.values.flatten().distinctBy { it.id }.size)
    }

    @Test
    fun rockAndRockAreOneGenre() {
        val groups = groupByNames(listOf(song(1, "Rock"), song(2, "rock"), song(3, "Rock, rock"))) { genresOf(it.genre) }
        assertEquals(1, groups.size)
        assertEquals("Rock", groups[0].first)            // the first song's spelling
        assertEquals(3, groups[0].second.size)
    }

    // ---- Matching the catalogue ----

    @Test
    fun spellingsOfOneGenreMatch() {
        val a = cat.genreIds("Post-Hardcore")
        assertTrue(a.isNotEmpty())
        assertEquals(a, cat.genreIds("post hardcore"))
        assertEquals(a, cat.genreIds("Post Hardcore"))
        assertEquals(cat.genreIds("Drum & Bass"), cat.genreIds("drum and bass"))
    }

    @Test
    fun popCommaRockIsTwoGenres() {
        val both = cat.genreIds("Pop, Rock")
        assertEquals(2, both.size)
        assertEquals(cat.genreIds("Pop") + cat.genreIds("Rock"), both)
    }

    @Test
    fun storeNamesAndUnknownNames() {
        assertTrue(cat.genreIds("R&B/Soul").isNotEmpty())
        assertTrue(cat.genreIds("Other").isEmpty())
        assertTrue(cat.genreIds("").isEmpty())
    }

    @Test
    fun savedIdsComeBackAsTheSameGenres() {
        val g = cat.genreIds("Metalcore")
        assertEquals(g, cat.indexes(cat.ids(g)))
    }

    // ---- Closeness ----

    @Test
    fun metalcoreIsCloserToDeathcoreThanToPostHardcoreThanToCountry() {
        val metalcore = cat.genreIds("Metalcore")
        val near = cat.related(metalcore)
        fun of(name: String) = cat.genreIds(name).maxOf { near[it] ?: 0.0 }
        assertEquals(1.0, of("Metalcore"), 0.0)
        assertTrue(of("Deathcore") > of("Post-hardcore"))
        assertTrue(of("Post-hardcore") > of("Country"))
    }
}
