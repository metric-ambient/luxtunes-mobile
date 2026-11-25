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

// ---- Playback ----------------------------------------------------------------------------------

/**
 * Settings → Playback: what happens when: songs ending and starting, phone calls and other
 * apps, headset buttons, and plugging in or connecting headphones, speakers and cars.
 */
@Composable
fun PlaybackSettingsScreen(nav: NavHostController) {
    val prefs = app().prefs
    val s by prefs.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Playback")
        Heading("Playing")
        ToggleRow(
            "Autoplay", "Play the next song when one ends. Off: stop after every song.", s.autoplay,
        ) { on -> prefs.update { it.copy(autoplay = on) } }
        ToggleRow(
            "Launch Now Playing View",
            "Starting a song opens Now Playing. Off: you stay where you are and the bar at the bottom shows the song.",
            s.launchNowPlaying,
        ) { on -> prefs.update { it.copy(launchNowPlaying = on) } }
        HorizontalDivider(color = Lux.panel)

        Heading("Calls and Other Apps")
        ToggleRow("Pause on Phone Call", "Pause when a call comes in or you make one.", s.pauseOnCall) { on ->
            prefs.update { it.copy(pauseOnCall = on) }
        }
        ToggleRow("Resume on Phone Call", "Carry on playing when the call ends.", s.resumeAfterCall) { on ->
            prefs.update { it.copy(resumeAfterCall = on) }
        }
        ToggleRow(
            "Pause on Focus Lost",
            "Pause when another app plays sound (a video, a voice message), and carry on after a short one.",
            s.pauseOnFocusLoss,
        ) { on -> prefs.update { it.copy(pauseOnFocusLoss = on) } }
        ToggleRow(
            "Ignore Other Apps",
            "Keep playing whatever other apps do (calls still follow the settings above).",
            s.ignoreOtherApps,
        ) { on -> prefs.update { it.copy(ignoreOtherApps = on) } }
        HorizontalDivider(color = Lux.panel)

        Heading("Headset Buttons")
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

        Heading("Wired Headphones")
        ToggleRow(
            "Begin Play on Connect",
            "Start playing when headphones are plugged in.",
            s.playOnWired,
        ) { on -> prefs.update { it.copy(playOnWired = on) } }
        HorizontalDivider(color = Lux.panel)

        Heading("Bluetooth")
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
        "LuxTunes Mobile plays the music that LuxTunes (on your computer) copies to the phone.\n\n" +
        "1. Connect the phone to the computer with a USB cable and choose \"File transfer\" on the phone.\n" +
        "2. In LuxTunes, pick the phone under Devices, choose what to sync, and sync.\n" +
        "3. Open this app. It finds the LuxTunes folder by itself and shows your library.\n\n" +
        "If the app asks where your library is, choose the phone's storage or the SD card " +
        "(whichever you synced to). You can change it later in Settings → Library & Sync.",
    "Plays, ratings and playlists go back to LuxTunes" to
        "Everything you do here is remembered: songs played and skipped, star ratings, " +
        "playlists you make or change, song details you edit, songs you add and equalizer " +
        "changes. At the next sync, LuxTunes picks it all up, so your computer library stays " +
        "up to date.\n\n" +
        "A song counts as played when it plays to the end, and as skipped when you press Next " +
        "between 2 and 20 seconds into it (from the app, the notification, headphones or the " +
        "car). Tapping quickly through songs doesn't count as skipping them.\n\n" +
        "Settings → Library & Sync shows what's waiting to go back.",
    "Playing music" to
        "Tap a song to play it (Settings → Tabs & Browsing → Default Song Action decides what plays: the " +
        "list from that song, just that song, or the list shuffled).\n\n" +
        "The floating bar near the bottom shows what's playing; tap it for Now Playing. Beside " +
        "it, Play plays whatever screen you're on (a tab, an artist, an album, a playlist or the " +
        "Radio Mix). The Shuffle switch next to it (lit while on) makes Play shuffle; each screen " +
        "remembers whether you had it on. Tapping the bar grows it into Now Playing, art and all; Back shrinks it back.\n\n" +
        "In Now Playing, tap the album art (or the empty space around it) to see the lyrics and " +
        "rate the song: the stars appear under the art when there's room, otherwise on it. " +
        "Tap again to hide them. With timed lyrics from LuxTunes, the " +
        "line being sung lights up and stays in the middle (Settings → Appearance turns this off).\n\n" +
        "Shuffle and Repeat are next to the play button. Turning Shuffle off goes back to the " +
        "original order. When they're on, they light up with a dot underneath.\n\n" +
        "Hold down Previous or Next to rewind or fast-forward through the song.\n\n" +
        "Names too long to fit (song, artist, album, a page's title) scroll across after a few " +
        "seconds, then pause and go again.\n\n" +
        "The bar at the bottom of Now Playing has Back, Radio Mix (a mix from the song), the " +
        "sleep timer, the equalizer, Add to Playlist, the queue and More (Get Info is there).\n\n" +
        "Shuffle works as in LuxTunes: albums you've heard in the last two weeks come up early " +
        "less often, and songs you heard in the last two days (or this session) wait until " +
        "everything else has played. Albums and artists are kept well apart: in a big list, " +
        "the same album doesn't come back for up to 150 songs, nor the same artist for up to 40. " +
        "Songs marked Skip When Shuffling in LuxTunes are left out. With Repeat All on, each " +
        "pass through the list is shuffled again.",
    "Finding things fast" to
        "Start scrolling a long list and a handle appears on the right edge. Drag it to jump " +
        "anywhere in the list; a big letter in the middle shows where you are (or the year, " +
        "rating or plays, depending on how the list is sorted).\n\n" +
        "At the top of each tab: Search and Sort. Search covers the whole library (the buttons " +
        "along its top narrow it to artists, albums, songs or playlists); it's also in every More " +
        "menu. It ignores case and accents (\"lumen\" finds Lümen, \"sigur ros\" finds Sigur Rós), " +
        "and finds songs with every word you type, in any of their details. Sort " +
        "has Inverse sort, to turn any order around: least played first, oldest first, Z to A. " +
        "Each tab remembers where you were in it.\n\n" +
        "The Albums tab has quick filters along the top: All, Recently Added and Compilations.\n\n" +
        "The tabs are along the bottom, with More at the bottom right (Search, Radio Mix, Sleep Timer, Add Folder, " +
        "Rescan Device, Change Theme, Check for Updates and Settings). When there are more tabs " +
        "than fit, an arrow at the edge shows which way they are; tap it or swipe the tabs.",
    "Adding songs from the phone" to
        "Settings → Library & Sync → Add Folder to Library (also in the ⋮ menus) moves the songs in " +
        "a folder on the phone into the LuxTunes folder, sorted into artist and album folders the " +
        "way LuxTunes does, and adds them to the library straight away.\n\n" +
        "To keep the originals where they are, turn on Do not move original files on import " +
        "(Settings → Library & Sync). The songs are then copied, so each one takes up twice the space.\n\n" +
        "At the next sync, LuxTunes on your computer adds them to its library too.",
    "Editing song details" to
        "Get Info (in a song's ⋮ menu, an album's or artist's ⋮ menu, Now Playing's More, or the info " +
        "button in select mode) has an Edit button. Change the name, artist, album, year, track " +
        "and disc numbers and more.\n\n" +
        "Genre, Artists and Featured Artist are pills: type a name and press Enter (or pick a " +
        "suggestion, or choose one from the library with ▾); typing ; (and , for genres) also " +
        "makes a pill, and pasting \"A; B\" makes two. ✕ removes a pill; tapping it puts it back " +
        "in the box to change. A song with several genres is listed under each in the Genres tab.\n\n" +
        "Artists is only for a song by several artists: a song by \"Lümen & Grayfield\" with " +
        "Artists Lümen and Grayfield is listed under each of them in the Artists tab, and Radio Mix " +
        "From This Artist takes it in. When each part of the Artist is already an artist of its " +
        "own, Edit suggests them.\n\n" +
        "With several songs, only the details you change are changed; a box showing Mixed means " +
        "the songs differ there.\n\n" +
        "The changes show straight away and go to LuxTunes at the next sync, which writes them " +
        "into the song files.",
    "Radio Mix" to
        "Radio Mix makes an endless mix of related songs from your library. Start one with the " +
        "Radio Mix button (two notes in a circular arrow) at the bottom of Now Playing (from the " +
        "song), an album page (from the album) or an artist page (from the artist), or from a ⋮ " +
        "menu: Start Radio Mix from Song, Album or Artist. Songs are picked by how close their " +
        "genres are, with the artist, your playlists, plays, ratings and year counting too. Near " +
        "the end, 25 more songs are added, never repeating one. Find it again at the top of the " +
        "Playlists tab or in the main More menu.\n\n" +
        "On the Radio Mix screen: ♥ likes a song on Radio Mixes like this one; ⊘ (not interested) " +
        "skips it, takes it off and keeps it off ones like it. Remove from Radio Mix counts the same " +
        "as ⊘. Tap the lit one again to undo. Refresh makes a new mix from the same start; Discover " +
        "(lit while on) prioritizes related songs you rarely or never play. More (⋮) has Save as " +
        "Playlist (it opens the new playlist so you can rename it) and Likes & Dislikes (also in " +
        "Settings).\n\n" +
        "Radio Mix is worked out on this phone. Nothing about it is sent anywhere or synced to " +
        "LuxTunes: each device has its own mix and its own likes.",
    "Queue (Up Next)" to
        "In any ⋮ menu, Add to Queue plays songs right after the current one (and after anything " +
        "you queued before). Add to End of Queue puts them at the very end.\n\n" +
        "See or change the queue with the list button at the bottom of Now Playing; Back " +
        "returns to Now Playing.",
    "Artists, albums and playlists" to
        "On an artist page, two buttons beside the picture switch with one tap: shuffling by " +
        "songs or by albums (or long-press the Shuffle switch beside the floating bar and pick " +
        "Shuffle by Song or Shuffle by Album; while Shuffle is on, it shows Song or Album), and Song View (all the songs in one list) or Album View. Each artist " +
        "remembers its own shuffle choices; Song View or Album View is kept for next time.\n\n" +
        "Back (the arrow) is at the bottom left of every page, and More (⋮) at the bottom right. " +
        "Between them, as in Now Playing: Radio Mix (a mix from the album or artist; on a playlist " +
        "it opens the Radio Mix), the sleep timer, the equalizer, Add to Playlist (for the page's " +
        "songs) and the queue. Get Info for the page's songs is in More.\n\n" +
        "Below an artist's albums, Featured On lists songs by other artists that feature them, " +
        "and Play takes them in with the artist's own songs " +
        "(Settings → Tabs & Browsing → Exclude Featured hides them).\n\n" +
        "Songs on a compilation (a soundtrack, a \"Now\" album) are listed under the compilation in " +
        "Artists: its Album Artist, or Various Artists. They also show under each of their artists " +
        "who has songs outside compilations; an artist only ever on compilations gets no entry of " +
        "their own (Search and Radio Mix still find them). Settings → Tabs & Browsing → Group Compilations " +
        "turns this off: then compilation songs are listed under each of their artists.",
    "Playlists and smart playlists" to
        "In the Playlists tab, use Create Playlist, or add songs from any ⋮ menu → Add to " +
        "playlist. When the list of playlists is longer than the window, an arrow at its top or " +
        "bottom shows there's more to scroll to.\n\nCreate Smart Playlist makes a playlist that fills itself from rules, " +
        "like \"Rating is 5 stars\" or \"Genre contains Rock\". Smart playlists work the same " +
        "way as in LuxTunes and go back to it at the next sync.",
    "Selecting several songs" to
        "Long-press a song, album or artist to start ticking, then tick the others you want (the " +
        "box at the top right ticks everything; tap it again to clear). Then add them to the " +
        "queue or a playlist, open Get Info (to edit " +
        "them together), or delete them from the phone.\n\n" +
        "Deleting only removes the file from this phone. LuxTunes decides what's on the phone: " +
        "if the song is still chosen there, the next sync copies it back.",
    "Equalizer and sound" to
        "Settings → Sound has the equalizer (with the same presets as LuxTunes), crossfade " +
        "between songs, playback speed for music, audiobooks and podcasts, ReplayGain (evens out " +
        "loudness) and mono.\n\n" +
        "The equalizer button at the bottom of Now Playing switches presets quickly.\n\n" +
        "Set EQ… in a song's, album's, artist's or playlist's ⋮ menu gives those songs their own " +
        "preset (as LuxTunes does). It goes to LuxTunes at the next sync.\n\n" +
        "What happens during phone calls and when another app plays sound (pause, carry on " +
        "afterwards, or ignore other apps) is in Settings → Playback → Calls and Other Apps.",
    "Making it look your way" to
        "Settings → Appearance: choose Dark, Material or Light, and your own background, accent and " +
        "text colors. Auto Accent takes the accent from the album art. Further down: no album " +
        "art, and highlighting timed lyrics.\n\n" +
        "Settings → Tabs & Browsing: choose which tabs show and what a tap does. In the overview, " +
        "long-press a tab at the bottom and drag it where you want it (hold it near an edge to " +
        "scroll the bar). It also has smart playlists at the end of the list, grouping " +
        "compilations and hiding Featured On.\n\n" +
        "Settings → Playback: Autoplay, and whether Now Playing opens when a song starts.",
    "Sleep timer" to
        "Tap the moon at the bottom of Now Playing (or choose Sleep Timer… in a ⋮ menu) and pick " +
        "how long: 15 minutes to 8 hours. When the time is up, the music fades out and pauses; " +
        "Play carries on from the same spot.\n\n" +
        "While it runs, the moon shows the time left. Tap it to change the time or turn it off.",
    "Headphones and Bluetooth" to
        "Settings → Playback: use headset buttons, start playing when headphones or " +
        "Bluetooth connect, and stop a car from starting music by itself.",
    "Android Auto" to
        "With the phone connected to a car (or Android Auto on the phone), LuxTunes shows on the " +
        "car's screen with four tabs: Playlists, Artists, Albums and Radio Mix. Pick a song in a " +
        "list and the list plays from there; an artist has Shuffle at the top; the Radio Mix tab " +
        "can start a new mix from the song playing. Search, and \"play …\" by voice, find artists, " +
        "albums and songs.\n\n" +
        "Android Auto only lists apps from the Play Store unless you allow others: in the Android " +
        "Auto settings on the phone, tap Version several times to turn on developer settings, " +
        "then in its ⋮ menu choose Developer settings and turn on Unknown sources.\n\n" +
        "Only car apps and the phone's own media controls can browse your library this way.",
    "Movies and TV shows" to
        "Turn on the Movies and TV Shows tabs in Settings → Tabs & Browsing. Videos play full screen: tap " +
        "the picture to show or hide the controls.",
    "Updates" to
        "Check for Updates is in the main More menu and in Settings. The app asks before " +
        "going online, downloads the new version from the project's GitHub page, and Android " +
        "asks you to confirm installing it.",
    "Something's not right" to
        "• No music: make sure the sync finished, then use Rescan Device in the main More menu or Settings.\n" +
        "• Plays or ratings didn't reach LuxTunes: open the app once after listening, before syncing.\n" +
        "• No album art: art arrives with the sync; until then, the art inside the song file is used.\n" +
        "• The app can't see the library: Settings → Library & Sync → Change Library Location.",
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
    "Genre data: MusicBrainz and Wikidata" to "CC0 (public domain)",
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
        Heading("LuxTunes Mobile")
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
