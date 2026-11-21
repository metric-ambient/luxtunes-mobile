package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import io.github.metricambient.luxtunes.data.Track

/** The bar along the bottom of the main screen, album, artist and playlist pages and Search. */
val BOTTOM_BAR = 64.dp

/** Room the floating player takes over a screen's list, so the end of the list can scroll clear of it. */
val LocalMiniSpace = compositionLocalOf { 0.dp }

/** The screen (back stack entry) being drawn, so it can hand its Play and Shuffle to the floating player. */
val LocalEntryId = compositionLocalOf { "" }

/**
 * Where the floating player is on the screen (as fractions of its width and height), so
 * Now Playing can open out of it and shrink back into it.
 */
object MiniSpot {
    var x = 0.5f
    var y = 0.9f
}

/** How far up the floating player sits on this screen: above its bottom bar, if it has one. */
fun miniLift(route: String?): Dp = when {
    route == null -> 0.dp
    route == Routes.HOME || route == Routes.SEARCH || route.startsWith("album/") ||
        route.startsWith("group/") || route.startsWith("playlist/") || route.startsWith(Routes.RADIO) -> BOTTOM_BAR
    else -> 0.dp
}

// ---- Play and Shuffle beside the floating player -------------------------------------------

/**
 * What the Play and Shuffle card plays: the screen showing. `label` is shown under the
 * shuffle icon (an artist's By Songs / By Albums), or nothing.
 */
class PlayCard(val owner: String) {
    var play: () -> Unit = {}
    var shuffle: () -> Unit = {}
    var label by mutableStateOf<String?>(null)
}

object PlayCards {
    var current by mutableStateOf<PlayCard?>(null)
}

/** This screen has songs to play: show Play and Shuffle for them beside the floating player. */
@Composable
fun PlayCardFor(play: () -> Unit, shuffle: () -> Unit, label: String? = null) {
    val id = LocalEntryId.current
    val card = remember(id) { PlayCard(id) }
    SideEffect {
        card.play = play
        card.shuffle = shuffle
        card.label = label
    }
    DisposableEffect(card) {
        PlayCards.current = card
        onDispose { if (PlayCards.current === card) PlayCards.current = null }
    }
}

/**
 * The floating player: what's playing (tap it for Now Playing) with pause and next, and
 * beside it the screen's Play and Shuffle card when it has one.
 */
@Composable
fun FloatingPlayer(nav: NavHostController, entryId: String?, modifier: Modifier = Modifier) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val state by app.player.state.collectAsState()
    val track = state.currentId?.let { library.byId[it] ?: app.repository.track(it) }      // (early: before the library is in)
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            val d = app.player.duration
            progress = if (d > 0) (app.player.position.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    val card = PlayCards.current?.takeIf { it.owner == entryId }
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { c ->
                    Morph.miniCard = c.boundsInRoot()
                    val root = c.findRootCoordinates().size
                    val at = c.boundsInRoot().center
                    if (root.width > 0 && root.height > 0) {
                        MiniSpot.x = at.x / root.width
                        MiniSpot.y = at.y / root.height
                    }
                }
                .shadow(10.dp, shape)
                .clip(shape)
                .background(Lux.raised)
                .clickable(onClickLabel = "Open Now Playing") {
                    if (track?.isVideo == true) nav.go(Routes.VIDEO) else Morph.open(nav)
                },
        ) {
            Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(
                    track,
                    Modifier.size(44.dp).onGloballyPositioned { Morph.miniArt = it.boundsInRoot() }.clip(RoundedCornerShape(10.dp)),
                    noteSize = 24.dp,
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    ScrollingText(track?.displayName ?: "Not playing", Lux.text, 15.sp)
                    if (track != null) ScrollingText(track.artist, Lux.dim, 13.sp)
                }
                SleepTimerBadge()
                IconButton(onClick = { app.player.togglePlay() }, modifier = Modifier.size(42.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play", tint = Lux.text, modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = { app.player.next() }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", tint = Lux.text, modifier = Modifier.size(30.dp))
                }
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(2.dp).background(Lux.accent))
        }
        if (card != null) {
            Row(
                Modifier
                    .width(110.dp)
                    .fillMaxHeight()
                    .shadow(10.dp, shape)
                    .clip(shape)
                    .background(Lux.raised)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier.size(42.dp, 48.dp).clip(RoundedCornerShape(13.dp)).clickable(onClickLabel = "Play from the top") { card.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, "Play from the top", tint = Lux.accent, modifier = Modifier.size(32.dp))
                }
                Column(
                    Modifier
                        .size(52.dp, 50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Lux.accent)
                        .clickable(onClickLabel = "Shuffle") { card.shuffle() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val label = card.label
                    Icon(LuxIcons.ShuffleOn, "Shuffle", tint = Lux.onAccent, modifier = Modifier.size(if (label == null) 26.dp else 22.dp))
                    if (label != null) Text(label, color = Lux.onAccent, fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

// ---- Bottom bars -----------------------------------------------------------------------------

/** A bottom-bar button: an icon, with its name under it on the main screen (`named`). */
@Composable
fun BarButton(
    icon: ImageVector, label: String, modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = Lux.icon, named: Boolean = true, onClick: () -> Unit,
) {
    Column(
        modifier
            .width(if (named) 60.dp else 52.dp)
            .fillMaxHeight()
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, if (named) null else label, tint = tint, modifier = Modifier.size(if (named) 25.dp else 26.dp))
        if (named) Text(label, color = tint, fontSize = 12.sp, maxLines = 1)
    }
}

/** One item of the More menu: its name, what it does, and an icon if it has one. */
class MoreItem(val label: String, val icon: ImageVector? = null, val action: () -> Unit)

/** The More button at the bottom right of every screen, and its menu (just as wide as it needs). */
@Composable
fun MoreButton(items: List<MoreItem>, title: String? = null, named: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarButton(Icons.Filled.MoreVert, "More", named = named) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!title.isNullOrBlank()) MenuTitle(title)
            for (item in items) {
                DropdownMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = if (item.icon != null) {
                        { Icon(item.icon, null, tint = if (item.label == "Settings") Lux.accent else Lux.icon) }
                    } else null,
                    onClick = { open = false; item.action() },
                )
            }
        }
    }
}

