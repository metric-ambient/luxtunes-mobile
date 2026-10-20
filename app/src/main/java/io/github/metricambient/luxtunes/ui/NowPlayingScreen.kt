package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
    val track = state.currentId?.let { library.byId[it] }
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

    Column(Modifier.fillMaxSize().background(Lux.background)) {
        // Title and actions
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track?.name ?: "Not playing", color = Lux.text, fontSize = 20.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (track != null) {
                    Text(
                        "${track.artist} / ${track.album}", color = Lux.dim, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { nav.go(Routes.QUEUE) }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, "Now Playing list", tint = Lux.icon)
            }
            val menu = mutableListOf<Pair<String, () -> Unit>>()
            if (track != null) {
                menu += "Go to Album" to { nav.go(Routes.album(track.albumKey)) }
                menu += "Go to Artist" to { nav.go(Routes.artist(track.albumArtist)) }
            }
            menu += "Search" to { nav.go(Routes.SEARCH) }
            menu += "Settings" to { nav.go(Routes.SETTINGS) }
            MoreMenu(menu)
        }
        Spacer(Modifier.height(8.dp))

        // Art; tap it for the rating (and lyrics, when LuxTunes has them)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (track != null) overlay = !overlay },
        ) {
            Artwork(track?.artwork, Modifier.fillMaxSize(), noteSize = 220.dp)
            if (overlay && track != null) {
                val lyrics = track.lyrics
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

        Spacer(Modifier.weight(1f))

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
                    .background(if (state.isPlaying) Lux.accent else Color(0xFFAAAAAA))
                    .clickable { app.player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    tint = Color.White, modifier = Modifier.size(70.dp),
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

        Spacer(Modifier.weight(1f))

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
                colors = SliderDefaults.colors(thumbColor = Lux.accent, activeTrackColor = Lux.accent, inactiveTrackColor = Color(0xFF444444)),
                modifier = Modifier.weight(1f),
            )
            Text(
                formatTime(duration), color = Lux.text, fontSize = 18.sp,
                textAlign = TextAlign.End, modifier = Modifier.width(64.dp),
            )
        }
    }
}
