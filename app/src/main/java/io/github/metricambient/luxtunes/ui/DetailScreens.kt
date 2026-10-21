package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.github.metricambient.luxtunes.LuxTunesApp
import io.github.metricambient.luxtunes.data.ArtistPlay
import io.github.metricambient.luxtunes.data.shuffleGroups
import kotlin.math.roundToInt
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
import io.github.metricambient.luxtunes.data.artTrackOf

/**
 * The top of an album, artist or playlist page: the art over a blurred copy of
 * itself, a bar with the song count, length and year, and a round play button.
 */
private fun LazyListScope.pageHeader(
    nav: NavHostController,
    art: Track?,
    summary: String,
    tracks: List<Track>,
    menu: Menu = emptyList(),
    fab: (@Composable (Modifier) -> Unit)? = null,       // replaces the plain play button
) {
    item {
        val player = app().player
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            Artwork(art, Modifier.fillMaxWidth().height(330.dp).blur(24.dp).alpha(0.6f), noteSize = 0.dp)
            Box(Modifier.fillMaxWidth().height(330.dp).background(Lux.background.copy(alpha = 0.25f)))
            Artwork(art, Modifier.align(Alignment.TopCenter).padding(top = 40.dp).size(225.dp), noteSize = 120.dp)
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { Dialogs.show(DialogRequest.AddToPlaylist(tracks.map { it.id })) }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", tint = Lux.text)
                }
                IconButton(onClick = { player.shuffle(tracks); showPlayer(nav, tracks) }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = Lux.text)
                }
                IconButton(onClick = { player.play(tracks); showPlayer(nav, tracks) }) {
                    Icon(Icons.Filled.PlayArrow, "Play", tint = Lux.text)
                }
                if (menu.isNotEmpty()) MoreMenu(menu, tint = Lux.text)
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
            val fabPlace = Modifier.align(Alignment.TopEnd).offset(x = (-24).dp, y = 270.dp).size(58.dp)
            if (fab != null) {
                fab(fabPlace)
            } else {
                FloatingActionButton(
                    onClick = { player.play(tracks); showPlayer(nav, tracks) },
                    shape = CircleShape,
                    containerColor = Lux.accent,
                    contentColor = Lux.onAccent,
                    modifier = fabPlace,
                ) {
                    Icon(Icons.Filled.MusicNote, "Play")
                }
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
    PageAccent(album.artTrack)
    val menu = groupMenu(album.tracks, nav)
    LazyColumn(Modifier.fillMaxSize()) {
        pageHeader(nav, album.artTrack, summaryOf(album.tracks, album.year), album.tracks, menu)
        item { Spacer(Modifier.height(16.dp)) }
        itemsIndexed(album.tracks, key = { _, t -> t.id }) { i, t ->
            SongRow(
                track = t,
                number = if (t.trackNumber > 0) t.trackNumber else i + 1,
                current = t.id == playerState.currentId,
                onClick = { tapSong(app, nav, album.tracks, i) },
                menu = songMenu(t, nav),
            )
        }
    }
}

@Composable
fun GroupScreen(nav: NavHostController, kind: String, name: String) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val settings by app.prefs.settings.collectAsState()
    val playerState by app.player.state.collectAsState()
    val artist = library.group(kind, name) ?: return Missing(nav, when (kind) {
        "genre" -> "genre"; "composer" -> "composer"; "tvshow" -> "show"; else -> "artist"
    })
    val tracks = artist.tracks                       // by album, oldest album first
    val years = artist.albums.map { it.year }.filter { it > 0 }
    PageAccent(artTrackOf(tracks))

    var dialOpen by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var fabBounds by remember { mutableStateOf(Rect.Zero) }
    val menu: Menu = listOf(
        (if (settings.artistSongs) "Show as Albums" else "Show as Songs") to {
            app.prefs.update { it.copy(artistSongs = !it.artistSongs) }
        },
        "Add to Queue" to { app.player.addToQueue(tracks) },
        "Delete" to { Dialogs.show(DialogRequest.Delete(tracks.map { it.id })) },
    )

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        LazyColumn(Modifier.fillMaxSize()) {
            pageHeader(
                nav, artTrackOf(tracks), summaryOf(tracks), tracks, menu,
                fab = { place ->
                    FloatingActionButton(
                        onClick = { dialOpen = true },
                        shape = CircleShape,
                        containerColor = Lux.accent,
                        contentColor = Lux.onAccent,
                        modifier = place.onGloballyPositioned { fabBounds = it.boundsInRoot() },
                    ) {
                        ArtistPlayIcon(settings.artistPlay, settings.artistPlay.label)
                    }
                },
            )
            item {
                Text(
                    artist.name + if (years.isNotEmpty()) "  ·  ${years.min()}–${years.max()}" else "",
                    color = Lux.dim, fontSize = 15.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            if (settings.artistSongs) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                    SongRow(
                        track = t,
                        current = t.id == playerState.currentId,
                        onClick = { tapSong(app, nav, tracks, i) },
                        menu = songMenu(t, nav),
                        leading = { Artwork(t, Modifier.size(56.dp), noteSize = 28.dp) },
                    )
                }
            } else {
                itemsIndexed(artist.albums, key = { _, a -> a.key }) { _, album ->
                    ListRow(
                        title = album.name,
                        subtitle = if (album.year > 0) "${album.year}" else plural(album.tracks.size, if (kind == "tvshow") "episode" else "song"),
                        onClick = { nav.go(Routes.album(album.key)) },
                        leading = { Artwork(album.artTrack, Modifier.size(64.dp), noteSize = 32.dp) },
                        menu = groupMenu(album.tracks, nav),
                    )
                }
            }
        }
        if (dialOpen) {
            SpeedDial(
                fab = fabBounds.translate(-origin),
                default = settings.artistPlay,
                onClose = { dialOpen = false },
                onPick = { choice ->
                    dialOpen = false
                    app.prefs.update { it.copy(artistPlay = choice) }
                    playArtist(app, nav, tracks, choice)
                },
            )
        }
    }
}

