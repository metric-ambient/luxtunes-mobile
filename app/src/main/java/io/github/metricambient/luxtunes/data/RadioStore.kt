package io.github.metricambient.luxtunes.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The current radio and the likes and dislikes, kept in the app's own private storage
 * (files/radio/), never in the shared LuxTunes folder, and never sent anywhere: nothing
 * here goes into changes.json. Each phone has its own radio and its own likes.
 */
class RadioStore(private val context: Context, private val repository: Repository) {
    private val folder = File(context.filesDir, "radio")
    private val currentFile = File(folder, "current.json")
    private val likesFile = File(folder, "likes.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _radio = MutableStateFlow<Radio?>(null)
    /** The current radio (null before the first one). */
    val radio: StateFlow<Radio?> = _radio.asStateFlow()

    private val _opinions = MutableStateFlow<Map<String, Opinion>>(emptyMap())
    /** Every liked (1) or disliked (-1) song, by its ID. */
    val opinions: StateFlow<Map<String, Opinion>> = _opinions.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** While a radio is being worked out. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val catalogue: GenreCatalogue? get() = GenreCatalogue.get(context)

    init {
        scope.launch {
            lock.withLock {
                val cat = catalogue
                _opinions.value = readLikes(cat)
                _radio.value = readRadio(cat)
            }
            // Songs gone from the library: their likes and dislikes go too.
            repository.state.collect { library ->
                if (!library.loaded || library.allTracks.isEmpty()) return@collect
                lock.withLock {
                    val kept = _opinions.value.filterKeys { it in library.byId }
                    if (kept.size != _opinions.value.size) { _opinions.value = kept; writeLikes(kept) }
                }
            }
        }
    }

    private fun input(): RadioMix.Input {
        val library = repository.state.value
        return RadioMix.Input(library.allTracks, library.playlists, _opinions.value, catalogue)
    }

    /** The radio's songs, in order (songs deleted meanwhile are left out). */
    fun tracks(radio: Radio?, library: LibraryState): List<Track> =
        radio?.trackIds.orEmpty().mapNotNull { library.byId[it] }

    /** A new radio from this song, its album or its artist, replacing the current one. Its songs, in order. */
    suspend fun start(kind: String, seed: Track): List<Track> = work {
        val radio = RadioMix.start(input(), kind, seed, discover = _radio.value?.discover ?: false)
        save(radio)
    }

    /**
     * A new mix from the same start (Refresh, or Discover turned on or off). `keep`: the
     * song playing, which stays on (first) so it carries on.
     */
    suspend fun refresh(discover: Boolean? = null, keep: String? = null): List<Track> = work {
        val old = _radio.value ?: return@work emptyList()
        val input = input()
        val seed = input.byId[old.seedId] ?: return@work emptyList()
        var radio = RadioMix.start(input, old.kind, seed, discover ?: old.discover)
        if (keep != null && keep in input.byId) radio = radio.copy(trackIds = listOf(keep) + radio.trackIds.filter { it != keep })
        save(radio)
    }

    /** More songs at the end of the radio (none it already has). The ones added. */
    suspend fun extend(count: Int = RadioMix.TOP_UP): List<Track> = work {
        val old = _radio.value ?: return@work emptyList()
        val input = input()
        val added = RadioMix.extend(input, old, count)
        if (added.isEmpty()) return@work emptyList()
        save(old.copy(trackIds = old.trackIds + added))
        added.mapNotNull { input.byId[it] }
    }

    /** Take songs off the radio (never out of the library). */
    fun remove(ids: Set<String>) {
        scope.launch {
            lock.withLock {
                val old = _radio.value
                if (old != null) save(old.copy(trackIds = old.trackIds.filter { it !in ids }))
            }
        }
    }

    /**
     * 1: liked, -1: disliked, 0: neither. Remembered with the current radio's genres and title
     * (`keepRadio`: with the ones it was given on, when changed on the Likes & Dislikes screen).
     */
    fun setOpinion(id: String, value: Int, keepRadio: Boolean = false) {
        scope.launch { lock.withLock {
            val now = _opinions.value.toMutableMap()
            if (value == 0) now.remove(id)
            else {
                val radio = _radio.value
                val old = now[id]
                now[id] = if (keepRadio && old != null) old.copy(value = if (value > 0) 1 else -1)
                else Opinion(if (value > 0) 1 else -1, radio?.genres.orEmpty(), radio?.title.orEmpty())
            }
            _opinions.value = now
            writeLikes(now)
        } }
    }

    private suspend fun <T> work(block: suspend () -> T): T = withContext(Dispatchers.Default) {
        lock.withLock {
            _busy.value = true
            try { block() } finally { _busy.value = false }
        }
    }

    private fun save(radio: Radio): List<Track> {
        _radio.value = radio
        writeRadio(radio)
        return tracks(radio, repository.state.value)
    }

    // ---- Files (private storage) ----------------------------------------------------------

    private fun writeRadio(r: Radio) {
        val json = JSONObject()
            .put("kind", r.kind).put("seed", r.seedId).put("title", r.title)
            .put("genres", JSONArray(catalogue?.ids(r.genres).orEmpty()))
            .put("tracks", JSONArray(r.trackIds))
            .put("discover", r.discover)
        write(currentFile, json.toString())
    }

    private fun readRadio(cat: GenreCatalogue?): Radio? = runCatching {
        if (!currentFile.exists()) return null
        val o = JSONObject(currentFile.readText())
        val kind = o.getString("kind").takeIf { it in Radio.KINDS } ?: return null
        Radio(
            kind = kind,
            seedId = o.getString("seed"),
            title = o.getString("title"),
            genres = cat?.indexes(strings(o.optJSONArray("genres"))).orEmpty(),
            trackIds = strings(o.optJSONArray("tracks")),
            discover = o.optBoolean("discover"),
        )
    }.getOrNull()

    private fun writeLikes(all: Map<String, Opinion>) {
        val list = JSONArray()
        for ((id, op) in all) {
            list.put(JSONObject().put("id", id).put("opinion", op.value)
                .put("genres", JSONArray(catalogue?.ids(op.genres).orEmpty()))
                .put("radio", op.radio).put("date", op.date))
        }
        write(likesFile, list.toString())
    }

    private fun readLikes(cat: GenreCatalogue?): Map<String, Opinion> = runCatching {
        if (!likesFile.exists()) return emptyMap()
        val list = JSONArray(likesFile.readText())
        (0 until list.length()).associate { i ->
            val o = list.getJSONObject(i)
            o.getString("id") to Opinion(
                if (o.getInt("opinion") > 0) 1 else -1,
                cat?.indexes(strings(o.optJSONArray("genres"))).orEmpty(),
                o.optString("radio"),
                o.optString("date"),
            )
        }
    }.getOrDefault(emptyMap())

    private fun strings(a: JSONArray?): List<String> = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }

    /** Written whole to a new file, then put in place, so a crash never leaves half a file. */
    private fun write(file: File, text: String) {
        runCatching {
            folder.mkdirs()
            val temp = File(folder, file.name + ".new")
            temp.writeText(text)
            if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
        }
    }
}
