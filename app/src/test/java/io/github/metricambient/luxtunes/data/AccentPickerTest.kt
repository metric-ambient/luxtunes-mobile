package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentPickerTest {
    private val black = 0xFF000000.toInt()
    private val lightBg = 0xFFF1F5FA.toInt()

    /** A "cover" made of (color, how many pixels) parts. */
    private fun cover(vararg parts: Pair<Long, Int>): IntArray =
        parts.flatMap { (c, n) -> List(n) { c.toInt() } }.toIntArray()

    private fun rgb(c: Int) = Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    @Test fun emptyPictureGivesNothing() {
        assertNull(AccentPicker.pick(IntArray(0), black))
    }

    @Test fun prominentColorWins() {
        val c = AccentPicker.pick(cover(0xFF1E88E5 to 800, 0xFF101010 to 200), black)!!
        val (r, g, b) = rgb(c)
        assertTrue("blue expected, got ${Integer.toHexString(c)}", b > 200 && r < 60 && g in 110..160)
    }

    @Test fun smallRedOnBlackIsUsedOnDark() {
        val c = AccentPicker.pick(cover(0xFF050505 to 950, 0xFFE02020 to 50), black)!!
        val (r, g, b) = rgb(c)
        assertTrue("red expected, got ${Integer.toHexString(c)}", r > 200 && g < 60 && b < 60)
    }

    @Test fun greyCoverGivesNothing() {
        // Grey would look like the "off" grey of the shuffle and repeat buttons.
        assertNull(AccentPicker.pick(cover(0xFF080808 to 700, 0xFF333333 to 200, 0xFF999999 to 100), black))
        assertNull(AccentPicker.pick(cover(0xFF101010 to 1000), black))
    }

    @Test fun darkNavyCoverIsLightenedOnDark() {
        val c = AccentPicker.pick(cover(0xFF0A1440 to 1000), black)!!
        assertTrue(AccentPicker.contrast(c, black) >= AccentPicker.MIN_CONTRAST)
        val (r, _, b) = rgb(c)
        assertTrue("still blue, got ${Integer.toHexString(c)}", b - r > 45)
    }

    @Test fun smallRedOnWhiteIsUsedOnLight() {
        val c = AccentPicker.pick(cover(0xFFFAFAFA to 950, 0xFFC01010 to 50), lightBg)!!
        val (r, g, b) = rgb(c)
        assertTrue("red expected, got ${Integer.toHexString(c)}", r > 150 && g < 60 && b < 60)
    }

    @Test fun resultIsAlwaysReadable() {
        val covers = listOf(
            cover(0xFFFFFF00 to 1000),                       // yellow: fine on black, not on light
            cover(0xFF202060 to 600, 0xFF404090 to 400),
            cover(0xFFE0F0FF to 900, 0xFF3070C0 to 100),
        )
        for (bg in listOf(black, lightBg, 0xFF303030.toInt())) {
            for (px in covers) {
                val c = AccentPicker.pick(px, bg) ?: continue
                assertTrue(AccentPicker.contrast(c, bg) >= AccentPicker.MIN_CONTRAST)
                val (r, g, b) = rgb(c)
                assertTrue("not grey: ${Integer.toHexString(c)}", maxOf(r, g, b) - minOf(r, g, b) >= 40)
            }
        }
    }
}
