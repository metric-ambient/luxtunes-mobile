package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay

/** The bar along the bottom of the main screen and of album, artist and playlist pages. */
val BOTTOM_BAR = 64.dp

/** Extra room a list leaves at its end (none now that the player is in the bottom bar). */
val LocalMiniSpace = compositionLocalOf { 0.dp }

/**
 * The bar along the bottom: back at the left (on pages), what's playing (tap it for
 * Now Playing) with play/pause and next, and the screen's ⋮ menu at the right.
 * A thin accent line along its top shows how far the song is.
 */
@Composable
fun BottomBar(nav: NavHostController, menu: Menu, back: Boolean, menuTitle: String? = null) {
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
    Box(Modifier.fillMaxWidth().height(BOTTOM_BAR).background(Lux.bottomBar)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (back) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text)
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClickLabel = "Open Now Playing") { nav.go(if (track?.isVideo == true) Routes.VIDEO else Routes.NOW) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track, Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)), noteSize = 26.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(track?.displayName ?: "Not playing", color = Lux.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (track != null) Text(track.artist, color = Lux.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SleepTimerBadge()
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Lux.accent)
                    .clickable { app.player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play", tint = Lux.onAccent, modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = { app.player.next() }) {
                Icon(Icons.Filled.SkipNext, "Next", tint = Lux.text, modifier = Modifier.size(30.dp))
            }
            if (menu.isNotEmpty()) {
                Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 16.dp).background(Lux.raised))
                MoreMenu(menu, title = menuTitle)
            } else {
                Spacer(Modifier.width(4.dp))
            }
        }
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth(progress).height(2.dp).background(Lux.accent))
    }
}

/** A page's bottom bar: back, what's playing, and the page's ⋮ menu. */
@Composable
fun PageBottomBar(nav: NavHostController, menu: Menu, title: String? = null) = BottomBar(nav, menu, back = true, menuTitle = title)

/**
 * A tab's icon with a small play or shuffle mark at its lower right: the "play this
 * tab from the top" and "shuffle this tab" buttons.
 */
@Composable
fun TabActionIcon(tab: ImageVector, shuffle: Boolean, description: String) {
    Box(Modifier.size(32.dp)) {
        Icon(tab, description, tint = Lux.icon, modifier = Modifier.size(24.dp).align(Alignment.TopStart).offset(1.dp, 1.dp))
        Box(
            Modifier.size(17.dp).align(Alignment.BottomEnd).clip(CircleShape).background(Lux.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (shuffle) LuxIcons.ShuffleOn else Icons.Filled.PlayArrow, null,
                tint = Lux.accent, modifier = Modifier.size(if (shuffle) 13.dp else 15.dp),
            )
        }
    }
}
