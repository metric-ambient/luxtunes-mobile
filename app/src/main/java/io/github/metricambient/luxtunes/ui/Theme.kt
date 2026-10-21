package io.github.metricambient.luxtunes.ui

import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.github.metricambient.luxtunes.data.AccentPicker
import io.github.metricambient.luxtunes.data.ThemeColors
import io.github.metricambient.luxtunes.data.ThemeStyle
import io.github.metricambient.luxtunes.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

/** Every color the screens use, worked out from a style's three colors. */
class LuxPalette(val style: ThemeStyle, colors: ThemeColors) {
    val background = Color(colors.background)
    val text = Color(colors.text)
    val chosenAccent = Color(colors.accent)
    val autoAccent = colors.autoAccent
    val isLight = background.luminance() > 0.5f
    val bar = lerp(background, text, if (isLight) 0.075f else 0.12f)      // bottom bars and menus
    val panel = lerp(background, text, if (isLight) 0.12f else 0.17f)
    val dim = lerp(text, background, if (isLight) 0.42f else 0.31f)       // second lines
    val icon = lerp(text, background, if (isLight) 0.3f else 0.26f)
    val faint = lerp(background, text, 0.33f)                              // unavailable, placeholders
    val track = lerp(background, text, 0.27f)                              // empty part of bars
    val shade = background.copy(alpha = 0.75f)                             // over album art
}

/**
 * The app's colors. They're snapshot state, so screens redraw when the theme
 * changes. With Auto accent, the accent follows the playing song's art, and an
 * album or artist page uses its own cover while it's showing.
 */
object Lux {
    private var palette by mutableStateOf(LuxPalette(ThemeStyle.DARK, ThemeColors.defaults(ThemeStyle.DARK)))
    private var playingAccent by mutableStateOf<Color?>(null)
    private val pageAccents = mutableStateListOf<Pair<Any, Color>>()

    val style: ThemeStyle get() = palette.style
    val isLight: Boolean get() = palette.isLight
    val auto: Boolean get() = palette.autoAccent
    val accent: Color
        get() = if (!palette.autoAccent) palette.chosenAccent
        else pageAccents.lastOrNull()?.second ?: playingAccent ?: Color(ThemeColors.BLUE)
    /** Text and icons drawn on top of the accent. */
    val onAccent: Color get() = if (accent.luminance() > 0.4f) Color.Black else Color.White
    val background: Color get() = palette.background
    val bar: Color get() = palette.bar
    val panel: Color get() = palette.panel
    val text: Color get() = palette.text
    val dim: Color get() = palette.dim
    val icon: Color get() = palette.icon
    val faint: Color get() = palette.faint
    val track: Color get() = palette.track
    val shade: Color get() = palette.shade
    /** The overview's tab bar: the accent in Material, the background otherwise. */
    val tabBar: Color get() = if (style == ThemeStyle.MATERIAL) accent else background

    internal fun use(p: LuxPalette) { palette = p }
    internal fun setPlaying(c: Color?) { if (playingAccent != c) playingAccent = c }
    internal fun setPage(token: Any, c: Color?) {
        val i = pageAccents.indexOfFirst { it.first === token }
        when {
            c == null -> if (i >= 0) pageAccents.removeAt(i)
            i < 0 -> pageAccents.add(token to c)
            pageAccents[i].second != c -> pageAccents[i] = token to c
        }
    }
    internal fun clearPage(token: Any) = setPage(token, null)
}

@Composable
fun LuxTheme(content: @Composable () -> Unit) {
    val prefs = app().prefs
    val settings by prefs.settings.collectAsState()
    val palette = remember(settings.themeStyle, settings.colors) { LuxPalette(settings.themeStyle, settings.colors) }
    SideEffect { Lux.use(palette) }     // (LuxTunesApp sets the saved one before the first frame)
    PlayingAccent()

    // Status and navigation bar icons: dark on a light background, light on a dark one.
    val activity = LocalContext.current.findActivity() as? ComponentActivity
    LaunchedEffect(palette.isLight) {
        val clear = AndroidColor.TRANSPARENT
        val style = if (palette.isLight) SystemBarStyle.light(clear, clear) else SystemBarStyle.dark(clear)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    val accent = Lux.accent
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = accent, onPrimary = Lux.onAccent, secondary = accent,
            background = palette.background, onBackground = palette.text,
            surface = palette.bar, onSurface = palette.text,
            surfaceVariant = palette.panel, onSurfaceVariant = palette.dim,
            surfaceContainer = palette.panel, surfaceContainerHigh = palette.panel,
            surfaceContainerHighest = palette.panel, surfaceContainerLow = palette.bar,
        )
    } else {
        darkColorScheme(
            primary = accent, onPrimary = Lux.onAccent, secondary = accent,
            background = palette.background, onBackground = palette.text,
            surface = palette.bar, onSurface = palette.text,
            surfaceVariant = palette.panel, onSurfaceVariant = palette.dim,
            surfaceContainer = palette.panel, surfaceContainerHigh = palette.panel,
            surfaceContainerHighest = palette.panel, surfaceContainerLow = palette.bar,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        // The text colour everywhere: it also gives taps their visible "flash".
        CompositionLocalProvider(LocalContentColor provides palette.text, content = content)
    }
}

// ---- Auto accent -----------------------------------------------------------------------

/** Auto accent from the playing song's art. */
@Composable
private fun PlayingAccent() {
    val app = app()
    val library by app.repository.state.collectAsState()
    val state by app.player.state.collectAsState()
    val color = rememberArtAccent(state.currentId?.let { library.byId[it] })
    SideEffect { Lux.setPlaying(color) }
}

/** While this page shows, Auto accent comes from `track`'s art (an album or artist page). */
@Composable
fun PageAccent(track: Track?) {
    val color = rememberArtAccent(track)
    val token = remember { Any() }
    DisposableEffect(token) { onDispose { Lux.clearPage(token) } }
    SideEffect { Lux.setPage(token, color) }
}

/** The accent `track`'s art gives on the current background, or null (no art, or Auto is off). */
@Composable
fun rememberArtAccent(track: Track?): Color? {
    val context = LocalContext.current
    val art = if (Lux.auto) app().repository.art(track) else null
    val background = Lux.background.toArgb()
    val key = art?.let { "${it.cover?.path}|${it.song?.path}|$background" }
    val color by produceState<Color?>(initialValue = key?.let { AccentCache.get(it) }, key) {
        if (art == null || key == null) { value = null; return@produceState }
        AccentCache.get(key)?.let { value = it; return@produceState }
        val request = ImageRequest.Builder(context).data(art).size(96).allowHardware(false).build()
        val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        val picked = bitmap?.let {
            withContext(Dispatchers.Default) {
                val pixels = IntArray(it.width * it.height)
                it.getPixels(pixels, 0, it.width, 0, 0, it.width, it.height)
                AccentPicker.pick(pixels, background)
            }
        }
        value = picked?.let { Color(it) }
        value?.let { AccentCache.put(key, it) }
    }
    return color
}

private object AccentCache {
    private val cache = LruCache<String, Color>(200)
    fun get(key: String): Color? = cache.get(key)
    fun put(key: String, c: Color) { cache.put(key, c) }
}
