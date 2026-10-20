package io.github.metricambient.luxtunes.data

import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * What happened to one song on the phone since the last sync, in the shape
 * changes.json needs (no Android code here, so it's unit-tested).
 *
 * The sent* fields remember what the last written changes.json held. When
 * LuxTunes empties the file, exactly that much has reached the computer;
 * anything recorded after the file was written is kept for next time.
 */
data class PendingEntry(
    val id: String,
    val plays: Int = 0,
    val lastPlayed: Long = 0,       // Unix ms, 0 = none
    val skips: Int = 0,
    val lastSkipped: Long = 0,
    val rating: Int? = null,        // set only when rated on the phone
    val sentPlays: Int = 0,
    val sentSkips: Int = 0,
    val sentRating: Int? = null,
) {
    val isEmpty: Boolean get() = plays == 0 && skips == 0 && rating == null

    fun played(at: Long) = copy(plays = plays + 1, lastPlayed = maxOf(lastPlayed, at))
    fun skipped(at: Long) = copy(skips = skips + 1, lastSkipped = maxOf(lastSkipped, at))
    fun rated(value: Int) = copy(rating = value.coerceIn(0, 100))

    /** The file now holds everything recorded so far. */
    fun markSent() = copy(sentPlays = plays, sentSkips = skips, sentRating = rating)

    /** LuxTunes took what the file held: keep only what came after. */
    fun delivered(): PendingEntry {
        val leftPlays = (plays - sentPlays).coerceAtLeast(0)
        val leftSkips = (skips - sentSkips).coerceAtLeast(0)
        return PendingEntry(
            id = id,
            plays = leftPlays,
            lastPlayed = if (leftPlays > 0) lastPlayed else 0,
            skips = leftSkips,
            lastSkipped = if (leftSkips > 0) lastSkipped else 0,
            rating = if (rating == sentRating) null else rating,
        )
    }
}

object ChangesJson {
    const val VERSION = 1

    /** UTC "YYYY-MM-DDTHH:MM:SSZ", the format LuxTunes uses. */
    fun isoTime(unixMs: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(unixMs / 1000))

    /** The whole file. Only songs with something to report are listed. */
    fun build(entries: List<PendingEntry>, libraryId: String?): String {
        val sb = StringBuilder()
        sb.append("{\"version\": ").append(VERSION)
        if (!libraryId.isNullOrEmpty()) sb.append(", \"library_id\": \"").append(clean(libraryId)).append('"')
        sb.append(", \"tracks\": [")
        var first = true
        for (e in entries.sortedBy { it.id }) {
            if (e.isEmpty) continue
            sb.append(if (first) "\n  " else ",\n  ")
            first = false
            sb.append("{\"id\": \"").append(clean(e.id)).append('"')
            if (e.plays > 0) {
                sb.append(", \"plays\": ").append(e.plays)
                if (e.lastPlayed > 0) sb.append(", \"last_played\": \"").append(isoTime(e.lastPlayed)).append('"')
            }
            if (e.skips > 0) {
                sb.append(", \"skips\": ").append(e.skips)
                if (e.lastSkipped > 0) sb.append(", \"last_skipped\": \"").append(isoTime(e.lastSkipped)).append('"')
            }
            if (e.rating != null) sb.append(", \"rating\": ").append(e.rating)
            sb.append('}')
        }
        sb.append(if (first) "]}\n" else "\n]}\n")
        return sb.toString()
    }

    /**
     * True when LuxTunes has emptied the file ("tracks": [] and nothing else to
     * report), meaning it took everything the file held.
     */
    fun isEmptied(text: String): Boolean {
        val compact = text.filterNot { it.isWhitespace() }
        return compact.contains("\"tracks\":[]") && !compact.contains("\"id\":")
    }

    // IDs are hex digits; this just keeps anything odd from breaking the JSON.
    private fun clean(text: String) = text.filter { it.isLetterOrDigit() }
}
