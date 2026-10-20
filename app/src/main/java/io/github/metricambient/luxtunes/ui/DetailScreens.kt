package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.data.Track

/**
 * The top of an album, artist or playlist page: the art over a blurred copy of
 * itself, a bar with the song count, length and year, and a round play button.
 */
private fun LazyListScope.pageHeader(
    nav: NavHostController,
    artwork: String?,
    summary: String,
    tracks: List<Track>,
) {
    item {
        val player = app().player
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            Artwork(artwork, Modifier.fillMaxWidth().height(330.dp).blur(24.dp).alpha(0.6f), noteSize = 0.dp)
            Box(Modifier.fillMaxWidth().height(330.dp).background(Color.Black.copy(alpha = 0.25f)))
            Artwork(artwork, Modifier.align(Alignment.TopCenter).padding(top = 40.dp).size(225.dp), noteSize = 120.dp)
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { player.addToQueue(tracks) }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue", tint = Lux.text)
                }
                IconButton(onClick = { player.shuffle(tracks) }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = Lux.text)
                }
                IconButton(onClick = { player.play(tracks) }) {
                    Icon(Icons.Filled.PlayArrow, "Play", tint = Lux.text)
                }
            }
            Text(
                summary,
                color = Lux.text, fontSize = 17.sp, textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 265.dp)
                    .fillMaxWidth()
                    .background(Lux.accent)
                    .padding(vertical = 6.dp),
            )
            FloatingActionButton(
                onClick = { player.play(tracks) },
                shape = CircleShape,
                containerColor = Lux.accent,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-24).dp, y = 270.dp).size(58.dp),
            ) {
                Icon(Icons.Filled.MusicNote, "Play")
            }
        }
    }
}

private fun summaryOf(tracks: List<Track>, year: Int = 0): String {
    val text = "${plural(tracks.size, "song")} (${formatLength(tracks.sumOf { it.totalTime })})"
    return if (year > 0) "$text · $year" else text
}

@Composable
private fun Missing(nav: NavHostController, what: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("This $what isn't on the phone any more.", color = Lux.dim)
    }
    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text) }
}

@Composable
fun AlbumScreen(nav: NavHostController, key: String) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val playerState by app.player.state.collectAsState()
    val album = library.albumByKey[key] ?: return Missing(nav, "album")
    LazyColumn(Modifier.fillMaxSize()) {
        pageHeader(nav, album.artwork, summaryOf(album.tracks, album.year), album.tracks)
        item { Spacer(Modifier.height(16.dp)) }
        itemsIndexed(album.tracks, key = { _, t -> t.id }) { i, t ->
            SongRow(
                track = t,
                number = if (t.trackNumber > 0) t.trackNumber else i + 1,
                current = t.id == playerState.currentId,
                onClick = { app.player.play(album.tracks, i); nav.go(Routes.NOW) },
                menu = songMenu(t),
            )
        }
    }
}

@Composable
fun ArtistScreen(nav: NavHostController, name: String) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val artist = library.artistByName[name] ?: return Missing(nav, "artist")
    val tracks = artist.tracks
    val years = artist.albums.map { it.year }.filter { it > 0 }
    LazyColumn(Modifier.fillMaxSize()) {
        pageHeader(nav, artist.albums.firstNotNullOfOrNull { it.artwork }, summaryOf(tracks), tracks)
        item {
            Text(
                artist.name + if (years.isNotEmpty()) "  ·  ${years.min()}–${years.max()}" else "",
                color = Lux.dim, fontSize = 15.sp,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        itemsIndexed(artist.albums, key = { _, a -> a.key }) { _, album ->
            ListRow(
                title = album.name,
                subtitle = if (album.year > 0) "${album.year}" else plural(album.tracks.size, "song"),
                onClick = { nav.go(Routes.album(album.key)) },
                leading = { Artwork(album.artwork, Modifier.size(64.dp), noteSize = 32.dp) },
                menu = listOf(
                    "Play" to { app.player.play(album.tracks) },
                    "Shuffle" to { app.player.shuffle(album.tracks) },
                    "Add to queue" to { app.player.addToQueue(album.tracks) },
                ),
            )
        }
    }
}

@Composable
fun PlaylistScreen(nav: NavHostController, id: String) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val playerState by app.player.state.collectAsState()
    val playlist = library.playlists.firstOrNull { it.id == id } ?: return Missing(nav, "playlist")
    val tracks = playlist.trackIds.mapNotNull { library.byId[it] }
    LazyColumn(Modifier.fillMaxSize()) {
        pageHeader(nav, tracks.firstNotNullOfOrNull { it.artwork }, summaryOf(tracks), tracks)
        item {
            Text(
                playlist.name + if (playlist.isSmart) "  ·  Smart playlist" else "",
                color = Lux.dim, fontSize = 15.sp,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { i, t ->
            SongRow(
                track = t,
                current = t.id == playerState.currentId,
                onClick = { app.player.play(tracks, i); nav.go(Routes.NOW) },
                menu = songMenu(t),
            )
        }
    }
}

/** Now Playing as a list: what's queued, with the current song highlighted. */
@Composable
fun QueueScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val playerState by app.player.state.collectAsState()
    val tracks = playerState.queue.mapNotNull { library.byId[it] }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text)
                }
                Text("Now Playing", color = Lux.text, fontSize = 22.sp)
            }
        }
        if (tracks.isEmpty()) {
            item { Text("Nothing queued.", color = Lux.dim, modifier = Modifier.padding(16.dp)) }
        }
        itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { i, t ->
            SongRow(
                track = t,
                current = i == playerState.queueIndex,
                onClick = {
                    app.player.skipToQueueItem(i)
                    // Back to Now Playing if we came from there, instead of stacking a second one.
                    if (!nav.popBackStack(Routes.NOW, inclusive = false)) nav.go(Routes.NOW)
                },
                menu = emptyList(),
            )
        }
    }
}
