package io.github.metricambient.luxtunes.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the LuxTunes folder is: the phone's own storage or a removable SD card. */
enum class StorageChoice { INTERNAL, REMOVABLE }

/** What a tap on a song does (the list is the one the song was tapped in). */
enum class SongAction(val label: String) {
    PLAY("Play"),             // the list, from this song
    PLAY_ONE("Play 1"),       // just this song
    SHUFFLE("Shuffle"),       // the list, shuffled
}

/** What a tap on an artist or album does. */
enum class GroupAction(val label: String) {
    VIEW("View"),
    PLAY("Play"),
    SHUFFLE("Shuffle"),
    SHUFFLE_ALBUMS("Shuffling by albums"),
    SHUFFLE_ARTISTS("Shuffling by artists"),
}

enum class SongSort(val label: String) {
    NAME("Name"), ARTIST("Artist"), ALBUM("Album"), RATING("Rating"),
    PLAYS("Most played"), RECENT("Recently played"),
}

enum class GroupSort(val label: String) { NAME("Name"), ARTIST("Artist"), YEAR("Year"), SONGS("Number of songs") }

val ALL_TABS = listOf("Artists", "Playlists", "Songs", "Albums")

data class AppSettings(
    val storage: StorageChoice? = null,          // null: not chosen yet
    val tabs: List<String> = ALL_TABS,           // shown, in this order
    val songArt: Boolean = false,                // album art in the Songs tab
    val playlistIcons: Boolean = true,
    val albumArt: Boolean = true,                // art in the Albums grid
    val songAction: SongAction = SongAction.PLAY,
    val playlistAction: GroupAction = GroupAction.VIEW,
    val artistAction: GroupAction = GroupAction.VIEW,
    val albumAction: GroupAction = GroupAction.VIEW,
    val gridPortrait: Int = 2,
    val gridLandscape: Int = 4,
    val songSort: SongSort = SongSort.NAME,
    val artistSort: GroupSort = GroupSort.NAME,
    val albumSort: GroupSort = GroupSort.NAME,
)

/** The app's settings, kept in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            storage = sp.getString("storage", null)?.let { name -> StorageChoice.entries.firstOrNull { it.name == name } },
            tabs = sp.getString("tabs", null)?.split(',')?.filter { it in ALL_TABS } ?: d.tabs,
            songArt = sp.getBoolean("songArt", d.songArt),
            playlistIcons = sp.getBoolean("playlistIcons", d.playlistIcons),
            albumArt = sp.getBoolean("albumArt", d.albumArt),
            songAction = enumOf(sp.getString("songAction", null), d.songAction),
            playlistAction = enumOf(sp.getString("playlistAction", null), d.playlistAction),
            artistAction = enumOf(sp.getString("artistAction", null), d.artistAction),
            albumAction = enumOf(sp.getString("albumAction", null), d.albumAction),
            gridPortrait = sp.getInt("gridPortrait", d.gridPortrait),
            gridLandscape = sp.getInt("gridLandscape", d.gridLandscape),
            songSort = enumOf(sp.getString("songSort", null), d.songSort),
            artistSort = enumOf(sp.getString("artistSort", null), d.artistSort),
            albumSort = enumOf(sp.getString("albumSort", null), d.albumSort),
        )
    }

    fun update(change: (AppSettings) -> AppSettings) {
        val s = change(_settings.value)
        _settings.value = s
        sp.edit()
            .putString("storage", s.storage?.name)
            .putString("tabs", s.tabs.joinToString(","))
            .putBoolean("songArt", s.songArt)
            .putBoolean("playlistIcons", s.playlistIcons)
            .putBoolean("albumArt", s.albumArt)
            .putString("songAction", s.songAction.name)
            .putString("playlistAction", s.playlistAction.name)
            .putString("artistAction", s.artistAction.name)
            .putString("albumAction", s.albumAction.name)
            .putInt("gridPortrait", s.gridPortrait)
            .putInt("gridLandscape", s.gridLandscape)
            .putString("songSort", s.songSort.name)
            .putString("artistSort", s.artistSort.name)
            .putString("albumSort", s.albumSort.name)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
