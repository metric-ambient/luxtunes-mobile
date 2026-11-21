package io.github.metricambient.luxtunes.data

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.pow

/** The name songs with no genre are listed under. */
const val UNKNOWN_GENRE = "Unknown Genre"

/**
 * A song's genres: "Metalcore, Post-hardcore" -> [Metalcore, Post-hardcore]. Several genres
 * share the one Genre field, separated by commas or semicolons (never "/": "R&B/Soul" is one).
 */
fun splitGenres(field: String?): List<String> =
    field.orEmpty().split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

/** The genres a song is listed under when browsing: its own, or Unknown Genre. */
fun genresOf(field: String?): List<String> = splitGenres(field).ifEmpty { listOf(UNKNOWN_GENRE) }

/**
 * Songs grouped by name, a song under each of its names (compared ignoring case; the
 * spelling of the first song seen is shown), sorted by name: (name, its songs).
 */
fun groupByNames(tracks: List<Track>, names: (Track) -> List<String>): List<Pair<String, List<Track>>> {
    val groups = LinkedHashMap<String, Pair<String, MutableList<Track>>>()
    for (t in tracks) {
        for (name in names(t).distinctBy { it.lowercase() }) {
            groups.getOrPut(name.lowercase()) { name to ArrayList() }.second += t
        }
    }
    return groups.values.map { it.first to it.second.toList() }.sortedBy { sortText(it.first) }
}

/**
 * LuxTunes' genre catalogue (about 3,300 genres and how they connect, from MusicBrainz and
 * Wikidata, CC0), shipped in the app's assets as genres.json. Nothing here goes online.
 * The same as the computer's core/genres.py.
 *
 *  - [genreIds]: the catalogue genres a Genre field names.
 *  - [related]: how close every genre is to some starting genres (0..1).
 *  - [ids] / [indexes]: indexes change when the catalogue is rebuilt; ids don't (save those).
 */
