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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Equalizer
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.data.Track
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import io.github.metricambient.luxtunes.data.ThemeStyle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(nav: NavHostController) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val state by app.player.state.collectAsState()
    val fileLyrics by app.repository.fileLyrics.collectAsState()
    val ratingsNow by app.repository.ratingsNow.collectAsState()
    val eq by app.repository.equalizer.collectAsState()
    var eqPopup by remember { mutableStateOf(false) }
    if (eqPopup) EqualizerPopup(nav) { eqPopup = false }
    val track = state.currentId?.let { library.byId[it] ?: app.repository.track(it) }      // (early: before the library is in)
    // A movie or TV episode belongs in the video screen.
    LaunchedEffect(track?.id) {
        if (track?.isVideo == true) nav.navigate(Routes.VIDEO) { popUpTo(Routes.NOW) { inclusive = true } }
    }
    var overlay by remember { mutableStateOf(false) }
    var artShown by remember { mutableStateOf(true) }      // false when the window has no room for it

    // Position, polled while this screen is showing.
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val settings by app.prefs.settings.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            position = app.player.position
            duration = app.player.duration
            delay(if (overlay && settings.followLyrics) 100 else 400)      // lyrics follow closely
        }
    }

    // The pieces of the screen, arranged below by orientation.
    val bottomBar: @Composable () -> Unit = {
        // Back at the left, the ⋮ menu at the right, the song's actions between
        Row(
            Modifier.fillMaxWidth().height(BOTTOM_BAR).background(Lux.bar).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text)
            }
            if (track != null) {
                IconButton(onClick = { Dialogs.show(DialogRequest.Info(listOf(track.id))) }) {
                    Icon(Icons.Filled.Info, "Get Info", tint = Lux.icon)
                }
                IconButton(onClick = { Dialogs.show(DialogRequest.AddToPlaylist(listOf(track.id))) }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", tint = Lux.icon)
                }
            }
            SleepTimerBadge(alwaysShow = true)
            IconButton(onClick = { eqPopup = true }) {
                Icon(Icons.Filled.Equalizer, "Equalizer", tint = if (eq.on) Lux.accent else Lux.icon)
            }
            IconButton(onClick = { nav.goFromNowPlaying(Routes.QUEUE) }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, "Play Queue - Up Next", tint = Lux.icon)
            }
            val menu = mutableListOf<Pair<String, () -> Unit>>()
            if (track != null) {
                menu += "Get Info" to { Dialogs.show(DialogRequest.Info(listOf(track.id))) }
                menu += "Go to Album" to { nav.upTo(track, album = true) }
                menu += "Go to Artist" to { nav.upTo(track, album = false) }
                menu += "Add to Playlist…" to { Dialogs.show(DialogRequest.AddToPlaylist(listOf(track.id))) }
                menu += "Set EQ…" to { Dialogs.show(DialogRequest.SetEq(listOf(track.id), track.displayName)) }
                menu += "Delete from Phone" to { Dialogs.show(DialogRequest.Delete(listOf(track.id))) }
            }
            menu += sleepTimerMenu()
            menu += "Add Folder to Library…" to { AddFolder.show() }
            menu += "Search" to { nav.goFromNowPlaying(Routes.SEARCH) }
            menu += "Settings" to { nav.goFromNowPlaying(Routes.SETTINGS) }
            MoreMenu(menu, title = track?.displayName)
        }
    }
    val titleBlock: @Composable () -> Unit = {
        // The song on its own line, artist / album below it
        // With no room for the art, tapping here shows the rating instead.
        val tap = if (!artShown && track != null) Modifier.clickable { overlay = !overlay } else Modifier
        Column(Modifier.fillMaxWidth().then(tap).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            ScrollingTitle(track?.displayName ?: "Not playing")
            if (track != null) {
                Text(
                    "${track.artist} / ${track.album}", color = Lux.dim, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val ratingBar: @Composable (Modifier) -> Unit = { place ->
        if (track != null) Box(
            place.background(Lux.shade).padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            // A tapped rating shows at once, before the library has caught up.
            val now = ratingsNow[track.id]
            StarRating(
                now ?: track.rating, if (now != null) false else track.ratingComputed,
                onRate = { app.repository.setRating(track.id, it) },
            )
        }
    }
    // ratingInside: the rating sits on the art; otherwise the layout puts it full width elsewhere.
    val artwork: @Composable (Dp, Boolean) -> Unit = { size, ratingInside ->
        // Art; tap it for the rating (and lyrics, when LuxTunes has them)
        Box(
            Modifier.size(size)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (track != null) overlay = !overlay },
        ) {
            Artwork(track, Modifier.fillMaxSize(), noteSize = minOf(220.dp, size * 0.6f))
            if (overlay && track != null) {
                val lyrics = track.lyrics ?: fileLyrics[track.id]
                if (lyrics != null || track.syncedLyrics != null) {
                    Box(Modifier.fillMaxSize().background(Lux.shade)) {
                        LyricsView(track.id, lyrics, track.syncedLyrics, position, settings.followLyrics)
                    }
                }
                if (ratingInside) ratingBar(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
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
            ToggleButton(
                if (state.shuffle) LuxIcons.ShuffleOn else LuxIcons.Shuffle, "Shuffle", state.shuffle,
            ) { app.player.toggleShuffle() }
            SkipButton(Icons.Filled.SkipPrevious, "Previous", forward = false) { app.player.previous() }
            PlayPauseButton(state.isPlaying) { app.player.togglePlay() }
            SkipButton(Icons.Filled.SkipNext, "Next", forward = true) { app.player.next() }
            val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
            ToggleButton(
                when {
                    state.repeatMode == Player.REPEAT_MODE_ONE -> LuxIcons.RepeatOneOn
                    repeatOn -> LuxIcons.RepeatOn
                    else -> LuxIcons.Repeat
                },
                "Repeat", repeatOn,
            ) { app.player.cycleRepeat() }
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

    // Split screen and small windows: the art shrinks first, then goes; the controls always stay.
    // The rating moves out of the art, at full size and full width, once the art is too small for it.
    // Any room left over goes between the art and the controls.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.fillMaxSize().background(Lux.background)) {
        if (landscape) {
            // Art on the right at full height; everything else to its left; the bar below both.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val art = minOf(maxHeight, maxWidth - CONTROLS_WIDTH)
                val showArt = art >= MIN_ART
                val ratingOutside = !showArt || art < RATING_ART
                LaunchedEffect(showArt) { artShown = showArt }
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        titleBlock()
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            if (overlay && ratingOutside && maxHeight >= RATING_HEIGHT) ratingBar(Modifier.fillMaxWidth())
                        }
                        controls()
                        seekBar()
                    }
                    if (showArt) artwork(art, !ratingOutside)
                }
            }
            bottomBar()
        } else {
            titleBlock()
            Spacer(Modifier.height(8.dp))
            // Square, as big as fits: the full width on tall phones, smaller on short ones.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val fits = minOf(maxWidth, maxHeight)
                val showArt = fits >= MIN_ART
                val ratingOutside = !showArt || fits < RATING_ART
                // The rating below the art needs its height; if that leaves the art too small, no rating.
                val ratingRoom = maxHeight - RATING_HEIGHT
                val showRating = overlay && ratingOutside && ratingRoom >= (if (showArt) MIN_ART else 0.dp)
                val art = if (showRating) minOf(maxWidth, ratingRoom) else fits
                LaunchedEffect(showArt) { artShown = showArt }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showArt) artwork(art, !ratingOutside)
                    if (showRating) ratingBar(Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(8.dp))
            controls()
            seekBar()
            bottomBar()
        }
    }
}

