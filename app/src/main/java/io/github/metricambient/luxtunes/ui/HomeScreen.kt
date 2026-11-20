package io.github.metricambient.luxtunes.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.data.Album
import io.github.metricambient.luxtunes.data.AppSettings
import io.github.metricambient.luxtunes.data.Artist
import io.github.metricambient.luxtunes.data.GroupAction
import io.github.metricambient.luxtunes.data.GroupSort
import io.github.metricambient.luxtunes.data.LibraryState
import io.github.metricambient.luxtunes.data.Playlist
import io.github.metricambient.luxtunes.data.SongSort
import io.github.metricambient.luxtunes.data.Track
import io.github.metricambient.luxtunes.data.sortText

// ---- Sorting ------------------------------------------------------------------------------

fun sortSongs(songs: List<Track>, sort: SongSort, reverse: Boolean = false): List<Track> =
    sortSongsOnly(songs, sort).let { if (reverse) it.reversed() else it }

private fun sortSongsOnly(songs: List<Track>, sort: SongSort): List<Track> = when (sort) {
    SongSort.NAME -> songs                       // already in name order
    SongSort.ARTIST -> songs.sortedWith(compareBy({ sortText(it.sortArtist) }, { it.albumKey }, { it.discNumber }, { it.trackNumber }))
    SongSort.ALBUM -> songs.sortedWith(compareBy({ sortText(it.sortAlbum) }, { it.discNumber }, { it.trackNumber }))
    SongSort.RATING -> songs.sortedByDescending { it.rating }
    SongSort.PLAYS -> songs.sortedByDescending { it.playCount }
    SongSort.RECENT -> songs.sortedByDescending { it.playDate ?: "" }
}

fun sortArtists(artists: List<Artist>, sort: GroupSort, reverse: Boolean = false): List<Artist> = when (sort) {
    GroupSort.SONGS -> artists.sortedByDescending { a -> a.albums.sumOf { it.tracks.size } }
    else -> artists
}.let { if (reverse) it.reversed() else it }

fun sortAlbums(albums: List<Album>, sort: GroupSort, reverse: Boolean = false): List<Album> =
    sortAlbumsOnly(albums, sort).let { if (reverse) it.reversed() else it }

private fun sortAlbumsOnly(albums: List<Album>, sort: GroupSort): List<Album> = when (sort) {
    GroupSort.NAME -> albums
    GroupSort.ARTIST -> albums.sortedWith(compareBy({ sortText(it.artist) }, { it.year }))
    GroupSort.YEAR -> albums.sortedWith(compareBy({ if (it.year == 0) Int.MAX_VALUE else it.year }, { sortText(it.name) }))
    GroupSort.SONGS -> albums.sortedByDescending { it.tracks.size }
}

// ---- What the fast-scroll bubble shows, by sort order --------------------------------------

private fun songLabel(t: Track, sort: SongSort): String = when (sort) {
    SongSort.NAME -> letterOf(t.sortName.ifBlank { t.name })
    SongSort.ARTIST -> letterOf(t.sortArtist.ifBlank { t.artist })
    SongSort.ALBUM -> letterOf(t.sortAlbum.ifBlank { t.album })
    SongSort.RATING -> if (t.rating >= 20) "★".repeat((t.rating + 10) / 20) else "–"
    SongSort.PLAYS -> "${t.playCount}"
    SongSort.RECENT -> t.playDate?.take(7) ?: "Never"
}

private fun groupLabel(a: Artist, sort: GroupSort): String =
    if (sort == GroupSort.SONGS) "${a.tracks.size}" else letterOf(a.name)

private fun albumLabel(a: Album, sort: GroupSort): String = when (sort) {
    GroupSort.NAME -> letterOf(a.tracks.firstOrNull()?.sortAlbum?.ifBlank { null } ?: a.name)
    GroupSort.ARTIST -> letterOf(a.artist)
    GroupSort.YEAR -> if (a.year > 0) "${a.year}" else "–"
    GroupSort.SONGS -> "${a.tracks.size}"
}


