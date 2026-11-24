package io.github.metricambient.luxtunes.data

import java.text.Normalizer
import java.util.Locale

/*
 * Lists of names in one field (as LuxTunes 0.9.2 on the computer, core/names.py): a song's
 * Artists (each artist of a collaboration, "Lümen; Grayfield") and its Featured Artists
 * ("Ann; Bo"). Names are separated by semicolons only: a comma or "&" can be part of a
 * name ("Tyler, the Creator", "Earth, Wind & Fire"); a semicolon never is. The Artist field
 * stays the text shown ("Lümen & Grayfield"); browsing goes by the Artists list.
 *
 * Also search's fold (ui/formatting.py): text without case or accents.
 */

/** "Lümen ;Grayfield;; lümen" -> [Lümen, Grayfield]: trimmed, spaces collapsed, each name once (ignoring case). */
fun splitNames(text: String?): List<String> {
    val seen = HashSet<String>()
    val names = ArrayList<String>()
    for (part in text.orEmpty().split(';')) {
        val name = part.trim().split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
        if (name.isNotEmpty() && seen.add(name.lowercase(Locale.ROOT))) names += name
    }
    return names
}

/** [Lümen, Grayfield] -> "Lümen; Grayfield" ("" for none): how they're stored and sent. */
fun joinNames(names: Iterable<String>): String = splitNames(names.joinToString(";")).joinToString("; ")

/** A song's genres, as Edit saves them: "Metalcore, Post-hardcore" ("" for none; repeats dropped). */
fun joinGenres(genres: Iterable<String>): String =
    genres.flatMap { splitGenres(it) }.distinctBy { it.lowercase(Locale.ROOT) }.joinToString(", ")

// How collaborations are usually written in an Artist field.
private val COLLABORATION = Regex("\\s+(?:&|and|x|×|\\+|with|vs\\.?|/)\\s+|\\s*,\\s+", RegexOption.IGNORE_CASE)
private val WHITESPACE = Regex("\\s+")

/**
 * The Artists an Artist text probably means, or [] if it's one name: "Lümen & Grayfield" ->
 * [Lümen, Grayfield], but only when each part is already an artist in `known` (lower-case
 * names). So "Simon & Garfunkel" stays whole: neither is an artist on their own.
 */
fun suggestSplit(artist: String?, known: Set<String>): List<String> {
    val text = artist.orEmpty().trim()
    if (text.isEmpty() || (text.lowercase(Locale.ROOT) in known && !COLLABORATION.containsMatchIn(text))) return emptyList()
    val parts = text.split(COLLABORATION).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2 || !parts.all { it.lowercase(Locale.ROOT) in known }) return emptyList()
    return splitNames(parts.joinToString(";"))
}

/** Everyone a song is by (lower case): its Artist, Album Artist and each of its Artists. */
fun whoOf(t: Track): Set<String> {
    val found = HashSet<String>()
    for (k in listOf("artist", "album_artist")) t.raw[k]?.toString()?.trim()?.lowercase(Locale.ROOT)?.let { found += it }
    for (n in t.artists) found += n.lowercase(Locale.ROOT)
    found.remove("")
    return found
}

/**
 * The Artists tab: every artist a song is listed under. A song with an Artists list is
 * under each of them, not under "Lümen & Grayfield"; its album's own artist keeps it too, unless
 * that's just those names put together (Lümen, for a Lümen album with one song with
 * Grayfield). Compilations grouped under the compilation stay there; without an Artists
 * list a song is listed as before (`base`: its artist, or the compilation's).
 */
fun browseArtists(t: Track, groupCompilations: Boolean): List<String> {
    val base = (if (groupCompilations && t.compilation) t.browseArtist else t.artist).trim()
    val listed = t.artists
    if (listed.isEmpty() || (t.compilation && groupCompilations)) return listOfNotNull(base.ifEmpty { null })
    val artist = t.raw["artist"]?.toString()?.trim().orEmpty()
    val albumArtist = t.raw["album_artist"]?.toString()?.trim()?.ifEmpty { null } ?: artist
    val known = listed.map { it.lowercase(Locale.ROOT) }.toSet()
    val together = albumArtist.equals(artist, ignoreCase = true) || suggestSplit(albumArtist, known).isNotEmpty()
    return splitNames(((if (together || albumArtist.isEmpty()) emptyList() else listOf(albumArtist)) + listed).joinToString(";"))
}

/**
 * The Artists tab's entries: (name, songs). Songs are listed as [browseArtists] says. With
 * Group Compilations on, a compilation's songs stay under the compilation (its Album Artist,
 * or Various Artists) and also show under each of their artists (Artists, else Artist) who
 * has a song outside compilations; an artist only ever on compilations gets no entry. Search
 * and Radio Mix still find every song by every artist. (LuxTunes 0.9.2, grid_groups.)
 */
