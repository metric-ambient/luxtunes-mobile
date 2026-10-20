package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.data.Album
import io.github.metricambient.luxtunes.data.LibraryState
import io.github.metricambient.luxtunes.data.Track

private val TABS = listOf("Artists", "Playlists", "Songs", "Albums")

/** The overview: tabs along the top, a search box in each, actions along the bottom. */
@Composable
fun HomeScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val queries = remember { mutableStateOf(List(TABS.size) { "" }) }
    val query = queries.value[tab]
    val setQuery = { q: String -> queries.value = queries.value.toMutableList().also { it[tab] = q } }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = Lux.background) {
            TABS.forEachIndexed { i, name ->
                Tab(
                    selected = i == tab,
                    onClick = { tab = i },
                    text = { Text(name.uppercase(), fontSize = 16.sp, maxLines = 1) },
                    selectedContentColor = Lux.text,
                    unselectedContentColor = Lux.dim,
                )
            }
        }
        if (library.tracks.isEmpty()) {
            Empty(library)
            return@Column
        }
        if (TABS[tab] != "Albums") SearchField(query, setQuery, Modifier.padding(vertical = 4.dp))
        Box(Modifier.weight(1f)) {
            when (TABS[tab]) {
                "Artists" -> ArtistsTab(nav, library, query)
                "Playlists" -> PlaylistsTab(nav, library, query)
                "Songs" -> SongsTab(nav, library, query)
                "Albums" -> AlbumsTab(nav, library)
            }
        }
        val player = app.player
        val all = library.tracks
        val byArtist = { all.sortedWith(compareBy({ it.sortAlbumArtist.lowercase() }, { it.albumKey }, { it.discNumber }, { it.trackNumber })) }
        val leading = if (TABS[tab] == "Songs") Icons.AutoMirrored.Filled.PlaylistPlay else Icons.Filled.PlayArrow
        if (TABS[tab] != "Playlists") {
            ActionBar(listOf(
                BarAction(leading, "Play all") {
                    if (TABS[tab] == "Songs") player.play(all.filter { matches(query, it.name, it.artist, it.album) })
                    else player.play(byArtist())
                },
                BarAction(Icons.Filled.Shuffle, "Shuffle all") { player.shuffle(all) },
                BarAction(Icons.AutoMirrored.Filled.Sort, "Sort by (coming next)", enabled = false) {},
                BarAction(Icons.Filled.Checklist, "Select (coming next)", enabled = false) {},
            ))
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
private fun ArtistsTab(nav: NavHostController, library: LibraryState, query: String) {
    val player = app().player
    val artists = library.artists.filter { matches(query, it.name) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist ->
            ListRow(
                title = artist.name,
                subtitle = plural(artist.albums.size, "album"),
                onClick = { nav.go(Routes.artist(artist.name)) },
                leading = { Icon(Icons.Filled.Person, null, tint = Lux.text, modifier = Modifier.size(48.dp)) },
                menu = listOf(
                    "Play" to { player.play(artist.tracks) },
                    "Shuffle" to { player.shuffle(artist.tracks) },
                    "Add to queue" to { player.addToQueue(artist.tracks) },
                ),
            )
        }
    }
}

@Composable
private fun SongsTab(nav: NavHostController, library: LibraryState, query: String) {
    val app = app()
    val playerState by app.player.state.collectAsState()
    val songs = library.tracks.filter { matches(query, it.name, it.artist, it.album) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(songs, key = { it.id }) { t ->
            SongRow(
                track = t,
                current = t.id == playerState.currentId,
                onClick = { app.player.play(songs, songs.indexOf(t)); nav.go(Routes.NOW) },
                menu = songMenu(t),
            )
        }
    }
}

/** The ⋮ menu of a song in a list. */
@Composable
fun songMenu(t: Track): List<Pair<String, () -> Unit>> {
    val player = app().player
    return listOf(
        "Play" to { player.play(listOf(t)) },
        "Add to queue" to { player.addToQueue(listOf(t)) },
    )
}

@Composable
private fun PlaylistsTab(nav: NavHostController, library: LibraryState, query: String) {
    val playerState by app().player.state.collectAsState()
    val lists = library.playlists.filter { matches(query, it.name) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListRow(
                title = "Now Playing",
                subtitle = plural(playerState.queue.size, "song"),
                onClick = { nav.go(Routes.QUEUE) },
                leading = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Lux.text, modifier = Modifier.size(40.dp)) },
            )
        }
        items(lists, key = { it.id }) { p ->
            ListRow(
                title = p.name,
                subtitle = (if (p.isSmart) "Smart playlist · " else "") + plural(p.trackIds.size, "song"),
                onClick = { nav.go(Routes.playlist(p.id)) },
                leading = {
                    Icon(
                        if (p.isSmart) Icons.Filled.Bolt else Icons.AutoMirrored.Filled.QueueMusic, null,
                        tint = if (p.isSmart) Lux.accent else Lux.text, modifier = Modifier.size(40.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun AlbumsTab(nav: NavHostController, library: LibraryState) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(library.albums, key = { it.key }) { album ->
            AlbumTile(album) { nav.go(Routes.album(album.key)) }
        }
    }
}

@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onClick)) {
        Artwork(album.artwork, Modifier.fillMaxSize(), noteSize = 96.dp)
        Text(
            album.name,
            color = Lux.text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Lux.shade)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}
