package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.SkipNext
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
import io.github.metricambient.luxtunes.data.artTrackOf
import kotlinx.coroutines.delay

// ---- Search across the whole library -------------------------------------------------------

/**
 * Search the whole library. It opens on All; the buttons along the top narrow it to
 * artists, albums, songs or playlists.
 */
@Composable
fun SearchScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val playerState by app.player.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf("All") }
    val q = query.trim()
    fun wants(kind: String) = q.isNotEmpty() && (scope == "All" || scope == kind)
    // Album artists, then artists with songs of their own (also each name in a song's Artists).
    val artists = if (!wants("Artists")) emptyList() else {
        val albumArtists = library.artists.map { it to "artist" }
        val own = albumArtists.map { it.first.name.lowercase() }.toSet()
        (albumArtists + library.trackArtists.filter { it.name.lowercase() !in own }.map { it to "trackartist" })
            .filter { matches(q, it.first.name) }.take(if (scope == "All") 20 else 200)
    }
    val albums = if (!wants("Albums")) emptyList() else library.albums.filter { matches(q, it.name, it.artist) }.take(if (scope == "All") 30 else 300)
    val songs = if (!wants("Songs")) emptyList() else library.tracks.filter { matchesSong(q, it) }.take(if (scope == "All") 100 else 500)
    val playlists = if (!wants("Playlists")) emptyList() else library.playlists.filter { matches(q, it.name) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search your library", color = Lux.dim) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Lux.icon) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear", tint = Lux.icon) }
            },
            shape = RoundedCornerShape(26.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Lux.raised, unfocusedContainerColor = Lux.raised,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Lux.accent, focusedTextColor = Lux.text, unfocusedTextColor = Lux.text,
            ),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).focusRequester(focus),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (name in listOf("All", "Artists", "Albums", "Songs", "Playlists")) {
                val on = name == scope
                Text(
                    name, color = if (on) Lux.onAccent else Lux.text, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) Lux.accent else Lux.raised)
                        .clickable { scope = name }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = LocalMiniSpace.current)) {
            if (artists.isNotEmpty() && scope == "All") item { Heading("Artists") }
            items(artists, key = { (a, kind) -> "ar:$kind:" + a.name }) { (a, kind) ->
                ListRow(a.name, plural(a.albums.size, "album"), onClick = { nav.go(Routes.group(kind, a.name)) },
                    leading = { Artwork(artTrackOf(a.tracks), Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)), noteSize = 24.dp, note = Icons.Filled.Person) })
            }
            if (albums.isNotEmpty() && scope == "All") item { Heading("Albums") }
            items(albums, key = { "al:" + it.key }) { a ->
                ListRow(a.name, a.artist, onClick = { nav.go(Routes.album(a.key)) },
                    leading = { Artwork(a.artTrack, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), noteSize = 24.dp) },
                    menu = groupMenu(a.tracks, nav, a.name))
            }
            if (songs.isNotEmpty() && scope == "All") item { Heading("Songs") }
            items(songs, key = { "so:" + it.id }) { t ->
                SongRow(t, onClick = { tapSong(app, nav, songs, songs.indexOf(t)) },
                    current = t.id == playerState.currentId, menu = songMenu(t, nav))
            }
            if (playlists.isNotEmpty() && scope == "All") item { Heading("Playlists") }
            items(playlists, key = { "pl:" + it.id }) { p ->
                ListRow(p.name, plural(p.trackIds.size, "song"), onClick = { nav.go(Routes.playlist(p.id)) },
                    leading = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Lux.text, modifier = Modifier.size(40.dp)) },
                    menu = playlistMenu(p, nav))
            }
            if (q.isNotEmpty() && artists.isEmpty() && albums.isEmpty() && songs.isEmpty() && playlists.isEmpty()) item {
                Text("Nothing found.", color = Lux.dim, modifier = Modifier.padding(16.dp))
            }
        }
        PageBottomBar(nav, emptyList())
    }
}

