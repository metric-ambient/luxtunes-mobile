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
import io.github.metricambient.luxtunes.ui.Art
import java.io.File

/** Everything the screens show. */
data class LibraryState(
    val loaded: Boolean = false,
    val folder: File? = null,
    val libraryName: String = "",
    val syncedAt: String? = null,
    val tracks: List<Track> = emptyList(),          // music, name order
    val albums: List<Album> = emptyList(),          // name order
    val artists: List<Artist> = emptyList(),        // album artists, name order
    val trackArtists: List<Artist> = emptyList(),   // song artists ("Artists" tab)
    val genres: List<Artist> = emptyList(),         // a genre and its albums
    val composers: List<Artist> = emptyList(),
    val movies: List<Track> = emptyList(),          // name order
    val tvShows: List<Artist> = emptyList(),        // a show; its "albums" are its seasons
    val podcasts: List<Album> = emptyList(),        // one per show
    val audiobooks: List<Album> = emptyList(),      // one per book
    val allTracks: List<Track> = emptyList(),       // every kind
    val playlists: List<Playlist> = emptyList(),    // LuxTunes' order, then the phone's
    val references: Map<String, String> = emptyMap(),   // playlists rules point to (id -> name), not shown
    val formatVersion: Int = 1,                     // library.db format; 2+ = playlists go back to LuxTunes
    val pendingCount: Int = 0,                      // songs with changes not yet sent
    val pendingPlaylists: Int = 0,                  // playlist changes not yet taken by LuxTunes
    val changesWritten: Long = 0,                   // when changes.json was last written (Unix ms)
    val changesError: String? = null,               // why it couldn't be written, if it couldn't
    val transferMode: Boolean = false,              // phone is in USB file-transfer mode
    val message: String? = null,                    // shown when there's nothing to play
) {
    val byId: Map<String, Track> by lazy { allTracks.associateBy { it.id } }
    val albumByKey: Map<String, Album> by lazy {
        (albums + podcasts + audiobooks + tvShows.flatMap { it.albums }).associateBy { it.key }
    }

    /** LuxTunes 0.6+ takes playlist changes back; before that only playlists made here can change. */
    fun canEdit(p: Playlist): Boolean = p.onPhone || formatVersion >= 2
    val artistByName: Map<String, Artist> by lazy { artists.associateBy { it.name } }

    /** A named group of songs for a group page: kind is artist, trackartist, genre, composer or tvshow. */
    fun group(kind: String, name: String): Artist? = when (kind) {
        "trackartist" -> trackArtists
        "genre" -> genres
        "composer" -> composers
        "tvshow" -> tvShows
        else -> artists
    }.firstOrNull { it.name == name }
}

/**
 * The library and the two-way sync with LuxTunes.
 *
 *  - In: whenever LuxTunes writes a new library.db, it's copied into the app's
 *    database (song details, ratings, play counts, playlists).
 *  - Out: plays, skips and ratings made on the phone are kept in the app's
 *    database and mirrored into changes.json. When LuxTunes has emptied that
 *    file, what it held has reached the computer and is taken off.
 *  - changes.json is written right away, also while the phone is plugged in,
 *    and once more when USB file transfer starts, so it's fresh when LuxTunes
 *    reads it. LuxTunes 0.6+ keeps whatever the phone added while it was
 *    reading (see settleChangesFile), so nothing is lost or counted twice.
 *  - Playlist changes (format v2) travel the same way, as PlaylistEdits.
 */
