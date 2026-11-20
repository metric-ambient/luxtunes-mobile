package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.metricambient.luxtunes.data.ThemeStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Narrower than this and the tabs scroll instead of sharing the bar. */
private val MIN_TAB = 72.dp
private val SCROLL_TAB = 76.dp
/** Holding a dragged tab this close to an edge scrolls the bar that way. */
private val EDGE = 44.dp

/**
 * The overview's bottom bar: the tabs, each an icon over its name, and More at the right.
 * If the tabs all fit they share the bar; if not, they scroll, with an arrow at each side
 * that has more. Tap a tab to show it. Long-press one and it lifts into a copy under your
 * finger: move it where it should go (hold it near an edge to scroll) and let go.
 */
@Composable
fun BottomTabs(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    more: List<MoreItem>,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf<String?>(null) }
    var fingerX by remember { mutableFloatStateOf(0f) }      // in the tab area, in pixels

    Row(Modifier.fillMaxWidth().height(BOTTOM_BAR).background(Lux.bottomBar)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val count = tabs.size.coerceAtLeast(1)
            val fits = maxWidth / count >= MIN_TAB
            val tabWidth = if (fits) maxWidth / count else SCROLL_TAB
            val tabPx = with(density) { tabWidth.toPx() }
            val areaPx = with(density) { maxWidth.toPx() }
            val edgePx = with(density) { EDGE.toPx() }

            /** Where the dragged tab would land: the slot under the finger. */
            fun target(): Int = ((fingerX + scroll.value) / tabPx).toInt().coerceIn(0, tabs.lastIndex)

            // While a tab is held near an edge, keep scrolling that way.
            LaunchedEffect(dragging) {
                if (dragging == null) return@LaunchedEffect
                while (true) {
                    val step = when {
                        fingerX < edgePx -> -12
                        fingerX > areaPx - edgePx -> 12
                        else -> 0
                    }
                    if (step != 0) scroll.scrollBy(step.toFloat())
                    delay(16)
                }
            }

            Row(Modifier.fillMaxSize().horizontalScroll(scroll, enabled = dragging == null)) {
                tabs.forEachIndexed { index, name ->
                    Box(
                        Modifier
                            .width(tabWidth)
                            .fillMaxHeight()
                            .alpha(if (name == dragging) 0.3f else 1f)
                            .pointerInput(name, tabs) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { at ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragging = name
                                        fingerX = index * tabPx - scroll.value + at.x
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        // The tab doesn't move, so its place in the area is known.
                                        fingerX = (index * tabPx - scroll.value + change.position.x).coerceIn(0f, areaPx)
                                    },
                                    onDragEnd = {
                                        val from = tabs.indexOf(name)
                                        val to = target()
                                        dragging = null
                                        if (from >= 0 && to != from) {
                                            onReorder(tabs.toMutableList().apply { removeAt(from); add(to, name) })
                                        }
                                    },
                                    onDragCancel = { dragging = null },
                                )
                            }
                            .clickable { onSelect(name) },
                        contentAlignment = Alignment.Center,
                    ) {
                        TabItem(name, name == selected)
                    }
                }
            }

            // More tabs off to the side: an arrow at that edge (tap it to scroll that way).
            if (!fits) {
                val page = { (scroll.viewportSize * 2 / 3).coerceAtLeast(1) }
                if (scroll.value > 0) EdgeArrow(left = true, lit = dragging != null, modifier = Modifier.align(Alignment.CenterStart)) {
                    scope.launch { scroll.animateScrollTo(scroll.value - page()) }
                }
                if (scroll.value < scroll.maxValue) EdgeArrow(left = false, lit = dragging != null, modifier = Modifier.align(Alignment.CenterEnd)) {
                    scope.launch { scroll.animateScrollTo(scroll.value + page()) }
                }
            }

            // The held tab: a copy under the finger, and a line where it would land.
            val held = dragging
            if (held != null) {
                val slotX = target() * tabPx - scroll.value
                Box(
                    Modifier
                        .offset { IntOffset(slotX.roundToInt() - 1, 0) }
                        .width(3.dp)
                        .fillMaxHeight()
                        .padding(vertical = 10.dp)
                        .background(Lux.accent)
                )
                Box(
                    Modifier
                        .zIndex(2f)
                        .offset { IntOffset((fingerX - tabPx / 2).roundToInt(), (-14).dp.roundToPx()) }
                        .width(tabWidth)
                        .height(BOTTOM_BAR - 4.dp)
                        .rotate(-4f)
                        .shadow(10.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Lux.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    TabItem(held, chosen = true, plain = true)
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 16.dp).background(Lux.raised))
        MoreButton(more)
    }
}

/**
 * One tab: its icon over its name. The chosen one is in the accent color with a line above
 * it (Dark and Light), or sits on a highlight that takes in the icon and the name (Material).
 * `plain`: no line or highlight (the copy being dragged).
 */
@Composable
private fun TabItem(name: String, chosen: Boolean, plain: Boolean = false) {
    val material = Lux.style == ThemeStyle.MATERIAL
    val color = when {
        !chosen -> Lux.icon
        material && !plain -> Lux.text
        else -> Lux.accent
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (chosen && !material && !plain) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(Lux.accent)
            )
        }
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (chosen && material && !plain) lerp(Lux.bottomBar, Lux.accent, 0.35f) else Color.Transparent)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(LuxIcons.tab(name), null, tint = color, modifier = Modifier.size(24.dp))
            Text(
                name, color = color, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** An arrow over a fade at one edge of the tabs, showing there are more that way. */
@Composable
private fun EdgeArrow(left: Boolean, lit: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val solid = Lux.bottomBar
    val fade = listOf(solid, solid.copy(alpha = 0.9f), solid.copy(alpha = 0f))
    Box(
        modifier
            .fillMaxHeight()
            .width(34.dp)
            .background(Brush.horizontalGradient(if (left) fade else fade.reversed()))
            .clickable(onClick = onClick),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(
            if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            if (left) "More tabs to the left" else "More tabs to the right",
            tint = if (lit) Lux.accent else Lux.text,
            modifier = Modifier.size(26.dp),
        )
    }
}
