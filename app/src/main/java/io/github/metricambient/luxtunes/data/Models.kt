package io.github.metricambient.luxtunes.data

/** One song, as the app shows it: LuxTunes' details plus what happened on the phone since the last sync. */
data class Track(
    val id: String,                 // LuxTunes' persistent ID (16 hex digits)
    val path: String,               // relative to the LuxTunes folder
    val artwork: String?,           // relative to the LuxTunes folder
    val name: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val genre: String,
    val year: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val totalTime: Long,            // ms
    val rating: Int,                // 0-100, 20 per star
    val ratingComputed: Boolean,    // the album's rating, not set for this song
    val playCount: Int,
    val playDate: String?,          // UTC "YYYY-MM-DDTHH:MM:SSZ"
    val skipCount: Int,
    val skipDate: String?,
    val lyrics: String?,
    val sortName: String,
    val sortArtist: String,
    val sortAlbumArtist: String,
    val sortAlbum: String,
    val startTime: Long,            // ms, 0 = from the start
    val stopTime: Long,             // ms, 0 = to the end
) {
    val albumKey: String get() = albumKeyOf(albumArtist, album)
}

fun albumKeyOf(albumArtist: String, album: String): String =
    albumArtist.lowercase() + "\u0001" + album.lowercase()

data class Album(
    val key: String,
    val name: String,
    val artist: String,
    val year: Int,
    val artwork: String?,
    val tracks: List<Track>,        // disc, then track number
)

data class Artist(
    val name: String,
    val albums: List<Album>,        // oldest first
) {
    val tracks: List<Track> get() = albums.flatMap { it.tracks }
}

data class Playlist(
    val id: String,
    val name: String,
    val isSmart: Boolean,
    val trackIds: List<String>,
    val onPhone: Boolean = false,   // made on the phone (LuxTunes' own ones are read-only for now)
)

/** Text used for sorting: case-insensitive, ignoring a leading "The ". */
fun sortText(text: String): String {
    val lower = text.trim().lowercase()
    return if (lower.startsWith("the ")) lower.substring(4) else lower
}
