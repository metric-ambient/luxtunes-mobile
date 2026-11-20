package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/** The bar along the bottom of the main screen and of album, artist and playlist pages. */
val BOTTOM_BAR = 60.dp

/** Room the floating mini player takes over a screen's list (0 when it isn't showing). */
val LocalMiniSpace = compositionLocalOf { 0.dp }

/** How far up the mini player floats on this screen: above its bottom bar, if it has one. */
fun miniLift(route: String?): Dp = when {
    route == null -> 0.dp
    route == Routes.HOME || route.startsWith("album/") || route.startsWith("group/") || route.startsWith("playlist/") -> BOTTOM_BAR
    else -> 0.dp
}

/** A page's bottom bar: back at the left, the page's ⋮ menu at the right. */
@Composable
fun PageBottomBar(nav: NavHostController, menu: Menu, title: String? = null) {
    Row(
        Modifier.fillMaxWidth().height(BOTTOM_BAR).background(Lux.bar).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Lux.text)
        }
        Spacer(Modifier.weight(1f))
        if (menu.isNotEmpty()) MoreMenu(menu, title = title)
    }
}

/**
 * A tab's icon with a small play or shuffle mark at its lower right: the "play this
 * tab from the top" and "shuffle this tab" buttons.
 */
@Composable
fun TabActionIcon(tab: ImageVector, shuffle: Boolean, description: String) {
    Box(Modifier.size(32.dp)) {
        Icon(tab, description, tint = Lux.icon, modifier = Modifier.size(24.dp).align(Alignment.TopStart).offset(1.dp, 1.dp))
        Box(
            Modifier.size(17.dp).align(Alignment.BottomEnd).clip(CircleShape).background(Lux.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (shuffle) LuxIcons.ShuffleOn else Icons.Filled.PlayArrow, null,
                tint = Lux.accent, modifier = Modifier.size(if (shuffle) 13.dp else 15.dp),
            )
        }
    }
}
