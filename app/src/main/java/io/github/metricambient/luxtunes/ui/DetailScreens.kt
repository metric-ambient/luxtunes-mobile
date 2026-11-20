package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.metricambient.luxtunes.data.sortText

/**
 * An album, artist or playlist page: the list, with the bottom bar (back, and the page's
 * ⋮ menu) under it. The floating mini player sits above the bar; the list leaves room for it.
 */
@Composable
private fun Page(nav: NavHostController, menu: Menu, title: String?, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = LocalMiniSpace.current),
            content = content,
        )
        PageBottomBar(nav, menu, title)
    }
}

/**
 * The top of a page: the art at the left; beside it the name and a line or two about it,
 * centred in the space above `toggles` (an artist's one-tap switches), whose bottom lines
 * up with the art's. Without toggles the name and info are centred beside the art.
 */
private fun LazyListScope.pageTop(
    art: Track?,
    title: String,
    info: String,
    maxArt: Dp,
    toggles: (@Composable RowScope.() -> Unit)? = null,
) {
    item {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
            val artSize = minOf(maxArt, maxWidth * 0.42f)
            Row(Modifier.fillMaxWidth().height(artSize)) {
                Artwork(art, Modifier.size(artSize).clip(RoundedCornerShape(16.dp)), noteSize = artSize * 0.5f)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        Text(
                            title, color = Lux.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (info.isNotEmpty()) Text(
                            info, color = Lux.dim, fontSize = 13.sp, lineHeight = 18.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (toggles != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { toggles() }
                    }
                }
            }
        }
    }
}

/** A one-tap switch at the top of an artist page: an icon and what a tap switches to. */
@Composable
private fun RowScope.ToggleChip(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .weight(1f, fill = false)
            .fillMaxHeight()
            .clip(RoundedCornerShape(17.dp))
            .background(Lux.raised)
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = Lux.text, modifier = Modifier.size(16.dp))
        Text(label, color = Lux.text, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** How a Shuffle button shuffles: by albums, or (also for the old "from the top" default) by songs. */
private fun shuffleBy(saved: ArtistPlay) = if (saved == ArtistPlay.SHUFFLE_ALBUMS) ArtistPlay.SHUFFLE_ALBUMS else ArtistPlay.SHUFFLE_SONGS

/** "11 songs · 42m" */
private fun lengthOf(tracks: List<Track>) = "${plural(tracks.size, "song")} · ${formatLength(tracks.sumOf { it.totalTime })}"

@Composable
private fun Missing(nav: NavHostController, what: String) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("This $what isn't on the phone any more.", color = Lux.dim)
        }
        PageBottomBar(nav, emptyList())
    }
}

