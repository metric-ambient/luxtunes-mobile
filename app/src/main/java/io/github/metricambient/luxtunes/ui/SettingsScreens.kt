package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.BuildConfigInfo
import io.github.metricambient.luxtunes.data.ALL_TABS
import io.github.metricambient.luxtunes.data.Actions
import io.github.metricambient.luxtunes.data.AppSettings
import io.github.metricambient.luxtunes.data.GroupAction
import io.github.metricambient.luxtunes.data.LuxFolder
import io.github.metricambient.luxtunes.data.SongAction
import io.github.metricambient.luxtunes.data.StorageChoice
import io.github.metricambient.luxtunes.data.Updater
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun ScreenTitle(nav: NavHostController, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text)
        }
        Text(title, color = Lux.text, fontSize = 24.sp, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (onClick != null) Lux.icon else Lux.faint, modifier = Modifier.size(40.dp))
        Column(Modifier.padding(start = 20.dp)) {
            Text(title, color = if (onClick != null) Lux.text else Lux.dim, fontSize = 18.sp)
            Text(subtitle, color = Lux.dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---- Settings ---------------------------------------------------------------------------

@Composable
fun SettingsScreen(nav: NavHostController) {
    val app = app()
    val context = LocalContext.current
    val library by app.repository.state.collectAsState()
    val settings by app.prefs.settings.collectAsState()
    // Nine rows, so the list fits a normal phone without scrolling (it scrolls if it has to).
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle(nav, "Settings") }
        item { SettingRow(Icons.Filled.GraphicEq, "Sound", "Equalizer, crossfade, speed, ReplayGain") { nav.go(Routes.SETTINGS_SOUND) } }
        item { SettingRow(Icons.Filled.Headphones, "Playback", "Autoplay, calls, headphones, Bluetooth") { nav.go(Routes.SETTINGS_PLAYBACK) } }
        item { SettingRow(Icons.Filled.Palette, "Appearance", "${settings.themeStyle.label} · colors, album art, lyrics") { nav.go(Routes.SETTINGS_THEME) } }
        item { SettingRow(Icons.Filled.Tab, "Tabs & Browsing", "Tabs, what a tap does, compilations, Featured On") { nav.go(Routes.SETTINGS_TABS) } }
        item {
            SettingRow(
                Icons.Filled.LibraryMusic, "Library & Sync",
                "${library.libraryName.ifEmpty { "LuxTunes" }}: ${plural(library.tracks.size, "song")} · Radio Mix likes",
            ) { nav.go(Routes.SETTINGS_LIBRARY) }
        }
        item {
            SettingRow(Icons.Filled.Sync, "Rescan Device", "Look for a new sync from LuxTunes now") {
                Notice.show("Looking for a new sync…")
                app.repository.refreshAsync()
            }
        }
        item { UpdateRow() }
        item { SettingRow(Icons.AutoMirrored.Filled.HelpOutline, "Help", "How to use the app") { nav.go(Routes.SETTINGS_HELP) } }
        item { SettingRow(Icons.Filled.Info, "About", "Version ${BuildConfigInfo.versionName(context)} · licenses, privacy") { nav.go(Routes.SETTINGS_ABOUT) } }
    }
}

// ---- My Library ------------------------------------------------------------------------------

