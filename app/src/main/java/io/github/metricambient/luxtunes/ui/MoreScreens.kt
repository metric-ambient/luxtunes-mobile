package io.github.metricambient.luxtunes.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

// ---- Headset/Bluetooth ----------------------------------------------------------------------

@Composable
fun HeadsetSettingsScreen(nav: NavHostController) {
    val prefs = app().prefs
    val s by prefs.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Headset/Bluetooth")
        ToggleRow(
            "Obey Headset Commands",
            "Play, pause and skip with the buttons on headphones, earbuds and Bluetooth devices.",
            s.obeyHeadset,
        ) { on -> prefs.update { it.copy(obeyHeadset = on) } }
        ToggleRow(
            "High Priority Mode",
            "Ask Android to send headset buttons to LuxTunes even when it isn't playing: " +
                "pressing play picks up where you left off.",
            s.highPriority,
        ) { on -> prefs.update { it.copy(highPriority = on) } }
        HorizontalDivider(color = Lux.panel)

        Heading("Wired Connection Settings")
        ToggleRow(
            "Begin Play on Connect",
            "Start playing when headphones are plugged in.",
            s.playOnWired,
        ) { on -> prefs.update { it.copy(playOnWired = on) } }
        HorizontalDivider(color = Lux.panel)

        Heading("Bluetooth Connection Settings")
        ToggleRow(
            "Begin Play on Bluetooth Connect",
            "Start playing when Bluetooth headphones, a speaker or a car connects.",
            s.playOnBluetooth,
        ) { on -> prefs.update { it.copy(playOnBluetooth = on) } }
        ToggleRow(
            "Block Receiver Auto Play",
            "Ignore the \"play\" a car or speaker sends by itself just after it connects.",
            s.blockReceiverPlay,
        ) { on -> prefs.update { it.copy(blockReceiverPlay = on) } }
        Text(
            "Connecting only starts music while LuxTunes is running (open, or in the background). " +
                "Android doesn't let apps wake up just because headphones were connected.",
            color = Lux.dim, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
        )
    }
}

// ---- Help -------------------------------------------------------------------------------------

private val HELP: List<Pair<String, String>> = listOf(
    "Getting your music onto the phone" to
        "LuxTunes for Android plays the music that LuxTunes (on your computer) copies to the phone.\n\n" +
        "1. Connect the phone to the computer with a USB cable and choose \"File transfer\" on the phone.\n" +
        "2. In LuxTunes, pick the phone under Devices, choose what to sync, and sync.\n" +
        "3. Open this app. It finds the LuxTunes folder by itself and shows your library.\n\n" +
        "If the app asks where your library is, choose the phone's storage or the SD card " +
        "(whichever you synced to). You can change it later in Settings → My Library.",
    "Plays, ratings and playlists go back to LuxTunes" to
        "Everything you do here is remembered: songs played and skipped, star ratings, and " +
        "playlists you make or change. At the next sync, LuxTunes picks it all up, so your " +
        "computer library stays up to date.\n\n" +
        "Settings → My Library shows what's waiting to go back.",
    "Playing music" to
        "Tap a song to play it (Settings → Tabs → Default Song Action decides what plays: the " +
        "list from that song, just that song, or the list shuffled).\n\n" +
        "The bar at the bottom shows what's playing; tap it for Now Playing. There, tap the " +
        "album art to rate the song and see its lyrics.\n\n" +
        "Shuffle and Repeat are next to the play button. Turning Shuffle off goes back to the " +
        "original order.",
    "Finding things fast" to
        "Start scrolling a long list and a handle appears on the right edge. Drag it to jump " +
        "anywhere in the list; a big letter in the middle shows where you are (or the year, " +
        "rating or plays, depending on how the list is sorted).\n\n" +
        "The sort button at the bottom has Inverse sort, to turn any order around: least played " +
        "first, oldest first, Z to A. Each tab remembers where you were in it.",
    "Adding songs from the phone" to
        "Settings → My Library → Add Folder to Library (also in the ⋮ menus) moves the songs in " +
        "a folder on the phone into the LuxTunes folder, sorted into artist and album folders the " +
        "way LuxTunes does, and adds them to the library straight away.\n\n" +
        "To keep the originals where they are, turn on Do not move original files on import " +
        "(Settings → My Library). The songs are then copied, so each one takes up twice the space.\n\n" +
        "At the next sync, LuxTunes on your computer adds them to its library too.",
    "Editing song details" to
        "Get Info (in a song's ⋮ menu, an album's or artist's ⋮ menu, Now Playing, or the info " +
        "button in select mode) has an Edit button. Change the name, artist, album, genre, year, " +
        "track and disc numbers and more.\n\n" +
        "With several songs, only the details you change are changed; a box showing Mixed means " +
        "the songs differ there.\n\n" +
        "The changes show straight away and go to LuxTunes at the next sync, which writes them " +
        "into the song files.",
    "Queue (Up Next)" to
        "In any ⋮ menu, Add to Queue plays songs right after the current one (and after anything " +
        "you queued before). Add to End of Queue puts them at the very end.\n\n" +
        "See or change the queue with the list button at the top of Now Playing.",
    "Artists, albums and the round button" to
        "On an artist page, the round button opens three choices: Play From Top, Shuffle By " +
        "Albums and Shuffle By Songs. The one you pick becomes the button's default.\n\n" +
        "The ⋮ at the top of an artist page can show all the songs in one list (Show as Songs).",
    "Playlists and smart playlists" to
        "In the Playlists tab, use Create Playlist, or add songs from any ⋮ menu → Add to " +
        "playlist.\n\nCreate Smart Playlist makes a playlist that fills itself from rules, " +
        "like \"Rating is 5 stars\" or \"Genre contains Rock\". Smart playlists work the same " +
        "way as in LuxTunes and go back to it at the next sync.",
    "Selecting several songs" to
        "Use the select button in the bar at the bottom of a list, tick the songs you want, " +
        "then add them to the queue or a playlist, or delete them from the phone.\n\n" +
        "Deleting only removes the file from this phone. LuxTunes decides what's on the phone: " +
        "if the song is still chosen there, the next sync copies it back.",
    "Equalizer and sound" to
        "Settings → Sound has the equalizer (with the same presets as LuxTunes), crossfade " +
        "between songs, playback speed for music, audiobooks and podcasts, ReplayGain (evens out " +
        "loudness) and mono.\n\n" +
        "The equalizer button at the top of Now Playing switches presets quickly.",
    "Making it look your way" to
        "Settings → Theme: choose Dark, Material or Light, and your own background, accent and " +
        "text colors. Auto Accent takes the accent from the album art.\n\n" +
        "Settings → Tabs: choose which tabs show and what a tap does. In the overview, " +
        "long-press a tab and drag it to move it.",
    "Sleep timer" to
        "Tap the moon at the top of Now Playing (or choose Sleep Timer… in a ⋮ menu) and pick " +
        "how long: 15 minutes to 8 hours. When the time is up, the music fades out and pauses; " +
        "Play carries on from the same spot.\n\n" +
        "While it runs, the moon shows the time left. Tap it to change the time or turn it off.",
    "Headphones and Bluetooth" to
        "Settings → Headset/Bluetooth: use headset buttons, start playing when headphones or " +
        "Bluetooth connect, and stop a car from starting music by itself.",
    "Movies and TV shows" to
        "Turn on the Movies and TV Shows tabs in Settings → Tabs. Videos play full screen: tap " +
        "the picture to show or hide the controls.",
    "Updates" to
        "Check for Updates is in Settings. The app asks before " +
        "going online, downloads the new version from the project's GitHub page, and Android " +
        "asks you to confirm installing it.",
    "Something's not right" to
        "• No music: make sure the sync finished, then use Rescan Device in Settings or the ⋮ menu.\n" +
        "• Plays or ratings didn't reach LuxTunes: open the app once after listening, before syncing.\n" +
        "• No album art: art arrives with the sync; until then, the art inside the song file is used.\n" +
        "• The app can't see the library: Settings → My Library → Change Library Location.",
)