// ---- The screen ---------------------------------------------------------------------------

/** The tabs whose rows can be ticked (long-press) and that have a sort order. */
private val EDITABLE = listOf("Artists", "Album Artists", "Songs", "Albums")

/**
 * The overview: the tab's name at the top with Search and Sort; the list; Play and
 * Shuffle beside the floating player; the tabs along the bottom with More at the right.
 * Long-press a row to start ticking several.
 */
@Composable
fun HomeScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val settings by app.prefs.settings.collectAsState()
    val tabs = settings.tabs.ifEmpty { listOf("Songs") }
    var tabName by rememberSaveable { mutableStateOf(tabs.first()) }
    if (tabName !in tabs) tabName = tabs.first()
    val tabStates = rememberSaveableStateHolder()
    val query = ""          // (searching is the Search screen now)

    // Select mode: ticked artist names / album keys / song IDs.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    fun stopSelecting() { selecting = false; selected = emptySet() }
    val toggle = { key: String -> selected = if (key in selected) selected - key else selected + key }
    // A long-press starts ticking with that row ticked (and in select mode, ticks or unticks it).
    val longPress = { key: String -> if (!selecting) { selecting = true; selected = setOf(key) } else toggle(key) }
    LaunchedEffect(tabName) { stopSelecting() }
    BackHandler(enabled = selecting) { stopSelecting() }

    // Everything the tab lists, as select keys (for Select all).
    fun allKeys(): Set<String> = when (tabName) {
        "Artists" -> library.trackArtists.map { it.name }
        "Album Artists" -> library.artists.map { it.name }
        "Albums" -> library.albums.map { it.key }
        else -> library.tracks.map { it.id }
    }.toSet()

    // The songs the ticked rows stand for, in a sensible order.
    fun selectedTracks(): List<Track> = when (tabName) {
        "Artists" -> library.trackArtists.filter { it.name in selected }.flatMap { it.tracks }
        "Album Artists" -> library.artists.filter { it.name in selected }.flatMap { it.tracks }
        "Albums" -> library.albums.filter { it.key in selected }.flatMap { it.tracks }
        else -> library.tracks.filter { it.id in selected }
    }

    // Play and Shuffle beside the floating player play this tab, in its order.
    if (library.allTracks.isNotEmpty() && tabName in listOf("Songs", "Albums", "Artists", "Album Artists", "Genres", "Composers")) {
        PlayCardFor(
            play = { tabSongs(tabName, library, settings)?.takeIf { it.isNotEmpty() }?.let { s -> app.player.play(s) { showPlayer(nav, s) } } },
            shuffle = { tabSongs(tabName, library, settings)?.takeIf { it.isNotEmpty() }?.let { s -> app.player.shuffle(s) { showPlayer(nav, s) } } },
        )
    }

    val sleep = sleepTimerMenu().map { (label, action) -> MoreItem(label, Icons.Filled.Bedtime, action) }
    val more = sleep + listOf(
        MoreItem("Add Folder to Library…", Icons.Filled.CreateNewFolder) { AddFolder.show() },
        MoreItem("Rescan Device", Icons.Filled.Sync) { Notice.show("Looking for a new sync…"); app.repository.refreshAsync() },
        MoreItem("Change Theme", Icons.Filled.Palette) { nav.go(Routes.SETTINGS_THEME) },
        MoreItem("Check for Updates", Icons.Filled.SystemUpdate) { UpdateCheck.start() },
        MoreItem("Settings", Icons.Filled.Settings) { nav.go(Routes.SETTINGS) },
    )

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            val all = allKeys()
            SelectBar(
                count = selected.size,
                allTicked = all.isNotEmpty() && selected.containsAll(all),
                onClose = { stopSelecting() },
                onAll = { selected = if (selected.containsAll(all)) emptySet() else all },
                onQueue = { app.player.addToQueue(selectedTracks()); stopSelecting() },
                onPlaylist = { Dialogs.show(DialogRequest.AddToPlaylist(selectedTracks().map { it.id })); stopSelecting() },
                onInfo = { Dialogs.show(DialogRequest.Info(selectedTracks().map { it.id })); stopSelecting() },
                onDelete = { Dialogs.show(DialogRequest.Delete(selectedTracks().map { it.id })); stopSelecting() },
            )
        } else {
            TabHeader(nav, tabName, library, settings)
        }
        Box(Modifier.weight(1f)) {
            if (library.allTracks.isEmpty()) {
                Empty(library)
            } else {
              // Each tab keeps its own scroll position while you're on other tabs or screens.
              tabStates.SaveableStateProvider(tabName) {
                when (tabName) {
                    "Artists" -> GroupTab(nav, library.trackArtists, "trackartist", Icons.Filled.Person,
                        settings.artistIcons, GroupAction.VIEW, settings.artistSort, query, selecting, selected, toggle, settings.artistSortReverse, longPress)
                    "Album Artists" -> GroupTab(nav, library.artists, "artist", Icons.Filled.Person,
                        settings.albumArtistIcons, GroupAction.VIEW, settings.artistSort, query, selecting, selected, toggle, settings.artistSortReverse, longPress)
                    "Genres" -> GroupTab(nav, library.genres, "genre", Icons.Filled.Category,
                        settings.genreIcons, settings.genreAction, GroupSort.NAME, query, false, emptySet(), toggle)
                    "Composers" -> GroupTab(nav, library.composers, "composer", Icons.Filled.Piano,
                        settings.composerIcons, settings.composerAction, GroupSort.NAME, query, false, emptySet(), toggle)
                    "Movies" -> MoviesTab(nav, library.movies, query)
                    "TV Shows" -> TvShowsTab(nav, library.tvShows, query)
                    "Playlists" -> PlaylistsTab(nav, library, settings, query)
                    "Songs" -> SongsTab(nav, library, settings, query, selecting, selected, toggle, longPress)
                    "Albums" -> AlbumsTab(nav, library, settings, query, selecting, selected, toggle, longPress)
                    "Podcasts" -> ShowsTab(nav, library.podcasts, query, settings.podcastAction, "No podcasts on this phone. Choose some on the Podcasts tab of the phone's page in LuxTunes.")
                    "Audiobooks" -> ShowsTab(nav, library.audiobooks, query, settings.audiobookAction, "No audiobooks on this phone. Choose some on the Books tab of the phone's page in LuxTunes.")
                }
              }
            }
        }
        BottomTabs(
            tabs = tabs,
            selected = tabName,
            onSelect = { tabName = it },
            onReorder = { order -> app.prefs.update { it.copy(tabs = order) } },
            more = more,
        )
    }
}