/** Settings → Library & Sync: where the library is, syncing, what's waiting to go back, Radio Mix likes. */
@Composable
fun LibrarySettingsScreen(nav: NavHostController) {
    val app = app()
    val context = LocalContext.current
    val library by app.repository.state.collectAsState()
    val settings by app.prefs.settings.collectAsState()
    val canChoose = LuxFolder.hasRemovable(context)
    val storage = when (settings.storage) {
        StorageChoice.REMOVABLE -> if (canChoose) "SD card" else "SD card (not in the phone)"
        StorageChoice.INTERNAL -> "Internal storage"
        null -> "Any"
    }
    val written = when {
        library.changesError != null -> "Couldn't write it (${library.changesError})"
        library.changesWritten > 0 -> "Written " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(library.changesWritten))
        else -> "Not written since the app started"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Library & Sync")
        InfoRow(
            "Library",
            "${library.libraryName.ifEmpty { "LuxTunes" }}: ${plural(library.tracks.size, "song")}" +
                if (library.libraryNamePending) "\nNew name waiting to go to LuxTunes" else "",
        )
        InfoRow("Storage", storage)
        InfoRow("Folder", library.folder?.path ?: "Not found")
        InfoRow("Last synced", library.syncedAt?.let { niceDate(it) } ?: "Never")
        InfoRow(
            "Waiting to go to LuxTunes",
            "${plural(library.pendingCount, "song")}, ${plural(library.pendingPlaylists, "playlist change")}",
        )
        InfoRow(
            "Sync format",
            "${library.formatVersion}" + if (library.formatVersion < 2) " (playlists go back with LuxTunes 0.6.0+)" else "",
        )
        InfoRow("changes.json", written)
        if (library.transferMode) InfoRow("USB file transfer", "On")
        Spacer(Modifier.height(16.dp))
        ButtonWithHelp(
            "Rename Library…",
            "Gives the library a new name. It shows here at once and goes to LuxTunes on your " +
                "computer at the next sync. LuxTunes (0.9.4 or later) asks there whether to use it or keep its own name.",
        ) { Dialogs.show(DialogRequest.RenameLibrary(library.libraryName.ifEmpty { "LuxTunes" })) }
        ButtonWithHelp(
            "Change Library Location",
            if (canChoose) "Choose whether the LuxTunes folder is on the phone's own storage or the SD card. " +
                "Choosing one looks for the library there again."
            else "There's no SD card in the phone, so the library is on its own storage. Choosing it looks for " +
                "the library there again (for example after LuxTunes switched to syncing to internal storage).",
        ) { Dialogs.show(DialogRequest.ChooseStorage(firstTime = false)) }
        ButtonWithHelp(
            "Add Folder to Library…",
            "Moves the songs in a folder on the phone into the LuxTunes folder (or copies them, with " +
                "\"Do not move original files on import\" on), sorted by artist and album as LuxTunes " +
                "does, and adds them to the library. They go to LuxTunes on your computer at the next sync.",
        ) { AddFolder.show() }
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            "Do not move original files on import",
            "Add Folder copies the songs and leaves the originals where they are. This duplicates " +
                "each file, so the songs take up twice the space.",
            settings.keepOriginals,
        ) { on -> app.prefs.update { it.copy(keepOriginals = on) } }
        HorizontalDivider(color = Lux.panel)
        Heading("Radio Mix")
        ChoiceRow("Radio Mix Likes & Dislikes", "Songs you liked or turned down on a Radio Mix (kept on this phone only)") {
            nav.go(Routes.RADIO_LIKES)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** A button with a round ? at the right edge that explains it. */
@Composable
private fun ButtonWithHelp(label: String, help: String, enabled: Boolean = true, onClick: () -> Unit) {
    var showHelp by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = Lux.accent, contentColor = Lux.onAccent),
        ) { Text(label) }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { showHelp = true }) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, "About $label", tint = Lux.icon)
        }
    }
    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false },
        title = { Text(label.removeSuffix("…")) },
        text = { Text(help) },
        confirmButton = { TextButton(onClick = { showHelp = false }) { Text("OK") } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, color = Lux.dim, fontSize = 14.sp)
        Text(value, color = Lux.text, fontSize = 17.sp)
    }
}

// ---- Check for Updates ---------------------------------------------------------------------

internal sealed interface UpdateStep {
    data object Idle : UpdateStep
    data object Ask : UpdateStep                         // "this goes online: OK?"
    data object Checking : UpdateStep
    data object UpToDate : UpdateStep
    data class Found(val release: Updater.Release) : UpdateStep
    data class Downloading(val progress: Float) : UpdateStep
    data class Ready(val apk: File) : UpdateStep
    data class Failed(val message: String) : UpdateStep
}

/** Check for Updates can start from Settings or from the overview's menu. */
object UpdateCheck {
    internal val state = mutableStateOf<UpdateStep>(UpdateStep.Idle)

    /** Ask first (it goes online), then check, download and install. */
    fun start() {
        if (state.value == UpdateStep.Idle) state.value = UpdateStep.Ask
    }
}

