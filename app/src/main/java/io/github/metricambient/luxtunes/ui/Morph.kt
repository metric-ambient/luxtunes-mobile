package io.github.metricambient.luxtunes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Now Playing opening out of the floating player: the player's card grows to fill the
 * screen while its album art grows to where Now Playing's art is; closing runs it back.
 * Positions are in root coordinates (pixels), noted as each piece is laid out.
 */
object Morph {
    enum class Way { OPEN, CLOSE }

    var miniCard = Rect.Zero          // the floating player's card
    var miniArt = Rect.Zero           // its album art
    var nowArt by mutableStateOf(Rect.Zero)      // Now Playing's album art, last time it showed
    var running by mutableStateOf<Way?>(null)
    val progress = Animatable(0f)     // 0: the floating player, 1: Now Playing
    // Its own scope (with the screen's frame clock): the screens that start it leave mid-way.
    private val scope = CoroutineScope(AndroidUiDispatcher.Main)

    /** From the floating player: open Now Playing out of it. */
    fun open(nav: NavHostController) {
        if (running != null || miniCard == Rect.Zero) { nav.go(Routes.NOW); return }
        running = Way.OPEN
        scope.launch {
            try {
                progress.snapTo(0f)
                nav.go(Routes.NOW)
                progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            } finally {
                running = null
            }
        }
    }

    /** Back from Now Playing: shrink it into the floating player (or just go back). */
    fun close(nav: NavHostController) {
        if (running != null) return
        if (miniCard == Rect.Zero || nav.previousBackStackEntry?.destination?.route.let { it == null || !hasPlayer(it) }) {
            nav.popBackStack(); return
        }
        running = Way.CLOSE
        scope.launch {
            try {
                progress.snapTo(1f)
                nav.popBackStack()
                progress.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
            } finally {
                running = null
            }
        }
    }

    /** Screens that show the floating player (so Now Playing can shrink back into it). */
    private fun hasPlayer(route: String) = route != Routes.NOW && route != Routes.VIDEO &&
        !route.startsWith(Routes.SETTINGS) && !route.startsWith("smart/")

    /** How visible Now Playing's own content is: hidden while the card grows, fading in at the end. */
    fun contentAlpha(): Float = if (running == Way.OPEN) ((progress.value - 0.7f) / 0.3f).coerceIn(0f, 1f) else 1f
}

/** The growing (or shrinking) card and art, drawn over everything while Morph runs. */
@Composable
fun MorphOverlay() {
    if (Morph.running == null) return
    val app = app()
    val library by app.repository.state.collectAsState()
    val state by app.player.state.collectAsState()
    val track = state.currentId?.let { library.byId[it] ?: app.repository.track(it) }
    val density = LocalDensity.current
    var area by remember { mutableStateOf(Rect.Zero) }
    val t = Morph.progress.value
    Box(Modifier.fillMaxSize().onGloballyPositioned { area = it.boundsInRoot() }) {
        if (area == Rect.Zero) return@Box
        val origin = area.topLeft
        // Now Playing's art: where it was last time, or (the first time) full width under the title.
        val artEnd = Morph.nowArt.takeIf { it != Rect.Zero }
            ?: with(density) { Rect(Offset(area.left, area.top + 74.dp.toPx()), androidx.compose.ui.geometry.Size(area.width, area.width)) }
        val card = lerp(Morph.miniCard, area, t)
        val art = lerp(Morph.miniArt, artEnd, t)
        val corner = lerp(16.dp, 0.dp, t)
        fun Rect.at() = IntOffset((left - origin.x).roundToInt(), (top - origin.y).roundToInt())
        Box(
            Modifier
                .offset { card.at() }
                .size(with(density) { card.width.toDp() }, with(density) { card.height.toDp() })
                .clip(RoundedCornerShape(corner))
                .background(lerp(Lux.raised, Lux.background, t))
        )
        Artwork(
            track,
            Modifier
                .offset { art.at() }
                .size(with(density) { art.width.toDp() })
                .clip(RoundedCornerShape(lerp(10.dp, 0.dp, t))),
            noteSize = with(density) { (art.width * 0.5f).toDp() },
        )
    }
}