/** A page's ⋮ menu items as More items (no icons). */
fun Menu.asMoreItems(): List<MoreItem> = map { (label, action) -> MoreItem(label, null, action) }

/**
 * A page's bottom bar: Back at the left, More at the right (icons only; names are for the
 * main screen). With `tracks` (album, artist and playlist pages), the same buttons as Now
 * Playing's between them: Radio Mix, the sleep timer, the equalizer, Add to Playlist (for the
 * page's songs) and the queue. `radio` starts a Radio Mix from what the page shows (album,
 * artist); without it the Radio Mix button opens the Radio Mix screen. Get Info (for the
 * page's songs) is in More.
 */
@Composable
fun PageBottomBar(
    nav: NavHostController,
    menu: Menu,
    title: String? = null,
    tracks: List<Track>? = null,
    radio: (() -> Unit)? = null,
) {
    val app = app()
    val eq by app.repository.equalizer.collectAsState()
    var eqPopup by remember { mutableStateOf(false) }
    if (eqPopup) EqualizerPopup(nav) { eqPopup = false }
    Row(
        Modifier.fillMaxWidth().height(BOTTOM_BAR).background(Lux.bottomBar).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text, named = false) { nav.popBackStack() }
        if (tracks != null) {
            val ids = tracks.map { it.id }
            IconButton(onClick = radio ?: { nav.go(Routes.RADIO) }) {
                Icon(LuxIcons.RadioMix, if (radio != null) "Start Radio Mix" else "Radio Mix", tint = Lux.icon)
            }
            SleepTimerBadge(alwaysShow = true)
            IconButton(onClick = { eqPopup = true }) {
                Icon(Icons.Filled.Equalizer, "Equalizer", tint = if (eq.on) Lux.accent else Lux.icon)
            }
            IconButton(onClick = { if (ids.isNotEmpty()) Dialogs.show(DialogRequest.AddToPlaylist(ids)) }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", tint = Lux.icon)
            }
            IconButton(onClick = { nav.go(Routes.QUEUE) }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, "Play Queue - Up Next", tint = Lux.icon)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        // Every page's More menu ends with Search (the same full search as everywhere).
        // Get Info for the page's songs too, if the page's menu doesn't have it.
        val info: Menu = if (!tracks.isNullOrEmpty() && menu.none { it.first == "Get Info" }) {
            listOf("Get Info" to { Dialogs.show(DialogRequest.Info(tracks.map { it.id })) })
        } else emptyList()
        if (menu.isNotEmpty() || info.isNotEmpty()) MoreButton((menu + info).asMoreItems() + MoreItem("Search") { nav.go(Routes.SEARCH) }, title, named = false)
    }
}
