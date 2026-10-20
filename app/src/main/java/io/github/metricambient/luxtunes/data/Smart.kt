package io.github.metricambient.luxtunes.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Smart playlists, ported from LuxTunes (src/luxtunes/core/smart.py) so the phone
 * reads, evaluates and writes exactly the same two binary blocks LuxTunes keeps:
 *
 *  - Smart Info (112 bytes): live updating, match rules, limit on, limit type,
 *    limit sort ("selected by"), limit value, match only checked items, "least …".
 *  - Smart Criteria: "SLst", version, number of rules, all (0) / any (1), then the
 *    rules. Each rule: field id, operator, 44 spare bytes, data length, data. Text
 *    is UTF-16 big-endian; numbers and dates are 68 bytes (from-value, from-date,
 *    from-units, to-value, to-date, to-units, 20 spare bytes). A nested group is a
 *    rule with field 0 whose data is another "SLst".
 *
 * Rules the app doesn't understand are kept byte for byte. No Android code here,
 * so it's unit-tested.
 */
object Smart {
    // ---- Fields -----------------------------------------------------------------------

    enum class Kind { TEXT, NUMBER, DATE, RATING, TIME, SIZE, BOOL, MEDIA, PLAYLIST }

    /** column: where a song keeps it in library.db (null: LuxTunes has no such data). */
    data class Field(val id: Int, val label: String, val kind: Kind, val column: String?)

    val FIELDS: Map<Int, Field> = listOf(
        Field(0x03, "Album", Kind.TEXT, "album"),
        Field(0x47, "Album Artist", Kind.TEXT, "album_artist"),
        Field(0x5a, "Album Rating", Kind.RATING, "album_rating"),
        Field(0x04, "Artist", Kind.TEXT, "artist"),
        Field(0x05, "Bit Rate", Kind.NUMBER, "bit_rate"),
        Field(0x23, "BPM", Kind.NUMBER, "bpm"),
        Field(0x0e, "Comments", Kind.TEXT, "comments"),
        Field(0x1f, "Compilation", Kind.BOOL, "compilation"),
        Field(0x12, "Composer", Kind.TEXT, "composer"),
        Field(0x10, "Date Added", Kind.DATE, "date_added"),
        Field(0x0a, "Date Modified", Kind.DATE, "date_modified"),
        Field(0x18, "Disc Number", Kind.NUMBER, "disc_number"),
        Field(0x08, "Genre", Kind.TEXT, "genre"),
        Field(0x27, "Grouping", Kind.TEXT, "grouping"),
        Field(0x09, "Kind", Kind.TEXT, "kind"),
        Field(0x17, "Last Played", Kind.DATE, "play_date"),
        Field(0x45, "Last Skipped", Kind.DATE, "skip_date"),
        Field(0x3c, "Media Kind", Kind.MEDIA, "media_kind"),
        Field(0x02, "Name", Kind.TEXT, "name"),
        Field(0x28, "Playlist", Kind.PLAYLIST, null),
        Field(0x16, "Plays", Kind.NUMBER, "play_count"),
        Field(0x39, "Podcast", Kind.BOOL, null),
        Field(0x29, "Purchased", Kind.BOOL, "purchased"),
        Field(0x19, "Rating", Kind.RATING, "rating"),
        Field(0x06, "Sample Rate", Kind.NUMBER, "sample_rate"),
        Field(0x3f, "Season", Kind.NUMBER, "season"),
        Field(0x3e, "Show", Kind.TEXT, "show"),
        Field(0x0c, "Size", Kind.SIZE, "size"),
        Field(0x44, "Skips", Kind.NUMBER, "skip_count"),
        Field(0x4f, "Sort Album", Kind.TEXT, "sort_album"),
        Field(0x51, "Sort Album Artist", Kind.TEXT, "sort_album_artist"),
        Field(0x50, "Sort Artist", Kind.TEXT, "sort_artist"),
        Field(0x52, "Sort Composer", Kind.TEXT, "sort_composer"),
        Field(0x4e, "Sort Name", Kind.TEXT, "sort_name"),
        Field(0x0d, "Time", Kind.TIME, "total_time"),
        Field(0x0b, "Track Number", Kind.NUMBER, "track_number"),
        Field(0x07, "Year", Kind.NUMBER, "year"),
    ).associateBy { it.id }

    const val GROUP_FIELD = 0

    // ---- Operators ----------------------------------------------------------------------

