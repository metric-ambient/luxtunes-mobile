package io.github.metricambient.luxtunes.data

import org.json.JSONObject

/**
 * What LuxTunes left in changes.json when it emptied it: entries the phone wrote
 * while LuxTunes was reading, which haven't been taken yet.
 */
data class Remainder(
    val tracks: Map<String, Kept>,       // song ID (upper case) -> what's still in the file
    val playlists: Set<String>,          // playlist IDs (upper case) still in the file
) {
    data class Kept(val plays: Int, val skips: Int, val rated: Boolean)

    companion object {
        /** null if the file isn't readable JSON. */
        fun parse(text: String): Remainder? = try {
            val json = JSONObject(text)
            val tracks = mutableMapOf<String, Kept>()
            json.optJSONArray("tracks")?.let { list ->
                for (i in 0 until list.length()) {
                    val e = list.optJSONObject(i) ?: continue
                    val id = e.optString("id").uppercase()
                    if (id.isEmpty()) continue
                    tracks[id] = Kept(e.optInt("plays", 0).coerceAtLeast(0), e.optInt("skips", 0).coerceAtLeast(0), e.has("rating"))
                }
            }
            val playlists = mutableSetOf<String>()
            json.optJSONArray("playlists")?.let { list ->
                for (i in 0 until list.length()) {
                    list.optJSONObject(i)?.optString("id")?.uppercase()?.takeIf { it.isNotEmpty() }?.let { playlists += it }
                }
            }
            Remainder(tracks, playlists)
        } catch (e: Exception) {
            null
        }
    }
}