class Repository(private val context: Context, private val prefs: Prefs) {
    private val db = AppDatabase(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** LuxTunes' own values; pending changes are added on top for showing. */
    private var libraryTracks: List<Track> = emptyList()
    private var pending: MutableMap<String, PendingEntry> = mutableMapOf()
    private var edits: MutableMap<String, PlaylistEdit> = mutableMapOf()

    /** Set by UsbState: true while the phone is in USB file-transfer mode. */
    @Volatile var transferMode: Boolean = false
        set(value) {
            val changed = field != value
            field = value
            if (changed) refreshAsync()       // starting: freshen changes.json; ending: pick up the sync
        }

    private var changesWritten = 0L
    private var changesError: String? = null

    fun refreshAsync() {
        scope.launch { refresh() }
    }

    /** Find the folder, take in a new library.db and settle changes.json. */
    suspend fun refresh() = lock.withLock {
        try {
            if (!LuxFolder.hasAccess(context)) {
                publish(null, "LuxTunes needs “All files access” to read your music.")
                return@withLock
            }
            ensureLoaded()
            val folder = LuxFolder.find(context, prefs.current.storage)
            if (folder == null) {
                val where = when (prefs.current.storage) {
                    StorageChoice.REMOVABLE -> " on the SD card"
                    StorageChoice.INTERNAL -> " in internal storage"
                    null -> ""
                }
                publish(null, "No music from LuxTunes$where yet. Sync this phone from LuxTunes on your computer " +
                    "(or change the storage in Settings → My Library), then open the app again.")
                return@withLock
            }
            settleChangesFile(folder)
            importIfNew(folder)
            writeChangesFileSafely(folder)
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
                if (folder != null) writeChangesFileSafely(folder)
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
        edits = db.loadEdits().associateBy { it.id }.toMutableMap()
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
                db.clearEdits()
                edits.clear()
            }
            // What LuxTunes took is in this library.db now.
            db.dropDeliveredEdits()
            edits = db.loadEdits().associateBy { it.id }.toMutableMap()
            db.setMeta("format_version", info["format_version"])
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

    /**
     * Has LuxTunes been at changes.json since the app last wrote it? LuxTunes reads
     * the file at the start of a sync and rewrites it straight away: empty, or with
     * only what the phone added while it was reading (the "remainder"). Then what
     * the app last wrote has arrived, minus that remainder.
     */
    private fun settleChangesFile(folder: File) {
        val file = File(folder, LuxFolder.CHANGES)
        if (!file.isFile) return
        val text = try { file.readText() } catch (e: Exception) { return }
        val written = db.meta("changes_text")
        val consumed = if (written != null) text != written else ChangesJson.isEmptied(text)
        if (!consumed) return
        val remainder = Remainder.parse(text)
        if (remainder == null) {
            Log.w(TAG, "changes.json changed but couldn't be read; leaving it")
            return
        }
        val sent = pending.values.filter { it.sentPlays > 0 || it.sentSkips > 0 || it.sentRating != null }
        val after = sent.map { e ->
            val kept = remainder.tracks[e.id.uppercase()]
            e.delivered(kept?.plays ?: 0, kept?.skips ?: 0, kept?.rated ?: false)
        }
        db.savePending(after)
        for (e in after) if (e.isEmpty) pending.remove(e.id) else pending[e.id] = e
        val arrived = edits.values.filter { it.state == PlaylistEdit.WRITTEN && it.id.uppercase() !in remainder.playlists }
        db.setEditStates(arrived.map { it.id }, PlaylistEdit.DELIVERED)
        for (e in arrived) edits[e.id] = e.copy(state = PlaylistEdit.DELIVERED)
        db.setMeta("changes_text", text)
        Log.i(TAG, "LuxTunes took the changes for ${sent.size} songs and ${arrived.size} playlists")
    }

    /** Write changes.json; on failure the changes stay in the app's database for next time. */
    private fun writeChangesFileSafely(folder: File) {
        try {
            writeChangesFile(folder)
            changesError = null
        } catch (e: Exception) {
            Log.e(TAG, "couldn't write changes.json", e)
            changesError = e.message ?: e.javaClass.simpleName
        }
    }

    /** Mirror the pending changes into changes.json (only when there's something new to say). */
    private fun writeChangesFile(folder: File) {
        settleChangesFile(folder)          // never overwrite an emptied file unread
        val entries = pending.values.filter { !it.isEmpty }
        val file = File(folder, LuxFolder.CHANGES)
        // Playlists only go to a LuxTunes that understands them (format 2), else they wait.
        val v2 = (db.meta("format_version")?.toIntOrNull() ?: 1) >= 2
        val waiting = if (v2) edits.values.filter { it.state != PlaylistEdit.DELIVERED }.sortedBy { it.created } else emptyList()
        val text = ChangesJson.build(entries, db.meta("library_id"), if (v2) waiting.map { ChangesJson.playlistEntry(it) } else null)
        val current = if (file.isFile) runCatching { file.readText() }.getOrNull() else null
        if (entries.isEmpty() && waiting.isEmpty() && (current == null || ChangesJson.isEmptied(current))) return
        if (text != current) {
            // Written in place (not via a renamed temp file), so the copy the
            // computer sees over USB stays the same file, just updated.
            file.writeText(text)
            if (file.readText() != text) throw java.io.IOException("changes.json didn't save correctly")
            db.setMeta("changes_text", text)
            changesWritten = System.currentTimeMillis()
            Log.i(TAG, "wrote changes.json: ${entries.size} songs")
            // Tell Android's media index, so the computer sees the new size and contents.
            MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)
        }
        // The file now holds everything recorded so far.
        val marked = pending.values.filter { it.sentPlays != it.plays || it.sentSkips != it.skips || it.sentRating != it.rating }
            .map { it.markSent() }
        if (marked.isNotEmpty()) {
            db.savePending(marked)
            for (e in marked) pending[e.id] = e
        }
        val newEdits = waiting.filter { it.state == PlaylistEdit.NEW }
        if (newEdits.isNotEmpty()) {
            db.setEditStates(newEdits.map { it.id }, PlaylistEdit.WRITTEN)
            for (e in newEdits) edits[e.id] = e.copy(state = PlaylistEdit.WRITTEN)
        }
    }

    // ---- Building what the screens show ------------------------------------------------

    private fun publish(folder: File?, message: String?) {
        val all = libraryTracks.map { withPending(it) }
            .sortedWith(compareBy({ sortText(it.sortName) }, { sortText(it.sortArtist) }))
        val music = all.filter { it.mediaKind == "music" }
        val albums = albumsOf(music).sortedWith(compareBy({ sortText(it.tracks.first().sortAlbum) }, { sortText(it.artist) }))
        val artists = albums.groupBy { it.artist.lowercase() }.map { (_, list) ->
            Artist(
                name = list.first().artist,
                albums = list.sortedWith(compareBy({ if (it.year == 0) Int.MAX_VALUE else it.year }, { sortText(it.name) })),
            )
        }.sortedBy { sortText(it.tracks.first().sortAlbumArtist) }
        val podcasts = albumsOf(all.filter { it.mediaKind == "podcast" }).sortedBy { sortText(it.name) }
        // Artists by the songs' own artist, genres and composers: each a group of (parts of) albums.
        fun groups(key: (Track) -> String?): List<Artist> =
            music.mapNotNull { t -> key(t)?.trim()?.ifEmpty { null }?.let { it to t } }
                .groupBy({ it.first.lowercase() }, { it })
                .map { (_, pairs) ->
                    Artist(
                        name = pairs.first().first,
                        albums = albumsOf(pairs.map { it.second })
                            .sortedWith(compareBy({ if (it.year == 0) Int.MAX_VALUE else it.year }, { sortText(it.name) })),
                    )
                }
                .sortedBy { sortText(it.name) }
        val trackArtists = groups { it.artist }
        val genres = groups { it.genre }
        val composers = groups { it.raw["composer"] as? String }
        val movies = all.filter { it.mediaKind == "movie" }
        val tvShows = all.filter { it.mediaKind == "tvshow" }
            .groupBy { ((it.raw["show"] as? String)?.ifBlank { null } ?: it.album).lowercase() }
            .map { (_, episodes) ->
                val show = (episodes.first().raw["show"] as? String)?.ifBlank { null } ?: episodes.first().album
                val seasons = episodes.groupBy { (it.raw["season"] as? Long) ?: 0L }.toSortedMap().map { (season, list) ->
                    val ordered = list.sortedWith(compareBy({ (it.raw["episode_number"] as? Long) ?: it.trackNumber.toLong() }, { sortText(it.name) }))
                    Album(
                        key = "tvshow\u0003${show.lowercase()}\u0003$season",
                        name = if (season > 0) "Season $season" else show,
                        artist = show,
                        year = ordered.maxOf { it.year },
                        artwork = ordered.firstNotNullOfOrNull { it.artwork },
                        tracks = ordered,
                    )
                }
                Artist(show, seasons)
            }
            .sortedBy { sortText(it.name) }
        val audiobooks = albumsOf(all.filter { it.mediaKind == "audiobook" }).sortedBy { sortText(it.name) }
        val references = db.loadReferences()
        _state.value = LibraryState(
            loaded = true,
            folder = folder,
            libraryName = db.meta("library_name").orEmpty(),
            syncedAt = db.meta("synced_at"),
            tracks = music,
            albums = albums,
            artists = artists,
            trackArtists = trackArtists,
            genres = genres,
            composers = composers,
            movies = movies,
            tvShows = tvShows,
            podcasts = podcasts,
            audiobooks = audiobooks,
            allTracks = all,
            playlists = playlistsShown(all, references),
            references = references.mapValues { it.value.first },
            formatVersion = db.meta("format_version")?.toIntOrNull() ?: 1,
            pendingCount = pending.values.count { !it.isEmpty },
            pendingPlaylists = edits.values.count { it.state != PlaylistEdit.DELIVERED },
            changesWritten = changesWritten,
            changesError = changesError,
            transferMode = transferMode,
            message = if (all.isEmpty()) message ?: "No songs yet." else message,
        )
    }

    private fun albumsOf(tracks: List<Track>): List<Album> = tracks.groupBy { it.albumKey }.map { (key, songs) ->
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
    }

    /**
     * LuxTunes' playlists with the phone's changes laid over them, then the ones
     * made here; live smart playlists re-evaluated against the songs on the phone.
     */
    private fun playlistsShown(all: List<Track>, references: Map<String, Pair<String, Set<String>>>): List<Playlist> {
        val base = db.loadPlaylists()
        val baseIds = base.map { it.id.uppercase() }.toSet()
        val shown = ArrayList<Playlist>()
        for (p in base) {
            val e = edits[p.id]
            when {
                e == null -> shown += p
                e.action == "delete" -> {}
                else -> shown += applyEdit(p, e)
            }
        }
        for (e in edits.values.sortedBy { it.created }) {
            if (e.action != "delete" && e.id.uppercase() !in baseIds) {
                shown += applyEdit(Playlist(e.id, e.name, e.isSmart, emptyList(), onPhone = true), e)
            }
        }
        // Live smart playlists: the rules decide, on the songs that are here.
        val songs = all.map { t -> object : Smart.Song {
            override val id = t.id
            override fun value(column: String) = t.smartValue(column)
        } }
        val byId = shown.associateBy { it.id.uppercase() }
        val now = System.currentTimeMillis() / 1000
        val cache = mutableMapOf<String, Set<String>>()
        fun members(id: String, stack: Set<String>): Set<String> {
            cache[id]?.let { return it }
            if (id in stack) return emptySet()                       // a loop of playlists
            val p = byId[id]
            val result: Set<String> = when {
                p == null -> references[id]?.second ?: emptySet()
                else -> evaluated(p, songs, now, stack + id) { i, st -> members(i, st) }.toSet()
            }
            cache[id] = result
            return result
        }
        return shown.map { p ->
            val rules = p.smart
            if (rules != null && rules.understood && rules.info.live) {
                p.copy(trackIds = evaluated(p, songs, now, setOf(p.id.uppercase())) { id, stack -> members(id, stack) })
            } else p
        }
    }

    private fun evaluated(
        p: Playlist, songs: List<Smart.Song>, now: Long, stack: Set<String>,
        members: ((String, Set<String>) -> Set<String>)? = null,
    ): List<String> {
        val rules = p.smart ?: return p.trackIds
        if (!rules.understood || !rules.info.live || members == null) return p.trackIds
        val wanted = Smart.evaluate(rules, songs, now, p.id.hashCode().toLong()) { id -> members(id, stack) }
        return Smart.merge(p.trackIds, wanted)
    }

    private fun applyEdit(p: Playlist, e: PlaylistEdit): Playlist = p.copy(
        name = e.name.ifEmpty { p.name },
        trackIds = if (e.isSmart) p.trackIds else e.items,
        smartInfo = if (e.isSmart) e.smartInfo else p.smartInfo,
        smartCriteria = if (e.isSmart) e.smartCriteria else p.smartCriteria,
        isSmart = e.isSmart,
        changed = e.state != PlaylistEdit.DELIVERED,
    )

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

    // ---- Changing playlists on the phone ------------------------------------------------
    //
    // Every change becomes a PlaylistEdit: shown straight away, written to
    // changes.json (format v2) and taken in by LuxTunes at the next sync. LuxTunes'
    // own playlists can only be changed when its library.db is format 2 or newer.

    private fun newId() = (1..16).map { "0123456789ABCDEF".random() }.joinToString("")

    /** A new plain playlist; `done` gets its ID (16 hex digits, the format LuxTunes uses). */
    fun createPlaylist(name: String, trackIds: List<String>, done: (String) -> Unit = {}) {
        val id = newId()
        editPlaylist(Playlist(id, name.trim().ifEmpty { "untitled playlist" }, false, trackIds, onPhone = true))
        done(id)
    }

    fun createSmartPlaylist(name: String, rules: Smart.Playlist, done: (String) -> Unit = {}) {
        val id = newId()
        val (info, criteria) = Smart.encode(rules)
        editPlaylist(Playlist(id, name.trim().ifEmpty { "untitled playlist" }, true, emptyList(),
            onPhone = true, smartInfo = info, smartCriteria = criteria))
        done(id)
    }

    fun setSmartRules(playlistId: String, name: String, rules: Smart.Playlist) {
        val p = editable(playlistId) ?: return
        val (info, criteria) = Smart.encode(rules)
        editPlaylist(p.copy(name = name.trim().ifEmpty { p.name }, smartInfo = info, smartCriteria = criteria))
    }

    fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        val p = editable(playlistId)?.takeIf { !it.isSmart } ?: return
        editPlaylist(p.copy(trackIds = p.trackIds + trackIds))
    }