/** The songs a tab's Play and Shuffle start, in the tab's order; null: the tab has none. */
private fun tabSongs(tabName: String, library: LibraryState, settings: AppSettings): List<Track>? = when (tabName) {
    "Songs" -> sortSongs(library.tracks, settings.songSort, settings.songSortReverse)
    "Albums" -> sortAlbums(library.albums, settings.albumSort, settings.albumSortReverse).flatMap { it.tracks }
    "Artists" -> sortArtists(library.trackArtists, settings.artistSort, settings.artistSortReverse).flatMap { it.tracks }
    "Album Artists" -> sortArtists(library.artists, settings.artistSort, settings.artistSortReverse).flatMap { it.tracks }
    "Genres" -> library.genres.flatMap { it.tracks }
    "Composers" -> library.composers.flatMap { it.tracks }
    else -> null
}

/** The top of the overview: the tab's name, then Search (the full search screen) and Sort. */
@Composable
private fun TabHeader(nav: NavHostController, tabName: String, library: LibraryState, settings: AppSettings) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tabName, color = Lux.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (!settings.hideSearch && library.allTracks.isNotEmpty()) {
            IconButton(onClick = { nav.go(Routes.SEARCH) }) { Icon(Icons.Filled.Search, "Search", tint = Lux.icon, modifier = Modifier.size(26.dp)) }
        }
        if (tabName in EDITABLE) SortButton(tabName, settings)
    }
}

