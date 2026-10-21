package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
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

    @Test fun greyCoverOnDarkGivesLightestGrey() {
        val c = AccentPicker.pick(cover(0xFF080808 to 700, 0xFF333333 to 200, 0xFF999999 to 100), black)!!
        assertEquals(0xFF999999.toInt(), c)
    }

    @Test fun allBlackCoverIsMadeReadable() {
        val c = AccentPicker.pick(cover(0xFF101010 to 1000), black)!!
        assertTrue(AccentPicker.contrast(c, black) >= AccentPicker.MIN_CONTRAST)
    }

    @Test fun smallRedOnWhiteIsUsedOnLight() {
        val c = AccentPicker.pick(cover(0xFFFAFAFA to 950, 0xFFC01010 to 50), lightBg)!!
        val (r, g, b) = rgb(c)
        assertTrue("red expected, got ${Integer.toHexString(c)}", r > 150 && g < 60 && b < 60)
    }

    @Test fun resultIsAlwaysReadable() {
        val covers = listOf(
            cover(0xFFFFFF00 to 1000),                       // yellow: fine on black, not on light
            cover(0xFF202040 to 600, 0xFF404060 to 400),
            cover(0xFFEEEEEE to 1000),
        )
        for (bg in listOf(black, lightBg, 0xFF303030.toInt())) {
            for (px in covers) {
                val c = AccentPicker.pick(px, bg)!!
                assertTrue(AccentPicker.contrast(c, bg) >= AccentPicker.MIN_CONTRAST)
            }
        }
    }
}
