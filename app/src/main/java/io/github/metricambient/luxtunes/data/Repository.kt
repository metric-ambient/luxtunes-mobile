package io.github.metricambient.luxtunes.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Everything the screens show. */
data class LibraryState(
    val loaded: Boolean = false,
    val folder: File? = null,
    val libraryName: String = "",
    val syncedAt: String? = null,
    val tracks: List<Track> = emptyList(),          // name order
    val albums: List<Album> = emptyList(),          // name order
    val artists: List<Artist> = emptyList(),        // name order
    val playlists: List<Playlist> = emptyList(),    // LuxTunes' order
    val pendingCount: Int = 0,                      // songs with changes not yet sent
    val message: String? = null,                    // shown when there's nothing to play
) {
    val byId: Map<String, Track> by lazy { tracks.associateBy { it.id } }
    val albumByKey: Map<String, Album> by lazy { albums.associateBy { it.key } }
    val artistByName: Map<String, Artist> by lazy { artists.associateBy { it.name } }
}

/**
 * The library and the two-way sync with LuxTunes.
 *
 *  - In: whenever LuxTunes writes a new library.db, it's copied into the app's
 *    database (song details, ratings, play counts, playlists).
 *  - Out: plays, skips and ratings made on the phone are kept in the app's
 *    database and mirrored into changes.json. When LuxTunes has emptied that
 *    file, what it held has reached the computer and is taken off.
 *  - While the phone is in USB file-transfer mode, changes.json isn't touched
 *    (LuxTunes may be reading it); everything is caught up afterwards.
 */