/** Shuffle or repeat: in the accent color with a dot under it when on. */
@Composable
private fun ToggleButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { role = Role.Switch; contentDescription = label + if (on) ", on" else ", off" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (on) Lux.accent else Lux.icon, modifier = Modifier.size(30.dp))
        if (on) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(6.dp).clip(CircleShape).background(Lux.accent))
    }
}

/**
 * Play/pause. Dark and Light: an accent ring with a soft glow around it. Material: the
 * solid button as before (accent while playing, grey while paused).
 */
@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    val material = Lux.style == ThemeStyle.MATERIAL
    val accent = Lux.accent
    Box(
        Modifier
            .size(96.dp)
            .then(
                if (material) Modifier
                else Modifier.drawBehind {
                    // The glow: fading out from just inside the ring to well beyond it.
                    drawCircle(
                        Brush.radialGradient(
                            0.55f to accent.copy(alpha = 0.45f), 1f to Color.Transparent,
                            center = center, radius = size.minDimension * 0.75f,
                        ),
                        radius = size.minDimension * 0.75f,
                    )
                }
            )
            .clip(CircleShape)
            .background(if (!material) Lux.background else if (playing) accent else Lux.dim)
            .then(if (material) Modifier else Modifier.border(3.dp, accent, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (playing) "Pause" else "Play",
            tint = when {
                !material -> accent
                playing -> Lux.onAccent
                else -> Lux.background
            },
            modifier = Modifier.size(62.dp),
        )
    }
}

