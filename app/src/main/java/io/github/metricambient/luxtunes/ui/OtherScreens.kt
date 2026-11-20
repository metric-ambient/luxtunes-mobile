package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay

// ---- Mini player (bottom of every screen) ----------------------------------------------------

/**
 * The mini player: a card floating over the bottom of the screen. Tap it for Now Playing;
 * play/pause and next on the right; a thin line along its bottom shows how far the song is.
 */
@Composable
fun MiniPlayer(nav: NavHostController, modifier: Modifier = Modifier) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val state by app.player.state.collectAsState()
    val track = state.currentId?.let { library.byId[it] ?: app.repository.track(it) }      // (early: before the library is in)
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            val d = app.player.duration
            progress = if (d > 0) (app.player.position.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Lux.panel)
            .clickable { nav.go(if (track?.isVideo == true) Routes.VIDEO else Routes.NOW) },
    ) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)), noteSize = 26.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track?.displayName ?: "Not playing", color = Lux.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track != null) Text(track.artist, color = Lux.dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SleepTimerBadge()
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Lux.accent)
                    .clickable { app.player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play", tint = Lux.onAccent, modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = { app.player.next() }) {
                Icon(Icons.Filled.SkipNext, "Next", tint = Lux.text, modifier = Modifier.size(32.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(2.dp).background(Lux.accent))
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
    val songs = if (q.isEmpty()) emptyList() else library.tracks.filter { matches(q, it.name, it.artist, it.album, it.featuredArtist.orEmpty()) }.take(100)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text) }
            SearchField(query, { query = it })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = LocalMiniSpace.current)) {
            if (artists.isNotEmpty()) item { Heading("Artists") }
            items(artists, key = { "ar:" + it.name }) { a ->
                ListRow(a.name, plural(a.albums.size, "album"), onClick = { nav.go(Routes.artist(a.name)) },
                    leading = { Icon(Icons.Filled.Person, null, tint = Lux.text, modifier = Modifier.size(40.dp)) })
            }
            if (albums.isNotEmpty()) item { Heading("Albums") }
            items(albums, key = { "al:" + it.key }) { a ->
                ListRow(a.name, a.artist, onClick = { nav.go(Routes.album(a.key)) },
                    leading = { Artwork(a.artTrack, Modifier.size(48.dp), noteSize = 24.dp) })
            }
            if (songs.isNotEmpty()) item { Heading("Songs") }
            items(songs, key = { "so:" + it.id }) { t ->
                SongRow(t, onClick = { tapSong(app, nav, songs, songs.indexOf(t)) },
                    current = t.id == playerState.currentId, menu = songMenu(t, nav))
            }
        }
    }
}

