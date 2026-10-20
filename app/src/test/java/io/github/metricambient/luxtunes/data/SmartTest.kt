package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Kotlin port against bytes made by LuxTunes' own smart.py (fake rules). */
class SmartTest {
    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val info = hex("01010103000000190000001900010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
    private val criteria = hex(
            "534c73740001000100000006000000000000000000000000000000000000000000000000000000000000000000000000" +
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000401000002" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a" +
            "00c9006d0065007200790000001900000010000000000000000000000000000000000000000000000000000000000000" +
            "000000000000000000000000000000000044000000000000003c00000000000000000000000000000001000000000000" +
            "003c00000000000000000000000000000001000000000000000000000000000000000000000000000010000002000000" +
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000442dae" +
            "2dae2dae2daefffffffffffffffe0000000000093a802dae2dae2dae2dae000000000000000000000000000000010000" +
            "000000000000000000000000000000000000000000170000010000000000000000000000000000000000000000000000" +
            "000000000000000000000000000000000000000000000000004400000000a1c44e000000000000000000000000000000" +
            "000100000000b491a2800000000000000000000000000000000100000000000000000000000000000000000000000000" +
            "002802000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000441a2b3c4d5e6f7081000000000000000000000000000000011a2b3c4d5e6f708100000000000000000000" +
            "000000000001000000000000000000000000000000000000000000000000000000010100000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000180534c737400010001000000020000" +
            "000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000003c000004000000000000000000000000000000" +
            "000000000000000000000000000000000000000000000000000000000000000000440000000000000001000000000000" +
            "000000000000000000010000000000000001000000000000000000000000000000010000000000000000000000000000" +
            "0000000000000000001f0000000100000000000000000000000000000000000000000000000000000000000000000000" +
            "000000000000000000000000004400000000000000010000000000000000000000000000000100000000000000010000" +
            "00000000000000000000000000010000000000000000000000000000000000000000" +
            ""
    )

    @Test
    fun decodesLuxTunesRules() {
        val p = Smart.decode(info, criteria)
        assertTrue(p.info.limit)
        assertEquals(Smart.SORT_PLAYS, p.info.limitSort)
        assertEquals(25L, p.info.limitValue)
        assertTrue(p.info.reverse)
        assertTrue(p.understood)
        val rules = p.rules.rules
        assertEquals(6, rules.size)
        assertEquals("Émery", (rules[0] as Smart.Rule).text)
        assertEquals(60L, (rules[1] as Smart.Rule).value)
        assertEquals(2L, (rules[2] as Smart.Rule).amount)
        assertEquals(Smart.WEEK, (rules[2] as Smart.Rule).units)
        assertEquals(631152000L, (rules[3] as Smart.Rule).value)
        assertEquals(946598400L, (rules[3] as Smart.Rule).value2)
        assertEquals("1A2B3C4D5E6F7081", (rules[4] as Smart.Rule).playlist)
        assertTrue((rules[5] as Smart.Group).matchAny)
    }

    @Test
    fun encodesExactlyLikeLuxTunes() {
        val p = Smart.decode(info, criteria)
        val (i, c) = Smart.encode(p)
        assertArrayEquals(info, i)
        assertArrayEquals(criteria, c)
    }

    private class FakeSong(override val id: String, val values: Map<String, Any?>) : Smart.Song {
        override fun value(column: String) = values[column]
    }

    @Test
    fun evaluatesRules() {
        val now = 1_792_000_000L
        val songs = listOf(
            FakeSong("A", mapOf("artist" to "Fake Band", "rating" to 80L, "play_count" to 5L)),
            FakeSong("B", mapOf("artist" to "Fake Band", "rating" to 40L, "play_count" to 9L)),
            FakeSong("C", mapOf("artist" to "Other", "rating" to 100L, "play_count" to 1L)),
        )
        val p = Smart.Playlist(
            Smart.Info(),
            Smart.Group(false, listOf(
                Smart.Rule(0x04, Smart.S_CONTAINS, text = "fake"),
                Smart.Rule(0x19, Smart.GREATER, value = 60),
            )),
        )
        val none = Smart.Members { emptySet() }
        assertEquals(listOf("A"), Smart.evaluate(p, songs, now, 1, none))
        val anyOf = p.copy(rules = p.rules.copy(matchAny = true))
        assertEquals(listOf("A", "B", "C"), Smart.evaluate(anyOf, songs, now, 1, none))
        val top1 = Smart.Playlist(Smart.Info(limit = true, limitValue = 1, limitSort = Smart.SORT_PLAYS), Smart.Group())
        assertEquals(listOf("B"), Smart.evaluate(top1, songs, now, 1, none))
        val notIn = Smart.Playlist(rules = Smart.Group(false, listOf(Smart.Rule(0x28, Smart.IS_NOT, playlist = "00000000000000AA"))))
        assertEquals(listOf("B", "C"), Smart.evaluate(notIn, songs, now, 1) { setOf("A") })
    }

    @Test
    fun refreshKeepsOrder() {
        assertEquals(listOf("B", "A", "C"), Smart.merge(listOf("B", "X", "A"), listOf("A", "B", "C")))
    }

    @Test
    fun unknownRulesAreKept() {
        assertFalse(Smart.Playlist(rules = Smart.Group(false, listOf(Smart.Rule(0x99, 1, raw = ByteArray(56))))).understood)
    }
}