    const val NEGATE = 0x02000000
    const val IS = 0x00000001
    const val IS_NOT = 0x02000001
    const val GREATER = 0x00000010
    const val LESS = 0x00000040
    const val RANGE = 0x00000100
    const val IN_LAST = 0x00000200
    const val NOT_IN_LAST = 0x02000200
    const val BIT_IS = 0x00000400
    const val BIT_IS_NOT = 0x02000400
    const val S_IS = 0x01000001
    const val S_CONTAINS = 0x01000002
    const val S_STARTS = 0x01000004
    const val S_ENDS = 0x01000008
    const val S_IS_NOT = 0x03000001
    const val S_NOT_CONTAINS = 0x03000002

    private val NUMBER_OPS = listOf(IS to "is", IS_NOT to "is not", GREATER to "is greater than",
        LESS to "is less than", RANGE to "is in the range")

    /** The operators the editor offers for each kind of field, with their words. */
    val OPERATORS: Map<Kind, List<Pair<Int, String>>> = mapOf(
        Kind.TEXT to listOf(S_CONTAINS to "contains", S_NOT_CONTAINS to "does not contain", S_IS to "is",
            S_IS_NOT to "is not", S_STARTS to "starts with", S_ENDS to "ends with"),
        Kind.NUMBER to NUMBER_OPS, Kind.RATING to NUMBER_OPS, Kind.TIME to NUMBER_OPS, Kind.SIZE to NUMBER_OPS,
        Kind.DATE to listOf(IS to "is", IS_NOT to "is not", GREATER to "is after", LESS to "is before",
            IN_LAST to "is in the last", NOT_IN_LAST to "is not in the last", RANGE to "is in the range"),
        Kind.BOOL to listOf(IS to "is true", IS_NOT to "is false"),
        Kind.MEDIA to listOf(BIT_IS to "is", BIT_IS_NOT to "is not"),
        Kind.PLAYLIST to listOf(IS to "is", IS_NOT to "is not"),
    )

    val MEDIA_KINDS = listOf(0x01 to "Music", 0x02 to "Movie", 0x04 to "Podcast", 0x08 to "Audiobook",
        0x20 to "Music Video", 0x40 to "TV Show")
    private val LUX_MEDIA = mapOf("music" to 0x01, "movie" to 0x02, "podcast" to 0x04, "audiobook" to 0x08, "tvshow" to 0x40)

    const val DATE_MARKER = 0x2DAE2DAE2DAE2DAEL
    const val DAY = 86400L
    const val WEEK = 604800L
    const val MONTH = 2628000L
    val DATE_UNITS = listOf(DAY to "days", WEEK to "weeks", MONTH to "months")
    private const val MAC_EPOCH = 2082844800L      // 1904-01-01 (the format's dates) to 1970-01-01

    // ---- Limits ---------------------------------------------------------------------------

    const val LIMIT_MINUTES = 1
    const val LIMIT_MB = 2
    const val LIMIT_ITEMS = 3
    const val LIMIT_HOURS = 4
    const val LIMIT_GB = 5
    val LIMIT_TYPES = listOf(LIMIT_ITEMS to "items", LIMIT_MINUTES to "minutes", LIMIT_HOURS to "hours",
        LIMIT_MB to "MB", LIMIT_GB to "GB")

    const val SORT_RANDOM = 0x02
    const val SORT_NAME = 0x05
    const val SORT_ALBUM = 0x06
    const val SORT_ARTIST = 0x07
    const val SORT_GENRE = 0x09
    const val SORT_ADDED = 0x15
    const val SORT_PLAYS = 0x19
    const val SORT_PLAYED = 0x1A
    const val SORT_RATING = 0x1C

    /** "selected by": (code, reversed, words). The "least …" ones are "most …" reversed. */
    val LIMIT_SORTS = listOf(
        Triple(SORT_RANDOM, false, "random"), Triple(SORT_ALBUM, false, "album"),
        Triple(SORT_ARTIST, false, "artist"), Triple(SORT_GENRE, false, "genre"),
        Triple(SORT_NAME, false, "name"),
        Triple(SORT_RATING, false, "highest rating"), Triple(SORT_RATING, true, "lowest rating"),
        Triple(SORT_PLAYED, false, "most recently played"), Triple(SORT_PLAYED, true, "least recently played"),
        Triple(SORT_PLAYS, false, "most often played"), Triple(SORT_PLAYS, true, "least often played"),
        Triple(SORT_ADDED, false, "most recently added"), Triple(SORT_ADDED, true, "least recently added"),
    )

