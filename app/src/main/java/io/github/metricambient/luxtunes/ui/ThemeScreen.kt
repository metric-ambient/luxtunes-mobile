package io.github.metricambient.luxtunes.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.data.AccentPicker
import io.github.metricambient.luxtunes.data.ThemeColors
import io.github.metricambient.luxtunes.data.ThemeStyle

private enum class ColorSlot(val label: String) { BACKGROUND("Background"), ACCENT("Accent"), TEXT("Text") }

// Suggested colors in the picker, by slot.
private val ACCENT_SUGGESTIONS = listOf(
    0xFF0099CC, 0xFF1E88E5, 0xFF00ACC1, 0xFF26A69A, 0xFF43A047,
    0xFFFDD835, 0xFFFB8C00, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA,
).map { Color(it) }
private val BACKGROUND_SUGGESTIONS = listOf(
    0xFF000000, 0xFF121212, 0xFF1E1F22, 0xFF303030, 0xFF1B2631,
    0xFFFFFFFF, 0xFFF1F5FA, 0xFFF5F5F5, 0xFFECEFF1, 0xFFFFF8E1,
).map { Color(it) }
private val TEXT_SUGGESTIONS = listOf(
    0xFFFFFFFF, 0xFFEEEEEE, 0xFFBDBDBD, 0xFFE4E4E4, 0xFFE3F2FD,
    0xFF000000, 0xFF212121, 0xFF424242, 0xFF37474F, 0xFF3E2723,
).map { Color(it) }

private fun hexOf(c: Color) = "%06X".format(c.toArgb() and 0xFFFFFF)

