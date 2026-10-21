package io.github.metricambient.luxtunes.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val state by app.player.state.collectAsState()
    val fileLyrics by app.repository.fileLyrics.collectAsState()
    val track = state.currentId?.let { library.byId[it] }
    // A movie or TV episode belongs in the video screen.
    LaunchedEffect(track?.id) {
        if (track?.isVideo == true) nav.navigate(Routes.VIDEO) { popUpTo(Routes.NOW) { inclusive = true } }
    }
    var overlay by remember { mutableStateOf(false) }

    // Position, polled while this screen is showing.
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            position = app.player.position
            duration = app.player.duration
            delay(400)
        }
    }

    // The pieces of the screen, arranged below by orientation.
    val topBar: @Composable () -> Unit = {
        // Back, then the actions
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text)
            }
            Spacer(Modifier.weight(1f))
            if (track != null) {
                IconButton(onClick = { Dialogs.show(DialogRequest.Info(track.id)) }) {
                    Icon(Icons.Filled.Info, "Get Info", tint = Lux.icon)
                }
                IconButton(onClick = { Dialogs.show(DialogRequest.AddToPlaylist(listOf(track.id))) }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", tint = Lux.icon)
                }
            }
            IconButton(onClick = { nav.go(Routes.QUEUE) }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, "Play Queue - Up Next", tint = Lux.icon)
            }
            val menu = mutableListOf<Pair<String, () -> Unit>>()
            if (track != null) {
                menu += "Get Info" to { Dialogs.show(DialogRequest.Info(track.id)) }
                menu += "Go to Album" to { nav.go(Routes.album(track.albumKey)) }
                menu += "Go to Artist" to { nav.go(Routes.artist(track.albumArtist)) }
                menu += "Add to playlist…" to { Dialogs.show(DialogRequest.AddToPlaylist(listOf(track.id))) }
                menu += "Delete from phone" to { Dialogs.show(DialogRequest.Delete(listOf(track.id))) }
            }
            menu += "Search" to { nav.go(Routes.SEARCH) }
            menu += "Settings" to { nav.go(Routes.SETTINGS) }
            MoreMenu(menu)
        }
    }
    val titleBlock: @Composable () -> Unit = {
        // The song on its own line, artist / album below it
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            ScrollingTitle(track?.name ?: "Not playing")
            if (track != null) {
                Text(
                    "${track.artist} / ${track.album}", color = Lux.dim, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val artwork: @Composable (Modifier) -> Unit = { place ->
        // Art; tap it for the rating (and lyrics, when LuxTunes has them)
        Box(
            place
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (track != null) overlay = !overlay },
        ) {
            Artwork(track, Modifier.fillMaxSize(), noteSize = 220.dp)
            if (overlay && track != null) {
                val lyrics = track.lyrics ?: fileLyrics[track.id]
                if (lyrics != null) {
                    Box(Modifier.fillMaxSize().background(Lux.shade)) {
                        Text(
                            lyrics,
                            color = Lux.text, fontSize = 18.sp, textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 64.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Lux.shade).padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    StarRating(track.rating, track.ratingComputed, onRate = { app.repository.setRating(track.id, it) })
                }
            }
        }
    }
    val controls: @Composable () -> Unit = {
        // Controls
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { app.player.toggleShuffle() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (state.shuffle) Lux.accent else Lux.icon, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { app.player.previous() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.SkipPrevious, "Previous", tint = Lux.icon, modifier = Modifier.size(56.dp))
            }
            Box(
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(if (state.isPlaying) Lux.accent else Lux.dim)
                    .clickable { app.player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    tint = if (state.isPlaying) Lux.onAccent else Lux.background, modifier = Modifier.size(70.dp),
                )
            }
            IconButton(onClick = { app.player.next() }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.SkipNext, "Next", tint = Lux.icon, modifier = Modifier.size(56.dp))
            }
            IconButton(onClick = { app.player.cycleRepeat() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat",
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) Lux.icon else Lux.accent,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
    val seekBar: @Composable () -> Unit = {
        // Seek bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = if (dragging) (dragValue * duration).toLong() else position
            Text(formatTime(shown), color = Lux.text, fontSize = 18.sp, modifier = Modifier.width(64.dp))
            Slider(
                value = if (dragging) dragValue else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    app.player.seekTo((dragValue * duration).toLong())
                    position = (dragValue * duration).toLong()
                    dragging = false
                },
                colors = SliderDefaults.colors(thumbColor = Lux.accent, activeTrackColor = Lux.accent, inactiveTrackColor = Lux.track),
                modifier = Modifier.weight(1f),
            )
            Text(
                formatTime(duration), color = Lux.text, fontSize = 18.sp,
                textAlign = TextAlign.End, modifier = Modifier.width(64.dp),
            )
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        // Art on the right at full height; everything else to its left.
        Row(Modifier.fillMaxSize().background(Lux.background)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                topBar()
                titleBlock()
                Spacer(Modifier.weight(1f))
                controls()
                seekBar()
            }
            artwork(Modifier.fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize().background(Lux.background)) {
            topBar()
            titleBlock()
            Spacer(Modifier.height(8.dp))
            // Square, as big as fits: the full width on tall phones, smaller on short ones.
            artwork(Modifier.align(Alignment.CenterHorizontally).weight(1f, fill = false))
            Spacer(Modifier.height(24.dp))
            controls()
            Spacer(Modifier.height(8.dp))
            seekBar()
        }
    }
}

/**
 * The song title. Too long to fit: after a few seconds it scrolls round once, pauses
 * a few seconds, and goes again, until the song changes. Short titles stay still.
 */
@Composable
private fun ScrollingTitle(text: String) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var boxWidth by remember { mutableIntStateOf(0) }
    var textWidth by remember(text) { mutableIntStateOf(0) }
    val gap = 48.dp
    val tooLong = boxWidth > 0 && textWidth > boxWidth
    LaunchedEffect(text, tooLong) {
        scroll.scrollTo(0)
        if (!tooLong) return@LaunchedEffect
        val loop = textWidth + with(density) { gap.roundToPx() }      // one title + the gap
        val millis = (loop / with(density) { 40.dp.toPx() } * 1000).toInt()   // 40 dp a second
        while (true) {
            delay(3000)
            scroll.animateScrollTo(loop, tween(millis, easing = LinearEasing))
            scroll.scrollTo(0)          // the second copy is where the first was: no jump
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { boxWidth = it.width }
            .horizontalScroll(scroll, enabled = false),
    ) {
        Text(
            text, color = Lux.text, fontSize = 22.sp, maxLines = 1, softWrap = false,
            onTextLayout = { textWidth = it.size.width },
        )
        if (tooLong) {
            Spacer(Modifier.width(gap))
            Text(text, color = Lux.text, fontSize = 22.sp, maxLines = 1, softWrap = false)
        }
    }
}