    // ---- The rules --------------------------------------------------------------------------

    sealed interface Item

    data class Rule(
        val field: Int,
        val op: Int,
        val text: String = "",            // text fields
        val value: Long = 0,              // number, unix seconds, rating 0-100, ms, bytes, media mask
        val value2: Long? = null,         // the end of "is in the range"
        val playlist: String = "",        // "Playlist is …": a persistent ID
        val amount: Long = 0,             // "in the last <amount> <units>"
        val units: Long = WEEK,
        val raw: ByteArray? = null,       // a rule the app doesn't understand: kept exactly
    ) : Item {
        // "this.field": inside a getter, a bare `field` means the getter's own backing field.
        val known: Boolean get() = raw == null && this.field in FIELDS
        val kind: Kind? get() = FIELDS[this.field]?.kind
    }

    data class Group(val matchAny: Boolean = false, val rules: List<Item> = emptyList()) : Item

    data class Info(
        val live: Boolean = true,
        val useRules: Boolean = true,
        val limit: Boolean = false,
        val limitType: Int = LIMIT_ITEMS,
        val limitSort: Int = SORT_RANDOM,
        val limitValue: Long = 25,
        val checkedOnly: Boolean = false,
        val reverse: Boolean = false,
    )

    data class Playlist(val info: Info = Info(), val rules: Group = Group()) {
        /** Every rule is one the app knows (else the playlist keeps LuxTunes' songs). */
        val understood: Boolean get() = ok(rules)
        private fun ok(g: Group): Boolean = g.rules.all { if (it is Group) ok(it) else (it as Rule).known }
    }

    /** What "Create Smart Playlist" starts with: Artist contains "". */
    fun defaultPlaylist() = Playlist(Info(), Group(false, listOf(Rule(0x04, S_CONTAINS))))

    class FormatError(message: String) : Exception(message)

    // ---- Reading and writing ----------------------------------------------------------------

    private const val INFO_SIZE = 112
    private const val HEADER_SIZE = 136
    private const val RULE_PAD = 44
    private const val NUMBER_SIZE = 68

