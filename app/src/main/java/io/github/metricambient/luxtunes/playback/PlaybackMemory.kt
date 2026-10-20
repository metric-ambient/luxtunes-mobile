package io.github.metricambient.luxtunes.playback

import android.content.Context
import androidx.media3.common.Player

/**
 * Where playback was: the Now Playing list, the song in it and how far in, plus
 * shuffle and repeat. Saved by the player service as things change, read back
 * when the app starts with nothing loaded.
 */
class PlaybackMemory(context: Context) {
    private val sp = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    data class Saved(
        val queue: List<String>,
        val currentId: String?,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int,
    )

    fun saveQueue(ids: List<String>) {
        sp.edit().putString("queue", ids.joinToString(",")).apply()
    }

    fun savePosition(currentId: String?, index: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        sp.edit()
            .putString("current", currentId)
            .putInt("index", index)
            .putLong("position", positionMs)
            .putBoolean("shuffle", shuffle)
            .putInt("repeat", repeatMode)
            .apply()
    }

    fun load(): Saved? {
        val queue = sp.getString("queue", null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        if (queue.isEmpty()) return null
        return Saved(
            queue = queue,
            currentId = sp.getString("current", null),
            index = sp.getInt("index", 0),
            positionMs = sp.getLong("position", 0L),
            shuffle = sp.getBoolean("shuffle", false),
            repeatMode = sp.getInt("repeat", Player.REPEAT_MODE_OFF),
        )
    }
}
