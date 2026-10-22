package io.github.metricambient.luxtunes.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagEditsTest {
    private val id = "1A2B3C4D5E6F7081"

    @Test
    fun onlyKnownFieldsWithTheRightTypes() {
        val clean = TagFields.clean(mapOf("name" to "Song", "year" to "1999", "track_number" to 7, "play_count" to 5L))
        assertEquals(mapOf("name" to "Song", "year" to 1999L, "track_number" to 7L), clean)
    }

    @Test
    fun jsonRoundTrip() {
        val fields = mapOf("album" to "Blue", "disc_count" to 2L, "comments" to "")
        assertEquals(fields, TagFields.fromJson(TagFields.toJson(fields)))
    }

    @Test
    fun editedTracksInChangesJson() {
        val entry = TagFields.entry(TagEdit(id, mapOf("artist" to "Someone \"quoted\"", "year" to 2001L)))
        val text = ChangesJson.build(emptyList(), "0123456789ABCDEF", emptyList(), null, null, listOf(entry))
        val edited = JSONObject(text).getJSONArray("edited_tracks").getJSONObject(0)
        assertEquals(id, edited.getString("id"))
        assertEquals("Someone \"quoted\"", edited.getString("artist"))
        assertEquals(2001, edited.getInt("year"))
    }

    @Test
    fun remainderListsEditsStillInTheFile() {
        val r = Remainder.parse("""{"version": 2, "tracks": [], "playlists": [], "edited_tracks": [{"id": "abc"}]}""")!!
        assertEquals(setOf("ABC"), r.edited)
    }

    @Test
    fun editsShowOnTheSong() {
        val raw = mapOf<String, Any?>("id" to id, "path" to "LuxTunes Media/Music/A/B/01 X.mp3", "name" to "X",
            "artist" to "A", "album" to "B", "year" to 1990L)
        val t = trackOf(raw + mapOf("album" to "New", "year" to 2000L))!!
        assertEquals("New", t.album)
        assertEquals(2000, t.year)
        assertEquals("A", t.albumArtist)
        assertTrue(t.raw["album"] == "New")
        assertNull(trackOf(mapOf("name" to "no id")))
    }
}
