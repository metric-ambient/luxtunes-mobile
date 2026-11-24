package io.github.metricambient.luxtunes.data

import java.io.File

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
    val syncedLyrics: String? = null,   // timed lyrics (LRC), from LuxTunes 0.8.5
    val sortName: String,
    val sortArtist: String,
    val sortAlbumArtist: String,
    val sortAlbum: String,
    val startTime: Long,            // ms, 0 = from the start
    val stopTime: Long,             // ms, 0 = to the end
    val mediaKind: String = "music",   // music, podcast, audiobook, movie, tvshow
    val featuredArtist: String? = null,  // names separated by semicolons: "Ann; Bo & Cy"
    val compilation: Boolean = false,    // part of a compilation album
    // Who the song is listed under when browsing, and the artist shown under its album:
    // a compilation's Album Artist (or "Various Artists"), else the Album Artist or Artist.
    val browseArtist: String = albumArtist,
    val browseSortArtist: String = sortAlbumArtist,
    val raw: Map<String, Any?> = emptyMap(),   // every library.db column, for smart playlist rules
) {
    /** Albums of different kinds (a podcast and an album with the same name) stay apart. */
    val albumKey: String get() = (if (mediaKind == "music") "" else "$mediaKind\u0002") + albumKeyOf(browseArtist, album)

    /** The featured artists, one name each. */
    val featured: List<String> get() = featuredNames(featuredArtist)

    /** LuxTunes 0.9.2: each artist of a song by several ("Lümen; Grayfield"); empty when it's just by `artist`. */
    val artists: List<String> by lazy { splitNames(raw["artists"]?.toString()) }

    /** What search looks in, folded (no case or accents): name, artists, album, composer, genre. */
    val searchText: String by lazy {
        fold(listOf(name, raw["artist"], raw["album_artist"], raw["album"], raw["composer"], genre, featuredArtist, raw["artists"])
            .joinToString(" ") { it?.toString().orEmpty() })
    }

    /** The name as shown: "Song (ft. Ann, Bo & Cy, Dee)". Sorting and editing use `name`. */
    val displayName: String get() = nameWithFeatured(name, featuredArtist)

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

/** A song from its library.db columns (null without an id and path). */
fun trackOf(raw: Map<String, Any?>): Track? {
    fun str(col: String): String? = raw[col]?.let { if (it is Double && it == Math.floor(it)) it.toLong().toString() else it.toString() }
    fun num(col: String): Long = when (val v = raw[col]) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: v.trim().toDoubleOrNull()?.toLong() ?: 0L
        else -> 0L
    }
    val id = str("id") ?: return null
    val path = str("path") ?: return null
    val artist = str("artist").orEmpty().ifBlank { "Unknown artist" }
    val albumArtist = str("album_artist").orEmpty().ifBlank { artist }
    val name = str("name").orEmpty().ifBlank { File(path).nameWithoutExtension }
    val album = str("album").orEmpty().ifBlank { "Unknown album" }
    val compilation = num("compilation") != 0L
    val ownAlbumArtist = str("album_artist")?.trim()?.ifEmpty { null }
    val sortAlbumArtist = str("sort_album_artist")?.ifBlank { null }
        ?: str("sort_artist")?.ifBlank { null } ?: albumArtist
    return Track(
        id = id,
        path = path,
        artwork = str("artwork")?.ifBlank { null },
        name = name,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        genre = str("genre").orEmpty(),
        year = num("year").toInt(),
        trackNumber = num("track_number").toInt(),
        discNumber = num("disc_number").toInt(),
        totalTime = num("total_time"),
        rating = num("rating").toInt(),
        ratingComputed = num("rating_computed") != 0L,
        playCount = num("play_count").toInt(),
        playDate = str("play_date"),
        skipCount = num("skip_count").toInt(),
        skipDate = str("skip_date"),
        lyrics = str("lyrics")?.replace("\r\n", "\n")?.replace('\r', '\n')?.trim()?.ifBlank { null },
        syncedLyrics = str("synced_lyrics")?.ifBlank { null },
        sortName = str("sort_name")?.ifBlank { null } ?: name,
        sortArtist = str("sort_artist")?.ifBlank { null } ?: artist,
        sortAlbumArtist = sortAlbumArtist,
        sortAlbum = str("sort_album")?.ifBlank { null } ?: album,
        startTime = num("start_time"),
        stopTime = num("stop_time"),
        mediaKind = str("media_kind")?.ifBlank { null } ?: "music",
        featuredArtist = str("featured_artist")?.ifBlank { null },
        compilation = compilation,
        browseArtist = if (compilation) ownAlbumArtist ?: VARIOUS_ARTISTS else albumArtist,
        browseSortArtist = when {
            !compilation -> sortAlbumArtist
            ownAlbumArtist != null -> str("sort_album_artist")?.ifBlank { null } ?: ownAlbumArtist
            else -> VARIOUS_ARTISTS
        },
        raw = raw,
    )
}

/** Where compilations without an Album Artist are listed (as in LuxTunes). */
const val VARIOUS_ARTISTS = "Various Artists"

/**
 * "Ann; Bo & Cy" -> [Ann, Bo & Cy]: split at semicolons only (from LuxTunes 0.9.2; a comma
 * or "&" can be part of a name, as in "Tyler, the Creator").
 */
fun featuredNames(raw: String?): List<String> = splitNames(raw)

/** "Song (ft. Ann, Bo & Cy, Dee)", or just the name without featured artists. */
fun nameWithFeatured(name: String, featured: String?): String {
    val names = featuredNames(featured)
    return if (names.isEmpty()) name else "$name (ft. ${names.joinToString(", ")})"
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