    fun decodeInfo(data: ByteArray?): Info {
        if (data == null || data.size < 14) return Info()
        fun b(i: Int) = data[i].toInt() and 0xFF
        return Info(
            live = b(0) != 0, useRules = b(1) != 0, limit = b(2) != 0,
            limitType = b(3).takeIf { it != 0 } ?: LIMIT_ITEMS,
            limitSort = b(7).takeIf { it != 0 } ?: SORT_RANDOM,
            limitValue = ByteBuffer.wrap(data, 8, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL,
            checkedOnly = b(12) != 0, reverse = b(13) != 0,
        )
    }

    fun encodeInfo(info: Info): ByteArray {
        val data = ByteArray(INFO_SIZE)
        data[0] = if (info.live) 1 else 0
        data[1] = if (info.useRules) 1 else 0
        data[2] = if (info.limit) 1 else 0
        data[3] = info.limitType.toByte()
        data[7] = info.limitSort.toByte()
        ByteBuffer.wrap(data, 8, 4).order(ByteOrder.BIG_ENDIAN).putInt(info.limitValue.coerceIn(0, 0xFFFFFFFFL).toInt())
        data[12] = if (info.checkedOnly) 1 else 0
        data[13] = if (info.reverse) 1 else 0
        return data
    }

    fun decodeCriteria(data: ByteArray?): Group {
        if (data == null || data.isEmpty()) return Group()
        return decodeGroup(data, 0).first
    }

    private fun u32(data: ByteArray, at: Int): Long =
        ByteBuffer.wrap(data, at, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL

    private fun decodeGroup(data: ByteArray, start: Int): Pair<Group, Int> {
        if (data.size < start + HEADER_SIZE || String(data, start, 4, Charsets.US_ASCII) != "SLst") {
            throw FormatError("not smart playlist rules")
        }
        val count = u32(data, start + 8)
        val conjunction = u32(data, start + 12)
        val rules = ArrayList<Item>()
        var at = start + HEADER_SIZE
        for (i in 0 until count) {
            if (at + 56 > data.size) throw FormatError("the rules end too soon")
            val fieldId = u32(data, at).toInt()
            val op = u32(data, at + 4).toInt()
            val length = u32(data, at + 52).toInt()
            if (length < 0 || at + 56 + length > data.size) throw FormatError("the rules end too soon")
            val body = data.copyOfRange(at + 56, at + 56 + length)
            val whole = data.copyOfRange(at, at + 56 + length)
            at += 56 + length
            rules += decodeRule(fieldId, op, body, whole)
        }
        return Group(conjunction == 1L, rules) to at
    }

    private fun decodeRule(fieldId: Int, op: Int, body: ByteArray, whole: ByteArray): Item {
        if (fieldId == GROUP_FIELD) {
            return try {
                decodeGroup(body, 0).first
            } catch (e: Exception) {
                Rule(fieldId, op, raw = whole)
            }
        }
        val f = FIELDS[fieldId] ?: return Rule(fieldId, op, raw = whole)
        if (f.kind == Kind.TEXT) return Rule(fieldId, op, text = String(body, Charsets.UTF_16BE))
        if (body.size != NUMBER_SIZE) return Rule(fieldId, op, raw = whole)
        val bb = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
        val a = bb.long
        val aDate = bb.long
        val aUnits = bb.long
        val b = bb.long
        return when (f.kind) {
            Kind.DATE -> if (a == DATE_MARKER) {
                Rule(fieldId, op, amount = -aDate, units = if (aUnits == 0L) DAY else aUnits)
            } else {
                Rule(fieldId, op, value = a - MAC_EPOCH, value2 = b - MAC_EPOCH)
            }
            Kind.PLAYLIST -> Rule(fieldId, op, playlist = "%016X".format(a))
            else -> Rule(fieldId, op, value = a, value2 = if ((op and NEGATE.inv()) == RANGE) b else null)
        }
    }

    fun encodeCriteria(group: Group): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        header.put("SLst".toByteArray(Charsets.US_ASCII))
        header.putShort(1.toShort()).putShort(1.toShort()).putInt(group.rules.size).putInt(if (group.matchAny) 1 else 0)
        out.write(header.array())
        for (rule in group.rules) out.write(encodeRule(rule))
        return out.toByteArray()
    }

    private fun numbers(a: Long, aDate: Long, aUnits: Long, b: Long, bDate: Long, bUnits: Long): ByteArray =
        ByteBuffer.allocate(NUMBER_SIZE).order(ByteOrder.BIG_ENDIAN)
            .putLong(a).putLong(aDate).putLong(aUnits).putLong(b).putLong(bDate).putLong(bUnits).array()

    private fun encodeRule(item: Item): ByteArray {
        val fieldId: Int
        val op: Int
        val body: ByteArray
        val pad = ByteArray(RULE_PAD)
        when (item) {
            is Group -> {
                fieldId = GROUP_FIELD
                op = IS
                body = encodeCriteria(item)
                pad[0] = 1                                   // how a nested group is written
            }
            is Rule -> {
                if (item.raw != null) return item.raw
                fieldId = item.field
                op = item.op
                body = when (item.kind) {
                    Kind.TEXT -> item.text.toByteArray(Charsets.UTF_16BE)
                    Kind.DATE -> if (item.op == IN_LAST || item.op == NOT_IN_LAST) {
                        numbers(DATE_MARKER, -item.amount, item.units, DATE_MARKER, 0, 1)
                    } else {
                        numbers(item.value + MAC_EPOCH, 0, 1, (item.value2 ?: item.value) + MAC_EPOCH, 0, 1)
                    }
                    Kind.PLAYLIST -> {
                        val n = java.lang.Long.parseUnsignedLong(item.playlist.ifEmpty { "0" }, 16)
                        numbers(n, 0, 1, n, 0, 1)
                    }
                    else -> numbers(item.value, 0, 1, item.value2 ?: item.value, 0, 1)
                }
            }
        }
        return ByteBuffer.allocate(8 + RULE_PAD + 4 + body.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(fieldId).putInt(op).put(pad).putInt(body.size).put(body).array()
    }

    fun decode(info: ByteArray?, criteria: ByteArray?) = Playlist(decodeInfo(info), decodeCriteria(criteria))
    fun encode(p: Playlist): Pair<ByteArray, ByteArray> = encodeInfo(p.info) to encodeCriteria(p.rules)

    // ---- Which songs match ----------------------------------------------------------------

    /** A song as the rules see it: its library.db columns (with the phone's own plays and ratings). */
    interface Song {
        val id: String
        fun value(column: String): Any?
    }

    /** "YYYY-MM-DDTHH:MM:SS…" as Unix seconds. */
    fun unix(value: Any?): Long? {
        val text = (value as? String)?.takeIf { it.length >= 19 } ?: return null
        return try {
            LocalDateTime.parse(text.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME).toEpochSecond(ZoneOffset.UTC)
        } catch (e: Exception) {
            null
        }
    }

    private fun number(v: Any?): Long = when (v) {
        is Long -> v
        is Int -> v.toLong()
        is Double -> v.toLong()
        is Boolean -> if (v) 1 else 0
        is String -> v.toLongOrNull() ?: 0
        else -> 0
    }

    /** Looks up "Playlist is …" members by persistent ID (empty if unknown or a loop). */
    fun interface Members {
        fun of(playlistId: String): Set<String>
    }

    fun ruleMatches(item: Item, song: Song, now: Long, members: Members): Boolean {
        if (item is Group) return groupMatches(item, song, now, members)
        val rule = item as Rule
        if (!rule.known) return true
        val f = FIELDS.getValue(rule.field)
        val negate = (rule.op and NEGATE) != 0
        val base = rule.op and NEGATE.inv()
        val raw = f.column?.let { song.value(it) }
        val hit: Boolean = when (f.kind) {
            Kind.PLAYLIST -> song.id in members.of(rule.playlist.uppercase())
            Kind.TEXT -> {
                val have = (raw?.toString() ?: "").lowercase()
                val want = rule.text.lowercase()
                when (rule.op and NEGATE.inv()) {
                    S_CONTAINS -> have.contains(want)
                    S_STARTS -> have.startsWith(want)
                    S_ENDS -> have.endsWith(want)
                    else -> have == want
                }
            }
            Kind.MEDIA -> {
                val mask = LUX_MEDIA[(raw as? String)?.ifEmpty { null } ?: "music"] ?: 0
                (mask.toLong() and rule.value) != 0L
            }
            Kind.BOOL -> number(raw) != 0L
            Kind.DATE -> {
                val whenSeconds = unix(raw)
                when {
                    base == IN_LAST -> whenSeconds != null && whenSeconds >= now - rule.amount * rule.units
                    whenSeconds == null -> false
                    base == GREATER -> whenSeconds > rule.value
                    base == LESS -> whenSeconds < rule.value
                    else -> {                                     // is / is in the range: inclusive
                        val end = rule.value2 ?: rule.value
                        whenSeconds in minOf(rule.value, end)..maxOf(rule.value, end)
                    }
                }
            }
            else -> {
                val v = number(raw)
                when (base) {
                    GREATER -> v > rule.value
                    LESS -> v < rule.value
                    RANGE -> {
                        val end = rule.value2 ?: rule.value
                        v in minOf(rule.value, end)..maxOf(rule.value, end)
                    }
                    else -> v == rule.value
                }
            }
        }
        return hit != negate
    }

    fun groupMatches(group: Group, song: Song, now: Long, members: Members): Boolean {
        if (group.rules.isEmpty()) return true
        return if (group.matchAny) group.rules.any { ruleMatches(it, song, now, members) }
        else group.rules.all { ruleMatches(it, song, now, members) }
    }

    private fun limitComparator(sort: Int): Comparator<Song>? = when (sort) {
        SORT_NAME -> compareBy<Song> { text(it, "sort_name", "name") }
        SORT_ALBUM -> compareBy<Song>({ text(it, "sort_album", "album") }, { number(it.value("disc_number")) },
            { number(it.value("track_number")) })
        SORT_ARTIST -> compareBy<Song> { text(it, "sort_artist", "artist") }
        SORT_GENRE -> compareBy<Song> { text(it, "genre", "genre") }
        SORT_ADDED -> compareBy<Song> { -(unix(it.value("date_added")) ?: 0L) }       // "most …" first
        SORT_PLAYS -> compareBy<Song>({ -number(it.value("play_count")) }, { -(unix(it.value("play_date")) ?: 0L) })
        SORT_PLAYED -> compareBy<Song> { -(unix(it.value("play_date")) ?: 0L) }
        SORT_RATING -> compareBy<Song> { if (number(it.value("rating_computed")) != 0L) 0L else -number(it.value("rating")) }
        else -> null
    }

    private fun text(s: Song, first: String, second: String): String =
        ((s.value(first) as? String)?.ifEmpty { null } ?: (s.value(second) as? String) ?: "").lowercase()

    private fun applyLimit(songs: List<Song>, info: Info, seed: Long): List<Song> {
        if (!info.limit) return songs                         // "selected by" only matters with a limit
        var ordered = songs
        if (info.limitSort == SORT_RANDOM) {
            ordered = songs.shuffled(Random(seed))             // the same pick each time
        } else {
            val c = limitComparator(info.limitSort)
            if (c != null) ordered = songs.sortedWith(if (info.reverse) c.reversed() else c)
        }
        val limit = info.limitValue.coerceAtLeast(0)
        if (info.limitType == LIMIT_ITEMS) return ordered.take(limit.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val sizeOf: (Song) -> Double = when (info.limitType) {
            LIMIT_MINUTES -> { s -> number(s.value("total_time")) / 60000.0 }
            LIMIT_HOURS -> { s -> number(s.value("total_time")) / 3600000.0 }
            LIMIT_MB -> { s -> number(s.value("size")) / 1048576.0 }
            else -> { s -> number(s.value("size")) / 1073741824.0 }
        }
        val chosen = ArrayList<Song>()
        var used = 0.0
        for (s in ordered) {
            val amount = sizeOf(s)
            if (used + amount > limit) continue                // skip what doesn't fit
            chosen += s
            used += amount
        }
        return chosen
    }

    /** The IDs of the songs this smart playlist holds, in its order. */
    fun evaluate(p: Playlist, songs: List<Song>, now: Long, seed: Long, members: Members): List<String> {
        val chosen = songs.filter { s ->
            (!p.info.checkedOnly || s.value("enabled").let { it == null || number(it) != 0L }) &&
                (!p.info.useRules || groupMatches(p.rules, s, now, members))
        }
        return applyLimit(chosen, p.info, seed).map { it.id }
    }

    /**
     * Keep the songs it already had in their places and add new ones at the end,
     * like LuxTunes' refresh.
     */
    fun merge(current: List<String>, wanted: List<String>): List<String> {
        val keep = wanted.toSet()
        val order = current.filter { it in keep }.distinct()
        val seen = order.toSet()
        return order + wanted.filter { it !in seen }
    }

    // ---- Words ----------------------------------------------------------------------------

    fun opLabel(kind: Kind, op: Int): String = OPERATORS[kind]?.firstOrNull { it.first == op }?.second ?: "?"

    fun dateText(unixSeconds: Long): String =
        DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDateTime.ofEpochSecond(unixSeconds, 0, ZoneOffset.UTC))

    /** One rule in words: "Year is in the range 1990 to 1999". */
    fun describe(item: Item, playlistName: (String) -> String = { it }): String {
        if (item is Group) {
            return "(" + item.rules.joinToString(if (item.matchAny) " or " else " and ") { describe(it, playlistName) } + ")"
        }
        val r = item as Rule
        if (!r.known) return "(a rule edited only on the computer)"
        val f = FIELDS.getValue(r.field)
        val op = opLabel(f.kind, r.op)
        val value = when (f.kind) {
            Kind.TEXT -> "“${r.text}”"
            Kind.BOOL -> return "${f.label} $op"
            Kind.MEDIA -> MEDIA_KINDS.firstOrNull { it.first.toLong() == r.value }?.second ?: "${r.value}"
            Kind.PLAYLIST -> playlistName(r.playlist)
            Kind.DATE -> if (r.op == IN_LAST || r.op == NOT_IN_LAST) {
                return "${f.label} $op ${r.amount} ${DATE_UNITS.firstOrNull { it.first == r.units }?.second ?: "units"}"
            } else dateText(r.value)
            Kind.RATING -> "${r.value / 20} stars"
            Kind.TIME -> "${r.value / 60000}:%02d".format((r.value / 1000) % 60)
            Kind.SIZE -> "${r.value / 1048576} MB"
            Kind.NUMBER -> "${r.value}"
        }
        var text = "${f.label} $op $value"
        if ((r.op and NEGATE.inv()) == RANGE) {
            val end = r.value2 ?: r.value
            text += " to " + when (f.kind) {
                Kind.DATE -> dateText(end)
                Kind.RATING -> "${end / 20} stars"
                Kind.TIME -> "${end / 60000}:%02d".format((end / 1000) % 60)
                Kind.SIZE -> "${end / 1048576} MB"
                else -> "$end"
            }
        }
        return text
    }
}