/** Settings → Theme: three styles, then the selected style's three colors. */
@Composable
fun ThemeSettingsScreen(nav: NavHostController) {
    val prefs = app().prefs
    val s by prefs.settings.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var editing by remember { mutableStateOf<ColorSlot?>(null) }
    val colors = s.colors

    fun setColors(change: (ThemeColors) -> ThemeColors) = prefs.update { st ->
        st.copy(themeColors = st.themeColors + (st.themeStyle to change(st.colors)))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Theme")
        Heading("Style")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (style in ThemeStyle.entries) {
                StylePreview(
                    style = style,
                    colors = s.themeColors[style] ?: ThemeColors.defaults(style),
                    selected = style == s.themeStyle,
                    modifier = Modifier.weight(1f),
                    onClick = { prefs.update { it.copy(themeStyle = style) } },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        prefs.update {
                            it.copy(themeStyle = style, themeColors = it.themeColors + (style to ThemeColors.defaults(style)))
                        }
                        Toast.makeText(context, "${style.label} colors reset", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
        Text(
            "Tap a style to use it. Long-press one to reset its colors.",
            color = Lux.dim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp),
        )
        Heading("Colors")
        ColorRow("Background", Color(colors.background), hexOf(Color(colors.background))) { editing = ColorSlot.BACKGROUND }
        ColorRow("Accent", Color(colors.accent), hexOf(Color(colors.accent))) { editing = ColorSlot.ACCENT }
        ColorRow("Text", Color(colors.text), hexOf(Color(colors.text))) { editing = ColorSlot.TEXT }
        HorizontalDivider(color = Lux.panel, modifier = Modifier.padding(top = 8.dp))
        ToggleRow(
            "Auto Accent",
            "Take the accent color from the album art: the playing song's, or the album or artist " +
                "you're looking at. When the art has no color that stands out on the background, " +
                "your own accent color above is used. Works in all three styles.",
            s.autoAccent,
        ) { on -> prefs.update { it.copy(autoAccent = on) } }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { slot ->
        val current = when (slot) {
            ColorSlot.BACKGROUND -> colors.background
            ColorSlot.ACCENT -> colors.accent
            ColorSlot.TEXT -> colors.text
        }
        ColorPickerDialog(
            title = slot.label,
            current = Color(current),
            suggestions = when (slot) {
                ColorSlot.BACKGROUND -> BACKGROUND_SUGGESTIONS
                ColorSlot.ACCENT -> ACCENT_SUGGESTIONS
                ColorSlot.TEXT -> TEXT_SUGGESTIONS
            },
            onDismiss = { editing = null },
            onPick = { picked ->
                editing = null
                setColors { c ->
                    when (slot) {
                        ColorSlot.BACKGROUND -> c.copy(background = picked.toArgb())
                        ColorSlot.TEXT -> c.copy(text = picked.toArgb())
                        ColorSlot.ACCENT -> c.copy(accent = picked.toArgb())
                    }
                }
            },
        )
    }
}

/** A small drawing of the overview in a style: tab bar, a few rows, the bottom bar. */
@Composable
private fun StylePreview(
    style: ThemeStyle,
    colors: ThemeColors,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bg = Color(colors.background)
    val text = Color(colors.text)
    val accent = if (selected) Lux.accent else Color(colors.accent)
    val onAccent = if (AccentPicker.luminance(accent.toArgb()) > 0.4) Color.Black else Color.White
    val material = style == ThemeStyle.MATERIAL
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(if (selected) 3.dp else 1.dp, if (selected) Lux.accent else Lux.panel, RoundedCornerShape(10.dp))
            .pointerInput(style) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() }) }
            .padding(if (selected) 6.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(4.dp)).background(bg)) {
            // Tabs
            Row(
                Modifier.fillMaxWidth().height(22.dp).background(if (material) accent else bg).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in 0 until 3) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (material) onAccent.copy(alpha = if (i == 0) 1f else 0.6f) else if (i == 0) text else lerp(text, bg, 0.4f))
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.fillMaxWidth().height(2.dp)
                                .background(if (i != 0) Color.Transparent else if (material) onAccent else accent)
                        )
                    }
                }
            }
            // Rows
            Column(Modifier.weight(1f).padding(6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0 until 4) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(lerp(bg, text, 0.2f)))
                        Column(Modifier.padding(start = 5.dp).weight(1f)) {
                            Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.8f else 0.6f).height(4.dp).background(text))
                            Spacer(Modifier.height(2.dp))
                            Box(Modifier.fillMaxWidth(0.4f).height(3.dp).background(lerp(text, bg, 0.45f)))
                        }
                    }
                }
            }
            // Bottom bar and mini player
            Box(Modifier.fillMaxWidth().height(2.dp).background(accent))
            Box(Modifier.fillMaxWidth().height(16.dp).background(lerp(bg, text, 0.12f)))
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Lux.accent, modifier = Modifier.size(18.dp))
            Text(style.label, color = if (selected) Lux.text else Lux.dim, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
    }
}

/** A color dot with its name (and value) to the right. */
@Composable
private fun ColorRow(label: String, color: Color, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color).border(1.dp, Lux.dim, CircleShape))
        Column(Modifier.padding(start = 18.dp)) {
            Text(label, color = Lux.text, fontSize = 18.sp)
            Text(value, color = Lux.dim, fontSize = 14.sp)
        }
    }
}

// ---- Color picker ----------------------------------------------------------------------

private val HUES = listOf(Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red)

/**
 * Pick a color: suggestions, a saturation/brightness square with a hue bar, or a
 * hex value. Shows the current color and the new one.
 */
