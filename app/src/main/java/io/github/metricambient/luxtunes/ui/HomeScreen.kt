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

/** The tabs whose rows can be ticked (Edit) and that have a sort order. */
private val EDITABLE = listOf("Artists", "Album Artists", "Songs", "Albums")

/**
 * The overview: the tab's name at the top with its play, shuffle, search and sort
 * buttons; the list; the tabs along the bottom, with the main ⋮ menu at the right.
 */
@Composable
fun HomeScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val settings by app.prefs.settings.collectAsState()
    val tabs = settings.tabs.ifEmpty { listOf("Songs") }
    var tabName by rememberSaveable { mutableStateOf(tabs.first()) }
    if (tabName !in tabs) tabName = tabs.first()
    val queries = remember { mutableStateOf(mapOf<String, String>()) }
    val tabStates = rememberSaveableStateHolder()
    val query = queries.value[tabName].orEmpty()
    val setQuery = { q: String -> queries.value = queries.value + (tabName to q) }
    // The search box shows after tapping the search button (and while it has text in it).
    var searching by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.hideSearch) { if (settings.hideSearch) { queries.value = emptyMap(); searching = false } }

    // Select mode: ticked artist names / album keys / song IDs.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    fun stopSelecting() { selecting = false; selected = emptySet() }
    val toggle = { key: String -> selected = if (key in selected) selected - key else selected + key }
    LaunchedEffect(tabName) { stopSelecting() }

    // The songs the ticked rows stand for, in a sensible order.
    fun selectedTracks(): List<Track> = when (tabName) {
        "Artists" -> library.trackArtists.filter { it.name in selected }.flatMap { it.tracks }
        "Album Artists" -> library.artists.filter { it.name in selected }.flatMap { it.tracks }
        "Albums" -> library.albums.filter { it.key in selected }.flatMap { it.tracks }
        else -> library.tracks.filter { it.id in selected }
    }

    val menu: Menu = (if (tabName in EDITABLE) listOf("Edit $tabName" to { selecting = true }) else emptyList<Pair<String, () -> Unit>>()) +
        sleepTimerMenu() + listOf(
            "Search" to { nav.go(Routes.SEARCH) },
            "Rescan Device" to { app.repository.refreshAsync() },
            "Add Folder to Library…" to { AddFolder.show() },
            "Switch Theme" to { nav.go(Routes.SETTINGS_THEME) },
            "Settings" to { nav.go(Routes.SETTINGS) },
        )

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectBar(
                count = selected.size,
                onClose = { stopSelecting() },
                onQueue = { app.player.addToQueue(selectedTracks()); stopSelecting() },
                onPlaylist = { Dialogs.show(DialogRequest.AddToPlaylist(selectedTracks().map { it.id })); stopSelecting() },
                onInfo = { Dialogs.show(DialogRequest.Info(selectedTracks().map { it.id })); stopSelecting() },
                onDelete = { Dialogs.show(DialogRequest.Delete(selectedTracks().map { it.id })); stopSelecting() },
            )
        } else {
            TabHeader(nav, tabName, library, settings, query, onSearch = { searching = !searching || query.isNotEmpty() })
        }
        Column(Modifier.weight(1f)) {
            if (library.allTracks.isEmpty()) {
                Empty(library)
            } else {
                if (!settings.hideSearch && (searching || query.isNotEmpty())) {
                    SearchField(query, setQuery, Modifier.padding(vertical = 4.dp))
                }
                Box(Modifier.weight(1f)) {
                  // Each tab keeps its own scroll position while you're on other tabs or screens.
                  tabStates.SaveableStateProvider(tabName) {
                    when (tabName) {
                        "Artists" -> GroupTab(nav, library.trackArtists, "trackartist", Icons.Filled.Person,
                            settings.artistIcons, GroupAction.VIEW, settings.artistSort, query, selecting, selected, toggle, settings.artistSortReverse)
                        "Album Artists" -> GroupTab(nav, library.artists, "artist", Icons.Filled.Person,
                            settings.albumArtistIcons, GroupAction.VIEW, settings.artistSort, query, selecting, selected, toggle, settings.artistSortReverse)
                        "Genres" -> GroupTab(nav, library.genres, "genre", Icons.Filled.Category,
                            settings.genreIcons, settings.genreAction, GroupSort.NAME, query, false, emptySet(), toggle)
                        "Composers" -> GroupTab(nav, library.composers, "composer", Icons.Filled.Piano,
                            settings.composerIcons, settings.composerAction, GroupSort.NAME, query, false, emptySet(), toggle)
                        "Movies" -> MoviesTab(nav, library.movies, query)
                        "TV Shows" -> TvShowsTab(nav, library.tvShows, query)
                        "Playlists" -> PlaylistsTab(nav, library, settings, query)
                        "Songs" -> SongsTab(nav, library, settings, query, selecting, selected, toggle)
                        "Albums" -> AlbumsTab(nav, library, settings, query, selecting, selected, toggle)
                        "Podcasts" -> ShowsTab(nav, library.podcasts, query, settings.podcastAction, "No podcasts on this phone. Choose some on the Podcasts tab of the phone's page in LuxTunes.")
                        "Audiobooks" -> ShowsTab(nav, library.audiobooks, query, settings.audiobookAction, "No audiobooks on this phone. Choose some on the Books tab of the phone's page in LuxTunes.")
                    }
                  }
                }
            }
        }
        BottomTabs(
            tabs = tabs,
            selected = tabName,
            onSelect = { tabName = it },
            onReorder = { order -> app.prefs.update { it.copy(tabs = order) } },
            menu = menu,
        )
    }
}

