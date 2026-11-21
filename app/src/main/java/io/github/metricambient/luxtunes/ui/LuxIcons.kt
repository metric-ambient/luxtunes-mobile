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

    /**
     * Radio Mix: two beamed notes inside a clockwise circular arrow ("a mix that keeps going").
     * Drawn on a 64 grid from radio-mix-icon.svg (shared with LuxTunes on the computer).
     */
    val RadioMix: ImageVector = ImageVector.Builder(
        name = "RadioMix", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 64f, viewportHeight = 64f,
    ).apply {
        fun stroke(path: String, width: Float) = addPath(
            pathData = addPathNodes(path), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width,
        )
        fun fill(path: String) = addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
            "M${cx - rx} ${cy}a$rx $ry 0 1 0 ${2 * rx} 0a$rx $ry 0 1 0 ${-2 * rx} 0Z"
        stroke("M56.43 23.11A26 26 0 1 1 40.89 7.57", 4f)          // the circular arrow
        stroke("M28.7 40.5L28.7 23.7M44.7 36.5L44.7 19.7", 2.5f)    // the note stems
        stroke("M28.7 23.7L44.7 19.7", 4.2f)                        // the beam
        fill("M48.41 10.3L42.94 1.93L38.84 13.21Z")                 // the arrowhead
        fill(ellipse(24.5f, 40.5f, 4.8f, 3.6f))                     // the note heads
        fill(ellipse(40.5f, 36.5f, 4.8f, 3.6f))
    }.build()

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