/**
 * Previous or Next: a tap skips; holding it down rewinds or fast-forwards through the
 * song (2 s every 150 ms, about 13× speed) until let go.
 */
@Composable
private fun SkipButton(icon: ImageVector, label: String, forward: Boolean, onTap: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .semantics { role = Role.Button; contentDescription = label }
            .pointerInput(forward) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { },              // (so a hold isn't also a tap)
                    onPress = {
                        val scan = scope.launch {
                            delay(viewConfiguration.longPressTimeoutMillis)
                            while (true) {
                                val end = app.player.duration
                                val to = app.player.position + if (forward) 2_000 else -2_000
                                app.player.seekTo(if (end > 0) to.coerceIn(0, end - 500) else to.coerceAtLeast(0))
                                delay(150)
                            }
                        }
                        tryAwaitRelease()
                        scan.cancel()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Lux.icon, modifier = Modifier.size(56.dp))
    }
}

/** Below this the art isn't worth showing. */
private val MIN_ART = 120.dp
/** Art smaller than this gets the rating below it instead of on it. */
private val RATING_ART = 280.dp
private val RATING_HEIGHT = 56.dp
/** What the controls need beside the art in landscape. */
private val CONTROLS_WIDTH = 344.dp

/**
 * Go to the song's album or artist the way the library is laid out, so Back goes up:
 * album → its artist → the main screen. An artist or album page already under Now
 * Playing is kept (with its scroll position); anything else above the main screen goes.
 */
private fun NavHostController.upTo(track: Track, album: Boolean) {
    fun arg(e: NavBackStackEntry, key: String) = e.arguments?.getString(key).orEmpty()
    fun isAlbum(e: NavBackStackEntry) = e.destination.route == "album/{key}" && arg(e, "key") == track.albumKey
    fun isArtist(e: NavBackStackEntry) = e.destination.route == "group/{kind}/{name}" &&
        arg(e, "kind") in listOf("artist", "trackartist") &&
        (arg(e, "name").equals(track.browseArtist, ignoreCase = true) || arg(e, "name").equals(track.artist, ignoreCase = true))
    fun wanted(e: NavBackStackEntry) = if (album) isAlbum(e) else isArtist(e)

    if (currentBackStackEntry?.destination?.route == Routes.NOW) popBackStack()
    while (true) {
        val top = currentBackStackEntry ?: break
        if (top.destination.route == Routes.HOME || wanted(top) || (album && isArtist(top))) break
        if (!popBackStack()) break
    }
    val top = currentBackStackEntry
    if (top != null && wanted(top)) return
    if (top?.destination?.route == Routes.HOME && track.browseArtist.isNotBlank()) navigate(Routes.artist(track.browseArtist))
    if (album) navigate(Routes.album(track.albumKey))
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
