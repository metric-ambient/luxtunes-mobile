package io.github.metricambient.luxtunes.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/** A playlist change made on the phone, waiting to go to LuxTunes (changes.json v2). */
data class PlaylistEdit(
    val id: String,
    val action: String,                 // "create", "update" or "delete"
    val name: String = "",
    val isSmart: Boolean = false,
    val items: List<String> = emptyList(),
    val smartInfo: ByteArray? = null,
    val smartCriteria: ByteArray? = null,
    val created: Long = System.currentTimeMillis(),
    val state: Int = NEW,
) {
    companion object {
        const val NEW = 0           // changed since changes.json was last written
        const val WRITTEN = 1       // in changes.json, waiting for LuxTunes
        const val DELIVERED = 2     // LuxTunes took it; dropped when its new library.db arrives
    }
}

/**
 * The app's own database (in its private storage). It holds a copy of the
 * library LuxTunes synced, plus what happened on the phone and hasn't reached
 * LuxTunes yet: plays, skips and ratings (`pending`) and playlist changes
 * (`playlist_edits`). The copy is replaced whenever LuxTunes writes a new
 * library.db; the other two only shrink once LuxTunes has taken the changes.
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(context, "app.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        createLibraryTables(db)
        db.execSQL(
            """CREATE TABLE pending (
                id TEXT PRIMARY KEY, plays INTEGER, last_played INTEGER, skips INTEGER,
                last_skipped INTEGER, rating INTEGER, sent_plays INTEGER, sent_skips INTEGER,
                sent_rating INTEGER)"""
        )
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
        createEditTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // The library copy gets every column (smart rules test them): rebuild it
            // and take library.db in again at the next refresh.
            db.execSQL("DROP TABLE IF EXISTS tracks")
            db.execSQL("DROP TABLE IF EXISTS playlists")
            db.execSQL("DROP TABLE IF EXISTS playlist_items")
            createLibraryTables(db)
            db.delete("meta", "key = ?", arrayOf("imported_stamp"))
            createEditTable(db)
            if (oldVersion == 2) movePhonePlaylists(db)
        }
    }

    private fun createLibraryTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY, path TEXT NOT NULL, artwork TEXT, " +
            TRACK_COLUMNS.joinToString(", ") + ")")
        db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT, is_smart INTEGER, position INTEGER, " +
            "smart_info BLOB, smart_criteria BLOB)")
        db.execSQL("CREATE TABLE playlist_items (playlist_id TEXT, position INTEGER, track_id TEXT)")
        db.execSQL("CREATE TABLE reference_playlists (id TEXT PRIMARY KEY, name TEXT, is_smart INTEGER, is_folder INTEGER)")
        db.execSQL("CREATE TABLE reference_items (playlist_id TEXT, track_id TEXT)")
    }

    private fun createEditTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE playlist_edits (
                id TEXT PRIMARY KEY, action TEXT, name TEXT, is_smart INTEGER, items TEXT,
                smart_info BLOB, smart_criteria BLOB, created INTEGER, state INTEGER)"""
        )
    }

    /** Milestone 2's phone playlists become "create" edits, so they go to LuxTunes too. */
    private fun movePhonePlaylists(db: SQLiteDatabase) {
        val items = mutableMapOf<String, MutableList<String>>()
        db.rawQuery("SELECT playlist_id, track_id FROM phone_playlist_items ORDER BY playlist_id, position", null).use { c ->
            while (c.moveToNext()) items.getOrPut(c.getString(0)) { mutableListOf() } += c.getString(1)
        }
        db.rawQuery("SELECT id, name, created FROM phone_playlists", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                writeEdit(db, PlaylistEdit(id, "create", c.getString(1) ?: "Playlist", false,
                    items[id].orEmpty(), created = c.getLong(2)))
            }
        }
        db.execSQL("DROP TABLE IF EXISTS phone_playlists")
        db.execSQL("DROP TABLE IF EXISTS phone_playlist_items")
    }

    // ---- Small settings and markers ----------------------------------------------

    fun meta(key: String): String? =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun setMeta(key: String, value: String?) {
        if (value == null) writableDatabase.delete("meta", "key = ?", arrayOf(key))
        else writableDatabase.insertWithOnConflict(
            "meta", null,
            ContentValues().apply { put("key", key); put("value", value) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // ---- The library copy ------------------------------------------------------------

    private fun copyRows(src: SQLiteDatabase, db: SQLiteDatabase, table: String, columns: List<String>) {
        val have = src.rawQuery("PRAGMA table_info($table)", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(1)) }
        }
        val wanted = columns.filter { it in have }
        if (wanted.isEmpty()) return
        src.rawQuery("SELECT ${wanted.joinToString(", ")} FROM $table", null).use { c ->
            while (c.moveToNext()) {
                val values = ContentValues()
                wanted.forEachIndexed { i, name ->
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> values.put(name, c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> values.put(name, c.getDouble(i))
                        Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                        Cursor.FIELD_TYPE_BLOB -> values.put(name, c.getBlob(i))
                        else -> values.put(name, c.getString(i))
                    }
                }
                db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    /**
     * Replace the library copy with a library.db from LuxTunes (already copied to
     * private storage). Returns its info table. Columns or tables it doesn't have
     * (an older LuxTunes) are left empty.
     */
    fun importLibrary(source: File): Map<String, String> {
        val src = SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val info = mutableMapOf<String, String>()
            src.rawQuery("SELECT key, value FROM info", null).use { c ->
                while (c.moveToNext()) info[c.getString(0)] = c.getString(1) ?: ""
            }
            val tables = src.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (t in listOf("tracks", "playlists", "playlist_items", "reference_playlists", "reference_items")) {
                    db.delete(t, null, null)
                }
                copyRows(src, db, "tracks", listOf("id", "path", "artwork") + TRACK_COLUMNS)
                copyRows(src, db, "playlists", listOf("id", "name", "is_smart", "position", "smart_info", "smart_criteria"))
                copyRows(src, db, "playlist_items", listOf("playlist_id", "position", "track_id"))
                if ("reference_playlists" in tables) {
                    copyRows(src, db, "reference_playlists", listOf("id", "name", "is_smart", "is_folder"))
                    copyRows(src, db, "reference_items", listOf("playlist_id", "track_id"))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return info
        } finally {
            src.close()
        }
    }

    /** Every song (music, podcasts, audiobooks…), with LuxTunes' values (pending changes not applied). */
    fun loadTracks(): List<Track> {
        val out = ArrayList<Track>()
        readableDatabase.rawQuery("SELECT * FROM tracks", null).use { c ->
            val names = c.columnNames
            fun str(col: String): String? = c.getColumnIndex(col).let { if (it < 0 || c.isNull(it)) null else c.getString(it) }
            fun num(col: String): Long = c.getColumnIndex(col).let { if (it < 0 || c.isNull(it)) 0L else c.getLong(it) }
            while (c.moveToNext()) {
                val id = str("id") ?: continue
                val path = str("path") ?: continue
                val raw = HashMap<String, Any?>(names.size)
                names.forEachIndexed { i, n ->
                    raw[n] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> c.getString(i)
                        else -> null
                    }
                }
                val artist = str("artist").orEmpty().ifBlank { "Unknown artist" }
                val albumArtist = str("album_artist").orEmpty().ifBlank { artist }
                val name = str("name").orEmpty().ifBlank { File(path).nameWithoutExtension }
                val album = str("album").orEmpty().ifBlank { "Unknown album" }
                out += Track(
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
                    sortName = str("sort_name")?.ifBlank { null } ?: name,
                    sortArtist = str("sort_artist")?.ifBlank { null } ?: artist,
                    sortAlbumArtist = str("sort_album_artist")?.ifBlank { null }
                        ?: str("sort_artist")?.ifBlank { null } ?: albumArtist,
                    sortAlbum = str("sort_album")?.ifBlank { null } ?: album,
                    startTime = num("start_time"),
                    stopTime = num("stop_time"),
                    mediaKind = str("media_kind")?.ifBlank { null } ?: "music",
                    raw = raw,
                )
            }
        }
        return out
    }

    /** LuxTunes' playlists, in its order (smart ones with their rules). */
    fun loadPlaylists(): List<Playlist> {
        val items = mutableMapOf<String, MutableList<String>>()
        readableDatabase.rawQuery(
            "SELECT playlist_id, track_id FROM playlist_items ORDER BY playlist_id, position", null
        ).use { c ->
            while (c.moveToNext()) items.getOrPut(c.getString(0)) { mutableListOf() } += c.getString(1)
        }
        val out = ArrayList<Playlist>()
        readableDatabase.rawQuery(
            "SELECT id, name, is_smart, smart_info, smart_criteria FROM playlists ORDER BY position", null
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                out += Playlist(
                    id = id, name = c.getString(1) ?: "Playlist", isSmart = c.getInt(2) != 0,
                    trackIds = items[id].orEmpty(),
                    smartInfo = if (c.isNull(3)) null else c.getBlob(3),
                    smartCriteria = if (c.isNull(4)) null else c.getBlob(4),
                )
            }
        }
        return out
    }

    /** Playlists that smart rules point to but that aren't synced: id -> (name, song IDs). */
    fun loadReferences(): Map<String, Pair<String, Set<String>>> {
        val items = mutableMapOf<String, MutableSet<String>>()
        readableDatabase.rawQuery("SELECT playlist_id, track_id FROM reference_items", null).use { c ->
            while (c.moveToNext()) items.getOrPut(c.getString(0).uppercase()) { mutableSetOf() } += c.getString(1)
        }
        val out = mutableMapOf<String, Pair<String, Set<String>>>()
        readableDatabase.rawQuery("SELECT id, name FROM reference_playlists", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0).uppercase()
                out[id] = (c.getString(1) ?: id) to items[id].orEmpty()
            }
        }
        return out
    }

    /** Songs deleted on the phone: gone from the copy until the next sync brings them back. */
    fun deleteTracks(ids: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) db.delete("tracks", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---- Playlist changes made on the phone ---------------------------------------------

    fun loadEdits(): List<PlaylistEdit> {
        val out = ArrayList<PlaylistEdit>()
        readableDatabase.rawQuery(
            "SELECT id, action, name, is_smart, items, smart_info, smart_criteria, created, state " +
                "FROM playlist_edits ORDER BY created", null
        ).use { c ->
            while (c.moveToNext()) {
                out += PlaylistEdit(
                    id = c.getString(0),
                    action = c.getString(1) ?: "update",
                    name = c.getString(2).orEmpty(),
                    isSmart = c.getInt(3) != 0,
                    items = c.getString(4)?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
                    smartInfo = if (c.isNull(5)) null else c.getBlob(5),
                    smartCriteria = if (c.isNull(6)) null else c.getBlob(6),
                    created = c.getLong(7),
                    state = c.getInt(8),
                )
            }
        }
        return out
    }

    fun saveEdit(edit: PlaylistEdit) = writeEdit(writableDatabase, edit)

    private fun writeEdit(db: SQLiteDatabase, e: PlaylistEdit) {
        db.insertWithOnConflict("playlist_edits", null, ContentValues().apply {
            put("id", e.id); put("action", e.action); put("name", e.name)
            put("is_smart", if (e.isSmart) 1 else 0); put("items", e.items.joinToString(","))
            if (e.smartInfo == null) putNull("smart_info") else put("smart_info", e.smartInfo)
            if (e.smartCriteria == null) putNull("smart_criteria") else put("smart_criteria", e.smartCriteria)
            put("created", e.created); put("state", e.state)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteEdit(id: String) {
        writableDatabase.delete("playlist_edits", "id = ?", arrayOf(id))
    }

    fun setEditStates(ids: Collection<String>, state: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) db.update("playlist_edits", ContentValues().apply { put("state", state) }, "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** A new library.db reflects everything LuxTunes took: those edits aren't needed any more. */
    fun dropDeliveredEdits() {
        writableDatabase.delete("playlist_edits", "state = ?", arrayOf(PlaylistEdit.DELIVERED.toString()))
    }

    // ---- Plays, skips and ratings not yet sent to LuxTunes ------------------------------------

    fun loadPending(): Map<String, PendingEntry> {
        val out = mutableMapOf<String, PendingEntry>()
        readableDatabase.rawQuery("SELECT * FROM pending", null).use { c ->
            fun num(col: String): Long = c.getColumnIndexOrThrow(col).let { if (c.isNull(it)) 0L else c.getLong(it) }
            fun opt(col: String): Int? = c.getColumnIndexOrThrow(col).let { if (c.isNull(it)) null else c.getInt(it) }
            while (c.moveToNext()) {
                val id = c.getString(c.getColumnIndexOrThrow("id"))
                out[id] = PendingEntry(
                    id = id,
                    plays = num("plays").toInt(), lastPlayed = num("last_played"),
                    skips = num("skips").toInt(), lastSkipped = num("last_skipped"),
                    rating = opt("rating"),
                    sentPlays = num("sent_plays").toInt(), sentSkips = num("sent_skips").toInt(),
                    sentRating = opt("sent_rating"),
                )
            }
        }
        return out
    }

    /** Save the given entries (removing ones with nothing left), in one transaction. */
    fun savePending(entries: Collection<PendingEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (e in entries) {
                if (e.isEmpty && e.sentPlays == 0 && e.sentSkips == 0 && e.sentRating == null) {
                    db.delete("pending", "id = ?", arrayOf(e.id))
                    continue
                }
                db.insertWithOnConflict("pending", null, ContentValues().apply {
                    put("id", e.id)
                    put("plays", e.plays); put("last_played", e.lastPlayed)
                    put("skips", e.skips); put("last_skipped", e.lastSkipped)
                    if (e.rating == null) putNull("rating") else put("rating", e.rating)
                    put("sent_plays", e.sentPlays); put("sent_skips", e.sentSkips)
                    if (e.sentRating == null) putNull("sent_rating") else put("sent_rating", e.sentRating)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearPending() {
        writableDatabase.delete("pending", null, null)
    }

    fun clearEdits() {
        writableDatabase.delete("playlist_edits", null, null)
    }

    companion object {
        /** The library.db song columns the app keeps (format v2: everything a smart rule can test). */
        val TRACK_COLUMNS = listOf(
            "media_kind", "name", "artist", "album_artist", "album", "genre", "composer", "grouping",
            "year", "track_number", "track_count", "disc_number", "disc_count", "total_time", "bit_rate",
            "sample_rate", "size", "rating", "rating_computed", "album_rating", "play_count", "play_date",
            "skip_count", "skip_date", "date_added", "date_modified", "compilation", "comments", "lyrics",
            "bookmarkable", "bookmark_time", "skip_when_shuffling", "start_time", "stop_time",
            "volume_adjustment", "show", "season", "episode_number", "sort_name", "sort_artist",
            "sort_album", "sort_album_artist", "sort_composer", "enabled", "bpm", "kind", "purchased",
        )
    }
}
