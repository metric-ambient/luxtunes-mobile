package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.metricambient.luxtunes.data.ThemeStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Narrower than this and the tabs scroll instead of sharing the bar. */
private val MIN_TAB = 72.dp
private val SCROLL_TAB = 78.dp

/**
 * The overview's tabs along the bottom, each an icon over its name, and the main ⋮ menu
 * at the right. If they all fit they share the bar; if not, they scroll, with an arrow
 * at each side that has more. Tap one to show it; long-press and drag one sideways to
 * move it (the new order is saved when you let go).
 */
@Composable
fun BottomTabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit, onReorder: (List<String>) -> Unit, menu: Menu) {
    var order by remember(tabs) { mutableStateOf(tabs) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<String, Int>() }
    val haptics = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    Row(Modifier.fillMaxWidth().height(BOTTOM_BAR).background(Lux.bar)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val fits = maxWidth / order.size.coerceAtLeast(1) >= MIN_TAB
            val tabWidth = if (fits) maxWidth / order.size.coerceAtLeast(1) else SCROLL_TAB
            Row(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                for (name in order) {
                    val isDragged = name == dragging
                    Box(
                        Modifier
                            .width(tabWidth)
                            .fillMaxHeight()
                            .onSizeChanged { widths[name] = it.width }
                            .zIndex(if (isDragged) 1f else 0f)
                            .offset { IntOffset(if (isDragged) offset.roundToInt() else 0, 0) }
                            .then(if (isDragged) Modifier.shadow(6.dp).background(Lux.panel) else Modifier)
                            .pointerInput(name) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragging = name
                                        offset = 0f
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        offset = 0f
                                        onReorder(order)
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        offset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        offset += amount.x
                                        // Passed half of the neighbour's width: swap places with it.
                                        val i = order.indexOf(name)
                                        if (offset > 0 && i < order.lastIndex) {
                                            val next = widths[order[i + 1]] ?: 0
                                            if (offset > next / 2f) {
                                                order = order.toMutableList().also { it[i] = it[i + 1]; it[i + 1] = name }
                                                offset -= next
                                            }
                                        } else if (offset < 0 && i > 0) {
                                            val prev = widths[order[i - 1]] ?: 0
                                            if (-offset > prev / 2f) {
                                                order = order.toMutableList().also { it[i] = it[i - 1]; it[i - 1] = name }
                                                offset += prev
                                            }
                                        }
                                    },
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
                if (scroll.value > 0) EdgeArrow(left = true, modifier = Modifier.align(Alignment.CenterStart)) {
                    scope.launch { scroll.animateScrollTo(scroll.value - page()) }
                }
                if (scroll.value < scroll.maxValue) EdgeArrow(left = false, modifier = Modifier.align(Alignment.CenterEnd)) {
                    scope.launch { scroll.animateScrollTo(scroll.value + page()) }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 14.dp).background(Lux.panel))
        Box(Modifier.width(52.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            MoreMenu(menu)
        }
    }
}

/**
 * One tab: its icon over its name. The chosen one is in the accent color with a line above
 * it (Dark and Light), or sits on a highlight that takes in the icon and the name (Material).
 */
@Composable
private fun TabItem(name: String, chosen: Boolean) {
    val material = Lux.style == ThemeStyle.MATERIAL
    val color = when {
        !chosen -> Lux.icon
        material -> Lux.text
        else -> Lux.accent
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (chosen && !material) {
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
                .background(if (chosen && material) lerp(Lux.bar, Lux.accent, 0.35f) else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
private fun EdgeArrow(left: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val solid = Lux.bar
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
            tint = Lux.text,
            modifier = Modifier.size(26.dp),
        )
    }
}