fun artistEntries(tracks: List<Track>, groupCompilations: Boolean): List<Pair<String, List<Track>>> {
    if (!groupCompilations) return groupByNames(tracks) { browseArtists(it, false) }
    // First: who has a song outside compilations.
    val ownSongs = HashSet<String>()
    for (t in tracks) if (!t.compilation) for (n in browseArtists(t, true)) ownSongs += n.lowercase(Locale.ROOT)
    // Then each compilation song also goes under those of its artists.
    return groupByNames(tracks) { t ->
        if (!t.compilation) browseArtists(t, true)
        else browseArtists(t, true) + t.artists.ifEmpty { listOf(t.artist) }.map { it.trim() }.filter { it.lowercase(Locale.ROOT) in ownSongs }
    }
}

// ---- Search: no case, no accents ------------------------------------------------------------

private val PLAIN_LETTERS = mapOf(
    'ø' to "o", 'æ' to "ae", 'œ' to "oe", 'ł' to "l", 'đ' to "d", 'ð' to "d",
    'þ' to "th", 'ı' to "i", 'ŋ' to "n", 'ħ' to "h",
)
private val ACCENTS = Regex("\\p{M}+")

/** Text as search compares it: no case and no accents ("Lümen" -> "lumen", "Straße" -> "strasse"). */
fun fold(text: String): String {
    val lower = text.lowercase(Locale.ROOT).replace("ß", "ss")
    val plain = Normalizer.normalize(lower, Normalizer.Form.NFKD).replace(ACCENTS, "")
    return buildString(plain.length) { for (c in plain) append(PLAIN_LETTERS[c] ?: c.toString()) }
}

/** What's typed in a search box, as the words to look for. */
fun searchWords(typed: String): List<String> = fold(typed).split(WHITESPACE).filter { it.isNotEmpty() }

/** True if every word appears somewhere in `folded` (text that went through [fold]). */
fun matchesWords(folded: String, words: List<String>): Boolean = words.all { folded.contains(it) }

/**
 * Names for a box being typed in (Get Info's suggestions): those that start with it, or
 * have a later word that does, ignoring case and accents ("tul" -> Lümen).
 */
fun suggestNames(typed: String, names: List<String>, leaveOut: Set<String> = emptySet(), limit: Int = 8): List<String> {
    val want = fold(typed.trim())
    if (want.isEmpty()) return emptyList()
    val starts = ArrayList<String>()
    val words = ArrayList<String>()
    for (n in names) {
        if (n.lowercase(Locale.ROOT) in leaveOut) continue
        val f = fold(n)
        when {
            f.startsWith(want) -> starts += n
            f.split(WORD_BREAK).drop(1).any { it.startsWith(want) } -> words += n
        }
    }
    return (starts + words).take(limit)
}

private val WORD_BREAK = Regex("[\\s\\-/]+")

// ---- Pills (Get Info's Genre, Featured Artist and Artists) ------------------------------------

/**
 * Pills with more added: each once (ignoring case), in order. Genres split at "," and ";",
 * names at ";" only (a comma can be part of a name).
 */
fun addPills(pills: List<String>, more: List<String>, genre: Boolean): List<String> =
    if (genre) (pills + more).flatMap { splitGenres(it) }.distinctBy { it.lowercase(Locale.ROOT) }
    else splitNames((pills + more).joinToString(";"))

/**
 * The text in a pill box changed from `old` to `new`: what becomes pills now, and what stays
 * in the box. Typing a separator (";", and "," for genres) makes a pill of what's before it;
 * pasting "A; B; C" makes three. Without a separator nothing changes.
 */
fun pillInput(old: String, new: String, genre: Boolean): Pair<List<String>, String> {
    fun isSeparator(c: Char) = c == ';' || (genre && c == ',')
    if (new.none(::isSeparator)) return emptyList<String>() to new
    val pasted = new.length - old.length > 1 && !isSeparator(new.last())
    val cut = if (pasted) new.length else new.indexOfLast(::isSeparator)
    val done = new.substring(0, cut).splitWhere { isSeparator(it) }.map { it.trim() }.filter { it.isNotEmpty() }
    val rest = if (pasted) "" else new.substring(cut + 1).trimStart()
    return done to rest
}

private fun String.splitWhere(at: (Char) -> Boolean): List<String> {
    val out = ArrayList<String>()
    val part = StringBuilder()
    for (c in this) if (at(c)) { out += part.toString(); part.clear() } else part.append(c)
    out += part.toString()
    return out
}