/** Sort by: a menu of the orders that make sense for this tab, the current one ticked. */
@Composable
private fun SortButton(tabName: String, settings: AppSettings) {
    val prefs = app().prefs
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, "Sort by", tint = Lux.icon, modifier = Modifier.size(26.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text("Sort by", color = Lux.accent, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            when (tabName) {
                "Songs" -> SongSort.entries.forEach { s ->
                    SortItem(s.label, s == settings.songSort) { prefs.update { it.copy(songSort = s) }; open = false }
                }
                "Artists", "Album Artists" -> listOf(GroupSort.NAME, GroupSort.SONGS).forEach { s ->
                    SortItem(s.label, s == settings.artistSort) { prefs.update { it.copy(artistSort = s) }; open = false }
                }
                else -> listOf(GroupSort.NAME, GroupSort.ARTIST, GroupSort.YEAR, GroupSort.SONGS).forEach { s ->
                    SortItem(s.label, s == settings.albumSort) { prefs.update { it.copy(albumSort = s) }; open = false }
                }
            }
            HorizontalDivider(color = Lux.panel)
            // The same order, the other way round (least played, oldest first, Z to A…).
            val reversed = when (tabName) {
                "Songs" -> settings.songSortReverse
                "Artists", "Album Artists" -> settings.artistSortReverse
                else -> settings.albumSortReverse
            }
            DropdownMenuItem(
                text = { Text("Inverse sort", color = Lux.text) },
                leadingIcon = {
                    Checkbox(
                        checked = reversed, onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = Lux.accent),
                    )
                },
                onClick = {
                    prefs.update {
                        when (tabName) {
                            "Songs" -> it.copy(songSortReverse = !it.songSortReverse)
                            "Artists", "Album Artists" -> it.copy(artistSortReverse = !it.artistSortReverse)
                            else -> it.copy(albumSortReverse = !it.albumSortReverse)
                        }
                    }
                    open = false
                },
            )
        }
    }
}

@Composable
private fun SortItem(label: String, current: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(if (current) "✓  $label" else "     $label", color = if (current) Lux.accent else Lux.text) },
        onClick = onClick,
    )
}

/**
 * The top bar in select mode: close, how many, add to queue / playlist, get info, delete,
 * and at the far right a box that ticks everything in the tab (tap again to clear).
 */
@Composable
private fun SelectBar(
    count: Int, allTicked: Boolean, onClose: () -> Unit, onAll: () -> Unit,
    onQueue: () -> Unit, onPlaylist: () -> Unit, onInfo: () -> Unit, onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).background(Lux.bottomBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Done", tint = Lux.text) }
        Text("$count selected", color = Lux.text, fontSize = 18.sp, maxLines = 1, modifier = Modifier.weight(1f))
        IconButton(onClick = onQueue, enabled = count > 0) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, "Add to Queue", tint = Lux.icon)
        }
        IconButton(onClick = onPlaylist, enabled = count > 0) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", tint = Lux.icon)
        }
        IconButton(onClick = onInfo, enabled = count > 0) {
            Icon(Icons.Filled.Info, "Get Info", tint = Lux.icon)
        }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(Icons.Filled.Delete, "Delete from Phone", tint = Lux.icon)
        }
        IconButton(onClick = onAll) {
            Icon(
                if (allTicked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                if (allTicked) "Untick all" else "Tick all", tint = if (allTicked) Lux.accent else Lux.text,
            )
        }
    }
}

