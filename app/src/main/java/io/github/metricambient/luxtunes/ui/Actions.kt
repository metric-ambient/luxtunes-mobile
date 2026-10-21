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
    showPlayer(nav, listOf(t))
}

/**
 * After starting playback: Now Playing (or the video screen for movies and TV).
 * With Settings → Operation → Launch Now Playing View off, music stays where you are.
 */
fun showPlayer(nav: NavHostController, tracks: List<Track>) {
    val video = tracks.firstOrNull()?.isVideo == true
    val app = nav.context.applicationContext as LuxTunesApp
    if (video) nav.go(Routes.VIDEO) else if (app.prefs.current.launchNowPlaying) nav.go(Routes.NOW)
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
        GroupAction.SHUFFLE_ALBUMS -> app.player.playShuffled(shuffleGroups(tracks) { it.albumKey }, tracks)
        GroupAction.SHUFFLE_ARTISTS -> app.player.playShuffled(shuffleGroups(tracks) { it.albumArtist.lowercase() }, tracks)
    }
    showPlayer(nav, tracks)
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
        "Play" to { app.player.play(listOf(t)); showPlayer(nav, listOf(t)) },
        "Add to Queue" to { app.player.addToQueue(listOf(t)) },
        "Add to End of Queue" to { app.player.addToEnd(listOf(t)) },
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
        "Add to Queue" to { app.player.addToQueue(tracks) },
        "Add to End of Queue" to { app.player.addToEnd(tracks) },
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
        "Add to Queue" to { app.player.addToQueue(tracks) },
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
