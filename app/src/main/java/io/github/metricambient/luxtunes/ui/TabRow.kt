package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.metricambient.luxtunes.data.ThemeStyle
import kotlin.math.roundToInt

/**
 * The overview's tabs. Tap one to show it; long-press and drag one sideways to
 * move it (the new order is saved when you let go).
 */
@Composable
fun DraggableTabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit, onReorder: (List<String>) -> Unit) {
    var order by remember(tabs) { mutableStateOf(tabs) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<String, Int>() }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val material = Lux.style == ThemeStyle.MATERIAL
    Column(Modifier.fillMaxWidth().background(Lux.tabBar)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            for (name in order) {
                val isDragged = name == dragging
                Box(
                    Modifier
                        .onSizeChanged { widths[name] = it.width }
                        .zIndex(if (isDragged) 1f else 0f)
                        .offset { IntOffset(if (isDragged) offset.roundToInt() else 0, 0) }
                        .then(if (isDragged) Modifier.shadow(6.dp).background(if (material) Lux.tabBar else Lux.panel) else Modifier)
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
                ) {
                    Column {
                        Text(
                            name.uppercase(),
                            fontSize = 16.sp,
                            maxLines = 1,
                            color = when {
                                material -> if (name == selected) Lux.onAccent else Lux.onAccent.copy(alpha = 0.7f)
                                else -> if (name == selected) Lux.text else Lux.dim
                            },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                        Box(
                            Modifier
                                .height(3.dp)
                                .width(with(density) { (widths[name] ?: 0).toDp() })
                                .background(if (name != selected) Color.Transparent else if (material) Lux.onAccent else Lux.accent)
                        )
                    }
                }
            }
        }
        if (!material) Box(Modifier.fillMaxWidth().height(1.dp).background(Lux.panel))
    }
}