@Composable
private fun Empty(library: LibraryState) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            if (!library.loaded) "Loading…" else library.message ?: "No songs yet.",
            color = Lux.dim, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Tick(on: Boolean) {
    Icon(
        if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null,
        tint = if (on) Lux.accent else Lux.dim, modifier = Modifier.size(28.dp),
    )
}

// ---- The tabs -------------------------------------------------------------------------------

/** Artists, album artists, genres or composers: one row per name; a tap opens (or plays) it. */
@Composable
private fun GroupTab(
    nav: NavHostController, groups: List<Artist>, kind: String, icon: ImageVector, showIcon: Boolean,
    action: GroupAction, sort: GroupSort, query: String,
    selecting: Boolean, selected: Set<String>, toggle: (String) -> Unit, reverse: Boolean = false,
    longPress: ((String) -> Unit)? = null,
) {
    val app = app()
    val list = sortArtists(groups.filter { matches(query, it.name) }, sort, reverse)
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Nothing here yet.", color = Lux.dim)
        }
        return
    }
    val listState = rememberLazyListState()
    FastScroll(listState, list.size, { i -> list.getOrNull(i)?.let { groupLabel(it, sort) } }) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = LocalMiniSpace.current)) {
        items(list, key = { it.name }) { group ->
            ListRow(
                title = group.name,
                subtitle = plural(group.albums.size, "album") + " · " + plural(group.tracks.size, "song"),
                onClick = {
                    if (selecting) toggle(group.name)
                    else tapGroup(app, nav, group.tracks, action, Routes.group(kind, group.name))
                },
                leading = optional(selecting || showIcon) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) { Tick(group.name in selected); Spacer(Modifier.size(12.dp)) }
                        if (showIcon) Icon(icon, null, tint = Lux.text, modifier = Modifier.size(48.dp))
                    }
                },
                menu = if (selecting) emptyList() else groupMenu(group.tracks, nav, group.name),
                onLongClick = longPress?.let { lp -> { lp(group.name) } },
            )
        }
    }
    }
}

/** Movies: one row each; a tap plays it full screen. */
@Composable
private fun MoviesTab(nav: NavHostController, movies: List<Track>, query: String) {
    val app = app()
    val list = movies.filter { matches(query, it.name, it.genre) }
    if (movies.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No movies on this phone. Choose some on the Movies tab of the phone's page in LuxTunes.",
                color = Lux.dim, textAlign = TextAlign.Center)
        }
        return
    }
    val listState = rememberLazyListState()
    FastScroll(listState, list.size, { i -> list.getOrNull(i)?.let { letterOf(it.sortName.ifBlank { it.name }) } }) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = LocalMiniSpace.current)) {
        items(list, key = { it.id }) { m ->
            ListRow(
                title = m.name,
                subtitle = listOfNotNull(m.year.takeIf { it > 0 }?.toString(), m.genre.ifEmpty { null }, formatTime(m.totalTime))
                    .joinToString(" · "),
                onClick = { playVideo(app, nav, listOf(m), 0) },
                leading = {
                    Artwork(m, Modifier.size(56.dp), noteSize = 40.dp, note = Icons.Filled.Movie)
                },
                menu = songMenu(m, nav),
            )
        }
    }
    }
}

/** TV shows: one row per show; a tap opens its seasons. */
@Composable
private fun TvShowsTab(nav: NavHostController, shows: List<Artist>, query: String) {
    val list = shows.filter { matches(query, it.name) }
    if (shows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No TV shows on this phone. Choose some on the TV Shows tab of the phone's page in LuxTunes.",
                color = Lux.dim, textAlign = TextAlign.Center)
        }
        return
    }
    val listState = rememberLazyListState()
    FastScroll(listState, list.size, { i -> list.getOrNull(i)?.let { letterOf(it.name) } }) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = LocalMiniSpace.current)) {
        items(list, key = { it.name }) { show ->
            ListRow(
                title = show.name,
                subtitle = plural(show.albums.size, "season") + " · " + plural(show.tracks.size, "episode"),
                onClick = { nav.go(Routes.group("tvshow", show.name)) },
                leading = { Icon(Icons.Filled.Tv, null, tint = Lux.text, modifier = Modifier.size(48.dp)) },
            )
        }
    }
    }
}

