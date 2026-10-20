package io.github.metricambient.luxtunes.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/**
 * The app's own database (in its private storage). It holds a copy of the
 * library LuxTunes synced, plus the plays, skips and ratings not yet sent back.
 * The copy is replaced whenever LuxTunes writes a new library.db; the pending
 * table never is (it only shrinks when LuxTunes has taken the changes).
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(context, "app.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE tracks (
                id TEXT PRIMARY KEY, path TEXT NOT NULL, artwork TEXT, media_kind TEXT,
                name TEXT, artist TEXT, album_artist TEXT, album TEXT, genre TEXT,
                year INTEGER, track_number INTEGER, disc_number INTEGER, total_time INTEGER,
                rating INTEGER, rating_computed INTEGER, play_count INTEGER, play_date TEXT,
                skip_count INTEGER, skip_date TEXT, lyrics TEXT, sort_name TEXT, sort_artist TEXT,
                sort_album_artist TEXT, sort_album TEXT, start_time INTEGER, stop_time INTEGER)"""
        )
        db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT, is_smart INTEGER, position INTEGER)")
        db.execSQL("CREATE TABLE playlist_items (playlist_id TEXT, position INTEGER, track_id TEXT)")
        db.execSQL(
            """CREATE TABLE pending (
                id TEXT PRIMARY KEY, plays INTEGER, last_played INTEGER, skips INTEGER,
                last_skipped INTEGER, rating INTEGER, sent_plays INTEGER, sent_skips INTEGER,
                sent_rating INTEGER)"""
        )
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Only version 1 so far.
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

    /**
     * Replace the library copy with a library.db from LuxTunes (already copied to
     * private storage). Returns its info table. Columns it doesn't have are left empty.
     */
    fun importLibrary(source: File): Map<String, String> {
        val src = SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val info = mutableMapOf<String, String>()
            src.rawQuery("SELECT key, value FROM info", null).use { c ->
                while (c.moveToNext()) info[c.getString(0)] = c.getString(1) ?: ""
            }
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("tracks", null, null)
                db.delete("playlists", null, null)
                db.delete("playlist_items", null, null)
                src.rawQuery("SELECT * FROM tracks", null).use { c ->
                    val copy = TRACK_COLUMNS.map { it to c.getColumnIndex(it) }.filter { it.second >= 0 }
                    while (c.moveToNext()) {
                        val values = ContentValues()
                        for ((name, index) in copy) {
                            when (c.getType(index)) {
                                Cursor.FIELD_TYPE_INTEGER -> values.put(name, c.getLong(index))
                                Cursor.FIELD_TYPE_FLOAT -> values.put(name, c.getDouble(index).toLong())
                                Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                                else -> values.put(name, c.getString(index))
                            }
                        }
                        db.insertWithOnConflict("tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                }
                src.rawQuery("SELECT id, name, is_smart, position FROM playlists", null).use { c ->
                    while (c.moveToNext()) {
                        db.insert("playlists", null, ContentValues().apply {
                            put("id", c.getString(0))
                            put("name", c.getString(1))
                            put("is_smart", if (c.isNull(2)) 0 else c.getInt(2))
                            put("position", if (c.isNull(3)) 0 else c.getInt(3))
                        })
                    }
                }
                src.rawQuery("SELECT playlist_id, position, track_id FROM playlist_items", null).use { c ->
                    while (c.moveToNext()) {
                        db.insert("playlist_items", null, ContentValues().apply {
                            put("playlist_id", c.getString(0))
                            put("position", c.getInt(1))
                            put("track_id", c.getString(2))
                        })
                    }
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

    /** Every music track, with LuxTunes' values (pending changes not applied). */
    fun loadTracks(): List<Track> {
        val out = ArrayList<Track>()
        readableDatabase.rawQuery(
            "SELECT * FROM tracks WHERE media_kind IS NULL OR media_kind = '' OR media_kind = 'music'", null
        ).use { c ->
            fun str(col: String): String? = c.getColumnIndex(col).let { if (it < 0 || c.isNull(it)) null else c.getString(it) }
            fun num(col: String): Long = c.getColumnIndex(col).let { if (it < 0 || c.isNull(it)) 0L else c.getLong(it) }
            while (c.moveToNext()) {
                val path = str("path") ?: continue
                val artist = str("artist").orEmpty().ifBlank { "Unknown artist" }
                val albumArtist = str("album_artist").orEmpty().ifBlank { artist }
                val name = str("name").orEmpty().ifBlank { File(path).nameWithoutExtension }
                val album = str("album").orEmpty().ifBlank { "Unknown album" }
                out += Track(
                    id = str("id") ?: continue,
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
                )
            }
        }
        return out
    }

    fun loadPlaylists(): List<Playlist> {
        val items = mutableMapOf<String, MutableList<String>>()
        readableDatabase.rawQuery(
            "SELECT playlist_id, track_id FROM playlist_items ORDER BY playlist_id, position", null
        ).use { c ->
            while (c.moveToNext()) items.getOrPut(c.getString(0)) { mutableListOf() } += c.getString(1)
        }
        val out = ArrayList<Playlist>()
        readableDatabase.rawQuery("SELECT id, name, is_smart FROM playlists ORDER BY position", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                out += Playlist(id, c.getString(1) ?: "Playlist", c.getInt(2) != 0, items[id].orEmpty())
            }
        }
        return out
    }

    // ---- Changes not yet sent to LuxTunes -------------------------------------------

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

    companion object {
        /** The library.db columns the app keeps. */
        val TRACK_COLUMNS = listOf(
            "id", "path", "artwork", "media_kind", "name", "artist", "album_artist", "album", "genre",
            "year", "track_number", "disc_number", "total_time", "rating", "rating_computed",
            "play_count", "play_date", "skip_count", "skip_date", "lyrics", "sort_name", "sort_artist",
            "sort_album_artist", "sort_album", "start_time", "stop_time",
        )
    }
}
