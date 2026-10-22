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

/** What a tap on a playlist, album, genre, composer, podcast or book does. */
enum class GroupAction(val label: String) {
    VIEW("View"),
    PLAY("Play"),
    SHUFFLE("Shuffle"),
    SHUFFLE_ALBUMS("Shuffle by albums"),
    SHUFFLE_ARTISTS("Shuffle by artists"),
}

/** The choices each kind of row offers (Settings → Tabs). */
object Actions {
    val BASIC = listOf(GroupAction.VIEW, GroupAction.PLAY, GroupAction.SHUFFLE)
    val ALL = GroupAction.entries.toList()
    val NO_ARTISTS = listOf(GroupAction.VIEW, GroupAction.PLAY, GroupAction.SHUFFLE, GroupAction.SHUFFLE_ALBUMS)
}

enum class SongSort(val label: String) {
    NAME("Name"), ARTIST("Artist"), ALBUM("Album"), RATING("Rating"),
    PLAYS("Most played"), RECENT("Recently played"),
}

enum class GroupSort(val label: String) { NAME("Name"), ARTIST("Artist"), YEAR("Year"), SONGS("Number of songs") }

/** How one song leads into the next. */
enum class Transition(val label: String) { GAPLESS("Gapless"), CROSSFADE("Crossfade") }

/** Loudness levelling from the files' ReplayGain tags. */
enum class ReplayGain(val label: String) { OFF("Off"), SONG("Prefer Song Gain"), ALBUM("Prefer Album Gain") }

/** The artist page's round play button: the last choice becomes its default. */
enum class ArtistPlay(val label: String) {
    FROM_TOP("Play From Top"),
    SHUFFLE_ALBUMS("Shuffle By Albums"),
    SHUFFLE_SONGS("Shuffle By Songs"),
}

/** The look of the app (Settings → Theme). */
enum class ThemeStyle(val label: String) { DARK("Dark"), MATERIAL("Material"), LIGHT("Light") }

/** A style's three colors (ARGB). */
data class ThemeColors(val background: Int, val accent: Int, val text: Int) {
    companion object {
        val BLUE = 0xFF0099CC.toInt()
        // Light's background is the soft white of LuxTunes' light theme (its "alt_base").
        fun defaults(style: ThemeStyle) = when (style) {
            ThemeStyle.DARK -> ThemeColors(0xFF000000.toInt(), BLUE, 0xFFFFFFFF.toInt())
            ThemeStyle.MATERIAL -> ThemeColors(0xFF303030.toInt(), BLUE, 0xFFFFFFFF.toInt())
            ThemeStyle.LIGHT -> ThemeColors(0xFFF1F5FA.toInt(), BLUE, 0xFF000000.toInt())
        }
    }
}

/** Every tab, in their first order (the user can drag them into another). */
val ALL_TABS = listOf(
    "Artists", "Album Artists", "Playlists", "Songs", "Albums", "Genres", "Composers",
    "Podcasts", "Audiobooks", "Movies", "TV Shows",
)

