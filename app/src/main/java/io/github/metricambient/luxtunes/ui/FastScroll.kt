package io.github.metricambient.luxtunes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.metricambient.luxtunes.data.sortText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The letter a name files under: "A"…"Z", or "#" for anything else ("The" is skipped). */
fun letterOf(name: String): String {
    val c = sortText(name).firstOrNull { !it.isWhitespace() } ?: return "#"
    val up = c.uppercaseChar()
    return if (up in 'A'..'Z') up.toString() else if (c.isLetter()) up.toString() else "#"
}

/** A list with a fast-scroll handle: see [FastScrollBox]. */
@Composable
fun FastScroll(state: LazyListState, count: Int, label: (Int) -> String?, content: @Composable () -> Unit) =
    FastScrollBox(
        count = count,
        scrolling = state.isScrollInProgress,
        position = { state.firstVisibleItemIndex.toFloat() / (count - state.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1) },
        scrollTo = { state.scrollToItem(it) },
        label = label,
        content = content,
    )

/** A grid with a fast-scroll handle. */
@Composable
fun FastScroll(state: LazyGridState, count: Int, label: (Int) -> String?, content: @Composable () -> Unit) =
    FastScrollBox(
        count = count,
        scrolling = state.isScrollInProgress,
        position = { state.firstVisibleItemIndex.toFloat() / (count - state.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1) },
        scrollTo = { state.scrollToItem(it) },
        label = label,
        content = content,
    )

/**
 * A handle on the right edge, shown while the list scrolls (long lists only). Drag it
 * to go straight to any part of the list; meanwhile a big letter in the middle shows
 * where you are ("M", or the year, plays… for other sort orders).
 */
@Composable
private fun FastScrollBox(
    count: Int,
    scrolling: Boolean,
    position: () -> Float,
    scrollTo: suspend (Int) -> Unit,
    label: (Int) -> String?,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        if (count < 40) return@Box
        FastScrollHandle(count, scrolling, position, scrollTo, label)
    }
}

@Composable
private fun BoxScope.FastScrollHandle(
    count: Int,
    scrolling: Boolean,
    position: () -> Float,
    scrollTo: suspend (Int) -> Unit,
    label: (Int) -> String?,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    var trackHeight by remember { mutableIntStateOf(1) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) shown = true
        else {
            delay(1500)
            shown = false
        }
    }
    val thumbHeight = 56.dp
    val thumbPx = with(density) { thumbHeight.toPx() }
    fun indexAt(f: Float) = (f * (count - 1)).roundToInt().coerceIn(0, count - 1)

    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(1.dp)
            .onSizeChanged { trackHeight = it.height },
    )
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.align(Alignment.TopEnd),
    ) {
        Box(
            Modifier
                .offset {
                    val f = if (dragging) fraction else position().coerceIn(0f, 1f)
                    IntOffset(0, ((trackHeight - thumbPx) * f).roundToInt().coerceAtLeast(0))
                }
                .width(28.dp)
                .size(width = 28.dp, height = thumbHeight)
                .pointerInput(count) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            fraction = position().coerceIn(0f, 1f)
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, dy ->
                        change.consume()
                        fraction = (fraction + dy / (trackHeight - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val target = indexAt(fraction)
                        scope.launch { scrollTo(target) }
                    }
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .padding(end = 3.dp)
                    .size(width = if (dragging) 8.dp else 6.dp, height = thumbHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (dragging) Lux.accent else Lux.dim.copy(alpha = 0.7f)),
            )
        }
    }
    if (dragging) {
        val text = label(indexAt(fraction))
        if (!text.isNullOrEmpty()) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(min = 96.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Lux.panel.copy(alpha = 0.92f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text, color = Lux.accent, textAlign = TextAlign.Center, maxLines = 1,
                    fontSize = if (text.length <= 2) 56.sp else 32.sp,
                )
            }
        }
    }
}
