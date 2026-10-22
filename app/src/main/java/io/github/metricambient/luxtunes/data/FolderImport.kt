package io.github.metricambient.luxtunes.data

import android.media.MediaMetadataRetriever
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Add Folder to Library, as in LuxTunes: finds the songs in a folder, reads their
 * tags, and works out where each goes in the LuxTunes folder, by LuxTunes' own
 * naming rules ("LuxTunes Media/Music/Artist/Album/07 Name.mp3"), so the computer
 * finds them where it would have put them.
 */
object FolderImport {
    /** Song files the phone can play. */
    val AUDIO = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav")
    const val MEDIA = "LuxTunes Media"
    private const val FOLDER_MAX = 40
    private const val FILE_MAX = 36
    private val INVALID = "/\\:*?\"<>|;".toSet() + setOf('‘', '’', '´', '“', '”')

    /** Every song file under `dir` (not hidden ones, not inside `skip`). */
    fun scan(dir: File, skip: File?): List<File> =
        dir.walkTopDown()
            .onEnter { d -> !d.name.startsWith(".") && (skip == null || d.canonicalPath != skip.canonicalPath) }
            .filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in AUDIO }
            .sortedBy { it.path.lowercase() }
            .toList()

    /**
     * Move `file` to `target`: a rename when both are on the same storage, else a copy
     * that's checked before the original is deleted. On failure the original stays.
     */
    fun move(file: File, target: File) {
        if (file.renameTo(target)) return
        file.copyTo(target)
        if (target.length() != file.length()) {
            target.delete()
            throw java.io.IOException("the copy of ${file.name} came out the wrong size")
        }
        if (!file.delete()) android.util.Log.w("FolderImport", "copied ${file.path} but couldn't delete it")
    }

    /** One folder or file name (LuxTunes' organize.safe_name). */
    fun safeName(text: String, limit: Int, folder: Boolean): String {
        var t = text.map { if (it in INVALID || it.code < 32) '_' else it }.joinToString("")
        if (t.startsWith(".")) t = "_" + t.substring(1)
        t = t.take(limit).trimEnd(' ', ' ')
        if (folder && t.endsWith(".")) t = t.dropLast(1) + "_"
        return t.ifEmpty { "_" }
    }

    /** "07 Name" or "1-07 Name" (LuxTunes' organize.file_stem). */
    fun fileStem(name: String, track: Int, disc: Int, discs: Int): String {
        if (track <= 0) return name
        var prefix = "%02d ".format(track)
        if (disc > 0 && (discs == 0 || discs > 1)) prefix = "$disc-$prefix"
        return prefix + name
    }

    /** "LuxTunes Media/Music/Artist/Album/07 Name.mp3", made unique against `taken` (lower case). */
    fun pathFor(values: Map<String, Any?>, extension: String, taken: MutableSet<String>): String {
        val artist = if ((values["compilation"] as? Int ?: 0) != 0) "Compilations"
        else (values["album_artist"] as? String)?.ifBlank { null } ?: (values["artist"] as? String)?.ifBlank { null } ?: "Unknown Artist"
        val album = (values["album"] as? String)?.ifBlank { null } ?: "Unknown Album"
        val stem = fileStem(
            (values["name"] as? String)?.ifBlank { null } ?: "Untitled",
            values["track_number"] as? Int ?: 0, values["disc_number"] as? Int ?: 0, values["disc_count"] as? Int ?: 0,
        )
        val base = listOf(MEDIA, "Music", safeName(artist, FOLDER_MAX, true), safeName(album, FOLDER_MAX, true),
            safeName(stem, FILE_MAX, false)).joinToString("/")
        var path = "$base.$extension"
        var n = 2
        while (path.lowercase() in taken) path = "$base ${n++}.$extension"
        taken += path.lowercase()
        return path
    }

    private fun kindOf(extension: String): String = when (extension) {
        "mp3" -> "MPEG audio file"
        "m4a", "aac" -> "AAC audio file"
        "flac" -> "FLAC audio file"
        "ogg", "oga" -> "Ogg Vorbis file"
        "opus" -> "Opus audio file"
        "wav" -> "WAV audio file"
        else -> "Audio file"
    }

    /** "3/12" → (3, 12) */
    private fun numbers(text: String?): Pair<Int, Int> {
        if (text.isNullOrBlank()) return 0 to 0
        val parts = text.split('/')
        return (parts[0].trim().toIntOrNull() ?: 0) to (parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0)
    }

    /** A song's details from its tags, as library.db columns (null if it can't be read). */
    fun readTags(file: File): Map<String, Any?>? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.path)
            fun tag(key: Int): String? = r.extractMetadata(key)?.trim()?.ifEmpty { null }
            val (track, tracks) = numbers(tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
            val (disc, discs) = numbers(tag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER))
            val year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull()
                ?: tag(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4)?.toIntOrNull() ?: 0
            val ext = file.extension.lowercase()
            mapOf(
                "media_kind" to "music",
                "name" to (tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension),
                "artist" to tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                "album_artist" to tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                "album" to tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                "genre" to tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
                "composer" to tag(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                "year" to year,
                "track_number" to track,
                "track_count" to tracks,
                "disc_number" to disc,
                "disc_count" to discs,
                "total_time" to (tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L),
                "bit_rate" to ((tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L) / 1000),
                "compilation" to (if (tag(MediaMetadataRetriever.METADATA_KEY_COMPILATION) == "1") 1 else 0),
                "size" to file.length(),
                "kind" to kindOf(ext),
                "date_added" to DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC).withNano(0).toInstant()),
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}
