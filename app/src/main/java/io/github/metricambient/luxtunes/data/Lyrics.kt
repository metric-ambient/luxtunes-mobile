package io.github.metricambient.luxtunes.data

/**
 * Timed lyrics in LRC form ("[01:02.10]A line"), as LuxTunes 0.8.5 sends them in
 * `synced_lyrics` (and as some files have them in their plain lyrics tag).
 */
object Lyrics {
    /** One sung line: when it starts (ms) and its words ("" for an instrumental gap). */
    data class Line(val ms: Long, val text: String)

    // [mm:ss], [mm:ss.x], [mm:ss.xx], [mm:ss.xxx], or with a colon before the fraction.
    private val TIME = Regex("""^\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** The lines sorted by time (a line with several times appears once per time), or null if nothing is timed. */
    fun parse(text: String?): List<Line>? {
        if (text.isNullOrBlank()) return null
        val out = ArrayList<Line>()
        for (raw in text.lines()) {
            var rest = raw.trim()
            val times = ArrayList<Long>()
            while (true) {
                val m = TIME.find(rest) ?: break
                val (min, sec, frac) = m.destructured
                val fraction = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').toLong()
                times += min.toLong() * 60_000 + sec.toLong() * 1000 + fraction
                rest = rest.substring(m.value.length)
            }
            // Tag lines ([ar:…], [ti:…]) and untimed lines have no times: skipped.
            val words = rest.trim()
            for (t in times) out += Line(t, words)
        }
        if (out.isEmpty()) return null
        out.sortBy { it.ms }          // stable: lines at the same time keep their order
        return out
    }

    /** The line being sung at `positionMs`: the last one starting at or before it (-1 before the first). */
    fun lineAt(lines: List<Line>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].ms <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }

    /** Just the words: timed lyrics without their times and tag lines; anything else as it is. */
    fun plain(text: String): String {
        if (parse(text) == null) return text
        val tag = Regex("""^\[[A-Za-z]+:.*]$""")
        return text.lines().mapNotNull { raw ->
            var rest = raw.trim()
            if (tag.matches(rest)) return@mapNotNull null
            while (true) {
                val m = TIME.find(rest) ?: break
                rest = rest.substring(m.value.length)
            }
            rest.trim()
        }.joinToString("\n").trim()
    }
}
