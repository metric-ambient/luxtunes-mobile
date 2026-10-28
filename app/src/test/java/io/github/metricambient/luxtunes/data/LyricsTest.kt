package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTest {
    private val lrc = """
        [ar:Some Artist]
        [00:12.40]First line of the song
        [00:16.85][01:02.10]A chorus line (sung twice)
        [00:21.00]
        [00:30:5]Colon fraction
        [61:00.123]Past an hour
    """.trimIndent()

    @Test
    fun parsesTimesTagsAndGaps() {
        val lines = Lyrics.parse(lrc)!!
        assertEquals(
            listOf(
                Lyrics.Line(12_400, "First line of the song"),
                Lyrics.Line(16_850, "A chorus line (sung twice)"),
                Lyrics.Line(21_000, ""),
                Lyrics.Line(30_500, "Colon fraction"),
                Lyrics.Line(62_100, "A chorus line (sung twice)"),
                Lyrics.Line(3_660_123, "Past an hour"),
            ),
            lines,
        )
    }

    @Test
    fun currentLine() {
        val lines = Lyrics.parse(lrc)!!
        assertEquals(-1, Lyrics.lineAt(lines, 5_000))
        assertEquals(0, Lyrics.lineAt(lines, 12_400))
        assertEquals(2, Lyrics.lineAt(lines, 25_000))          // the empty gap: nothing lit
        assertEquals(4, Lyrics.lineAt(lines, 70_000))
        assertEquals(5, Lyrics.lineAt(lines, 9_999_999))
    }

    @Test
    fun plainTextStaysPlain() {
        assertNull(Lyrics.parse("Just words\nNo times"))
        assertEquals("Just words", Lyrics.plain("Just words"))
        assertEquals("First line of the song\nA chorus line (sung twice)\n\nColon fraction\nPast an hour", Lyrics.plain(lrc))
    }
}