/** Settings → Check for Updates. */
@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    SettingRow(Icons.Filled.SystemUpdate, "Check for Updates", "This copy: ${BuildConfigInfo.versionName(context)}") {
        UpdateCheck.start()
    }
}

/** The Check for Updates pop-ups (shown over every screen). Asks before going online, and again before downloading. */
@Composable
fun UpdateDialogs() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by UpdateCheck.state
    val installed = remember { runCatching { Updater.installedBuild(context) }.getOrDefault(0) }

    val close = { step = UpdateStep.Idle }
    when (val s = step) {
        UpdateStep.Idle -> {}
        UpdateStep.Ask -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Check for updates?") },
            text = {
                Text(
                    "This connects to the internet: it asks github.com for the latest release of " +
                        "LuxTunes. Nothing about you or your music is sent."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    step = UpdateStep.Checking
                    scope.launch {
                        step = try {
                            val release = Updater.latest()
                            when {
                                release == null -> UpdateStep.Failed(
                                    "No release found on GitHub yet."
                                )
                                release.build > installed -> UpdateStep.Found(release)
                                else -> UpdateStep.UpToDate
                            }
                        } catch (e: Exception) {
                            UpdateStep.Failed("Couldn't reach GitHub: ${e.message ?: e.javaClass.simpleName}")
                        }
                    }
                }) { Text("Check") }
            },
            dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        )
        UpdateStep.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Checking…") },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        UpdateStep.UpToDate -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Up to date") },
            text = { Text("You have the latest version.") },
            confirmButton = { TextButton(onClick = close) { Text("OK") } },
        )
        is UpdateStep.Found -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Update available") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${s.release.tag} (build ${s.release.build}) is available; you have build $installed.")
                    if (s.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.release.notes, color = Lux.dim, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    val size = if (s.release.size > 0) "%.1f MB".format(s.release.size / 1048576.0) else "About 10 MB"
                    Text("Download it now? ($size from github.com.)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    step = UpdateStep.Downloading(0f)
                    scope.launch {
                        step = try {
                            UpdateStep.Ready(Updater.download(context, s.release) { p -> step = UpdateStep.Downloading(p) })
                        } catch (e: Exception) {
                            UpdateStep.Failed("Download failed: ${e.message ?: e.javaClass.simpleName}")
                        }
                    }
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = close) { Text("Not now") } },
        )
        is UpdateStep.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading…") },
            text = { LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        is UpdateStep.Ready -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Ready to install") },
            text = {
                Text(
                    if (Updater.canInstall(context)) "Android will ask you to confirm the update."
                    else "First allow LuxTunes to install apps (\"Install unknown apps\"), " +
                        "then come back and tap Install."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Updater.canInstall(context)) { Updater.install(context, s.apk); close() }
                    else Updater.openInstallPermission(context)
                }) { Text(if (Updater.canInstall(context)) "Install" else "Allow") }
            },
            dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        )
        is UpdateStep.Failed -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Check for Updates") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = close) { Text("OK") } },
        )
    }
}

/** A setting with a title, a line about it and an on/off switch. */
@Composable
internal fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Lux.text, fontSize = 18.sp)
            Text(subtitle, color = Lux.dim, fontSize = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Lux.onAccent, checkedTrackColor = Lux.accent,
                uncheckedThumbColor = Lux.dim, uncheckedTrackColor = Lux.panel, uncheckedBorderColor = Lux.dim,
            ),
        )
    }
}

// ---- Tabs ---------------------------------------------------------------------------------

