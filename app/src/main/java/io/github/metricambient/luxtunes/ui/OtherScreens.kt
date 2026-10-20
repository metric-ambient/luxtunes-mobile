package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.BuildConfigInfo
import kotlinx.coroutines.delay

// ---- Mini player (bottom of every screen) ----------------------------------------------------

@Composable
fun MiniPlayer(nav: NavHostController, showMenu: Boolean) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val state by app.player.state.collectAsState()
    val track = state.currentId?.let { library.byId[it] }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            val d = app.player.duration
            progress = if (d > 0) (app.player.position.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    Column(Modifier.fillMaxWidth().background(Lux.background)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF9E9E9E))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Lux.accent))
        }
        Row(
            Modifier.fillMaxWidth().height(64.dp).clickable { nav.go(Routes.NOW) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track?.artwork, Modifier.size(64.dp), noteSize = 36.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track?.name ?: "Not playing", color = Lux.text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track != null) Text(track.artist, color = Lux.dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { app.player.togglePlay() }, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play", tint = Lux.icon, modifier = Modifier.size(44.dp),
                )
            }
            if (showMenu) {
                MoreMenu(listOf(
                    "Search" to { nav.go(Routes.SEARCH) },
                    "Rescan Device" to { app.repository.refreshAsync() },
                    "Settings" to { nav.go(Routes.SETTINGS) },
                ))
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

// ---- Search across the whole library -------------------------------------------------------

@Composable
fun SearchScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val playerState by app.player.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val artists = if (q.isEmpty()) emptyList() else library.artists.filter { matches(q, it.name) }.take(20)
    val albums = if (q.isEmpty()) emptyList() else library.albums.filter { matches(q, it.name, it.artist) }.take(30)
    val songs = if (q.isEmpty()) emptyList() else library.tracks.filter { matches(q, it.name, it.artist, it.album) }.take(100)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text) }
            SearchField(query, { query = it })
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (artists.isNotEmpty()) item { Heading("Artists") }
            items(artists, key = { "ar:" + it.name }) { a ->
                ListRow(a.name, plural(a.albums.size, "album"), onClick = { nav.go(Routes.artist(a.name)) },
                    leading = { Icon(Icons.Filled.Person, null, tint = Lux.text, modifier = Modifier.size(40.dp)) })
            }
            if (albums.isNotEmpty()) item { Heading("Albums") }
            items(albums, key = { "al:" + it.key }) { a ->
                ListRow(a.name, a.artist, onClick = { nav.go(Routes.album(a.key)) },
                    leading = { Artwork(a.artwork, Modifier.size(48.dp), noteSize = 24.dp) })
            }
            if (songs.isNotEmpty()) item { Heading("Songs") }
            items(songs, key = { "so:" + it.id }) { t ->
                SongRow(t, onClick = { app.player.play(songs, songs.indexOf(t)); nav.go(Routes.NOW) },
                    current = t.id == playerState.currentId, menu = songMenu(t))
            }
        }
    }
}

// ---- Settings --------------------------------------------------------------------------------

@Composable
fun SettingsScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    var about by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                IconButton(onClick = { if (about) about = false else nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text)
                }
                Text(if (about) "About" else "Settings", color = Lux.text, fontSize = 24.sp, modifier = Modifier.padding(start = 16.dp))
            }
        }
        if (about) {
            item { AboutText() }
            return@LazyColumn
        }
        val later = "Coming in a later version"
        item { SettingRow(Icons.Filled.Album, "Sound", "EQ, fade in/out, customize playback · $later") }
        item { SettingRow(Icons.Filled.Build, "Operation", "Lock screen, font, shuffle… · $later") }
        item { SettingRow(Icons.Filled.Palette, "Theme Color", later) }
        item { SettingRow(Icons.Filled.Headphones, "Headset/Bluetooth", "Set volume on connect and more · $later") }
        item {
            val folder = library.folder?.path ?: "not found"
            val synced = library.syncedAt?.replace('T', ' ')?.removeSuffix("Z")?.plus(" UTC") ?: "never"
            SettingRow(
                Icons.Filled.LibraryMusic, "My Library",
                "${library.libraryName.ifEmpty { "LuxTunes" }}: ${plural(library.tracks.size, "song")}\n" +
                    "Folder: $folder\nLast synced: $synced\n" +
                    "Waiting to go to LuxTunes: ${plural(library.pendingCount, "song")}\n" +
                    "changes.json: " + when {
                        library.changesError != null -> "couldn't write it (${library.changesError})"
                        library.changesWritten > 0 -> "written " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                            .format(java.util.Date(library.changesWritten))
                        else -> "not written since the app started"
                    } + if (library.transferMode) "\nUSB file transfer: on" else "",
            )
        }
        item { SettingRow(Icons.Filled.Refresh, "Rescan Device", "Look for a new sync from LuxTunes now") { app.repository.refreshAsync() } }
        item { SettingRow(Icons.Filled.Image, "Album Art", later) }
        item { SettingRow(Icons.Filled.Tab, "Tabs", "Choose which tabs to see · $later") }
        item { SettingRow(Icons.Filled.Info, "About / Legal", "Version, license") { about = true } }
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
        Icon(icon, null, tint = if (onClick != null || title == "My Library") Lux.icon else Color(0xFF666666), modifier = Modifier.size(48.dp))
        Column(Modifier.padding(start = 20.dp)) {
            Text(title, color = Lux.text, fontSize = 19.sp)
            Text(subtitle, color = Lux.dim, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AboutText() {
    Column(Modifier.padding(20.dp)) {
        Text("LuxTunes Mobile ${BuildConfigInfo.versionName(app())}", color = Lux.text, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Plays the music LuxTunes syncs to this phone, and sends plays, skips and ratings " +
                "back at the next sync.\n\n" +
                "Written entirely by an AI, Claude Opus 5.5 (\"vibe-coded\"). For copyright purposes, " +
                "metric-ambient is the author.\n\n" +
                "Provided \"as is\", without warranty of any kind. Keep backups of your music.\n\n" +
                "License: GNU General Public License, version 3 or later.\n" +
                "Uses AndroidX (Jetpack Compose, Media3) and Coil, under the Apache License 2.0.\n\n" +
                "The app has no internet access.",
            color = Lux.dim, fontSize = 15.sp,
        )
    }
}
