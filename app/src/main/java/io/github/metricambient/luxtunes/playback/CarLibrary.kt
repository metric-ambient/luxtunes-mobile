package io.github.metricambient.luxtunes.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.metricambient.luxtunes.data.LibraryState
import io.github.metricambient.luxtunes.data.Radio
import io.github.metricambient.luxtunes.data.Shuffle
import io.github.metricambient.luxtunes.data.Track
import io.github.metricambient.luxtunes.data.fold
import io.github.metricambient.luxtunes.data.matchesWords
import io.github.metricambient.luxtunes.data.searchWords

/**
 * What a car (Android Auto) shows: four tabs (Playlists, Artists, Albums, Radio Mix), each
 * a list to open, ending in songs to play. Built from the library the phone already has.
 *
 * Every list and song has an ID. Lists: "playlist:<id>", "artist:<name>", "album:<key>",
 * "radio", "search:<words>". A song in a list: "<list>␟<position>␟<song ID>", so playing it
 * plays that list from there. "radio:start" starts a Radio Mix from the song playing, and
 * "artist:<name>␟shuffle" shuffles an artist. (␟ is the unit separator, \u001F, which no
 * name contains.)
 */
object CarLibrary {
    const val ROOT = "root"
    private const val PLAYLISTS = "tab:playlists"
    private const val ARTISTS = "tab:artists"
    private const val ALBUMS = "tab:albums"
    private const val RADIO_TAB = "tab:radio"
    const val RADIO_START = "radio:start"
    private const val SEP = '\u001F'

    /** The car apps (and the phone's own) that may see the library: nothing else can list it. */
    val ALLOWED_BROWSERS = setOf(
        "com.google.android.projection.gearhead",       // Android Auto
        "com.google.android.carassistant",              // Assistant while driving
        "com.google.android.googlequicksearchbox",      // Assistant
        "com.android.car.media",                        // cars with Android built in
        "com.android.systemui",                         // the system's media controls
    )

    fun root(): MediaItem = folder(ROOT, "LuxTunes", null, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    /** The items of a list (empty if it's unknown). */
    fun children(id: String, library: LibraryState, radio: Radio?): List<MediaItem> = when {
        id == ROOT -> listOf(
            folder(PLAYLISTS, "Playlists", null, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            folder(ARTISTS, "Artists", null, MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
            folder(ALBUMS, "Albums", null, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            folder(RADIO_TAB, "Radio Mix", null, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        )
        id == PLAYLISTS -> library.playlists.mapNotNull { p ->
            val count = p.trackIds.count { it in library.byId }
            if (count == 0) null else folder("playlist:${p.id}", p.name, plural(count, "song"), MediaMetadata.MEDIA_TYPE_PLAYLIST)
        }
        id == ARTISTS -> library.artists.map { a ->
            folder("artist:${a.name}", a.name, plural(a.albums.size, "album"), MediaMetadata.MEDIA_TYPE_ARTIST)
        }
        id == ALBUMS -> library.albums.map { a ->
            folder("album:${a.key}", a.name, a.artist, MediaMetadata.MEDIA_TYPE_ALBUM)
        }
        id == RADIO_TAB -> {
            val start = playable(RADIO_START, "Start Radio Mix", "From the song playing")
            listOf(start) + songs("radio", songsOf("radio", library, radio))
        }
        id.startsWith("artist:") -> library.group("artist", id.removePrefix("artist:"))?.let { artist ->
            listOf(playable("$id${SEP}shuffle", "Shuffle ${artist.name}", plural(artist.tracks.size, "song"))) +
                artist.albums.map { a -> folder("album:${a.key}", a.name, if (a.year > 0) "${a.year}" else null, MediaMetadata.MEDIA_TYPE_ALBUM) }
        }.orEmpty()
        else -> songs(id, songsOf(id, library, radio))
    }

    /** The songs of a list that plays: a playlist, an album, the Radio Mix or a search. */
    fun songsOf(list: String, library: LibraryState, radio: Radio?): List<Track> = when {
        list == "radio" -> radio?.trackIds.orEmpty().mapNotNull { library.byId[it] }
        list.startsWith("playlist:") ->
            library.playlists.firstOrNull { it.id == list.removePrefix("playlist:") }?.trackIds.orEmpty().mapNotNull { library.byId[it] }
        list.startsWith("album:") -> library.albumByKey[list.removePrefix("album:")]?.tracks.orEmpty()
        list.startsWith("artist:") -> library.group("artist", list.removePrefix("artist:"))?.tracks.orEmpty()
        list.startsWith("search:") -> search(list.removePrefix("search:"), library)
        else -> emptyList()
    }

    /** Songs whose details have every word searched for (case and accents ignored). */
    fun search(query: String, library: LibraryState): List<Track> {
        val words = searchWords(query)
        if (words.isEmpty()) return emptyList()
        return library.tracks.filter { matchesWords(it.searchText, words) }
    }

    /**
     * What a car asked to play, as songs and where to start (null: not one of the car's
     * IDs). A song in a list plays the list from that song.
     */
    fun resolve(id: String, library: LibraryState, radio: Radio?): Pair<List<Track>, Int>? {
        val parts = id.split(SEP)
        if (parts.size == 2 && parts[1] == "shuffle" && parts[0].startsWith("artist:")) {
            val songs = songsOf(parts[0], library, radio)
            return Shuffle.order(songs, library.allTracks).ifEmpty { songs } to 0
        }
        if (parts.size != 3) return null
        val songs = songsOf(parts[0], library, radio)
        val at = parts[1].toIntOrNull() ?: return null
        // The list may have changed since the car showed it: find the song again.
        val index = if (songs.getOrNull(at)?.id == parts[2]) at else songs.indexOfFirst { it.id == parts[2] }
        return if (index < 0) null else songs to index
    }

    /** A spoken or typed "play …": an artist (shuffled), an album, else the songs that match; nothing said: everything shuffled. */
    fun playFromSearch(query: String, library: LibraryState): List<Track> {
        val q = fold(query.trim())
        if (q.isEmpty()) return Shuffle.order(library.tracks, library.allTracks)
        library.artists.firstOrNull { fold(it.name) == q }?.let { return Shuffle.order(it.tracks, library.allTracks) }
        library.albums.firstOrNull { fold(it.name) == q }?.let { return it.tracks }
        return search(query, library)
    }

    /** The song ID inside a car's ID ("album:…␟3␟<song>" -> "<song>"), or the ID itself. */
    fun songId(id: String): String = id.substringAfterLast(SEP)

    /** One item by ID, for a car that asks for it alone. */
    fun item(id: String, library: LibraryState): MediaItem? {
        if (id == ROOT) return root()
        library.byId[songId(id)]?.let { return song(id, it) }
        return null
    }

    // ---- Building items ----------------------------------------------------------------

    private fun songs(list: String, tracks: List<Track>): List<MediaItem> =
        tracks.mapIndexed { i, t -> song("$list$SEP$i$SEP${t.id}", t) }

    private fun song(id: String, t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.displayName)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()

    private fun playable(id: String, title: String, subtitle: String?): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()

    private fun folder(id: String, title: String, subtitle: String?, type: Int): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(type)
                .build()
        )
        .build()

    private fun plural(n: Int, one: String) = "$n ${if (n == 1) one else one + "s"}"
}
