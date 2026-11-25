package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.LuxTunesApp
import io.github.metricambient.luxtunes.data.Track
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

// Radio Mixes are worked out away from the screen; this outlives the screen that started one.
private val radioScope = MainScope()

/** Song menu → Start Radio Mix…: from this song, its album or its artist. */
fun chooseRadio(app: LuxTunesApp, nav: NavHostController, t: Track) {
    val kinds = listOf("song", "album", "artist")
    Dialogs.show(DialogRequest.Choose("Start Radio Mix", listOf("From This Song", "From This Album", "From This Artist"), -1) { i ->
        startRadio(app, nav, kinds[i], t)
    })
}

/**
 * A new Radio Mix (replacing the current one), shown and played from the top. If the song
 * playing is in it (Now Playing's Radio Mix button), that song carries on and the mix
 * plays around it.
 */
fun startRadio(app: LuxTunesApp, nav: NavHostController, kind: String, seed: Track) {
    if (app.radio.busy.value) return
    Notice.show("Starting Radio Mix…")
    radioScope.launch {
        val tracks = app.radio.start(kind, seed)
        if (tracks.isEmpty()) { Notice.show("No related songs found"); return@launch }
        val playingId = app.player.state.value.currentId
        if (playingId != null && tracks.any { it.id == playingId }) app.player.replaceAround(tracks) else app.player.play(tracks)
        nav.go(Routes.RADIO)
    }
}

/**
 * Radio Mix: an endless mix of related songs, from your library, worked out on the phone.
 * Each song has ♥ (like it on mixes like this one) and ⊘ (not interested: off this mix and
 * mixes like it; Remove from Radio Mix does the same). Near the end, 25 more songs are
 * added (PlaybackService).
 */
@Composable
fun RadioScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val playerState by app.player.state.collectAsState()
    val radio by app.radio.radio.collectAsState()
    val opinions by app.radio.opinions.collectAsState()
    val busy by app.radio.busy.collectAsState()
    val tracks = app.radio.tracks(radio, library)
    val playing = playerState.currentId != null && playerState.currentId in radio?.trackIds.orEmpty()

    /** A new mix (Refresh, or Discover on or off). The song playing carries on. */
    fun remix(discover: Boolean? = null) {
        if (busy) return
        radioScope.launch {
            val keep = if (playing) playerState.currentId else null
            val fresh = app.radio.refresh(discover, keep)
            if (playing && fresh.isNotEmpty()) app.player.replaceAround(fresh)
        }
    }
    fun play(index: Int) {
        app.player.play(tracks, index)
        showPlayer(nav, listOfNotNull(tracks.getOrNull(index)))
    }
    /** ⊘, or Remove from Radio Mix: a dislike; skipped if it's playing, and off the mix. */
    fun dislike(t: Track) {
        app.radio.setOpinion(t.id, -1)
        app.radio.remove(setOf(t.id))
        if (playing) app.player.removeFromQueue(setOf(t.id), skipIfPlaying = true)
    }

    val r = radio
    val discoverOn = r?.discover == true
    val menu: Menu = if (r == null) emptyList() else listOf(
        "Save as Playlist" to {
            // Straight to the new playlist, with its name ready to change ("<…> Radio" if left).
            app.repository.createPlaylist(r.title, tracks.map { it.id }) { id ->
                radioScope.launch {
                    withTimeoutOrNull(5_000) { app.repository.state.first { s -> s.playlists.any { it.id == id } } }
                        ?: return@launch
                    nav.go(Routes.playlist(id))
                    Dialogs.show(DialogRequest.RenamePlaylist(id, r.title))
                }
            }
        },
        "Refresh" to { remix() },
        (if (discoverOn) "Turn Discover Off" else "Turn Discover On") to { remix(!discoverOn) },
        "Likes & Dislikes" to { nav.go(Routes.RADIO_LIKES) },
    )
    if (r != null && tracks.isNotEmpty()) PlayCardFor(
        key = "radio",
        play = { play(0) },
        shuffle = { app.player.shuffle(tracks) { showPlayer(nav, tracks) } },
    )
    if (r == null || tracks.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (busy) "Working out the Radio Mix…" else "No Radio Mix yet: start one from a song's menu.",
                    color = Lux.dim, textAlign = TextAlign.Center,
                )
            }
            PageBottomBar(nav, listOf("Likes & Dislikes" to { nav.go(Routes.RADIO_LIKES) }), "Radio Mix")
        }
        return
    }
    val seed = library.byId[r.seedId]
    PageAccent(seed ?: tracks.first())
    val info = "Radio Mix · " + plural(tracks.size, "song") + (if (discoverOn) " · Discover" else "") +
        "\nRelated songs from your library" + if (busy) "\nWorking…" else ""
    // The Radio Mix button here: a new mix from the song playing.
    val playingTrack = playerState.currentId?.let { library.byId[it] }
    val fromPlaying: (() -> Unit)? = playingTrack?.let { t -> { startRadio(app, nav, "song", t) } }
    Page(nav, menu, r.title, tracks, radio = fromPlaying) {
        pageTop(seed ?: tracks.first(), r.title, info, maxArt = 140.dp) {
            ToggleChip(Icons.Filled.Refresh, "Refresh", "A new mix from the same start") { remix() }
            // Lit (accent) while on.
            ToggleChip(
                Icons.Filled.Explore, "Discover",
                "Discover: prioritizes related songs you rarely or never play" + if (discoverOn) " (on)" else " (off)",
                lit = discoverOn,
            ) { remix(!discoverOn) }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            val opinion = opinions[t.id]?.value ?: 0
            SongRow(
                track = t,
                current = t.id == playerState.currentId,
                onClick = { play(i) },
                menu = songMenu(t, nav, listOf("Remove from Radio Mix" to { dislike(t) })),
                leading = {
                    OpinionButtons(opinion, onLike = {
                        app.radio.setOpinion(t.id, if (opinion == 1) 0 else 1)
                    }, onDislike = {
                        if (opinion == -1) app.radio.setOpinion(t.id, 0)
                        else dislike(t)
                    })
                },
                subtitle = "${t.artist} · ${t.album}",
            )
        }
    }
}