/** The songs a tab's play and shuffle buttons start, in the tab's order; null: the tab has none. */
private fun tabSongs(tabName: String, library: LibraryState, settings: AppSettings, query: String): List<Track>? = when (tabName) {
    "Songs" -> sortSongs(library.tracks.filter { matches(query, it.name, it.artist, it.album, it.featuredArtist.orEmpty()) }, settings.songSort, settings.songSortReverse)
    "Albums" -> sortAlbums(library.albums, settings.albumSort, settings.albumSortReverse).flatMap { it.tracks }
    "Artists" -> sortArtists(library.trackArtists, settings.artistSort, settings.artistSortReverse).flatMap { it.tracks }
    "Album Artists" -> sortArtists(library.artists, settings.artistSort, settings.artistSortReverse).flatMap { it.tracks }
    "Genres" -> library.genres.flatMap { it.tracks }
    "Composers" -> library.composers.flatMap { it.tracks }
    else -> null
}

/** The top of the overview: the tab's name, then play from the top, shuffle, search and sort. */
@Composable
private fun TabHeader(
    nav: NavHostController, tabName: String, library: LibraryState, settings: AppSettings, query: String, onSearch: () -> Unit,
) {
    val app = app()
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tabName, color = Lux.text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        val hasSongs = tabSongs(tabName, library, settings, "") != null && library.allTracks.isNotEmpty()
        if (hasSongs) {
            val icon = LuxIcons.tab(tabName)
            IconButton(onClick = {
                val songs = tabSongs(tabName, library, settings, query).orEmpty()
                if (songs.isNotEmpty()) app.player.play(songs) { showPlayer(nav, songs) }
            }) { TabActionIcon(icon, shuffle = false, description = "Play $tabName from the top") }
            IconButton(onClick = {
                val songs = tabSongs(tabName, library, settings, query).orEmpty()
                if (songs.isNotEmpty()) app.player.shuffle(songs) { showPlayer(nav, songs) }
            }) { TabActionIcon(icon, shuffle = true, description = "Shuffle $tabName") }
        }
        if (!settings.hideSearch && library.allTracks.isNotEmpty()) {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search", tint = Lux.icon, modifier = Modifier.size(26.dp)) }
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

/** The top bar in select mode: close, how many, add to queue / playlist, get info, delete. */
@Composable
private fun SelectBar(
    count: Int, onClose: () -> Unit, onQueue: () -> Unit, onPlaylist: () -> Unit, onInfo: () -> Unit, onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(Lux.bar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Done", tint = Lux.text) }
        Text("$count selected", color = Lux.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
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
    selecting: Boolean, selected: Set<String>, toggle: (String) -> Unit,
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

@Composable
private fun AlbumsTab(
    nav: NavHostController, library: LibraryState, settings: AppSettings, query: String,
    selecting: Boolean, selected: Set<String>, toggle: (String) -> Unit,
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
        contentPadding = PaddingValues(bottom = LocalMiniSpace.current),
    ) {
        items(albums, key = { it.key }) { album ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable {
                        if (selecting) toggle(album.key)
                        else tapGroup(app, nav, album.tracks, settings.albumAction, Routes.album(album.key))
                    },
            ) {
                if (settings.albumArt) Artwork(album.artTrack, Modifier.fillMaxSize(), noteSize = (96 / columns * 2).dp)
                else Box(Modifier.fillMaxSize().background(Lux.panel))
                Text(
                    album.name,
                    color = Lux.text, fontSize = if (columns > 2) 13.sp else 17.sp,
                    maxLines = if (settings.albumArt) 1 else 3, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(if (settings.albumArt) Alignment.BottomCenter else Alignment.Center)
                        .fillMaxWidth()
                        .background(if (settings.albumArt) Lux.shade else Lux.panel)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                )
                if (selecting) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { Tick(album.key in selected) }
            }
        }
    }
    }
    }
}
