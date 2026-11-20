package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
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
 * centred in the space above the Play and Shuffle buttons, which line up with the art's
 * bottom. `shuffleBy` null: Shuffle has no choices (an album: by songs only).
 */
private fun LazyListScope.pageTop(
    art: Track?,
    title: String,
    info: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    shuffleBy: ArtistPlay? = null,
    onShuffleBy: (ArtistPlay) -> Unit = {},
) {
    item {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
            val artSize = minOf(150.dp, maxWidth * 0.42f)
            // Narrow windows (split screen): Play shows just its icon, so Shuffle keeps its words.
            val roomy = maxWidth - artSize - 12.dp >= 200.dp
            Row(Modifier.fillMaxWidth().height(artSize)) {
                Artwork(art, Modifier.size(artSize).clip(RoundedCornerShape(16.dp)), noteSize = artSize * 0.5f)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        Text(
                            title, color = Lux.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (info.isNotEmpty()) Text(
                            info, color = Lux.dim, fontSize = 13.sp, lineHeight = 17.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayButton(roomy, onPlay)
                        ShuffleButton(Modifier.weight(1f), shuffleBy, onShuffle, onShuffleBy)
                    }
                }
            }
        }
    }
}

/** Play from the top: the accent button. */
@Composable
private fun PlayButton(withWord: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxHeight()
            .width(if (withWord) 72.dp else 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Lux.accent)
            .clickable(onClickLabel = "Play from the top", onClick = onClick)
            .padding(end = if (withWord) 6.dp else 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, if (withWord) null else "Play", tint = Lux.onAccent, modifier = Modifier.size(26.dp))
        if (withWord) Text("Play", color = Lux.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Shuffle, with what it shuffles by underneath (By Songs / By Albums). The switch at the
 * right flips between the two at once; the choice is kept for next time.
 */
@Composable
private fun ShuffleButton(modifier: Modifier, by: ArtistPlay?, onShuffle: () -> Unit, onBy: (ArtistPlay) -> Unit) {
    Row(modifier.fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Lux.raised)) {
        Row(
            Modifier.weight(1f).fillMaxHeight().clickable(onClick = onShuffle).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(LuxIcons.Shuffle, null, tint = Lux.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text("Shuffle", color = Lux.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                if (by != null) Text(
                    if (by == ArtistPlay.SHUFFLE_ALBUMS) "By Albums" else "By Songs",
                    color = Lux.text.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1,
                )
            }
        }
        if (by != null) {
            val other = if (by == ArtistPlay.SHUFFLE_ALBUMS) ArtistPlay.SHUFFLE_SONGS else ArtistPlay.SHUFFLE_ALBUMS
            Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(lerp(Lux.raised, Lux.text, 0.25f)))
            Box(
                Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .clickable(onClickLabel = if (other == ArtistPlay.SHUFFLE_ALBUMS) "Shuffle by albums" else "Shuffle by songs") { onBy(other) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.SwapVert, "Switch between shuffling by songs and by albums", tint = Lux.text, modifier = Modifier.size(22.dp))
            }
        }
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
    val info = listOf(album.artist, if (album.year > 0) "${album.year}" else "").filter { it.isNotBlank() }.joinToString(" · ") +
        "\n" + lengthOf(album.tracks)
    Page(nav, menu, album.name) {
        pageTop(
            album.artTrack, album.name, info,
            onPlay = { playWith(app, nav, album.tracks, ArtistPlay.FROM_TOP) },
            onShuffle = { playWith(app, nav, album.tracks, ArtistPlay.SHUFFLE_SONGS) },
        )
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
        (if (settings.artistSongs) "Show as Albums" else "Show as Songs") to {
            app.prefs.update { it.copy(artistSongs = !it.artistSongs) }
        },
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
    Page(nav, menu, artist.name) {
        pageTop(
            artTrackOf(tracks), artist.name, info,
            onPlay = { playWith(app, nav, all, ArtistPlay.FROM_TOP) },
            onShuffle = { playWith(app, nav, all, by) },
            shuffleBy = by,
            onShuffleBy = { choice -> app.prefs.update { it.copy(artistPlay = choice) } },
        )
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
    val by = shuffleBy(settings.playlistPlay)
    Page(nav, playlistMenu(playlist, nav), playlist.name) {
        pageTop(
            artTrackOf(tracks), playlist.name, kindText + "\n" + lengthOf(tracks),
            onPlay = { playWith(app, nav, tracks, ArtistPlay.FROM_TOP) },
            onShuffle = { playWith(app, nav, tracks, by) },
            shuffleBy = by,
            onShuffleBy = { choice -> app.prefs.update { it.copy(playlistPlay = choice) } },
        )
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
