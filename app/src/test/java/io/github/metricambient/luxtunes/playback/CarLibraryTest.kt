package io.github.metricambient.luxtunes.playback

import io.github.metricambient.luxtunes.data.Album
import io.github.metricambient.luxtunes.data.Artist
import io.github.metricambient.luxtunes.data.LibraryState
import io.github.metricambient.luxtunes.data.Playlist
import io.github.metricambient.luxtunes.data.Track
import io.github.metricambient.luxtunes.data.trackOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a car plays when a song is picked in one of its lists (made-up library). */
class CarLibraryTest {
    private fun song(n: Int, artist: String, album: String): Track =
        trackOf(mapOf("id" to "%016X".format(n), "path" to "x/$n.mp3", "name" to "Song $n",
            "artist" to artist, "album" to album, "track_number" to n.toLong()))!!

    private val first = (1..3).map { song(it, "Lümen", "Paper Tales") }
    private val second = (4..5).map { song(it, "Grayfield", "Harbor") }
    private val all = first + second
    private val albums = listOf(
        Album(first[0].albumKey, "Paper Tales", "Lümen", 2020, null, first),
        Album(second[0].albumKey, "Harbor", "Grayfield", 2021, null, second),
    )
    private val library = LibraryState(
        loaded = true, tracks = all, albums = albums, allTracks = all,
        artists = listOf(Artist("Lümen", listOf(albums[0])), Artist("Grayfield", listOf(albums[1]))),
        playlists = listOf(Playlist("P1", "Mix", false, listOf(second[1].id, first[0].id, second[1].id))),
    )

    @Test
    fun aSongInAnAlbumPlaysTheAlbumFromThatSong() {
        val id = "album:${albums[0].key}\u001F1\u001F${first[1].id}"
        val (songs, at) = CarLibrary.resolve(id, library, null)!!
        assertEquals(first, songs)
        assertEquals(1, at)
        assertEquals(first[1].id, CarLibrary.songId(id))
    }

    @Test
    fun aPlaylistKeepsItsOrderAndRepeats() {
        val (songs, at) = CarLibrary.resolve("playlist:P1\u001F2\u001F${second[1].id}", library, null)!!
        assertEquals(listOf(second[1], first[0], second[1]), songs)
        assertEquals(2, at)
    }

    @Test
    fun aListThatChangedFindsTheSongAgain() {
        // The car showed it third, but it's first now.
        val (_, at) = CarLibrary.resolve("album:${albums[1].key}\u001F7\u001F${second[0].id}", library, null)!!
        assertEquals(0, at)
        assertNull(CarLibrary.resolve("album:${albums[1].key}\u001F0\u001FFFFFFFFFFFFFFFFF", library, null))
    }

    @Test
    fun theAppsOwnSongIdsAreLeftAlone() {
        assertNull(CarLibrary.resolve(first[0].id, library, null))
        assertEquals(first[0].id, CarLibrary.songId(first[0].id))
    }

    @Test
    fun shufflingAnArtistTakesAllTheirSongs() {
        val (songs, at) = CarLibrary.resolve("artist:Lümen\u001Fshuffle", library, null)!!
        assertEquals(first.toSet(), songs.toSet())
        assertEquals(0, at)
    }

    @Test
    fun shufflingAnArtistByAlbumKeepsEachAlbumTogether() {
        val more = (6..7).map { song(it, "Lümen", "Second") }
        val lib = library.copy(
            tracks = all + more, allTracks = all + more,
            artists = listOf(Artist("Lümen", listOf(albums[0], Album(more[0].albumKey, "Second", "Lümen", 2022, null, more)))),
        )
        val (songs, at) = CarLibrary.resolve("artist:Lümen\u001Fshuffle-albums", lib, null)!!
        assertEquals(0, at)
        assertTrue(songs == first + more || songs == more + first)       // each album whole, in its own order
        assertNull(CarLibrary.resolve("artist:Lümen\u001Fsomething", lib, null))
    }

    @Test
    fun playingByVoice() {
        assertEquals(second.toSet(), CarLibrary.playFromSearch("grayfield", library).toSet())   // an artist
        assertEquals(first, CarLibrary.playFromSearch("paper tales", library))                  // an album, in order
        assertEquals(listOf(first[1]), CarLibrary.playFromSearch("song 2", library))            // songs that match
        assertTrue(CarLibrary.search("nothing like this", library).isEmpty())
    }
}