@Composable
private fun ColorPickerDialog(
    title: String,
    current: Color,
    suggestions: List<Color>,
    onDismiss: () -> Unit,
    onPick: (Color) -> Unit,
) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(current.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(hexOf(current)) }
    val picked = Color.hsv(hue.coerceIn(0f, 359.9f), sat, value)

    fun choose(c: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        // Greys have no hue of their own: keep the bar where it is.
        if (hsv[1] > 0f) hue = hsv[0]
        sat = hsv[1]; value = hsv[2]
        hex = hexOf(c)
    }
    fun moved() { hex = hexOf(Color.hsv(hue.coerceIn(0f, 359.9f), sat, value)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Lux.panel) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(title, color = Lux.text, fontSize = 20.sp, modifier = Modifier.padding(bottom = 12.dp))

                // Suggestions
                for (row in suggestions.chunked(5)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (c in row) {
                            val chosen = hexOf(c) == hex
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (chosen) 3.dp else 1.dp, if (chosen) Lux.text else Lux.dim, CircleShape)
                                    .clickable { choose(c) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                // Saturation (across) and brightness (down), then the hue bar
                Row(Modifier.fillMaxWidth().height(200.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue.coerceIn(0f, 359.9f), 1f, 1f))))
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                            .pointerInput(Unit) {
                                fun at(p: Offset) {
                                    sat = (p.x / size.width).coerceIn(0f, 1f)
                                    value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                                    moved()
                                }
                                detectTapGestures { at(it) }
                            }
                            .pointerInput(Unit) {
                                fun at(p: Offset) {
                                    sat = (p.x / size.width).coerceIn(0f, 1f)
                                    value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                                    moved()
                                }
                                detectDragGestures(onDragStart = { at(it) }) { change, _ -> change.consume(); at(change.position) }
                            }
                            .drawWithContent {
                                drawContent()
                                val c = Offset(sat * size.width, (1f - value) * size.height)
                                drawCircle(Color.Black, radius = 10.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
                                drawCircle(Color.White, radius = 8.5.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                            },
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .width(30.dp)
                            .fillMaxHeight()
                            .background(Brush.verticalGradient(HUES))
                            .pointerInput(Unit) {
                                fun at(p: Offset) {
                                    hue = (1f - (p.y / size.height).coerceIn(0f, 1f)) * 360f
                                    moved()
                                }
                                detectTapGestures { at(it) }
                            }
                            .pointerInput(Unit) {
                                fun at(p: Offset) {
                                    hue = (1f - (p.y / size.height).coerceIn(0f, 1f)) * 360f
                                    moved()
                                }
                                detectDragGestures(onDragStart = { at(it) }) { change, _ -> change.consume(); at(change.position) }
                            }
                            .drawWithContent {
                                drawContent()
                                val y = (1f - hue / 360f) * size.height
                                drawRect(Color.Black, topLeft = Offset(0f, y - 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 6.dp.toPx()), style = Stroke(1.dp.toPx()))
                                drawRect(Color.White, topLeft = Offset(1.dp.toPx(), y - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width - 2.dp.toPx(), 4.dp.toPx()), style = Stroke(2.dp.toPx()))
                            },
                    )
                }
                Spacer(Modifier.height(16.dp))

                // Old → new, as hex
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("#" + hexOf(current), color = Lux.text, fontSize = 18.sp)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Lux.text, modifier = Modifier.padding(horizontal = 14.dp))
                    Text("#", color = Lux.text, fontSize = 18.sp)
                    Column(Modifier.width(96.dp).padding(start = 4.dp)) {
                        BasicTextField(
                            value = hex,
                            onValueChange = { typed ->
                                val clean = typed.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                                hex = clean
                                if (clean.length == 6) {
                                    val c = Color(0xFF000000 or clean.toLong(16))
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
                                    if (hsv[1] > 0f) hue = hsv[0]
                                    sat = hsv[1]; value = hsv[2]
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = Lux.text, fontSize = 18.sp),
                            cursorBrush = SolidColor(Lux.accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(Modifier.fillMaxWidth().height(2.dp).background(Lux.accent))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    PreviewDot(current)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Lux.text, modifier = Modifier.padding(horizontal = 14.dp))
                    PreviewDot(picked)
                }
                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Lux.accent, fontSize = 17.sp) }
                    TextButton(
                        onClick = { onPick(picked) },
                        enabled = hex.length == 6,
                    ) { Text("OK", color = Lux.accent, fontSize = 17.sp) }
                }
            }
        }
    }
}

/** A color in a circle. */
@Composable
private fun PreviewDot(c: Color) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(c).border(1.dp, Lux.dim, CircleShape))
}
