package io.github.metricambient.luxtunes.playback

import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.id3.TextInformationFrame

/**
 * The ReplayGain values in a song file's tags, in dB: REPLAYGAIN_TRACK_GAIN and
 * REPLAYGAIN_ALBUM_GAIN, from ID3 TXXX frames (MP3), Vorbis comments (FLAC, Ogg)
 * and iTunes-style "----" atoms (M4A).
 */
object ReplayGainTags {
    data class Gains(val track: Double?, val album: Double?)

    fun find(tracks: Tracks): Gains {
        var track: Double? = null
        var album: Double? = null
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val metadata: Metadata = group.getTrackFormat(i).metadata ?: continue
                for (k in 0 until metadata.length()) {
                    val (key, value) = keyValue(metadata.get(k)) ?: continue
                    when (key.uppercase()) {
                        "REPLAYGAIN_TRACK_GAIN" -> track = track ?: parse(value)
                        "REPLAYGAIN_ALBUM_GAIN" -> album = album ?: parse(value)
                    }
                }
            }
        }
        return Gains(track, album)
    }

    /** "-6.54 dB" → -6.54 */
    fun parse(text: String): Double? =
        Regex("[-+]?\\d+(\\.\\d+)?").find(text.replace(',', '.'))?.value?.toDoubleOrNull()

    private fun keyValue(entry: Metadata.Entry): Pair<String, String>? {
        if (entry is TextInformationFrame) {
            if (entry.id != "TXXX") return null
            val key = entry.description ?: return null
            return key to (entry.values.firstOrNull() ?: return null)
        }
        // Vorbis comments and M4A atoms: read by field name (their classes moved between Media3 versions).
        return try {
            when (entry.javaClass.simpleName) {
                "VorbisComment" -> (field(entry, "key") ?: return null) to (field(entry, "value") ?: return null)
                "InternalFrame" -> (field(entry, "description") ?: return null) to (field(entry, "text") ?: return null)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun field(o: Any, name: String): String? = o.javaClass.getField(name).get(o) as? String
}
