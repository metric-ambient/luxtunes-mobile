package io.github.metricambient.luxtunes.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.metricambient.luxtunes.data.Lyrics

/**
 * A song's lyrics. Timed ones (with Settings → Operation → Highlight lyrics on) light
 * up the line being sung and keep it in the middle; otherwise just the words, scrolled
 * freely. `bottomSpace` keeps the last lines clear of whatever sits below (the rating).
 */
@Composable
fun LyricsView(
    songId: String,
    plain: String?,
    synced: String?,
    positionMs: Long,
    follow: Boolean,
    modifier: Modifier = Modifier,
    bottomSpace: Dp = 64.dp,
) {
    val timed = remember(songId, plain, synced) { Lyrics.parse(synced) ?: Lyrics.parse(plain) }
    if (!follow || timed == null) {
        val words = remember(songId, plain, synced) { plain?.let { Lyrics.plain(it) } ?: synced?.let { Lyrics.plain(it) } ?: "" }
        Text(
            words,
            color = Lux.text, fontSize = 18.sp, textAlign = TextAlign.Center,
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = bottomSpace)
                .verticalScroll(remember(songId) { ScrollState(0) })
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
        return
    }

    val current = Lyrics.lineAt(timed, positionMs)
    val dim = lerp(Lux.background, Lux.text, 0.45f)
    val state = remember(songId) { LazyListState() }      // a new song starts at the top
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The line being sung moves to the middle (only when it changes, or after a seek).
        LaunchedEffect(songId, current) {
            if (current < 0) return@LaunchedEffect
            var item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }
            if (item == null) {
                state.scrollToItem(current)
                item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current } ?: return@LaunchedEffect
            }
            val middle = (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2
            state.animateScrollBy((item.offset + item.size / 2 - middle).toFloat(), tween(200))
        }
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().padding(bottom = bottomSpace),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = maxHeight / 2),
        ) {
            itemsIndexed(timed) { i, line ->
                val now = i == current
                Text(
                    line.text,
                    color = if (now) Lux.text else dim,
                    fontSize = 18.sp,
                    fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }
    }
}