private fun playArtist(app: LuxTunesApp, nav: NavHostController, tracks: List<Track>, how: ArtistPlay) {
    if (tracks.isEmpty()) return
    when (how) {
        ArtistPlay.FROM_TOP -> app.player.play(tracks)
        ArtistPlay.SHUFFLE_ALBUMS -> app.player.playShuffled(shuffleGroups(tracks) { it.albumKey }, tracks)
        ArtistPlay.SHUFFLE_SONGS -> app.player.shuffle(tracks)
    }
    showPlayer(nav, tracks)
}

@Composable
private fun ArtistPlayIcon(how: ArtistPlay, description: String?, size: Dp = 28.dp) {
    when (how) {
        ArtistPlay.FROM_TOP -> Icon(Icons.Filled.PlayArrow, description, modifier = Modifier.size(size))
        ArtistPlay.SHUFFLE_ALBUMS -> Icon(Icons.Filled.Shuffle, description, modifier = Modifier.size(size))
        ArtistPlay.SHUFFLE_SONGS -> Box(Modifier.size(size)) {
            Icon(Icons.Filled.Shuffle, description, modifier = Modifier.size(size * 0.55f).align(Alignment.TopStart))
            Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(size * 0.8f).align(Alignment.BottomEnd))
        }
    }
}

/**
 * The artist play button, opened: the other choices stacked above it with their
 * names, the current default in its place. Tapping outside closes it.
 */
@Composable
private fun SpeedDial(fab: Rect, default: ArtistPlay, onClose: () -> Unit, onPick: (ArtistPlay) -> Unit) {
    val density = LocalDensity.current
    val fabWidth = with(density) { fab.width.toDp() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    // Bottom-right corner on the button's bottom-right corner.
                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        p.place((fab.right - p.width).roundToInt(), (fab.bottom - p.height).roundToInt())
                    }
                },
        ) {
            val order = ArtistPlay.entries.filter { it != default } + default
            for (how in order) {
                val main = how == default
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        how.label,
                        color = Color.Black, fontSize = 17.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .clickable { onPick(how) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.width(fabWidth), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(if (main) fabWidth else 46.dp)
                                .clip(CircleShape)
                                .background(Lux.accent)
                                .clickable { onPick(how) },
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(LocalContentColor provides Lux.onAccent) {
                                ArtistPlayIcon(how, how.label, if (main) 28.dp else 24.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(nav: NavHostController, id: String) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val playerState by app.player.state.collectAsState()
    val playlist = library.playlists.firstOrNull { it.id == id } ?: return Missing(nav, "playlist")
    // (position in the playlist, song): songs no longer on the phone are skipped.
    val entries = playlist.trackIds.mapIndexedNotNull { pos, tid -> library.byId[tid]?.let { pos to it } }
    val tracks = entries.map { it.second }
    LazyColumn(Modifier.fillMaxSize()) {
        pageHeader(nav, artTrackOf(tracks), summaryOf(tracks), tracks)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    playlist.name + when {
                        playlist.isSmart -> "  ·  Smart playlist"
                        playlist.onPhone -> "  ·  Made on this phone"
                        else -> ""
                    } + if (playlist.changed) "  ·  Changes go to LuxTunes at the next sync" else "",
                    color = Lux.dim, fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                MoreMenu(playlistMenu(playlist, nav))
            }
        }
        itemsIndexed(entries, key = { _, e -> "${e.first}:${e.second.id}" }) { i, (pos, t) ->
            val remove: Menu = if (!playlist.isSmart && library.canEdit(playlist)) {
                listOf("Remove from playlist" to { app.repository.removeFromPlaylist(playlist.id, pos) })
            } else emptyList()
            SongRow(
                track = t,
                current = t.id == playerState.currentId,
                onClick = { tapSong(app, nav, tracks, i) },
                menu = songMenu(t, nav, remove),
            )
        }
    }
}

/** The play queue ("Play Queue - Up Next"), with the current song highlighted. */
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
                Text("Play Queue - Up Next", color = Lux.text, fontSize = 22.sp)
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