@Composable
private fun SongsTab(
    nav: NavHostController, library: LibraryState, settings: AppSettings, query: String,
    selecting: Boolean, selected: Set<String>, toggle: (String) -> Unit, longPress: (String) -> Unit,
) {
    val app = app()
    val playerState by app.player.state.collectAsState()
    val songs = sortSongs(library.tracks.filter { matches(query, it.name, it.artist, it.album, it.featuredArtist.orEmpty()) }, settings.songSort, settings.songSortReverse)
    val listState = rememberLazyListState()
    FastScroll(listState, songs.size, { i -> songs.getOrNull(i)?.let { songLabel(it, settings.songSort) } }) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = LocalMiniSpace.current)) {
        items(songs, key = { it.id }) { t ->
            val showArt = settings.songArt
            SongRow(
                track = t,
                current = t.id == playerState.currentId,
                onClick = { if (selecting) toggle(t.id) else tapSong(app, nav, songs, songs.indexOf(t)) },
                menu = if (selecting) emptyList() else songMenu(t, nav),
                leading = optional(selecting || showArt) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) { Tick(t.id in selected); Spacer(Modifier.size(12.dp)) }
                        if (showArt) Artwork(t, Modifier.size(48.dp), noteSize = 24.dp)
                    }
                },
                onLongClick = { longPress(t.id) },
            )
        }
    }
    }
}

@Composable
private fun PlaylistsTab(nav: NavHostController, library: LibraryState, settings: AppSettings, query: String) {
    val app = app()
    val playerState by app.player.state.collectAsState()
    val lists = library.playlists.filter { matches(query, it.name) }
    val icons = settings.playlistIcons
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalMiniSpace.current)) {
        item {
            ListRow(
                title = "Play Queue - Up Next",
                subtitle = plural(playerState.queue.size, "song"),
                onClick = { nav.go(Routes.QUEUE) },
                leading = optional(icons) { PlaylistIcon(Icons.AutoMirrored.Filled.PlaylistPlay, Lux.text) },
            )
        }
        // Smart playlists, then the others (or the other way round: Settings → Operation),
        // each group under its own Create button.
        val groups = if (settings.smartAtEnd) listOf(false, true) else listOf(true, false)
        for (smart in groups) {
            item(key = if (smart) "create-smart" else "create") {
                if (smart) {
                    ListRow(
                        title = "Create Smart Playlist",
                        subtitle = null,
                        onClick = { nav.go(Routes.SMART_NEW) },
                        leading = optional(icons) { PlaylistIcon(Icons.Filled.Bolt, Lux.accent) },
                    )
                } else {
                    ListRow(
                        title = "Create Playlist",
                        subtitle = null,
                        onClick = { Dialogs.show(DialogRequest.NewPlaylist(emptyList())) },
                        leading = optional(icons) { PlaylistIcon(Icons.Filled.Add, Lux.accent) },
                    )
                }
            }
            items(lists.filter { it.isSmart == smart }, key = { it.id }) { p -> PlaylistRow(p, nav, library, settings) }
        }
    }
}

@Composable
private fun PlaylistRow(p: Playlist, nav: NavHostController, library: LibraryState, settings: AppSettings) {
    val app = app()
    val tracks = p.trackIds.mapNotNull { library.byId[it] }
    ListRow(
        title = p.name,
        subtitle = when {
            p.isSmart -> "Smart playlist · "
            p.onPhone -> "Made on this phone · "
            else -> ""
        } + plural(tracks.size, "song") + if (p.changed) " · not synced yet" else "",
        onClick = { tapGroup(app, nav, tracks, settings.playlistAction, Routes.playlist(p.id)) },
        leading = optional(settings.playlistIcons) {
            PlaylistIcon(
                if (p.isSmart) Icons.Filled.Bolt else Icons.AutoMirrored.Filled.QueueMusic,
                if (p.isSmart) Lux.accent else Lux.text,
            )
        },
        menu = playlistMenu(p, nav),
    )
}

