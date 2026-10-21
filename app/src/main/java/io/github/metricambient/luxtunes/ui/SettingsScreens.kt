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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (onClick != null) Lux.icon else Lux.faint, modifier = Modifier.size(48.dp))
        Column(Modifier.padding(start = 20.dp)) {
            Text(title, color = if (onClick != null) Lux.text else Lux.dim, fontSize = 19.sp)
            Text(subtitle, color = Lux.dim, fontSize = 14.sp)
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
    val later = "Coming in a later version"
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle(nav, "Settings") }
        item { SettingRow(Icons.Filled.Album, "Sound", "Equalizer, crossfade, speed, ReplayGain") { nav.go(Routes.SETTINGS_SOUND) } }
        item { SettingRow(Icons.Filled.Build, "Operation", "Now Playing, playlists") { nav.go(Routes.SETTINGS_OPERATION) } }
        item { SettingRow(Icons.Filled.Folder, "Tabs", "Choose which tabs to see, and what a tap does") { nav.go(Routes.SETTINGS_TABS) } }
        item { SettingRow(Icons.Filled.Palette, "Theme", "${settings.themeStyle.label} · style and colors") { nav.go(Routes.SETTINGS_THEME) } }
        item { SettingRow(Icons.Filled.Headphones, "Headset/Bluetooth", "Set volume on connect and more · $later") }
        item {
            SettingRow(
                Icons.Filled.LibraryMusic, "My Library",
                "${library.libraryName.ifEmpty { "LuxTunes" }}: ${plural(library.tracks.size, "song")}",
            ) { nav.go(Routes.SETTINGS_LIBRARY) }
        }
        item { SettingRow(Icons.Filled.Refresh, "Rescan Device", "Look for a new sync from LuxTunes now") { app.repository.refreshAsync() } }
        item { UpdateRow() }
        item { SettingRow(Icons.Filled.Info, "About / Legal", "Version, license") { nav.go(Routes.SETTINGS_ABOUT) } }
    }
}

// ---- My Library ------------------------------------------------------------------------------

/** Settings → My Library: where the library is, when it was synced, what's waiting to go back. */
@Composable
fun LibrarySettingsScreen(nav: NavHostController) {
    val app = app()
    val context = LocalContext.current
    val library by app.repository.state.collectAsState()
    val settings by app.prefs.settings.collectAsState()
    val storage = when (settings.storage) {
        StorageChoice.REMOVABLE -> "SD card"
        StorageChoice.INTERNAL -> "Internal storage"
        null -> "Any"
    }
    val written = when {
        library.changesError != null -> "Couldn't write it (${library.changesError})"
        library.changesWritten > 0 -> "Written " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(library.changesWritten))
        else -> "Not written since the app started"
    }
    val canChoose = LuxFolder.hasRemovable(context)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "My Library")
        InfoRow("Library", "${library.libraryName.ifEmpty { "LuxTunes" }}: ${plural(library.tracks.size, "song")}")
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
        Button(
            onClick = { Dialogs.show(DialogRequest.ChooseStorage(firstTime = false)) },
            enabled = canChoose,
            colors = ButtonDefaults.buttonColors(containerColor = Lux.accent, contentColor = Lux.onAccent),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text("Change Library Location") }
        Text(
            if (canChoose) "Choose whether the LuxTunes folder is on the phone's own storage or the SD card."
            else "There's no SD card in the phone, so the library can only be on its own storage.",
            color = Lux.dim, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, color = Lux.dim, fontSize = 14.sp)
        Text(value, color = Lux.text, fontSize = 17.sp)
    }
}

// ---- Check for Updates ---------------------------------------------------------------------

private sealed interface UpdateStep {
    data object Idle : UpdateStep
    data object Ask : UpdateStep                         // "this goes online: OK?"
    data object Checking : UpdateStep
    data object UpToDate : UpdateStep
    data class Found(val release: Updater.Release) : UpdateStep
    data class Downloading(val progress: Float) : UpdateStep
    data class Ready(val apk: File) : UpdateStep
    data class Failed(val message: String) : UpdateStep
}

/** Settings → Check for Updates. Asks before going online, and again before downloading. */
@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<UpdateStep>(UpdateStep.Idle) }
    val installed = remember { runCatching { Updater.installedBuild(context) }.getOrDefault(0) }

    SettingRow(Icons.Filled.SystemUpdate, "Check for Updates", "This copy: ${BuildConfigInfo.versionName(context)}") {
        step = UpdateStep.Ask
    }

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
                    Text("Download it now? (About 10 MB from github.com.)")
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

// ---- Operation ------------------------------------------------------------------------------

@Composable
fun OperationSettingsScreen(nav: NavHostController) {
    val prefs = app().prefs
    val s by prefs.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Operation")
        ToggleRow(
            "Launch Now Playing View",
            "Starting a song opens Now Playing. Off: you stay where you are and the bar at the bottom shows the song.",
            s.launchNowPlaying,
        ) { on -> prefs.update { it.copy(launchNowPlaying = on) } }
        ToggleRow(
            "Smart Playlists at end of list",
            "In the Playlists tab, show smart playlists after your other playlists.",
            s.smartAtEnd,
        ) { on -> prefs.update { it.copy(smartAtEnd = on) } }
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
    val prefs = app().prefs
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
        ScreenTitle(nav, "Tabs")

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
            Text("LuxTunes for Android ${BuildConfigInfo.versionName(context)}", color = Lux.text, fontSize = 20.sp)
            Text("Signed by ${BuildConfigInfo.signer(context)}", color = Lux.dim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Plays the music LuxTunes syncs to this phone, and sends plays, skips and ratings " +
                    "back at the next sync.\n\n" +
                    "Written entirely by an AI, Claude Opus 5.5 (\"vibe-coded\"). For copyright purposes, " +
                    "metric-ambient is the author.\n\n" +
                    "Provided \"as is\", without warranty of any kind. Keep backups of your music.\n\n" +
                    "License: GNU General Public License, version 3 or later.\n" +
                    "Uses AndroidX (Jetpack Compose, Media3) and Coil, under the Apache License 2.0.\n\n" +
                    "The app only goes online when you ask it to check for updates.",
                color = Lux.dim, fontSize = 15.sp,
            )
        }
    }
}
