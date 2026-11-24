package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturedAndCompilationTest {
    private fun song(extra: Map<String, Any?>): Track =
        trackOf(mapOf<String, Any?>("id" to "1A2B3C4D5E6F7081", "path" to "x/1.mp3", "name" to "Song", "artist" to "Me") + extra)!!

    @Test
    fun featuredNames() {
        assertTrue(featuredNames(null).isEmpty())
        assertTrue(featuredNames("").isEmpty())
        assertTrue(featuredNames(" ; ;").isEmpty())
        // From LuxTunes 0.9.2 only semicolons separate: a comma can be part of a name.
        assertEquals(listOf("Ann, Bo & Cy", "Dee"), featuredNames("Ann, Bo & Cy; Dee"))
        assertEquals(listOf("Tyler, the Creator", "Bo"), featuredNames("Tyler, the Creator; Bo"))
        assertEquals(listOf("Earth, Wind & Fire"), featuredNames("Earth, Wind & Fire"))
    }

    @Test
    fun nameWithFeatured() {
        assertEquals("Song (ft. Ann, Bo & Cy)", nameWithFeatured("Song", "Ann; Bo & Cy"))
        assertEquals("Song (ft. Ann, Bo)", nameWithFeatured("Song", "Ann;Bo"))
        assertEquals("Song", nameWithFeatured("Song", " ; "))
        assertEquals("Song", nameWithFeatured("Song", null))
    }

    @Test
    fun aSongShowsItsFeaturedArtistsButKeepsItsName() {
        val t = song(mapOf("featured_artist" to "Ann; Bo"))
        assertEquals("Song (ft. Ann, Bo)", t.displayName)
        assertEquals("Song", t.name)
        assertEquals("Song", t.sortName)              // sorting ignores the "(ft. …)" part
        // A library.db from an older LuxTunes has no such column.
        val old = song(emptyMap())
        assertNull(old.featuredArtist)
        assertEquals("Song", old.displayName)
    }

    @Test
    fun compilationsAreBrowsedUnderTheCompilation() {
        val plain = song(mapOf("album" to "Own Album"))
        assertEquals("Me", plain.browseArtist)
        val withAlbumArtist = song(mapOf("album" to "Lantern Valley I", "album_artist" to "Lantern Valley", "compilation" to 1L))
        assertEquals("Lantern Valley", withAlbumArtist.browseArtist)
        assertEquals("Me", withAlbumArtist.artist)    // the song's own artist is unchanged
        val various = song(mapOf("album" to "Now 1", "compilation" to 1L))
        assertEquals(VARIOUS_ARTISTS, various.browseArtist)
        // All of a compilation's songs make one album, whoever sings each.
        val other = trackOf(mapOf("id" to "00FF00FF00FF00FF", "path" to "x/2.mp3", "artist" to "Someone Else",
            "album" to "Now 1", "compilation" to 1L))!!
        assertEquals(various.albumKey, other.albumKey)
    }
}