/**
 * Every song liked or turned down on a radio, with the radio it was on. Tapping the lit
 * button (or Forget in its menu) sets it back to neither; the song itself stays.
 */
@Composable
fun RadioLikesScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val playerState by app.player.state.collectAsState()
    val opinions by app.radio.opinions.collectAsState()
    val entries = opinions.entries.sortedByDescending { it.value.date }
        .mapNotNull { (id, op) -> library.byId[id]?.let { it to op } }
    val tracks = entries.map { it.first }
    Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = LocalMiniSpace.current),
        ) {
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
                    Text("Radio Mix Likes & Dislikes", color = Lux.text, fontSize = 24.sp)
                    Text(
                        "♥ plays a song more on Radio Mixes like the one you liked it on; ⊘ keeps it off them. " +
                            "Kept on this phone only.",
                        color = Lux.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (entries.isEmpty()) item {
                Text("Nothing yet: use ♥ and ⊘ on the Radio Mix screen.", color = Lux.dim, modifier = Modifier.padding(16.dp))
            }
            itemsIndexed(entries, key = { _, e -> e.first.id }) { i, (t, op) ->
                SongRow(
                    track = t,
                    current = t.id == playerState.currentId,
                    onClick = { tapSong(app, nav, tracks, i) },
                    menu = songMenu(t, nav, listOf("Forget" to { app.radio.setOpinion(t.id, 0) })),
                    leading = {
                        OpinionButtons(op.value, onLike = {
                            app.radio.setOpinion(t.id, if (op.value == 1) 0 else 1, keepRadio = true)
                        }, onDislike = {
                            app.radio.setOpinion(t.id, if (op.value == -1) 0 else -1, keepRadio = true)
                        })
                    },
                    subtitle = t.artist + if (op.radio.isNotEmpty()) " · From ${op.radio}" else "",
                )
            }
        }
        PageBottomBar(nav, emptyList(), "Radio Mix Likes & Dislikes")
    }
}

/** ♥ and ⊘ in front of a Radio Mix song; the chosen one is lit. */
@Composable
private fun OpinionButtons(opinion: Int, onLike: () -> Unit, onDislike: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmallToggle(if (opinion == 1) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            "Like this song on Radio Mixes like this one" + if (opinion == 1) " (liked; tap to undo)" else "", opinion == 1, onLike)
        SmallToggle(Icons.Filled.Block,
            "Not interested: skip it, take it off this Radio Mix and keep it off ones like it" +
                if (opinion == -1) " (chosen; tap to undo)" else "", opinion == -1, onDislike)
    }
}

@Composable
private fun SmallToggle(icon: ImageVector, description: String, lit: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, description, tint = if (lit) Lux.accent else Lux.icon, modifier = Modifier.size(20.dp))
    }
}
