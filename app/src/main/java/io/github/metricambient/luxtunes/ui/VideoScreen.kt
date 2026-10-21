package io.github.metricambient.luxtunes.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController

/**
 * Movies and TV episodes, full screen. Tap the picture to show or hide the controls
 * (back arrow at the top; seek bar and play buttons at the bottom); they fade out by
 * themselves after a few seconds of playing. Playback pauses when you leave.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoScreen(nav: NavHostController) {
    val app = app()
    val state by app.player.state.collectAsState()
    val activity = LocalContext.current.findActivity()

    // Hide the status and navigation bars (a swipe brings them back for a moment)
    // and keep the screen on while here.
    DisposableEffect(Unit) {
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            bars?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (app.repository.track(app.player.state.value.currentId ?: "")?.isVideo == true) app.player.pause()
        }
    }

    var controls by remember { mutableStateOf(true) }
    var touched by remember { mutableIntStateOf(0) }          // restarts the hide timer
    LaunchedEffect(controls, state.isPlaying, touched) {
        if (controls && state.isPlaying) {
            delay(3500)
            controls = false
        }
    }

    // Position, polled while the controls show.
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(controls) {
        while (controls) {
            position = app.player.position
            duration = app.player.duration
            delay(400)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val controller = app.player.controllerOrNull
        if (state.connected && controller != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false           // the controls below are the app's own
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { view -> view.player = controller },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Taps anywhere on the picture show or hide the controls.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { controls = !controls; touched++ } },
        )
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Back arrow, on a soft shade so it shows on bright pictures
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                        .padding(8.dp),
                ) {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                }
                // Seek bar and buttons near the bottom
                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = if (dragging) (dragValue * duration).toLong() else position
                        Text(formatTime(shown), color = Color.White, fontSize = 14.sp, modifier = Modifier.width(64.dp))
                        Slider(
                            value = if (dragging) dragValue else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            onValueChange = { dragging = true; dragValue = it; touched++ },
                            onValueChangeFinished = {
                                app.player.seekTo((dragValue * duration).toLong())
                                position = (dragValue * duration).toLong()
                                dragging = false
                                touched++
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Lux.accent, activeTrackColor = Lux.accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatTime(duration), color = Color.White, fontSize = 14.sp,
                            textAlign = TextAlign.End, modifier = Modifier.width(64.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { app.player.previous(); touched++ }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.width(24.dp))
                        IconButton(onClick = { app.player.togglePlay(); touched++ }, modifier = Modifier.size(64.dp)) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White, modifier = Modifier.size(48.dp),
                            )
                        }
                        Spacer(Modifier.width(24.dp))
                        IconButton(onClick = { app.player.next(); touched++ }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }
        }
    }
}

/** The Activity behind a Compose context (it may be wrapped). */
fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