data class AppSettings(
    val storage: StorageChoice? = null,          // null: not chosen yet
    val tabs: List<String> = listOf("Artists", "Playlists", "Songs", "Albums"),   // shown
    val songArt: Boolean = false,                // album art in the Songs tab
    val playlistIcons: Boolean = true,
    val albumArt: Boolean = true,                // art in the Albums grid
    val artistIcons: Boolean = true,
    val albumArtistIcons: Boolean = true,
    val genreIcons: Boolean = true,
    val composerIcons: Boolean = true,
    val songAction: SongAction = SongAction.PLAY,
    val playlistAction: GroupAction = GroupAction.VIEW,
    val albumAction: GroupAction = GroupAction.VIEW,
    val genreAction: GroupAction = GroupAction.VIEW,
    val composerAction: GroupAction = GroupAction.VIEW,
    val podcastAction: GroupAction = GroupAction.VIEW,
    val audiobookAction: GroupAction = GroupAction.VIEW,
    val gridPortrait: Int = 2,
    val gridLandscape: Int = 4,
    val songSort: SongSort = SongSort.NAME,
    val artistSort: GroupSort = GroupSort.NAME,
    val albumSort: GroupSort = GroupSort.NAME,
    val songSortReverse: Boolean = false,        // "Inverse sort" in the sort menu
    val artistSortReverse: Boolean = false,
    val albumSortReverse: Boolean = false,
    // Settings → Sound
    val autoplay: Boolean = true,                // off: stop after every song
    val transition: Transition = Transition.GAPLESS,
    val crossfadeSeconds: Int = 5,               // 1-15
    val musicSpeed: Float = 1f,                  // 0.5-2.0
    val musicPitchFix: Boolean = true,           // keep the pitch when the speed changes
    val audiobookSpeed: Float = 1f,
    val audiobookPitchFix: Boolean = true,
    val podcastSpeed: Float = 1f,
    val podcastPitchFix: Boolean = true,
    val replayGain: ReplayGain = ReplayGain.OFF,
    val forceMono: Boolean = false,
    // Sound → Audio Priority
    val pauseOnCall: Boolean = true,
    val resumeAfterCall: Boolean = true,
    val pauseOnFocusLoss: Boolean = true,
    val ignoreOtherApps: Boolean = false,
    // Headset/Bluetooth
    val obeyHeadset: Boolean = true,
    val highPriority: Boolean = false,
    val playOnWired: Boolean = false,
    val playOnBluetooth: Boolean = false,
    val blockReceiverPlay: Boolean = false,
    // Operation
    val noAlbumArt: Boolean = false,
    // Tabs
    val hideSearch: Boolean = false,
    val sleepMinutes: Int = 30,                  // the sleep timer's last choice
    val launchNowPlaying: Boolean = true,        // starting a song opens Now Playing
    val smartAtEnd: Boolean = false,             // Playlists tab: smart playlists after the others
    val artistPlay: ArtistPlay = ArtistPlay.FROM_TOP,
    val albumPlay: ArtistPlay = ArtistPlay.FROM_TOP,      // the same round button on album pages
    val playlistPlay: ArtistPlay = ArtistPlay.FROM_TOP,   // … and on playlist pages
    val artistSongs: Boolean = false,            // artist pages: one list of songs, not albums
    val themeStyle: ThemeStyle = ThemeStyle.DARK,
    val autoAccent: Boolean = false,             // accent from the album art, in every style
    // Each style keeps its own colors, so switching back and forth loses nothing.
    val themeColors: Map<ThemeStyle, ThemeColors> = ThemeStyle.entries.associateWith { ThemeColors.defaults(it) },
) {
    val colors: ThemeColors get() = themeColors[themeStyle] ?: ThemeColors.defaults(themeStyle)
}

