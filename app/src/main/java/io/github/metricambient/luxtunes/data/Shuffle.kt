package io.github.metricambient.luxtunes.data

import kotlin.random.Random

/**
 * LuxTunes' "spread" shuffle: songs are grouped by artist, each group is
 * shuffled, and each artist's songs are spread evenly through the order with a
 * random offset and a little jitter. The same artist rarely plays twice in a row.
 */
fun <T> spreadShuffle(items: List<T>, random: Random = Random.Default, key: (T) -> String): List<T> {
    data class Placed<E>(val position: Double, val tie: Double, val item: E)

    val placed = ArrayList<Placed<T>>(items.size)
    for (group in items.groupBy(key).values) {
        val shuffled = group.shuffled(random)
        val n = shuffled.size
        val offset = random.nextDouble() / n
        shuffled.forEachIndexed { i, item ->
            val jitter = random.nextDouble(-0.1, 0.1) / n
            placed += Placed(offset + i.toDouble() / n + jitter, random.nextDouble(), item)
        }
    }
    placed.sortWith(compareBy({ it.position }, { it.tie }))
    return placed.map { it.item }
}

fun artistKey(t: Track): String = t.albumArtist.lowercase()

/**
 * Groups (albums or artists) in random order, each group's songs kept in their
 * own order: "Shuffling by albums" / "Shuffling by artists".
 */
fun shuffleGroups(tracks: List<Track>, key: (Track) -> String): List<Track> =
    tracks.groupBy(key).values.shuffled().flatten()
