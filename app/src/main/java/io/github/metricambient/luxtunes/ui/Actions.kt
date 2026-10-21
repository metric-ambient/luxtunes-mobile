package io.github.metricambient.luxtunes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.LuxTunesApp
import io.github.metricambient.luxtunes.data.GroupAction
import io.github.metricambient.luxtunes.data.Playlist
import io.github.metricambient.luxtunes.data.SongAction
import io.github.metricambient.luxtunes.data.Track
import io.github.metricambient.luxtunes.data.shuffleGroups

typealias Menu = List<Pair<String, () -> Unit>>

/**
 * A tap on a song in `list`: what plays follows Settings → Tabs → Default Song
 * Action, and Now Playing always opens.
 */
fun tapSong(app: LuxTunesApp, nav: NavHostController, list: List<Track>, index: Int) {
    val t = list.getOrNull(index) ?: return
    when (app.prefs.current.songAction) {
        SongAction.PLAY -> app.player.play(list, index)
        SongAction.PLAY_ONE -> app.player.play(listOf(t))
        SongAction.SHUFFLE -> app.player.shuffleFrom(list, t)
    }
    nav.go(if (t.isVideo) Routes.VIDEO else Routes.NOW)
}

/** After starting playback: Now Playing (or the video screen for movies and TV). */
fun showPlayer(nav: NavHostController, tracks: List<Track>) {
    nav.go(if (tracks.firstOrNull()?.isVideo == true) Routes.VIDEO else Routes.NOW)
}

/** Movies and TV episodes: just this one (or the season from here), in the video screen. */
fun playVideo(app: LuxTunesApp, nav: NavHostController, list: List<Track>, index: Int) {
    app.player.play(list, index)
    nav.go(Routes.VIDEO)
}

/** Play a group of songs (artist, album, playlist) the way an action asks. */
fun playGroup(app: LuxTunesApp, nav: NavHostController, tracks: List<Track>, action: GroupAction) {
    if (tracks.isEmpty()) return
    when (action) {
        GroupAction.VIEW, GroupAction.PLAY -> app.player.play(tracks)
        GroupAction.SHUFFLE -> app.player.shuffle(tracks)
        GroupAction.SHUFFLE_ALBUMS -> app.player.play(shuffleGroups(tracks) { it.albumKey })
        GroupAction.SHUFFLE_ARTISTS -> app.player.play(shuffleGroups(tracks) { it.albumArtist.lowercase() })
    }
    nav.go(if (tracks.first().isVideo) Routes.VIDEO else Routes.NOW)
}

/** A tap on an artist, album or playlist: open it, or play it (Settings → Tabs). */
fun tapGroup(app: LuxTunesApp, nav: NavHostController, tracks: List<Track>, action: GroupAction, route: String) {
    if (action == GroupAction.VIEW) nav.go(route) else playGroup(app, nav, tracks, action)
}

/** The ⋮ menu of a song. `extra` adds items for where it's shown (e.g. remove from playlist). */
@Composable
fun songMenu(t: Track, nav: NavHostController, extra: Menu = emptyList()): Menu {
    val app = app()
    return listOf(
        "Play" to { app.player.play(listOf(t)); nav.go(if (t.isVideo) Routes.VIDEO else Routes.NOW) },
        "Play next" to { app.player.playNext(listOf(t)) },
        "Add to Now Playing" to { app.player.addToQueue(listOf(t)) },
        "Add to playlist…" to { Dialogs.show(DialogRequest.AddToPlaylist(listOf(t.id))) },
        "Go to album" to { nav.go(Routes.album(t.albumKey)) },
        "Go to artist" to { nav.go(Routes.artist(t.albumArtist)) },
        "Get info" to { Dialogs.show(DialogRequest.Info(t.id)) },
    ) + extra + listOf(
        "Delete from phone" to { Dialogs.show(DialogRequest.Delete(listOf(t.id))) },
    )
}

/** The ⋮ menu of a group of songs (artist, album). */
@Composable
fun groupMenu(tracks: List<Track>, nav: NavHostController): Menu {
    val app = app()
    val ids = tracks.map { it.id }
    return listOf(
        "Play" to { playGroup(app, nav, tracks, GroupAction.PLAY) },
        "Shuffle" to { playGroup(app, nav, tracks, GroupAction.SHUFFLE) },
        "Play next" to { app.player.playNext(tracks) },
        "Add to Now Playing" to { app.player.addToQueue(tracks) },
        "Add to playlist…" to { Dialogs.show(DialogRequest.AddToPlaylist(ids)) },
        "Delete from phone" to { Dialogs.show(DialogRequest.Delete(ids)) },
    )
}

/** The ⋮ menu of a playlist. Phone playlists can also be renamed and deleted. */
@Composable
fun playlistMenu(p: Playlist, nav: NavHostController): Menu {
    val app = app()
    val library by app.repository.state.collectAsState()
    val tracks = p.trackIds.mapNotNull { library.byId[it] }
    val base: Menu = listOf(
        "Play" to { playGroup(app, nav, tracks, GroupAction.PLAY) },
        "Shuffle" to { playGroup(app, nav, tracks, GroupAction.SHUFFLE) },
        "Add to Now Playing" to { app.player.addToQueue(tracks) },
    )
    if (!library.canEdit(p)) return base
    val smart: Menu = if (p.isSmart && p.smart != null) {
        listOf("Edit smart playlist" to { nav.go(Routes.smartEdit(p.id)) })
    } else emptyList()
    return base + smart + listOf(
        "Rename" to { Dialogs.show(DialogRequest.RenamePlaylist(p.id, p.name)) },
        "Delete playlist" to { Dialogs.show(DialogRequest.DeletePlaylist(p.id, p.name)) },
    )
}