@Composable
fun TabsSettingsScreen(nav: NavHostController) {
    val app = app()
    val prefs = app.prefs
    val s by prefs.settings.collectAsState()

    fun showTab(name: String, on: Boolean) = prefs.update { cur ->
        // Shown again: back at the end of the row (drag it elsewhere in the overview).
        val tabs = if (on) (if (name in cur.tabs) cur.tabs else cur.tabs + name) else cur.tabs - name
        cur.copy(tabs = tabs.ifEmpty { cur.tabs })          // at least one tab stays
    }

    fun chooseGroup(current: GroupAction, options: List<GroupAction>, set: (AppSettings, GroupAction) -> AppSettings) {
        Dialogs.show(DialogRequest.Choose("Select Action", options.map { it.label }, options.indexOf(current)) { i ->
            prefs.update { set(it, options[i]) }
        })
    }

    fun chooseColumns(title: String, current: Int, most: Int, set: (AppSettings, Int) -> AppSettings) {
        val options = (1..most).toList()
        Dialogs.show(DialogRequest.Choose(title, options.map { "$it" }, options.indexOf(current)) { i ->
            prefs.update { set(it, options[i]) }
        })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Tabs & Browsing")
        Heading("Browsing")
        ToggleRow(
            "Group Compilations",
            "In Artists, a compilation's songs are listed under the compilation (its Album Artist, or " +
                "Various Artists), not under each song's own artist.",
            s.groupCompilations,
        ) { on ->
            prefs.update { it.copy(groupCompilations = on) }
            app.repository.refreshAsync()            // the lists are rebuilt with the new setting
        }
        ToggleRow(
            "Smart Playlists at end of list",
            "In the Playlists tab, show smart playlists after your other playlists.",
            s.smartAtEnd,
        ) { on -> prefs.update { it.copy(smartAtEnd = on) } }
        ToggleRow(
            "Exclude Featured",
            "Excludes Featured On section in the Artist view.",
            s.excludeFeatured,
        ) { on -> prefs.update { it.copy(excludeFeatured = on) } }
        HorizontalDivider(color = Lux.panel)

        TabSection("Songs", "Display songs tab", "Songs" in s.tabs, { showTab("Songs", it) }) {
            CheckRow("Display album art", s.songArt) { v -> prefs.update { it.copy(songArt = v) } }
            ChoiceRow("Default Song Action", s.songAction.label) {
                val options = SongAction.entries.toList()
                Dialogs.show(DialogRequest.Choose("Select Action", options.map { it.label }, options.indexOf(s.songAction)) { i ->
                    prefs.update { it.copy(songAction = options[i]) }
                })
            }
        }
        TabSection("Playlists", "Display playlists tab", "Playlists" in s.tabs, { showTab("Playlists", it) }) {
            CheckRow("Display playlist icon", s.playlistIcons) { v -> prefs.update { it.copy(playlistIcons = v) } }
            ChoiceRow("Default Playlist Action", s.playlistAction.label) {
                chooseGroup(s.playlistAction, Actions.ALL) { cur, a -> cur.copy(playlistAction = a) }
            }
        }
        TabSection("Albums", "Display albums tab", "Albums" in s.tabs, { showTab("Albums", it) }) {
            ChoiceRow("Number of portrait grid items", "${s.gridPortrait}") {
                chooseColumns("Portrait grid items", s.gridPortrait, 4) { cur, n -> cur.copy(gridPortrait = n) }
            }
            ChoiceRow("Number of landscape grid items", "${s.gridLandscape}") {
                chooseColumns("Landscape grid items", s.gridLandscape, 6) { cur, n -> cur.copy(gridLandscape = n) }
            }
            CheckRow("Display album art", s.albumArt) { v -> prefs.update { it.copy(albumArt = v) } }
            ChoiceRow("Default Album Action", s.albumAction.label) {
                chooseGroup(s.albumAction, Actions.BASIC) { cur, a -> cur.copy(albumAction = a) }
            }
        }
        TabSection("Artists", "Display artists tab", "Artists" in s.tabs, { showTab("Artists", it) }) {
            CheckRow("Display artist icon", s.artistIcons) { v -> prefs.update { it.copy(artistIcons = v) } }
        }
        TabSection("Album Artists", "Display album artists tab", "Album Artists" in s.tabs, { showTab("Album Artists", it) }) {
            CheckRow("Display album artist icon", s.albumArtistIcons) { v -> prefs.update { it.copy(albumArtistIcons = v) } }
        }
        TabSection("Genres", "Display genres tab", "Genres" in s.tabs, { showTab("Genres", it) }) {
            CheckRow("Display genre icon", s.genreIcons) { v -> prefs.update { it.copy(genreIcons = v) } }
            ChoiceRow("Default Genre Action", s.genreAction.label) {
                chooseGroup(s.genreAction, Actions.ALL) { cur, a -> cur.copy(genreAction = a) }
            }
        }
        TabSection("Composers", "Display composers tab", "Composers" in s.tabs, { showTab("Composers", it) }) {
            CheckRow("Display composer icon", s.composerIcons) { v -> prefs.update { it.copy(composerIcons = v) } }
            ChoiceRow("Default Composer Action", s.composerAction.label) {
                chooseGroup(s.composerAction, Actions.NO_ARTISTS) { cur, a -> cur.copy(composerAction = a) }
            }
        }
        TabSection("Podcasts", "Display podcasts tab", "Podcasts" in s.tabs, { showTab("Podcasts", it) }) {
            ChoiceRow("Default Podcast Action", s.podcastAction.label) {
                chooseGroup(s.podcastAction, Actions.BASIC) { cur, a -> cur.copy(podcastAction = a) }
            }
        }
        TabSection("Audiobooks", "Display audiobooks tab", "Audiobooks" in s.tabs, { showTab("Audiobooks", it) }) {
            ChoiceRow("Default Audiobook Action", s.audiobookAction.label) {
                chooseGroup(s.audiobookAction, Actions.BASIC) { cur, a -> cur.copy(audiobookAction = a) }
            }
        }
        TabSection("Movies", "Display Movies tab", "Movies" in s.tabs, { showTab("Movies", it) })
        TabSection("TV Shows", "Display TV Shows tab", "TV Shows" in s.tabs, { showTab("TV Shows", it) })
        Spacer(Modifier.height(24.dp))
    }
}