    /** How many of these songs are already in the playlist (for the "duplicates" question). */
    fun duplicatesIn(playlistId: String, trackIds: List<String>): Int {
        val have = _state.value.playlists.firstOrNull { it.id == playlistId }?.trackIds?.toSet() ?: return 0
        return trackIds.count { it in have }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val p = editable(playlistId) ?: return
        editPlaylist(p.copy(name = name.trim().ifEmpty { p.name }))
    }

    /** Remove the song at this position (positions, since a song can be in a list twice). */
    fun removeFromPlaylist(playlistId: String, position: Int) {
        val p = editable(playlistId)?.takeIf { !it.isSmart } ?: return
        if (position !in p.trackIds.indices) return
        editPlaylist(p.copy(trackIds = p.trackIds.filterIndexed { i, _ -> i != position }))
    }

    fun deletePlaylist(playlistId: String) {
        val p = editable(playlistId) ?: return
        editPlaylist(p, delete = true)
    }

    private fun editable(id: String): Playlist? {
        val state = _state.value
        return state.playlists.firstOrNull { it.id == id }?.takeIf { state.canEdit(it) }
    }

    private fun editPlaylist(p: Playlist, delete: Boolean = false) {
        scope.launch {
            lock.withLock {
                ensureLoaded()
                val old = edits[p.id]
                val inLuxTunes = !p.onPhone || old?.state == PlaylistEdit.DELIVERED
                if (delete && !inLuxTunes && (old == null || old.state == PlaylistEdit.NEW)) {
                    // Never left the phone: just forget it.
                    db.deleteEdit(p.id)
                    edits.remove(p.id)
                } else {
                    val e = PlaylistEdit(
                        id = p.id,
                        action = when {
                            delete -> "delete"
                            inLuxTunes -> "update"
                            else -> "create"
                        },
                        name = p.name,
                        isSmart = p.isSmart,
                        items = if (p.isSmart) emptyList() else p.trackIds,
                        smartInfo = if (p.isSmart) p.smartInfo else null,
                        smartCriteria = if (p.isSmart) p.smartCriteria else null,
                        created = old?.created ?: System.currentTimeMillis(),
                        state = PlaylistEdit.NEW,
                    )
                    db.saveEdit(e)
                    edits[p.id] = e
                }
                val folder = _state.value.folder
                if (folder != null) writeChangesFileSafely(folder)
                publish(folder, _state.value.message)
            }
        }
    }