class GenreCatalogue(json: String) {
    private val genreIdList: List<String>
    val names: List<String>
    private val indexOfId: Map<String, Int>
    private val byKey = HashMap<String, List<Int>>()
    private val storeNames: Set<String>
    private val neighbours: Array<IntArray>
    private val weights: Array<DoubleArray>
    private val walks = object : LinkedHashMap<Int, Map<Int, Double>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Map<Int, Double>>?): Boolean = size > 512
    }

    init {
        val data = JSONObject(json)
        val genres = data.getJSONArray("genres")
        val ids = ArrayList<String>(genres.length())
        val names = ArrayList<String>(genres.length())
        for (i in 0 until genres.length()) {
            val g = genres.getJSONObject(i)
            ids += g.getString("id")
            names += g.getString("name")
            val aliases = g.optJSONArray("aliases")
            val all = listOf(g.getString("name")) + (0 until (aliases?.length() ?: 0)).map { aliases!!.getString(it) }
            for (name in all) {
                byKey.putIfAbsent(key(name), listOf(i))
                byKey.putIfAbsent(key(name.replace(MUSIC_SUFFIX, "")), listOf(i))
            }
        }
        genreIdList = ids
        this.names = names
        indexOfId = ids.withIndex().associate { it.value to it.index }
        val store = data.optJSONObject("store_names")
        val storeSet = HashSet<String>()
        if (store != null) {
            for (name in store.keys()) {
                val list = store.getJSONArray(name)
                storeSet += name
                byKey[key(name)] = (0 until list.length()).map { list.getInt(it) }     // store names win
            }
        }
        storeNames = storeSet
        // Links: the strongest kind's weight, plus 0.05 for each extra kind.
        val linked = Array(ids.size) { LinkedHashMap<Int, Double>() }
        val links = data.getJSONArray("links")
        for (i in 0 until links.length()) {
            val l = links.getJSONArray(i)
            val a = l.getInt(0)
            val b = l.getInt(1)
            val kinds = l.getString(2)
            val weight = kinds.maxOf { LINK_WEIGHTS[it] ?: 0.0 } + 0.05 * (kinds.length - 1)
            linked[a][b] = weight
            linked[b][a] = weight
        }
        neighbours = Array(ids.size) { linked[it].keys.toIntArray() }
        weights = Array(ids.size) { linked[it].values.toDoubleArray() }
    }

    val size: Int get() = genreIdList.size

    /** Lasting ids ("mb:…", "wd:…") for genre indexes. */
    fun ids(indexes: List<Int>): List<String> = indexes.filter { it in genreIdList.indices }.map { genreIdList[it] }

    /** Genre indexes for saved ids (ids no longer in the catalogue are left out). */
    fun indexes(ids: List<String>): List<Int> = ids.mapNotNull { indexOfId[it] }

    /**
     * The catalogue genres a Genre field names, in order, without repeats. The whole field
     * is tried first ("Drum & Bass", "R&B/Soul"), then each part between ; / and ,
     * ("Pop, Rock"). Unknown names are skipped.
     */
    fun genreIds(field: String?): List<Int> {
        if (field.isNullOrBlank()) return emptyList()
        whole(field)?.let { return it }
        val found = ArrayList<Int>()
        for (part in field.trim().split(SEPARATORS)) {
            for (i in lookup(part)) if (i !in found) found += i
        }
        return found
    }

    /** The whole field as one name: always for a store name, otherwise only without separators. */
    private fun whole(field: String): List<Int>? {
        val f = field.trim()
        return if (f in storeNames || !SEPARATORS.containsMatchIn(f)) byKey[key(field)] else null
    }

    private fun lookup(part: String): List<Int> {
        if (part.isEmpty()) return emptyList()
        byKey[key(part)]?.let { return it }
        if (" & " in part || " and " in part.lowercase()) {                // "Alternative & Punk"
            val pieces = part.split(AND)
            if (pieces.all { key(it) in byKey }) return pieces.flatMap { byKey.getValue(key(it)) }
        }
        return emptyList()
    }

    /** How close every reachable genre is to these (0..1; the starting genres are 1). */
    fun related(ids: List<Int>): Map<Int, Double> {
        val starts = ids.distinct().filter { it in 0 until size }
        if (starts.isEmpty()) return emptyMap()
        val total = HashMap<Int, Double>()
        for (start in starts) {
            for ((genre, score) in walk(start)) total[genre] = maxOf(total[genre] ?: 0.0, score)
        }
        for (start in starts) total[start] = 1.0
        return total
    }

    /**
     * A random walk from `start`: each step every genre keeps 30% of its weight as visits
     * and sends the rest to its neighbours, by link weight. A genre's score is its visits
     * over the square root of its number of links (so hubs like Rock don't relate
     * everything), divided by the best score.
     */
    private fun walk(start: Int): Map<Int, Double> = synchronized(walks) { walks[start] } ?: run {
        val n = size
        var here = DoubleArray(n).also { it[start] = 1.0 }
        val visits = DoubleArray(n)
        repeat(STEPS) {
            val next = DoubleArray(n)
            for (g in 0 until n) {
                val mass = here[g]
                if (mass == 0.0) continue
                visits[g] += RETURN * mass
                val near = neighbours[g]
                if (near.isEmpty()) continue
                val w = weights[g]
                val sum = w.sum()
                for (k in near.indices) next[near[k]] += (1 - RETURN) * mass * w[k] / sum
            }
            here = next
        }
        val scores = HashMap<Int, Double>()
        for (g in 0 until n) {
            if (g == start || visits[g] == 0.0 || neighbours[g].isEmpty()) continue
            scores[g] = visits[g] / neighbours[g].size.toDouble().pow(HUB_DISCOUNT)
        }
        val top = scores.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val result = scores.mapValues { it.value / top }
        synchronized(walks) { walks[start] = result }
        result
    }

    companion object {
        private val LINK_WEIGHTS = mapOf('o' to 0.9, 'p' to 0.8, 'f' to 0.5, 't' to 0.5, 'i' to 0.4)
        private val SEPARATORS = Regex("\\s*[;/,]\\s*")
        private val AND = Regex("\\s+(?:&|and)\\s+", RegexOption.IGNORE_CASE)
        private val MUSIC_SUFFIX = Regex(" music$", RegexOption.IGNORE_CASE)
        private val MARKS = Regex("\\p{M}+")
        private val NOT_ALNUM = Regex("[^0-9a-z]+")
        private const val RETURN = 0.3
        private const val STEPS = 30
        private const val HUB_DISCOUNT = 0.5

        /** The form names are matched in: case, accents, spaces and punctuation don't count; "&" is "and". */
        fun key(text: String): String {
            val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD).replace(MARKS, "")
            return plain.replace("&", " and ").replace(NOT_ALNUM, "")
        }

        @Volatile private var loaded: GenreCatalogue? = null

        /** The catalogue from the app's assets (read once; null if it can't be read). */
        fun get(context: Context): GenreCatalogue? = loaded ?: synchronized(this) {
            loaded ?: runCatching {
                GenreCatalogue(context.assets.open("genres.json").bufferedReader().use { it.readText() })
            }.getOrNull().also { loaded = it }
        }
    }
}
