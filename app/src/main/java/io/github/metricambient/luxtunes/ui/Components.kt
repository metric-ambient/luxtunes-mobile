package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** Album art from the LuxTunes folder, or a music note when there's none. */
@Composable
fun Artwork(path: String?, modifier: Modifier = Modifier, noteSize: Dp = 48.dp) {
    val file = app().repository.artworkFile(path)
    Box(modifier.background(Color(0xFF1F1F1F)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.MusicNote, null, tint = Color(0xFF555555), modifier = Modifier.size(noteSize))
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---- Rows ------------------------------------------------------------------------------------

/** A list row: optional picture, two lines of text, optional ⋮ menu. */
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    highlighted: Boolean = false,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        if (menu.isNotEmpty()) MoreMenu(menu) else Box(Modifier.width(16.dp))
    }
}

@Composable
fun MoreMenu(items: List<Pair<String, () -> Unit>>, icon: ImageVector = Icons.Filled.MoreVert, tint: Color = Lux.icon) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(icon, "More", tint = tint) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
) {
    val lead: (@Composable () -> Unit)? = when {
        leading != null -> leading
        number != null -> @Composable { TrackNumber(number) }
        else -> null
    }
    ListRow(
        title = track.name,
        subtitle = "${track.artist} (${formatTime(track.totalTime)})",
        onClick = onClick,
        highlighted = current,
        menu = menu,
        leading = lead,
    )
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

data class BarAction(val icon: ImageVector, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun ActionBar(actions: List<BarAction>) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(Lux.bar),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (a in actions) {
            IconButton(onClick = a.onClick, enabled = a.enabled) {
                Icon(a.icon, a.label, tint = if (a.enabled) Lux.icon else Color(0xFF555555), modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
fun Heading(text: String) {
    Text(
        text, color = Lux.accent, fontWeight = FontWeight.Medium, fontSize = 15.sp,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