/** Podcasts (one row per show) or audiobooks (one per book); a tap opens its episodes or chapters. */
@Composable
private fun ShowsTab(nav: NavHostController, shows: List<Album>, query: String, action: GroupAction, empty: String) {
    val app = app()
    val list = shows.filter { matches(query, it.name, it.artist) }
    if (shows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(empty, color = Lux.dim, textAlign = TextAlign.Center)
        }
        return
    }
    val listState = rememberLazyListState()
    FastScroll(listState, list.size, { i -> list.getOrNull(i)?.let { letterOf(it.name) } }) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = LocalMiniSpace.current)) {
        items(list, key = { it.key }) { show ->
            ListRow(
                title = show.name,
                subtitle = "${show.artist} · ${plural(show.tracks.size, "item")}",
                onClick = { tapGroup(app, nav, show.tracks, action, Routes.album(show.key)) },
                leading = { Artwork(show.artTrack, Modifier.size(56.dp), noteSize = 28.dp) },
                menu = groupMenu(show.tracks, nav),
            )
        }
    }
    }
}

@Composable
private fun PlaylistIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(40.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsTab(
    nav: NavHostController, library: LibraryState, settings: AppSettings, query: String,
    selecting: Boolean, selected: Set<String>, toggle: (String) -> Unit, longPress: (String) -> Unit,
) {
    val app = app()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (landscape) settings.gridLandscape.coerceIn(1, 6) else settings.gridPortrait.coerceIn(1, 4)
    // Quick filters along the top: every album, the newest added, or compilations.
    var filter by rememberSaveable { mutableStateOf("All") }
    val all = library.albums.filter { matches(query, it.name, it.artist) }
    val albums = when (filter) {
        // The 60 albums with the most recently added songs, newest first.
        "Recently Added" -> all.sortedByDescending { a -> a.tracks.maxOfOrNull { it.raw["date_added"]?.toString().orEmpty() }.orEmpty() }.take(60)
        "Compilations" -> sortAlbums(all.filter { a -> a.tracks.any { it.compilation } }, settings.albumSort, settings.albumSortReverse)
        else -> sortAlbums(all, settings.albumSort, settings.albumSortReverse)
    }
    val gridState = rememberLazyGridState()
    Column(Modifier.fillMaxSize()) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (name in listOf("All", "Recently Added", "Compilations")) {
            val on = name == filter
            Text(
                if (name == "All") "All ${all.size}" else name,
                color = if (on) Lux.onAccent else Lux.text, fontSize = 14.sp,
                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) Lux.accent else Lux.panel)
                    .clickable { filter = name }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
    FastScroll(gridState, albums.size, { i -> albums.getOrNull(i)?.let { if (filter == "Recently Added") null else albumLabel(it, settings.albumSort) } }) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(), state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp + LocalMiniSpace.current),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Each album: its rounded cover, then its name and artist underneath.
        items(albums, key = { it.key }) { album ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = {
                            if (selecting) toggle(album.key)
                            else tapGroup(app, nav, album.tracks, settings.albumAction, Routes.album(album.key))
                        },
                        onLongClick = { longPress(album.key) },
                    ),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                    if (settings.albumArt) Artwork(album.artTrack, Modifier.fillMaxSize(), noteSize = (96 / columns * 2).dp)
                    else Box(Modifier.fillMaxSize().background(Lux.placeholder))
                    if (selecting) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { Tick(album.key in selected) }
                }
                Text(
                    album.name, color = Lux.text, fontSize = if (columns > 2) 13.sp else 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    album.artist, color = Lux.dim, fontSize = if (columns > 2) 12.sp else 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
    }
    }
}
