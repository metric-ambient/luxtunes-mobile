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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import io.github.metricambient.luxtunes.data.AppSettings
import io.github.metricambient.luxtunes.data.GroupAction
import io.github.metricambient.luxtunes.data.LuxFolder
import io.github.metricambient.luxtunes.data.SongAction
import io.github.metricambient.luxtunes.data.StorageChoice
import io.github.metricambient.luxtunes.data.Updater
import kotlinx.coroutines.launch
import java.io.File

@Composable
private fun ScreenTitle(nav: NavHostController, title: String) {
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
        Icon(icon, null, tint = if (onClick != null) Lux.icon else Color(0xFF666666), modifier = Modifier.size(48.dp))
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
        item { SettingRow(Icons.Filled.Album, "Sound", "EQ, fade in/out, customize playback · $later") }
        item { SettingRow(Icons.Filled.Build, "Operation", "Lock screen, font, shuffle… · $later") }
        item { SettingRow(Icons.Filled.Palette, "Theme Color", later) }
        item { SettingRow(Icons.Filled.Headphones, "Headset/Bluetooth", "Set volume on connect and more · $later") }
        item {
            val folder = library.folder?.path ?: "not found"
            val synced = library.syncedAt?.let { niceDate(it) } ?: "never"
            val storage = when (settings.storage) {
                StorageChoice.REMOVABLE -> "SD card"
                StorageChoice.INTERNAL -> "Internal storage"
                null -> "any"
            }
            val written = when {
                library.changesError != null -> "couldn't write it (${library.changesError})"
                library.changesWritten > 0 -> "written " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                    .format(java.util.Date(library.changesWritten))
                else -> "not written since the app started"
            }
            val canChoose = LuxFolder.hasRemovable(context)
            SettingRow(
                Icons.Filled.LibraryMusic, "My Library",
                "${library.libraryName.ifEmpty { "LuxTunes" }}: ${plural(library.tracks.size, "song")}\n" +
                    "Storage: $storage${if (canChoose) " (tap to change)" else ""}\n" +
                    "Folder: $folder\nLast synced: $synced\n" +
                    "Waiting to go to LuxTunes: ${plural(library.pendingCount, "song")}\n" +
                    "changes.json: $written" + if (library.transferMode) "\nUSB file transfer: on" else "",
                onClick = if (canChoose) { { Dialogs.show(DialogRequest.ChooseStorage(firstTime = false)) } } else null,
            )
        }
        item { SettingRow(Icons.Filled.Refresh, "Rescan Device", "Look for a new sync from LuxTunes now") { app.repository.refreshAsync() } }
        item { SettingRow(Icons.Filled.Image, "Album Art", later) }
        item { SettingRow(Icons.Filled.Folder, "Tabs", "Choose which tabs to see, and what a tap does") { nav.go(Routes.SETTINGS_TABS) } }
        item { UpdateRow() }
        item { SettingRow(Icons.Filled.Info, "About / Legal", "Version, license") { nav.go(Routes.SETTINGS_ABOUT) } }
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
                        "LuxTunes Mobile. Nothing about you or your music is sent."
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
                                    "No release found. (Until the project's GitHub page is public, the app can't see its releases.)"
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
                    else "First allow LuxTunes Mobile to install apps (\"Install unknown apps\"), " +
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

// ---- Tabs ---------------------------------------------------------------------------------

@Composable
fun TabsSettingsScreen(nav: NavHostController) {
    val prefs = app().prefs
    val s by prefs.settings.collectAsState()

    fun showTab(name: String, on: Boolean) = prefs.update { cur ->
        val tabs = if (on) ALL_TABS.filter { it in cur.tabs || it == name } else cur.tabs - name
        cur.copy(tabs = tabs.ifEmpty { cur.tabs })          // at least one tab stays
    }

    fun chooseGroup(title: String, current: GroupAction, options: List<GroupAction>, set: (AppSettings, GroupAction) -> AppSettings) {
        Dialogs.show(DialogRequest.Choose(title, options.map { it.label }, options.indexOf(current)) { i ->
            prefs.update { set(it, options[i]) }
        })
    }

    fun chooseColumns(title: String, current: Int, set: (AppSettings, Int) -> AppSettings) {
        val options = (1..6).toList()
        Dialogs.show(DialogRequest.Choose(title, options.map { "$it" }, options.indexOf(current)) { i ->
            prefs.update { set(it, options[i]) }
        })
    }

    val allGroup = GroupAction.entries.toList()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Tabs")

        Heading("Songs")
        CheckRow("Display songs tab", "Songs" in s.tabs) { showTab("Songs", it) }
        CheckRow("Display album art", s.songArt) { v -> prefs.update { it.copy(songArt = v) } }
        ChoiceRow("Default Song Action", s.songAction.label) {
            val options = SongAction.entries.toList()
            Dialogs.show(DialogRequest.Choose("Select Action", options.map { it.label }, options.indexOf(s.songAction)) { i ->
                prefs.update { it.copy(songAction = options[i]) }
            })
        }
        HorizontalDivider(color = Lux.panel)

        Heading("Playlists")
        CheckRow("Display playlists tab", "Playlists" in s.tabs) { showTab("Playlists", it) }
        CheckRow("Display playlist icon", s.playlistIcons) { v -> prefs.update { it.copy(playlistIcons = v) } }
        ChoiceRow("Default Playlist Action", s.playlistAction.label) {
            chooseGroup("Select Action", s.playlistAction, listOf(GroupAction.VIEW, GroupAction.PLAY, GroupAction.SHUFFLE)) { cur, a ->
                cur.copy(playlistAction = a)
            }
        }
        HorizontalDivider(color = Lux.panel)

        Heading("Albums")
        CheckRow("Display albums tab", "Albums" in s.tabs) { showTab("Albums", it) }
        ChoiceRow("Number of portrait grid items", "${s.gridPortrait}") {
            chooseColumns("Portrait grid items", s.gridPortrait) { cur, n -> cur.copy(gridPortrait = n) }
        }
        ChoiceRow("Number of landscape grid items", "${s.gridLandscape}") {
            chooseColumns("Landscape grid items", s.gridLandscape) { cur, n -> cur.copy(gridLandscape = n) }
        }
        CheckRow("Display album art", s.albumArt) { v -> prefs.update { it.copy(albumArt = v) } }
        ChoiceRow("Default Album Action", s.albumAction.label) {
            chooseGroup("Select Action", s.albumAction, allGroup) { cur, a -> cur.copy(albumAction = a) }
        }
        HorizontalDivider(color = Lux.panel)

        Heading("Artists")
        CheckRow("Display artists tab", "Artists" in s.tabs) { showTab("Artists", it) }
        ChoiceRow("Default Artist Action", s.artistAction.label) {
            chooseGroup("Select Action", s.artistAction, allGroup) { cur, a -> cur.copy(artistAction = a) }
        }
        Spacer(Modifier.height(24.dp))
    }
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
private fun ChoiceRow(label: String, value: String, onClick: () -> Unit) {
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
                    "License: GNU General Public License, version 3 or later.\n" +
                    "Uses AndroidX (Jetpack Compose, Media3) and Coil, under the Apache License 2.0.\n\n" +
                    "The app only goes online when you ask it to check for updates.",
                color = Lux.dim, fontSize = 15.sp,
            )
        }
    }
}
