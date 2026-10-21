package io.github.metricambient.luxtunes.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Picks an accent color from album art pixels (ARGB) that stands out on `background`:
 * 1. the cover's most prominent color, if it's readable on the background;
 * 2. else its strongest readable splash of color (a bit of red on a black cover);
 * 3. else its readable shade furthest from the background (the lightest grey on dark);
 * 4. else the prominent color, lightened or darkened until it's readable.
 */
object AccentPicker {
    /** Contrast needed against the background (the usual minimum for icons and lines). */
    const val MIN_CONTRAST = 3.0
    private const val MIN_SHARE = 0.003      // ignore specks smaller than this part of the picture
    private const val COLORFUL = 0.25        // chroma that counts as "a color" rather than a grey

    private class Swatch(val color: Int, val share: Double) {
        val chroma: Double = run {
            val r = (color shr 16) and 0xFF; val g = (color shr 8) and 0xFF; val b = color and 0xFF
            (max(r, max(g, b)) - min(r, min(g, b))) / 255.0
        }
        val brightness: Double = run {
            val r = (color shr 16) and 0xFF; val g = (color shr 8) and 0xFF; val b = color and 0xFF
            max(r, max(g, b)) / 255.0
        }
        // Big areas count, but colorful ones count more; near-black barely counts.
        val score: Double = sqrt(share) * (0.12 + chroma) * (if (brightness < 0.2) 0.3 else 1.0)
    }

    fun pick(pixels: IntArray, background: Int): Int? {
        // Group similar colors: 8 levels per channel, averaged within each group.
        val count = IntArray(512)
        val rs = LongArray(512); val gs = LongArray(512); val bs = LongArray(512)
        var total = 0
        for (p in pixels) {
            if ((p ushr 24) < 128) continue           // mostly transparent
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val bin = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
            count[bin]++; rs[bin] += r.toLong(); gs[bin] += g.toLong(); bs[bin] += b.toLong()
            total++
        }
        if (total == 0) return null
        val swatches = (0 until 512).filter { count[it] > 0 }.map { i ->
            val n = count[i]
            val c = (0xFF shl 24) or ((rs[i] / n).toInt() shl 16) or ((gs[i] / n).toInt() shl 8) or (bs[i] / n).toInt()
            Swatch(c, n.toDouble() / total)
        }
        val prominent = swatches.maxBy { it.score }
        if (contrast(prominent.color, background) >= MIN_CONTRAST) return prominent.color

        val readable = swatches.filter { it.share >= MIN_SHARE && contrast(it.color, background) >= MIN_CONTRAST }
        readable.filter { it.chroma >= COLORFUL }.maxByOrNull { sqrt(it.share) * it.chroma }?.let { return it.color }
        readable.maxByOrNull { contrast(it.color, background) }?.let { return it.color }
        return readableVersion(prominent.color, background)
    }

    /** `color` moved toward white (dark background) or black (light one) until readable. */
    fun readableVersion(color: Int, background: Int): Int {
        val target = if (luminance(background) > 0.4) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        var t = 0.0
        var c = color
        while (contrast(c, background) < MIN_CONTRAST && t < 1.0) {
            t += 0.05
            c = mix(color, target, t)
        }
        return c
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Relative luminance (0 black … 1 white), as used for contrast ratios. */
    fun luminance(c: Int): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun m(sh: Int) = ((((a shr sh) and 0xFF) * (1 - t)) + (((b shr sh) and 0xFF) * t)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }
}
