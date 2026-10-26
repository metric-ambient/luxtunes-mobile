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
 * 2. Songs heard recently (started this session or played < 2 days ago) move to the back.
 * 3. Neighbor spreading prevents artist clashes (within 2 songs) and album clashes (within 8 songs).
 */
fun spreadShuffle(tracks: List<Track>, currentTrack: Track? = null): List<Track> {
    if (tracks.isEmpty()) return tracks

    // If a current track is playing/selected, keep it first and shuffle the rest
    val workList = if (currentTrack != null && tracks.contains(currentTrack)) {
        val mutable = tracks.toMutableList()
        mutable.remove(currentTrack)
        mutable
    } else {
        tracks.toMutableList()
    }

    // Step 1: Fair Fisher-Yates shuffle using SecureRandom + nanoTime seed
    val secureRandom = SecureRandom()
    val seed = secureRandom.nextLong() xor System.nanoTime()
    Collections.shuffle(workList, Random(seed))

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
                // Move the non-clashing song to index i, shifting intermediate items back
                val songToMove = ordered.removeAt(foundIndex)
                ordered.add(i, songToMove)
            }
        }
    }

    // Re-insert current track at the very beginning if requested/active
    return if (currentTrack != null && tracks.contains(currentTrack)) {
        listOf(currentTrack) + ordered
    } else {
        ordered
    }
}

fun artistKey(t: Track): String {
    // Album artist takes precedence; fallback to track artist if blank
    val artist = t.albumArtist.ifBlank { t.artist }
    return artist.lowercase().trim()
}

fun albumKey(t: Track): String {
    val album = t.album
    if (album.isBlank()) return ""
    val aKey = artistKey(t)
    // Combine album name with artist key so different artists' "Greatest Hits" differ
    return if (aKey.isBlank()) {
        album.lowercase().trim()
    } else {
        "$aKey:${album.lowercase().trim()}"
    }
}

fun isRecent(t: Track): Boolean {
    val now = System.currentTimeMillis()
    // Note: Adjust property names (`playDate`, `startedInSession`) to match your exact Track model
    val playDate = t.playDate // or t.play_date
    val startedInSession = t.startedInSession // or t.isStartedInSession
    
    val playedRecently = playDate != null && (now - playDate < TWO_DAYS_MILLIS)
    return startedInSession || playedRecently
}

fun clashes(order: List<Track>, i: Int, song: Track): Boolean {
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
