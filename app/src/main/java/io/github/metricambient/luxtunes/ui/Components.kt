package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.metricambient.luxtunes.LuxTunesApp
import io.github.metricambient.luxtunes.data.Track

@Composable
fun app(): LuxTunesApp = LocalContext.current.applicationContext as LuxTunesApp

// ---- Text helpers ----------------------------------------------------------------------

/** "4:05" */
fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "44m" or "4h, 14m" */
fun formatLength(ms: Long): String {
    val minutes = (ms / 60000).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60}h, ${minutes % 60}m" else "${minutes}m"
}

fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

// ---- Pictures ----------------------------------------------------------------------------

/**
 * A song's picture: the album cover LuxTunes copied, else the art inside the
 * song file, else `note` (a music note).
 */
@Composable
fun Artwork(
    song: Track?,
    modifier: Modifier = Modifier,
    noteSize: Dp = 48.dp,
    note: ImageVector = Icons.Filled.MusicNote,
) {
    val app = app()
    val settings by app.prefs.settings.collectAsState()
    // Settings → Operation → Disable Album Art: always the plain note.
    val art = if (settings.noAlbumArt) null else app.repository.art(song)
    Box(modifier.background(Lux.placeholder), contentAlignment = Alignment.Center) {
        Icon(note, null, tint = Lux.dim, modifier = Modifier.size(noteSize))
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---- Rows ------------------------------------------------------------------------------------

/** A row's picture slot, only when `show` (null means no slot at all). */
fun optional(show: Boolean, content: @Composable () -> Unit): (@Composable () -> Unit)? =
    if (show) content else null

/** A list row: optional picture, two lines of text, optional ⋮ menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    highlighted: Boolean = false,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
    onLongClick: (() -> Unit)? = null,       // long-press: start ticking (select mode)
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (highlighted) Lux.accent else Lux.text,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 14.sp, color = Lux.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (menu.isNotEmpty()) MoreMenu(menu, title = title) else Box(Modifier.width(16.dp))
    }
}

@Composable
fun MoreMenu(
    items: List<Pair<String, () -> Unit>>,
    icon: ImageVector = Icons.Filled.MoreVert,
    tint: Color = Lux.icon,
    title: String? = null,          // what the menu is for, shown at its top
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(icon, "More", tint = tint) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!title.isNullOrBlank()) MenuTitle(title)
            for ((label, action) in items) {
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}

@Composable
fun SongRow(
    track: Track,
    onClick: () -> Unit,
    current: Boolean,
    menu: List<Pair<String, () -> Unit>>,
    number: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String = "${track.artist} (${formatTime(track.totalTime)})",
    onLongClick: (() -> Unit)? = null,
) {
    if (leading == null && number != null) {
        ListRow(track.displayName, subtitle, onClick, leading = { TrackNumber(number) }, highlighted = current, menu = menu, onLongClick = onLongClick)
    } else {
        ListRow(track.displayName, subtitle, onClick, leading = leading, highlighted = current, menu = menu, onLongClick = onLongClick)
    }
}

@Composable
private fun TrackNumber(n: Int) {
    Text("$n", fontSize = 30.sp, color = Lux.text, modifier = Modifier.width(44.dp))
}

// ---- Search box --------------------------------------------------------------------------

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Search, "Search", tint = Lux.text, modifier = Modifier.size(32.dp))
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Lux.accent,
                unfocusedIndicatorColor = Lux.dim,
            ),
        )
    }
}

/** Case-insensitive "contains" on any of the texts. */
fun matches(query: String, vararg texts: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || texts.any { it.contains(q, ignoreCase = true) }
}

// ---- Stars -------------------------------------------------------------------------------------

/** Five stars; tapping the star already set clears the rating. */
@Composable
fun StarRating(rating: Int, computed: Boolean, onRate: (Int) -> Unit, size: Dp = 44.dp) {
    val stars = (rating + 10) / 20
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val filled = i <= stars
            Icon(
                if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "$i stars",
                tint = when {
                    filled && computed -> Lux.dim         // the album's rating, not this song's
                    filled -> Lux.accent
                    else -> Lux.dim
                },
                modifier = Modifier
                    .size(size)
                    .clickable { onRate(if (i == stars && !computed) 0 else i * 20) }
                    .padding(4.dp),
            )
        }
    }
}

// ---- Bottom action bar ------------------------------------------------------------------

@Composable
fun Heading(text: String) {
    Text(
        text, color = Lux.accent, fontWeight = FontWeight.Medium, fontSize = 15.sp,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** The name at the top of a ⋮ menu, on a lighter band, so you can see what it's for. */
@Composable
fun MenuTitle(text: String) {
    Text(
        text, color = Lux.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .background(lerp(Lux.panel, Lux.text, 0.12f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * One line of text. Too long to fit: after a few seconds it scrolls round once, pauses a
 * few seconds, and goes again, until the text changes. Short text stays still. Each time
 * it comes into view it starts again from the left, still.
 */
@Composable
fun ScrollingText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var boxWidth by remember { mutableIntStateOf(0) }
    var textWidth by remember(text) { mutableIntStateOf(0) }
    val gap = 48.dp
    val tooLong = boxWidth > 0 && textWidth > boxWidth
    LaunchedEffect(text, tooLong) {
        scroll.scrollTo(0)
        if (!tooLong) return@LaunchedEffect
        val loop = textWidth + with(density) { gap.roundToPx() }      // one copy + the gap
        val millis = (loop / with(density) { 40.dp.toPx() } * 1000).toInt()   // 40 dp a second
        while (true) {
            delay(3000)
            scroll.animateScrollTo(loop, tween(millis, easing = LinearEasing))
            scroll.scrollTo(0)          // the second copy is where the first was: no jump
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .onSizeChanged { boxWidth = it.width }
            .horizontalScroll(scroll, enabled = false),
    ) {
        Text(
            text, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, softWrap = false,
            onTextLayout = { textWidth = it.size.width },
        )
        if (tooLong) {
            Spacer(Modifier.width(gap))
            Text(text, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, softWrap = false)
        }
    }
}
