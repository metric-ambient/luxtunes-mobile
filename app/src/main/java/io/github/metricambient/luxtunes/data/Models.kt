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
    val mediaKind: String = "music",   // music, podcast, audiobook, movie, tvshow
    val raw: Map<String, Any?> = emptyMap(),   // every library.db column, for smart playlist rules
) {
    /** Albums of different kinds (a podcast and an album with the same name) stay apart. */
    val albumKey: String get() = (if (mediaKind == "music") "" else "$mediaKind\u0002") + albumKeyOf(albumArtist, album)

    /** Movies and TV episodes play in the video screen. */
    val isVideo: Boolean get() = mediaKind == "movie" || mediaKind == "tvshow"

    /** What smart playlist rules see: LuxTunes' values with the phone's plays and rating. */
    fun smartValue(column: String): Any? = when (column) {
        "play_count" -> playCount.toLong()
        "play_date" -> playDate
        "skip_count" -> skipCount.toLong()
        "skip_date" -> skipDate
        "rating" -> rating.toLong()
        "rating_computed" -> if (ratingComputed) 1L else 0L
        "media_kind" -> mediaKind
        else -> raw[column]
    }
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
) {
    /** The song whose cover (or embedded art) pictures the album. */
    val artTrack: Track? get() = artTrackOf(tracks)
}

/** The first song with a cover, else the first song (for its embedded art). */
fun artTrackOf(tracks: List<Track>): Track? = tracks.firstOrNull { it.artwork != null } ?: tracks.firstOrNull()

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
    val onPhone: Boolean = false,          // made on the phone, not in LuxTunes yet
    val smartInfo: ByteArray? = null,      // smart playlists: LuxTunes' two rule blocks
    val smartCriteria: ByteArray? = null,
    val changed: Boolean = false,          // changed on the phone, not yet taken by LuxTunes
) {
    /** The rules, if the app can read them. */
    val smart: Smart.Playlist? by lazy {
        if (!isSmart) null else try { Smart.decode(smartInfo, smartCriteria) } catch (e: Exception) { null }
    }
}

/** Text used for sorting: case-insensitive, ignoring a leading "The ". */
fun sortText(text: String): String {
    val lower = text.trim().lowercase()
    return if (lower.startsWith("the ")) lower.substring(4) else lower
}
