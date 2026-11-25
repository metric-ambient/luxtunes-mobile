package io.github.metricambient.luxtunes.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/**
 * What each screen's Play button last did, remembered per screen: whether Shuffle is on
 * ("album:…", "group:artist:…", "playlist:…", "tab:Songs", "radio"), and for an artist
 * (or other group) whether it shuffles by albums or by songs. Kept in the app's own
 * settings on this phone; never synced.
 */
object PlayChoices {
    private var prefs: SharedPreferences? = null
    private val shuffle = mutableStateMapOf<String, Boolean>()
    private val byAlbums = mutableStateMapOf<String, Boolean>()

    /** Read what was saved (once, when the app starts). */
    fun load(context: Context) {
        val p = context.getSharedPreferences("play_choices", Context.MODE_PRIVATE)
        prefs = p
        for ((k, v) in p.all) {
            if (v !is Boolean) continue
            when {
                k.startsWith("s:") -> shuffle[k.substring(2)] = v
                k.startsWith("a:") -> byAlbums[k.substring(2)] = v
            }
        }
    }

    /** Shuffle on for this screen (off until turned on). */
    fun shuffleOn(key: String): Boolean = shuffle[key] ?: false

    fun setShuffle(key: String, on: Boolean) {
        shuffle[key] = on
        val edit = prefs?.edit() ?: return
        if (on) edit.putBoolean("s:$key", true) else edit.remove("s:$key")
        edit.apply()
    }

    /** This artist shuffles by albums; `default` (Settings' choice) until one is chosen for it. */
    fun byAlbums(key: String, default: Boolean): Boolean = byAlbums[key] ?: default

    fun setByAlbums(key: String, on: Boolean) {
        byAlbums[key] = on
        prefs?.edit()?.putBoolean("a:$key", on)?.apply()
    }
}