class Repository(private val context: Context) {
    private val db = AppDatabase(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** LuxTunes' own values; pending changes are added on top for showing. */
    private var libraryTracks: List<Track> = emptyList()
    private var pending: MutableMap<String, PendingEntry> = mutableMapOf()

    /** Set by UsbState: true while the phone is in USB file-transfer mode. */
    @Volatile var transferMode: Boolean = false
        set(value) {
            val ended = field && !value
            field = value
            if (ended) refreshAsync()
        }

    fun refreshAsync() {
        scope.launch { refresh() }
    }

    /** Find the folder, take in a new library.db and settle changes.json. */
    suspend fun refresh() = lock.withLock {
        try {
            if (!LuxFolder.hasAccess(context)) {
                publish(null, "LuxTunes Mobile needs “All files access” to read your music.")
                return@withLock
            }
            ensureLoaded()
            val folder = LuxFolder.find(context)
            if (folder == null) {
                publish(null, "No music from LuxTunes yet. Sync this phone from LuxTunes on your computer, then open the app again.")
                return@withLock
            }
            if (!transferMode) settleChangesFile(folder)
            importIfNew(folder)
            if (!transferMode) writeChangesFile(folder)
            publish(folder, null)
        } catch (e: Exception) {
            Log.e(TAG, "refresh failed", e)
            publish(_state.value.folder, "Couldn't read the LuxTunes folder: ${e.message}")
        }
    }

    // ---- Recording what happens on the phone --------------------------------------

    fun recordPlay(id: String) = change(id) { it.played(System.currentTimeMillis()) }
    fun recordSkip(id: String) = change(id) { it.skipped(System.currentTimeMillis()) }
    fun setRating(id: String, rating: Int) = change(id) { it.rated(rating) }

    private fun change(id: String, how: (PendingEntry) -> PendingEntry) {
        scope.launch {
            lock.withLock {
                ensureLoaded()
                val updated = how(pending[id] ?: PendingEntry(id))
                pending[id] = updated
                db.savePending(listOf(updated))
                val folder = _state.value.folder
                try {
                    if (folder != null && !transferMode) writeChangesFile(folder)
                } catch (e: Exception) {
                    // Kept in the app's database; written at the next chance.
                    Log.e(TAG, "couldn't write changes.json", e)
                }
                publish(folder, _state.value.message)
            }
        }
    }

    // ---- In: library.db ---------------------------------------------------------------

    private var loadedFromDb = false

    /** The first time: what the app had from earlier runs. */
    private fun ensureLoaded() {
        if (loadedFromDb) return
        libraryTracks = db.loadTracks()
        pending = db.loadPending().toMutableMap()
        loadedFromDb = true
    }

    private fun importIfNew(folder: File) {
        val source = File(folder, LuxFolder.DATABASE)
        val stamp = "${source.lastModified()}:${source.length()}"
        if (stamp == db.meta("imported_stamp") && libraryTracks.isNotEmpty()) return
        // Copy first: LuxTunes may replace the file at any moment, and SQLite
        // shouldn't read a file on shared storage directly.
        val copy = File(context.cacheDir, "library-import.db")
        source.inputStream().use { input -> copy.outputStream().use { input.copyTo(it) } }
        try {
            val info = db.importLibrary(copy)
            val newId = info["library_id"]
            val oldId = db.meta("library_id")
            if (oldId != null && newId != null && oldId != newId) {
                // Synced from a different computer library: plays recorded for the
                // old one must not be sent to this one.
                Log.i(TAG, "library changed ($oldId -> $newId): dropping pending changes")
                db.clearPending()
                pending.clear()
            }
            db.setMeta("library_id", newId)
            db.setMeta("library_name", info["library_name"])
            db.setMeta("synced_at", info["synced_at"])
            db.setMeta("imported_stamp", stamp)
            libraryTracks = db.loadTracks()
            Log.i(TAG, "imported library.db: ${libraryTracks.size} songs, synced ${info["synced_at"]}")
        } finally {
            copy.delete()
        }
    }

    // ---- Out: changes.json --------------------------------------------------------------

    /** If LuxTunes emptied changes.json, what the file held has reached the computer. */
    private fun settleChangesFile(folder: File) {
        val file = File(folder, LuxFolder.CHANGES)
        if (!file.isFile) return
        val text = try { file.readText() } catch (e: Exception) { return }
        if (!ChangesJson.isEmptied(text)) return
        val sent = pending.values.filter { it.sentPlays > 0 || it.sentSkips > 0 || it.sentRating != null }
        if (sent.isEmpty()) return
        val after = sent.map { it.delivered() }
        db.savePending(after)
        for (e in after) if (e.isEmpty) pending.remove(e.id) else pending[e.id] = e
        Log.i(TAG, "LuxTunes took the changes for ${sent.size} songs")
    }

    /** Mirror the pending changes into changes.json (only when there's something new to say). */
    private fun writeChangesFile(folder: File) {
        settleChangesFile(folder)          // never overwrite an emptied file unread
        val entries = pending.values.filter { !it.isEmpty }
        val file = File(folder, LuxFolder.CHANGES)
        val text = ChangesJson.build(entries, db.meta("library_id"))
        val current = if (file.isFile) runCatching { file.readText() }.getOrNull() else null
        if (entries.isEmpty() && (current == null || ChangesJson.isEmptied(current))) return
        if (text != current) {
            val temp = File(folder, ".changes.json.tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) {
                    temp.delete()
                    throw java.io.IOException("can't write ${file.path}")
                }
            }
            // So the computer sees the new file over USB right away.
            MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)
        }
        // The file now holds everything recorded so far.
        val marked = pending.values.filter { it.sentPlays != it.plays || it.sentSkips != it.skips || it.sentRating != it.rating }
            .map { it.markSent() }
        if (marked.isNotEmpty()) {
            db.savePending(marked)
            for (e in marked) pending[e.id] = e
        }
    }

    // ---- Building what the screens show ------------------------------------------------

    private fun publish(folder: File?, message: String?) {
        val tracks = libraryTracks.map { withPending(it) }
            .sortedWith(compareBy({ sortText(it.sortName) }, { sortText(it.sortArtist) }))
        val albums = tracks.groupBy { it.albumKey }.map { (key, songs) ->
            val ordered = songs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { sortText(it.sortName) }))
            val first = ordered.first()
            Album(
                key = key,
                name = first.album,
                artist = first.albumArtist,
                year = ordered.maxOf { it.year },
                artwork = ordered.firstNotNullOfOrNull { it.artwork },
                tracks = ordered,
            )
        }.sortedWith(compareBy({ sortText(it.tracks.first().sortAlbum) }, { sortText(it.artist) }))
        val artists = albums.groupBy { it.artist.lowercase() }.map { (_, list) ->
            Artist(
                name = list.first().artist,
                albums = list.sortedWith(compareBy({ if (it.year == 0) Int.MAX_VALUE else it.year }, { sortText(it.name) })),
            )
        }.sortedBy { sortText(it.tracks.first().sortAlbumArtist) }
        _state.value = LibraryState(
            loaded = true,
            folder = folder,
            libraryName = db.meta("library_name").orEmpty(),
            syncedAt = db.meta("synced_at"),
            tracks = tracks,
            albums = albums,
            artists = artists,
            playlists = db.loadPlaylists(),
            pendingCount = pending.values.count { !it.isEmpty },
            message = if (tracks.isEmpty()) message ?: "No songs yet." else message,
        )
    }

    private fun withPending(t: Track): Track {
        val p = pending[t.id] ?: return t
        if (p.isEmpty) return t
        return t.copy(
            playCount = t.playCount + p.plays,
            playDate = laterOf(t.playDate, p.lastPlayed),
            skipCount = t.skipCount + p.skips,
            skipDate = laterOf(t.skipDate, p.lastSkipped),
            rating = p.rating ?: t.rating,
            ratingComputed = if (p.rating != null) false else t.ratingComputed,
        )
    }

    private fun laterOf(date: String?, unixMs: Long): String? {
        if (unixMs <= 0) return date
        val ours = ChangesJson.isoTime(unixMs)
        return if (date == null || ours > date) ours else date
    }

    // ---- For the player ------------------------------------------------------------------

    fun track(id: String): Track? = _state.value.byId[id]

    /** A playable item for a song (the player service asks for these by ID). */
    fun mediaItem(id: String): MediaItem? {
        val t = track(id) ?: return null
        val folder = _state.value.folder ?: return null
        val builder = MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(Uri.fromFile(File(folder, t.path)))
            .setMediaMetadata(metadata(t, folder))
        if (t.startTime > 0 || t.stopTime > 0) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(t.startTime.coerceAtLeast(0))
                    .setEndPositionMs(if (t.stopTime > t.startTime) t.stopTime else C.TIME_END_OF_SOURCE)
                    .build()
            )
        }
        return builder.build()
    }

    /** What the screens send to the player: the song's ID plus what to show. */
    fun requestItem(t: Track): MediaItem =
        MediaItem.Builder().setMediaId(t.id).setMediaMetadata(metadata(t, _state.value.folder)).build()

    private fun metadata(t: Track, folder: File?): MediaMetadata {
        val b = MediaMetadata.Builder()
            .setTitle(t.name)
            .setArtist(t.artist)
            .setAlbumTitle(t.album)
            .setAlbumArtist(t.albumArtist)
        if (folder != null && t.artwork != null) b.setArtworkUri(Uri.fromFile(File(folder, t.artwork)))
        return b.build()
    }

    fun artworkFile(path: String?): File? {
        val folder = _state.value.folder ?: return null
        return if (path == null) null else File(folder, path)
    }

    companion object {
        private const val TAG = "LuxTunes"
    }
}
