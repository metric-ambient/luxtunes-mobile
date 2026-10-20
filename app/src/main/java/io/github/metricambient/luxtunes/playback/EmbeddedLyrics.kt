package io.github.metricambient.luxtunes.playback

import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import java.nio.charset.Charset

/**
 * Finds the lyrics stored in a song file, from the tags the player has already
 * read: ID3 "USLT" (MP3), "©lyr" (M4A, which Media3 reports as USLT) and the
 * Vorbis comment LYRICS / UNSYNCEDLYRICS (FLAC, Ogg).
 */
object EmbeddedLyrics {

    fun find(tracks: Tracks): String? {
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                find(metadata)?.let { return it }
            }
        }
        return null
    }

    private fun find(metadata: Metadata): String? {
        for (i in 0 until metadata.length()) {
            val text = when (val entry = metadata.get(i)) {
                is TextInformationFrame -> if (entry.id == "USLT") entry.values.joinToString("\n") else null
                is BinaryFrame -> if (entry.id == "USLT") parseUslt(entry.data) else null
                else -> vorbisLyrics(entry)
            }
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    /** USLT: encoding byte, 3-letter language, description ending in a null, then the text. */
    private fun parseUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val charset: Charset = when (data[0].toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val wide = data[0].toInt() == 1 || data[0].toInt() == 2
        var pos = 4
        // Skip the description and its null terminator (two zero bytes for UTF-16).
        if (wide) {
            while (pos + 1 < data.size && !(data[pos].toInt() == 0 && data[pos + 1].toInt() == 0)) pos += 2
            pos += 2
        } else {
            while (pos < data.size && data[pos].toInt() != 0) pos++
            pos += 1
        }
        if (pos >= data.size) return null
        return String(data, pos, data.size - pos, charset).trimEnd('\u0000')
    }

    /** VorbisComment lives in different packages in different Media3 versions; read it by name. */
    private fun vorbisLyrics(entry: Metadata.Entry): String? {
        if (entry.javaClass.simpleName != "VorbisComment") return null
        return try {
            val key = entry.javaClass.getField("key").get(entry) as? String ?: return null
            if (!key.equals("LYRICS", ignoreCase = true) && !key.equals("UNSYNCEDLYRICS", ignoreCase = true)) return null
            entry.javaClass.getField("value").get(entry) as? String
        } catch (e: Exception) {
            null
        }
    }
}