/** The app's settings, kept in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    private fun load(): AppSettings {
        val d = AppSettings()
        val saved = sp.getString("tabs", null)?.split(',')
        return AppSettings(
            storage = sp.getString("storage", null)?.let { name -> StorageChoice.entries.firstOrNull { it.name == name } },
            tabs = saved?.filter { it in ALL_TABS }?.distinct() ?: d.tabs,       // in the user's order
            songArt = sp.getBoolean("songArt", d.songArt),
            playlistIcons = sp.getBoolean("playlistIcons", d.playlistIcons),
            albumArt = sp.getBoolean("albumArt", d.albumArt),
            artistIcons = sp.getBoolean("artistIcons", d.artistIcons),
            albumArtistIcons = sp.getBoolean("albumArtistIcons", d.albumArtistIcons),
            genreIcons = sp.getBoolean("genreIcons", d.genreIcons),
            composerIcons = sp.getBoolean("composerIcons", d.composerIcons),
            songAction = enumOf(sp.getString("songAction", null), d.songAction),
            playlistAction = enumOf(sp.getString("playlistAction", null), d.playlistAction),
            albumAction = enumOf(sp.getString("albumAction", null), d.albumAction).let { if (it in Actions.BASIC) it else d.albumAction },
            genreAction = enumOf(sp.getString("genreAction", null), d.genreAction),
            composerAction = enumOf(sp.getString("composerAction", null), d.composerAction)
                .let { if (it in Actions.NO_ARTISTS) it else d.composerAction },
            podcastAction = enumOf(sp.getString("podcastAction", null), d.podcastAction),
            audiobookAction = enumOf(sp.getString("audiobookAction", null), d.audiobookAction),
            gridPortrait = sp.getInt("gridPortrait", d.gridPortrait),
            gridLandscape = sp.getInt("gridLandscape", d.gridLandscape),
            songSort = enumOf(sp.getString("songSort", null), d.songSort),
            artistSort = enumOf(sp.getString("artistSort", null), d.artistSort),
            albumSort = enumOf(sp.getString("albumSort", null), d.albumSort),
            songSortReverse = sp.getBoolean("songSortReverse", d.songSortReverse),
            artistSortReverse = sp.getBoolean("artistSortReverse", d.artistSortReverse),
            albumSortReverse = sp.getBoolean("albumSortReverse", d.albumSortReverse),
            autoplay = sp.getBoolean("autoplay", d.autoplay),
            transition = enumOf(sp.getString("transition", null), d.transition),
            crossfadeSeconds = sp.getInt("crossfadeSeconds", d.crossfadeSeconds).coerceIn(1, 15),
            musicSpeed = sp.getFloat("musicSpeed", d.musicSpeed).coerceIn(0.5f, 2f),
            musicPitchFix = sp.getBoolean("musicPitchFix", d.musicPitchFix),
            audiobookSpeed = sp.getFloat("audiobookSpeed", d.audiobookSpeed).coerceIn(0.5f, 2f),
            audiobookPitchFix = sp.getBoolean("audiobookPitchFix", d.audiobookPitchFix),
            podcastSpeed = sp.getFloat("podcastSpeed", d.podcastSpeed).coerceIn(0.5f, 2f),
            podcastPitchFix = sp.getBoolean("podcastPitchFix", d.podcastPitchFix),
            replayGain = enumOf(sp.getString("replayGain", null), d.replayGain),
            forceMono = sp.getBoolean("forceMono", d.forceMono),
            pauseOnCall = sp.getBoolean("pauseOnCall", d.pauseOnCall),
            resumeAfterCall = sp.getBoolean("resumeAfterCall", d.resumeAfterCall),
            pauseOnFocusLoss = sp.getBoolean("pauseOnFocusLoss", d.pauseOnFocusLoss),
            ignoreOtherApps = sp.getBoolean("ignoreOtherApps", d.ignoreOtherApps),
            obeyHeadset = sp.getBoolean("obeyHeadset", d.obeyHeadset),
            highPriority = sp.getBoolean("highPriority", d.highPriority),
            playOnWired = sp.getBoolean("playOnWired", d.playOnWired),
            playOnBluetooth = sp.getBoolean("playOnBluetooth", d.playOnBluetooth),
            blockReceiverPlay = sp.getBoolean("blockReceiverPlay", d.blockReceiverPlay),
            noAlbumArt = sp.getBoolean("noAlbumArt", d.noAlbumArt),
            hideSearch = sp.getBoolean("hideSearch", d.hideSearch),
            sleepMinutes = sp.getInt("sleepMinutes", d.sleepMinutes)
                .let { if (it in io.github.metricambient.luxtunes.playback.SleepTimer.CHOICES) it else d.sleepMinutes },
            launchNowPlaying = sp.getBoolean("launchNowPlaying", d.launchNowPlaying),
            smartAtEnd = sp.getBoolean("smartAtEnd", d.smartAtEnd),
            artistPlay = enumOf(sp.getString("artistPlay", null), d.artistPlay),
            albumPlay = enumOf(sp.getString("albumPlay", null), d.albumPlay),
            playlistPlay = enumOf(sp.getString("playlistPlay", null), d.playlistPlay),
            artistSongs = sp.getBoolean("artistSongs", d.artistSongs),
            themeStyle = enumOf(sp.getString("themeStyle", null), d.themeStyle),
            // (Before, each style had its own Auto: on if any was.)
            autoAccent = sp.getBoolean("autoAccent", ThemeStyle.entries.any { sp.getBoolean("theme.${it.name}.auto", false) }),
            themeColors = ThemeStyle.entries.associateWith { style ->
                val def = ThemeColors.defaults(style)
                ThemeColors(
                    background = sp.getInt("theme.${style.name}.background", def.background),
                    accent = sp.getInt("theme.${style.name}.accent", def.accent),
                    text = sp.getInt("theme.${style.name}.text", def.text),
                )
            },
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
            .putBoolean("artistIcons", s.artistIcons)
            .putBoolean("albumArtistIcons", s.albumArtistIcons)
            .putBoolean("genreIcons", s.genreIcons)
            .putBoolean("composerIcons", s.composerIcons)
            .putString("songAction", s.songAction.name)
            .putString("playlistAction", s.playlistAction.name)
            .putString("albumAction", s.albumAction.name)
            .putString("genreAction", s.genreAction.name)
            .putString("composerAction", s.composerAction.name)
            .putString("podcastAction", s.podcastAction.name)
            .putString("audiobookAction", s.audiobookAction.name)
            .putInt("gridPortrait", s.gridPortrait)
            .putInt("gridLandscape", s.gridLandscape)
            .putString("songSort", s.songSort.name)
            .putString("artistSort", s.artistSort.name)
            .putString("albumSort", s.albumSort.name)
            .putBoolean("songSortReverse", s.songSortReverse)
            .putBoolean("artistSortReverse", s.artistSortReverse)
            .putBoolean("albumSortReverse", s.albumSortReverse)
            .putBoolean("autoplay", s.autoplay)
            .putString("transition", s.transition.name)
            .putInt("crossfadeSeconds", s.crossfadeSeconds)
            .putFloat("musicSpeed", s.musicSpeed)
            .putBoolean("musicPitchFix", s.musicPitchFix)
            .putFloat("audiobookSpeed", s.audiobookSpeed)
            .putBoolean("audiobookPitchFix", s.audiobookPitchFix)
            .putFloat("podcastSpeed", s.podcastSpeed)
            .putBoolean("podcastPitchFix", s.podcastPitchFix)
            .putString("replayGain", s.replayGain.name)
            .putBoolean("forceMono", s.forceMono)
            .putBoolean("pauseOnCall", s.pauseOnCall)
            .putBoolean("resumeAfterCall", s.resumeAfterCall)
            .putBoolean("pauseOnFocusLoss", s.pauseOnFocusLoss)
            .putBoolean("ignoreOtherApps", s.ignoreOtherApps)
            .putBoolean("obeyHeadset", s.obeyHeadset)
            .putBoolean("highPriority", s.highPriority)
            .putBoolean("playOnWired", s.playOnWired)
            .putBoolean("playOnBluetooth", s.playOnBluetooth)
            .putBoolean("blockReceiverPlay", s.blockReceiverPlay)
            .putBoolean("noAlbumArt", s.noAlbumArt)
            .putBoolean("hideSearch", s.hideSearch)
            .putInt("sleepMinutes", s.sleepMinutes)
            .putBoolean("launchNowPlaying", s.launchNowPlaying)
            .putBoolean("smartAtEnd", s.smartAtEnd)
            .putString("artistPlay", s.artistPlay.name)
            .putString("albumPlay", s.albumPlay.name)
            .putString("playlistPlay", s.playlistPlay.name)
            .putBoolean("artistSongs", s.artistSongs)
            .putString("themeStyle", s.themeStyle.name)
            .putBoolean("autoAccent", s.autoAccent)
            .also { e ->
                for ((style, c) in s.themeColors) {
                    e.putInt("theme.${style.name}.background", c.background)
                    e.putInt("theme.${style.name}.accent", c.accent)
                    e.putInt("theme.${style.name}.text", c.text)
                }
            }
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
