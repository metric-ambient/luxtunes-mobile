package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesTest {
    private var next = 0
    private fun song(extra: Map<String, Any?>): Track {
        val n = next++
        return trackOf(mapOf<String, Any?>("id" to "%016X".format(n), "path" to "x/$n.mp3", "name" to "Song $n") + extra)!!
    }

    // ---- Splitting names ----

    @Test
    fun namesSplitAtSemicolonsOnly() {
        assertEquals(listOf("Lümen", "Grayfield"), splitNames(" Lümen ;Grayfield;; lümen "))
        assertEquals(listOf("Tyler, the Creator", "Bo"), splitNames("Tyler, the Creator; Bo"))
        assertEquals(listOf("Earth, Wind & Fire"), splitNames("Earth, Wind & Fire"))
        assertEquals(listOf("Big Band"), splitNames("  Big    Band "))
        assertEquals("Lümen; Grayfield", joinNames(listOf("Lümen", " Grayfield", "lümen")))
        assertEquals("", joinNames(emptyList()))
    }

    @Test
    fun suggestionOnlyWhenEveryPartIsAnArtist() {
        val known = setOf("lümen", "grayfield", "simon & garfunkel")
        assertEquals(listOf("Lümen", "Grayfield"), suggestSplit("Lümen & Grayfield", known))
        assertEquals(listOf("Lümen", "Grayfield"), suggestSplit("Lümen x Grayfield", known))
        assertTrue(suggestSplit("Simon & Garfunkel", known).isEmpty())
        assertTrue(suggestSplit("Lümen", known).isEmpty())
        assertTrue(suggestSplit("Lümen & Someone New", known).isEmpty())
    }

    // ---- Browsing by artist ----

    @Test
    fun aSongWithArtistsIsUnderEachOfThem() {
        // A duet on its own: under Lümen and Grayfield, not "Lümen & Grayfield".
        val duet = song(mapOf("artist" to "Lümen & Grayfield", "artists" to "Lümen; Grayfield", "album" to "Single"))
        assertEquals(listOf("Lümen", "Grayfield"), browseArtists(duet, groupCompilations = true))
        // On a Lümen album: still under Lümen (once) and Grayfield.
        val onAlbum = song(mapOf("artist" to "Lümen & Grayfield", "artists" to "Lümen; Grayfield", "album_artist" to "Lümen", "album" to "LP"))
        assertEquals(listOf("Lümen", "Grayfield"), browseArtists(onAlbum, groupCompilations = true))
        // On someone else's album, it stays there too.
        val mixtape = song(mapOf("artist" to "Lümen & Grayfield", "artists" to "Lümen; Grayfield", "album_artist" to "DJ Q", "album" to "Tape"))
        assertEquals(listOf("DJ Q", "Lümen", "Grayfield"), browseArtists(mixtape, groupCompilations = true))
        // Without Artists, as before; a grouped compilation stays under the compilation.
        assertEquals(listOf("Me"), browseArtists(song(mapOf("artist" to "Me")), groupCompilations = true))
        val comp = song(mapOf("artist" to "Lümen & Grayfield", "artists" to "Lümen; Grayfield", "album" to "Now 1", "compilation" to 1L))
        assertEquals(listOf(VARIOUS_ARTISTS), browseArtists(comp, groupCompilations = true))
    }

    @Test
    fun theArtistsTabListsADuetUnderBothNamesOnly() {
        val duet = song(mapOf("artist" to "Lümen & Grayfield", "artists" to "Lümen; Grayfield"))
        val solo = song(mapOf("artist" to "Lümen"))
        val groups = groupByNames(listOf(duet, solo)) { browseArtists(it, true) }.toMap()
        assertEquals(setOf("Lümen", "Grayfield"), groups.keys)
        assertEquals(2, groups.getValue("Lümen").size)
        assertEquals(listOf(duet), groups.getValue("Grayfield"))
    }

    // ---- Compilation songs on the Artists tab ----

    private fun example(): List<Track> = listOf(
        song(mapOf("artist" to "Ann", "album" to "Ann Single")),
        song(mapOf("artist" to "Ann & Guest", "artists" to "Ann; Guest", "album" to "Hits 2026", "compilation" to 1L)),
        song(mapOf("artist" to "Bo", "album" to "Hits 2026", "compilation" to 1L)),
        song(mapOf("artist" to "Bo", "album" to "Hits 2027", "compilation" to 1L)),
        song(mapOf("artist" to "Ann & Cy", "artists" to "Ann; Cy", "album" to "Duo")),
    )

    private fun entries(songs: List<Track>, group: Boolean): Map<String, List<Int>> =
        artistEntries(songs, group).associate { (name, list) -> name to list.map { songs.indexOf(it) + 1 } }

    @Test
    fun compilationSongsAlsoGoUnderArtistsWithSongsOfTheirOwn() {
        val songs = example()
        assertEquals(mapOf("Ann" to listOf(1, 2, 5), "Cy" to listOf(5), VARIOUS_ARTISTS to listOf(2, 3, 4)), entries(songs, true))
        // Group Compilations off: everyone gets an entry, and there's no Various Artists.
        assertEquals(mapOf("Ann" to listOf(1, 2, 5), "Bo" to listOf(3, 4), "Cy" to listOf(5), "Guest" to listOf(2)), entries(songs, false))
    }

    @Test
    fun anArtistWhoseOwnSongIsGoneLosesTheirEntry() {
        val songs = example().filterIndexed { i, _ -> i != 0 && i != 4 }      // Ann's own songs deleted
        assertEquals(setOf(VARIOUS_ARTISTS), entries(songs, true).keys)
        // Search and From This Artist still find compilation songs.
        val all = example()
        assertTrue(matchesWords(all[1].searchText, searchWords("guest")))
        assertEquals(setOf(all[2].id, all[3].id), RadioMix.seedTracks(all, "artist", all[2]).map { it.id }.toSet())
    }

    // ---- Search ----

    @Test
    fun searchIgnoresCaseAndAccents() {
        val lumen = fold("Lümen")
        for (typed in listOf("lumen", "LUMEN", "Lümen")) assertTrue(matchesWords(lumen, searchWords(typed)))
        assertTrue(matchesWords(fold("Sigur Rós"), searchWords("sigur ros")))
        assertTrue(matchesWords(fold("Straße"), searchWords("strasse")))
        assertTrue(matchesWords(fold("Beyoncé"), searchWords("beyonce")))
        assertTrue(matchesWords(fold("Motörhead"), searchWords("motorhead")))
        assertTrue(matchesWords(fold("Røyksopp"), searchWords("royksopp")))
        assertFalse(matchesWords(fold("Lümen"), searchWords("lumen grayfield")))
    }

    @Test
    fun songSearchTakesEveryWordAndTheArtistsList() {
        val duet = song(mapOf("artist" to "Lümen & Grayfield", "album" to "LP"))
        assertTrue(matchesWords(duet.searchText, searchWords("lumen grayfield")))
        val listed = song(mapOf("artist" to "Somebody", "artists" to "Lümen; Grayfield"))
        assertTrue(matchesWords(listed.searchText, searchWords("grayfield")))
    }

    @Test
    fun suggestionsIgnoreAccentsAndMatchLaterWords() {
        val names = listOf("Lümen", "Grayfield", "The Tull Band")
        assertEquals(listOf("Lümen", "The Tull Band"), suggestNames("tul", names))
        assertEquals(listOf("The Tull Band"), suggestNames("tul", names, leaveOut = setOf("lümen")))
    }

    // ---- Pills ----

    @Test
    fun pillBoxes() {
        // A comma stays in a name; a semicolon makes a pill.
        assertEquals(emptyList<String>() to "Tyler, the Creator", pillInput("Tyler, the Creato", "Tyler, the Creator", genre = false))
        assertEquals(listOf("Tyler, the Creator") to "", pillInput("Tyler, the Creator", "Tyler, the Creator;", genre = false))
        // In Genre a comma does too.
        assertEquals(listOf("Pop") to "", pillInput("Pop", "Pop,", genre = true))
        // Pasting makes a pill of each.
        assertEquals(listOf("A", "B") to "", pillInput("", "A; B", genre = false))
        // Repeats are dropped, ignoring case; genres keep their own splitting.
        assertEquals(listOf("Ann", "Bo"), addPills(listOf("Ann"), listOf("ann", "Bo"), genre = false))
        assertEquals(listOf("Metalcore", "Post-hardcore"), addPills(listOf("Metalcore"), listOf("Post-hardcore, metalcore"), genre = true))
        assertEquals("Metalcore, Post-hardcore", joinGenres(listOf("Metalcore", "Post-hardcore")))
    }

    // ---- Radio Mix: From This Artist ----

    @Test
    fun everyoneASongIsBy() {
        val duet = song(mapOf("artist" to "Lümen & Grayfield", "artists" to "Lümen; Grayfield", "album_artist" to "Lümen"))
        assertEquals(setOf("lümen & grayfield", "lümen", "grayfield"), whoOf(duet))
    }
}