@Composable
fun AlbumScreen(nav: NavHostController, key: String) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val playerState by app.player.state.collectAsState()
    // Its songs, to follow the album if Edit renames it.
    val seen = remember(key) { mutableListOf<String>() }
    val album = library.albumByKey[key] ?: library.albumWith(seen) ?: return Missing(nav, "album")
    if (seen.isEmpty()) seen += album.tracks.map { it.id }
    PageAccent(album.artTrack)
    val menu = groupMenu(album.tracks, nav, album.name)
    val info = album.artist + "\n" +
        (listOf(if (album.year > 0) "${album.year}" else "") + lengthOf(album.tracks)).filter { it.isNotBlank() }.joinToString(" · ")
    PlayCardFor(
        play = { playWith(app, nav, album.tracks, ArtistPlay.FROM_TOP) },
        shuffle = { playWith(app, nav, album.tracks, ArtistPlay.SHUFFLE_SONGS) },
    )
    Page(nav, menu, album.name) {
        pageTop(album.artTrack, album.name, info, maxArt = 176.dp)
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
    val seen = remember(kind, name) { mutableListOf<String>() }
    val artist = library.group(kind, name) ?: library.groupWith(kind, seen) ?: return Missing(nav, when (kind) {
        "genre" -> "genre"; "composer" -> "composer"; "tvshow" -> "show"; else -> "artist"
    })
    if (seen.isEmpty()) seen += artist.tracks.map { it.id }
    val tracks = artist.tracks                       // by album, oldest album first
    val years = artist.albums.map { it.year }.filter { it > 0 }
    // Songs by other artists that feature this one (as LuxTunes' "Featured On").
    val featuredOn = remember(library, kind, artist.name, settings.excludeFeatured) {
        if (settings.excludeFeatured || (kind != "artist" && kind != "trackartist")) emptyList() else {
            val own = tracks.map { it.id }.toSet()
            val who = artist.name.lowercase()
            library.tracks.filter { t -> t.id !in own && t.featured.any { it.lowercase() == who } }
                .sortedWith(compareBy({ sortText(it.browseSortArtist) }, { sortText(it.sortAlbum) }, { it.discNumber }, { it.trackNumber }))
        }
    }
    PageAccent(artTrackOf(tracks))

    val menu: Menu = listOf(
        "Add to Queue" to { app.player.addToQueue(tracks) },
        "Add to End of Queue" to { app.player.addToEnd(tracks) },
        "Add to Playlist…" to { Dialogs.show(DialogRequest.AddToPlaylist(tracks.map { it.id })) },
        "Set EQ…" to { Dialogs.show(DialogRequest.SetEq(tracks.map { it.id }, artist.name)) },
        "Get Info" to { Dialogs.show(DialogRequest.Info(tracks.map { it.id })) },
        "Delete from Phone" to { Dialogs.show(DialogRequest.Delete(tracks.map { it.id })) },
    )
    val info = (if (kind == "tvshow") plural(artist.albums.size, "season") else plural(artist.albums.size, "album")) +
        " · " + plural(tracks.size, if (kind == "tvshow") "episode" else "song") + "\n" + formatLength(tracks.sumOf { it.totalTime })
    // Play and Shuffle take in the Featured On songs too (unless Exclude Featured hides them).
    val all = tracks + featuredOn
    val by = shuffleBy(settings.artistPlay)
    val other = if (by == ArtistPlay.SHUFFLE_ALBUMS) ArtistPlay.SHUFFLE_SONGS else ArtistPlay.SHUFFLE_ALBUMS
    PlayCardFor(
        play = { playWith(app, nav, all, ArtistPlay.FROM_TOP) },
        shuffle = { playWith(app, nav, all, by) },
        label = if (by == ArtistPlay.SHUFFLE_ALBUMS) "By Albums" else "By Songs",
    )
    Page(nav, menu, artist.name) {
        pageTop(artTrackOf(tracks), artist.name, info, maxArt = 140.dp) {
            // Each shows what a tap switches to.
            ToggleChip(
                LuxIcons.Shuffle, if (other == ArtistPlay.SHUFFLE_ALBUMS) "by Albums" else "by Songs",
                if (other == ArtistPlay.SHUFFLE_ALBUMS) "Shuffle by albums" else "Shuffle by songs",
            ) { app.prefs.update { it.copy(artistPlay = other) } }
            ToggleChip(
                if (settings.artistSongs) Icons.Filled.Album else Icons.AutoMirrored.Filled.ViewList,
                if (settings.artistSongs) "Album View" else "Song View",
                if (settings.artistSongs) "Show albums" else "Show all songs",
            ) { app.prefs.update { it.copy(artistSongs = !it.artistSongs) } }
        }
        item {
            Text(
                artist.name + if (years.isNotEmpty()) "  ·  ${years.min()}–${years.max()}" else "",
                color = Lux.dim, fontSize = 15.sp,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
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
        if (featuredOn.isNotEmpty()) {
            item {
                // Styled like the artist line above.
                val featuredYears = featuredOn.map { it.year }.filter { it > 0 }
                Text(
                    "Featured On" + if (featuredYears.isNotEmpty()) "  ·  ${featuredYears.min()}–${featuredYears.max()}" else "",
                    color = Lux.dim, fontSize = 15.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(featuredOn, key = { _, t -> "featured\u0000" + t.id }) { i, t ->
                SongRow(
                    track = t,
                    current = t.id == playerState.currentId,
                    onClick = { tapSong(app, nav, featuredOn, i) },
                    menu = songMenu(t, nav),
                    leading = { Artwork(t, Modifier.size(56.dp), noteSize = 28.dp) },
                    subtitle = "${t.artist} · ${t.album}",
                )
            }
        }
    }
}

private fun playWith(app: LuxTunesApp, nav: NavHostController, tracks: List<Track>, how: ArtistPlay) {
    if (tracks.isEmpty()) return
    when (how) {
        ArtistPlay.FROM_TOP -> app.player.play(tracks)
        ArtistPlay.SHUFFLE_ALBUMS -> app.player.playShuffled(shuffleGroups(tracks) { it.albumKey }, tracks)
        ArtistPlay.SHUFFLE_SONGS -> { app.player.shuffle(tracks) { showPlayer(nav, tracks) }; return }
    }
    showPlayer(nav, tracks)
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
    val settings by app.prefs.settings.collectAsState()
    val kindText = when {
        playlist.isSmart -> "Smart Playlist"
        playlist.onPhone -> "Made on this phone"
        else -> "Playlist"
    }
    PlayCardFor(
        play = { playWith(app, nav, tracks, ArtistPlay.FROM_TOP) },
        shuffle = { playWith(app, nav, tracks, ArtistPlay.SHUFFLE_SONGS) },
    )
    Page(nav, playlistMenu(playlist, nav), playlist.name) {
        pageTop(artTrackOf(tracks), playlist.name, kindText + "\n" + lengthOf(tracks), maxArt = 176.dp)
        if (playlist.changed) item {
            Text(
                "Changes go to LuxTunes at the next sync",
                color = Lux.dim, fontSize = 14.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        itemsIndexed(entries, key = { _, e -> "${e.first}:${e.second.id}" }) { i, (pos, t) ->
            val remove: Menu = if (!playlist.isSmart && library.canEdit(playlist)) {
                listOf("Remove from Playlist" to { app.repository.removeFromPlaylist(playlist.id, pos) })
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalMiniSpace.current)) {
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
                    // Now Playing takes the queue's place, so Back goes to the screen before both.
                    nav.navigate(Routes.NOW) { popUpTo(Routes.QUEUE) { inclusive = true }; launchSingleTop = true }
                },
                menu = emptyList(),
            )
        }
    }
}
