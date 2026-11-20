package io.github.metricambient.luxtunes.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The app's own icons (drawn as lines on a 24 grid); everything else is Material. */
object LuxIcons {
    private const val SHUFFLE = "M3 7h3.5c4 0 6 10 10 10H21M3 17h3.5c1.6 0 2.8-1.6 3.9-3.6M14 7h7M18 4l3 3-3 3M18 14l3 3-3 3"
    private const val REPEAT = "M17 3l3 3-3 3M20 6H8a4 4 0 0 0-4 4v1M7 21l-3-3 3-3M4 18h12a4 4 0 0 0 4-4v-1"
    private const val ONE = "M11.5 10.5l1.5-1v5"

    private fun lines(name: String, path: String, width: Float) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        fill = null,
        stroke = SolidColor(Color.Black),      // (tinted where it's used)
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

    val Shuffle = lines("Shuffle", SHUFFLE, 2f)
    val Repeat = lines("Repeat", REPEAT, 2f)
    val RepeatOne = lines("RepeatOne", REPEAT + ONE, 2f)
    // Turned on: a touch bolder.
    val ShuffleOn = lines("ShuffleOn", SHUFFLE, 2.4f)
    val RepeatOn = lines("RepeatOn", REPEAT, 2.4f)
    val RepeatOneOn = lines("RepeatOneOn", REPEAT + ONE, 2.4f)

    /** A tab's icon in the bottom bar and on its play and shuffle buttons. */
    fun tab(name: String): ImageVector = when (name) {
        "Artists", "Album Artists" -> Icons.Filled.Person
        "Albums" -> Icons.Filled.Album
        "Songs" -> Icons.Filled.MusicNote
        "Playlists" -> Icons.AutoMirrored.Filled.QueueMusic
        "Genres" -> Icons.Filled.Category
        "Composers" -> Icons.Filled.Piano
        "Podcasts" -> Icons.Filled.Mic
        "Audiobooks" -> Icons.Filled.Book
        "Movies" -> Icons.Filled.Movie
        "TV Shows" -> Icons.Filled.Tv
        else -> Icons.Filled.MusicNote
    }
}