/** One tab's settings: a heading, "Display … tab", then its own options. */
@Composable
private fun TabSection(title: String, label: String, shown: Boolean, onShow: (Boolean) -> Unit, content: @Composable () -> Unit = {}) {
    Heading(title)
    CheckRow(label, shown, onShow)
    content()
    HorizontalDivider(color = Lux.panel)
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Lux.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Checkbox(
            checked = checked, onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = Lux.accent),
        )
    }
}

@Composable
internal fun ChoiceRow(label: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = Lux.text, fontSize = 18.sp)
        Text(value, color = Lux.dim, fontSize = 14.sp)
    }
}

// ---- About ---------------------------------------------------------------------------------

@Composable
fun AboutScreen(nav: NavHostController) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "About")
        Column(Modifier.padding(20.dp)) {
            Text("LuxTunes Mobile ${BuildConfigInfo.versionName(context)}", color = Lux.text, fontSize = 20.sp)
            Text("Signed by ${BuildConfigInfo.signer(context)}", color = Lux.dim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Plays the music LuxTunes syncs to this phone, and sends plays, skips and ratings " +
                    "back at the next sync.\n\n" +
                    "Written entirely by an AI, Claude Opus 5.5 (\"vibe-coded\"). For copyright purposes, " +
                    "metric-ambient is the author.\n\n" +
                    "Provided \"as is\", without warranty of any kind. Keep backups of your music.\n\n" +
                    "License: GNU General Public License, version 3 or later.",
                color = Lux.dim, fontSize = 15.sp,
            )
        }
        ChoiceRow("Open-Source Licenses", "The licenses of LuxTunes and the libraries it uses") {
            nav.go(Routes.SETTINGS_LICENSES)
        }
        HorizontalDivider(color = Lux.panel)
        Heading("Privacy")
        Text(
            "LuxTunes Mobile collects nothing about you. It has no accounts, no ads, no " +
                "analytics and no tracking. Your music, ratings, plays and playlists stay on this " +
                "phone and go only to your own computer, over the USB cable, when you sync.\n\n" +
                "The app goes online only when you choose Check for Updates (and it asks first). " +
                "It then asks github.com for the newest version, and downloads it if you say so. " +
                "Like any website, GitHub sees your internet address when that happens; nothing " +
                "about you or your music is sent.\n\n" +
                "Radio Mix is worked out on the phone, with a genre list that comes inside the app. The " +
                "mix and your Radio Mix likes and dislikes are kept in the app's own private storage, " +
                "never in the shared LuxTunes folder, and are never sent anywhere or synced.\n\n" +
                "Genre data: MusicBrainz and Wikidata (CC0).",
            color = Lux.dim, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}