/** Settings → Help: short guides; tap one to open it. */
@Composable
fun HelpScreen(nav: NavHostController) {
    var open by rememberSaveable { mutableStateOf(-1) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Help")
        HELP.forEachIndexed { i, (title, body) ->
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                Row(
                    Modifier.fillMaxWidth().clickable { open = if (open == i) -1 else i }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, color = Lux.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Icon(if (open == i) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Lux.icon)
                }
                if (open == i) {
                    Text(body, color = Lux.dim, fontSize = 16.sp, lineHeight = 22.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
                }
            }
            HorizontalDivider(color = Lux.panel)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---- Licenses ---------------------------------------------------------------------------------

/** The open-source parts the app is built from, with their licenses. */
private val LIBRARIES = listOf(
    "AndroidX: Jetpack Compose, Material 3 and icons, Activity, Core, Lifecycle, Navigation" to "Apache License 2.0",
    "AndroidX Media3 (ExoPlayer, session, extractor, UI)" to "Apache License 2.0",
    "Kotlin standard library and kotlinx.coroutines" to "Apache License 2.0",
    "Coil (image loading), with OkHttp and Okio" to "Apache License 2.0",
    "Guava" to "Apache License 2.0",
)

@Composable
fun LicensesScreen(nav: NavHostController) {
    var showing by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val text = remember(showing) {
        showing?.let { file ->
            runCatching { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }.getOrNull()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Open-Source Licenses")
        Heading("LuxTunes for Android")
        Text(
            "GNU General Public License, version 3 or later. You may share and change it under " +
                "those terms; it comes with no warranty.",
            color = Lux.dim, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp),
        )
        ChoiceRow("Read the GNU GPL 3.0", "The full license text") { showing = "gpl-3.0.txt" }
        HorizontalDivider(color = Lux.panel)
        Heading("Built with")
        for ((name, license) in LIBRARIES) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(name, color = Lux.text, fontSize = 16.sp)
                Text(license, color = Lux.dim, fontSize = 14.sp)
            }
        }
        ChoiceRow("Read the Apache License 2.0", "The full license text") { showing = "apache-2.0.txt" }
        if (text != null) {
            HorizontalDivider(color = Lux.panel)
            Text(
                text, color = Lux.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
