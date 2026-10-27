package io.github.metricambient.luxtunes.data

import java.security.SecureRandom
import java.util.Collections
import java.util.Random

private const val ARTIST_GAP = 2
private const val ALBUM_GAP = 8
private const val LOOK_AHEAD = 300
private const val TWO_DAYS_MILLIS = 2L * 24 * 60 * 60 * 1000L

/**
 * LuxTunes' new shuffle algorithm:
 * 1. Fair Fisher-Yates shuffle using a SecureRandom seed mixed with nano-time.
 * 2. Songs heard recently (played < 2 days ago) move to the back.
 * 3. Neighbor spreading prevents artist clashes (within 2 songs) and album clashes (within 8 songs).
 */
fun <T> spreadShuffle(
    items: List<T>,
    random: Random = Random(SecureRandom().nextLong() xor System.nanoTime()),
    currentTrack: T? = null,
    key: (T) -> String = { "artist" }
): List<T> {
    if (items.isEmpty()) return items

    // If a current track is playing/selected, keep it first and shuffle the rest
    val workList = if (currentTrack != null && items.contains(currentTrack)) {
        val mutable = items.toMutableList()
        mutable.remove(currentTrack)
        mutable
    } else {
        items.toMutableList()
    }

    // Step 1: Fair Fisher-Yates shuffle using provided or secure random
    Collections.shuffle(workList, random)

    // Step 2: Songs heard recently go to the back
    val (recent, notRecent) = workList.partition { isRecent(it) }
    val ordered = (notRecent + recent).toMutableList()

    // Step 3: Spread neighbors apart (avoiding artist and album clashes)
    for (i in 1 until ordered.size) {
        val currentSong = ordered[i]
        if (clashes(ordered, i, currentSong)) {
            val limit = minOf(ordered.size, i + LOOK_AHEAD)
            var foundIndex = -1
            for (j in (i + 1) until limit) {
                if (!clashes(ordered, i, ordered[j])) {
                    foundIndex = j
                    break
                }
            }
            if (foundIndex != -1) {
                val songToMove = ordered.removeAt(foundIndex)
                ordered.add(i, songToMove)
            }
        }
    }

    // Re-insert current track at the very beginning if requested/active
    return if (currentTrack != null && items.contains(currentTrack)) {
        listOf(currentTrack) + ordered
    } else {
        ordered
    }
}

fun <T> artistKey(t: T): String {
    return if (t is Track) {
        val artist = t.albumArtist.ifBlank { t.artist }
        artist.lowercase().trim()
    } else {
        t.toString().lowercase().trim()
    }
}

fun <T> albumKey(t: T): String {
    if (t !is Track) return ""
    val album = t.album
    if (album.isBlank()) return ""
    val aKey = artistKey(t)
    return if (aKey.isBlank()) {
        album.lowercase().trim()
    } else {
        "$aKey:${album.lowercase().trim()}"
    }
}

private fun <T> isRecent(t: T): Boolean {
    if (t !is Track) return false
    val now = System.currentTimeMillis()
    val playDate = t.playDate ?: return false
    
    // Safely convert playDate to Long milliseconds if it isn't already
    val playDateMillis = when (playDate) {
        is Long -> playDate
        is Number -> playDate.toLong()
        else -> {
            try {
                // Fallback if it's a java.util.Date or similar object with getTime()
                playDate.javaClass.getMethod("getTime").invoke(playDate) as? Long ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }
    
    return playDateMillis > 0 && (now - playDateMillis < TWO_DAYS_MILLIS)
}

private fun <T> clashes(order: List<T>, i: Int, song: T): Boolean {
    val aKey = artistKey(song)
    val alKey = albumKey(song)

    val maxBack = maxOf(ARTIST_GAP, ALBUM_GAP)
    for (back in 1..maxBack) {
        val prevIndex = i - back
        if (prevIndex < 0) break
        val before = order[prevIndex]

        if (back <= ARTIST_GAP && aKey.isNotEmpty() && artistKey(before) == aKey) {
            return true
        }
        if (back <= ALBUM_GAP && alKey.isNotEmpty() && albumKey(before) == alKey) {
            return true
        }
    }
    return false
}

/**
 * Groups (albums or artists) in random order, each group's songs kept in their
 * own order: "Shuffling by albums" / "Shuffling by artists".
 */
fun shuffleGroups(tracks: List<Track>, key: (Track) -> String): List<Track> =
    tracks.groupBy(key).values.shuffled().flatten()