    // ---- Deleting songs from the phone ---------------------------------------------------

    /**
     * Delete these songs' files from the phone. They're only gone until the next
     * sync: if they're still chosen in LuxTunes, it copies them again (LuxTunes
     * decides what's on the phone). Pending plays and ratings are kept.
     */
    fun deleteFromPhone(ids: Collection<String>) {
        scope.launch {
            lock.withLock {
                val folder = _state.value.folder ?: return@withLock
                val byId = _state.value.byId
                for (id in ids) {
                    val t = byId[id] ?: continue
                    val file = File(folder, t.path)
                    if (file.exists() && !file.delete()) Log.w(TAG, "couldn't delete ${file.path}")
                    else MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)
                }
                db.deleteTracks(ids)
                libraryTracks = libraryTracks.filter { it.id !in ids }
                publish(folder, _state.value.message)
            }
        }
    }

    // ---- Lyrics from the song files ------------------------------------------------------

    /**
     * Lyrics the player found in the song files themselves (by song ID). LuxTunes
     * only puts lyrics in library.db for songs whose lyrics were edited there.
     */
    private val _fileLyrics = MutableStateFlow<Map<String, String>>(emptyMap())
    val fileLyrics: StateFlow<Map<String, String>> = _fileLyrics.asStateFlow()

    fun foundLyrics(id: String, text: String) {
        val clean = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (clean.isEmpty() || _fileLyrics.value[id] == clean) return
        _fileLyrics.value = _fileLyrics.value + (id to clean)
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
        // No cover yet: leave it unset so the player uses the art inside the file.
        val cover = if (folder != null && t.artwork != null) File(folder, t.artwork) else null
        if (cover != null && cover.isFile) b.setArtworkUri(Uri.fromFile(cover))
        return b.build()
    }

    /** A song's picture: its album cover, else the art inside the file. */
    fun art(t: Track?): Art? {
        val folder = _state.value.folder ?: return null
        if (t == null) return null
        return Art(t.artwork?.let { File(folder, it) }, File(folder, t.path))
    }

    companion object {
        private const val TAG = "LuxTunes"
    }
}
