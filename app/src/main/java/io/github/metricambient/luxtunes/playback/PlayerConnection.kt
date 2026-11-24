package io.github.metricambient.luxtunes.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.metricambient.luxtunes.LuxTunesApp
import io.github.metricambient.luxtunes.data.Track
import io.github.metricambient.luxtunes.data.Shuffle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screens show about playback. */
data class PlayerState(
    val connected: Boolean = false,
    val currentId: String? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<String> = emptyList(),      // song IDs in play order
    val queueIndex: Int = -1,
)

/**
 * Playing, or about to be: also true for the moment it buffers after a seek or
 * between songs, so the play button doesn't flicker to "paused" then.
 */
private val Player.wantsToPlay: Boolean
    get() = playWhenReady &&
        playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED &&
        playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE

/** The screens' link to PlaybackService. Connected while the app is on screen. */
class PlayerConnection(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var waiting: ((MediaController) -> Unit)? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val repository get() = (context.applicationContext as LuxTunesApp).repository

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            update()
            if (c.mediaItemCount == 0 && waiting == null) restore(c)
            waiting?.invoke(c)
            waiting = null
        }, ContextCompat.getMainExecutor(context))
    }

    private val scope = MainScope()
    private var restoring: Job? = null

    /**
     * Nothing loaded (the app was closed): put back the Now Playing list, the song and
     * the position from last time, paused. The song itself goes in at once (read on its
     * own, so its art and position show straight away); the rest of the list joins it
     * around it once the library is in, which takes a few seconds.
     */
    private fun restore(c: MediaController) {
        val saved = PlaybackMemory(context).load() ?: return
        restoring?.cancel()
        restoring = scope.launch {
            val first = saved.currentId?.let { id -> withContext(Dispatchers.IO) { repository.loadEarly(id) } }
            if (controller !== c || c.mediaItemCount > 0) return@launch      // something else started meanwhile
            if (first != null) {
                PlaybackMemory.restoring = true
                c.setMediaItems(listOf(repository.requestItem(first)), 0, saved.positionMs)
                startRestored(c, saved.repeatMode)
            }
            try {
                restoreRest(c, saved, first)
            } finally {
                PlaybackMemory.restoring = false
            }
        }
    }

    /** The rest of the list from last time, once the library is in (see restore). */
    private suspend fun restoreRest(c: MediaController, saved: PlaybackMemory.Saved, first: Track?) {
        val library = repository.state.first { it.loaded && it.folder != null && it.allTracks.isNotEmpty() }
        if (controller !== c) return
        val tracks = saved.queue.mapNotNull { library.byId[it] }
        if (tracks.isEmpty()) return
        var index = saved.index
        if (index !in tracks.indices || tracks[index].id != saved.currentId) {
            index = tracks.indexOfFirst { it.id == saved.currentId }.coerceAtLeast(0)
        }
        if (first == null) {
            if (c.mediaItemCount > 0) return
            val position = if (tracks[index].id == saved.currentId) saved.positionMs else 0L
            c.setMediaItems(tracks.map { repository.requestItem(it) }, index, position)
            startRestored(c, saved.repeatMode)
            return
        }
        // Only the early song is in, and still the only one: add the rest around it.
        if (c.mediaItemCount != 1 || c.getMediaItemAt(0).mediaId != first.id || tracks[index].id != first.id) return
        val before = tracks.take(index).map { repository.requestItem(it) }
        val after = tracks.drop(index + 1).map { repository.requestItem(it) }
        if (before.isNotEmpty()) c.addMediaItems(0, before)
        if (after.isNotEmpty()) c.addMediaItems(after)
    }

    /** After restoring: our own shuffle state, the repeat mode, ready to play (or playing). */
    private fun startRestored(c: MediaController, repeatMode: Int) {
        c.shuffleModeEnabled = false              // shuffle is ours (see "Shuffle" below)
        loadShuffle()
        c.repeatMode = repeatMode
        c.prepare()
        if (playWhenRestored) {
            playWhenRestored = false
            c.play()
        }
    }

    fun release() {
        restoring?.cancel()
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    /** Run now if connected, else as soon as the connection is up. */
    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else {
            waiting = action
            connect()
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = update()
    }

    private fun update() {
        val c = controller ?: return
        checkLoaded(c)
        val ids = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        _state.value = PlayerState(
            connected = true,
            currentId = c.currentMediaItem?.mediaId,
            isPlaying = c.wantsToPlay,
            shuffle = shuffled,
            repeatMode = c.repeatMode,
            queue = ids,
            queueIndex = c.currentMediaItemIndex,
        )
    }

    // ---- Position (polled by the screens that show it) --------------------------------

    val position: Long get() = controller?.currentPosition ?: 0L

    /** For the video screen, which shows the picture through it. */
    val controllerOrNull: MediaController? get() = controller
    val duration: Long get() = controller?.duration?.takeIf { it > 0 } ?: 0L

    // ---- Commands --------------------------------------------------------------------------

    /** Play these songs in this order, starting with the one at `start`. Shuffle goes off. */
    fun play(tracks: List<Track>, start: Int = 0, ready: () -> Unit = {}) = start(tracks, start, null, ready)

    /**
     * Play `order`, a shuffled version of `original`, with the Shuffle button on;
     * turning Shuffle off then goes back to `original`'s order.
     */
    fun playShuffled(order: List<Track>, original: List<Track>) = start(order, 0, original)

    private fun start(tracks: List<Track>, start: Int, original: List<Track>?, ready: () -> Unit = {}) {
        if (tracks.isEmpty()) { finishLoading(); return }
        if (tracks.size >= BIG_LIST && _busy.value == null) _busy.value = "Loading ${"%,d".format(tracks.size)} songs…"
        val items = tracks.map { repository.requestItem(it) }
        setShuffle(original != null, original?.map { it.id }.orEmpty())
        withController { c ->
            c.shuffleModeEnabled = false
            upNextEnd = 0
            c.setMediaItems(items, start.coerceIn(0, items.size - 1), 0L)
            c.prepare()
            c.play()
            waitUntilLoaded(c, ready)
        }
    }

    /**
     * Play in LuxTunes' shuffle order (see Shuffle). The shuffle is worked out away from
     * the screen's thread, so a big library doesn't freeze the app; `ready` runs once
     * the player has the songs.
     */
    fun shuffle(tracks: List<Track>, ready: () -> Unit = {}) =
        shuffled(tracks, tracks, ready) { Shuffle.order(tracks, allSongs()).ifEmpty { tracks } }

    /** Play this list in random order, starting with `first`. */
    fun shuffleFrom(tracks: List<Track>, first: Track, ready: () -> Unit = {}) =
        shuffled(tracks, tracks, ready) { listOf(first) + Shuffle.order(tracks.filter { it.id != first.id }, allSongs()) }

    private fun shuffled(tracks: List<Track>, original: List<Track>, ready: () -> Unit, order: () -> List<Track>) {
        if (tracks.isEmpty() || _busy.value != null) return
        if (tracks.size >= BIG_LIST) _busy.value = "Shuffling ${"%,d".format(tracks.size)} songs…"
        scope.launch {
            val shuffledOrder = withContext(Dispatchers.Default) { order() }
            start(shuffledOrder, 0, original, ready)
        }
    }

    // ---- Long lists: what the player is getting ready -------------------------------------

    private val _busy = MutableStateFlow<String?>(null)

    /** While a long list is shuffled or loaded: what's happening ("Shuffling 7,612 songs…"), else null. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private var whenLoaded: (() -> Unit)? = null
    private var loadTimeout: Job? = null

    /** `ready` runs (and busy ends) once the player has started on the new list, or failed to. */
    private fun waitUntilLoaded(c: MediaController, ready: () -> Unit) {
        whenLoaded = ready
        loadTimeout?.cancel()
        loadTimeout = scope.launch { delay(60_000); finishLoading() }     // never stay busy for good
        checkLoaded(c)
    }

    private fun checkLoaded(c: MediaController) {
        if (whenLoaded == null) return
        // The controller shows a new list at once, but READY (or an error) only comes from the player.
        if (c.playbackState == Player.STATE_READY || c.playbackState == Player.STATE_ENDED || c.playerError != null) {
            finishLoading()
        }
    }

    private fun finishLoading() {
        val ready = whenLoaded
        whenLoaded = null
        loadTimeout?.cancel()
        _busy.value = null
        ready?.invoke()
    }

    /** The whole library, for shuffle's "albums heard lately". */
    private fun allSongs() = repository.state.value.allTracks

    // Where the songs added with "Add to Queue" end, so the next ones go after them.
    private var upNextEnd = 0

    /**
     * Add to Queue, as in LuxTunes: these songs play after the current one and after
     * anything queued before them, then the list carries on.
     */
    fun addToQueue(tracks: List<Track>) {
        val items = tracks.map { repository.requestItem(it) }
        if (items.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(items)
                c.prepare()
                c.play()
                upNextEnd = items.size
            } else {
                val at = maxOf(c.currentMediaItemIndex + 1, upNextEnd).coerceAtMost(c.mediaItemCount)
                c.addMediaItems(at, items)
                upNextEnd = at + items.size
            }
        }
        if (shuffled) setShuffle(true, unshuffled + tracks.map { it.id })
    }

    /** Add these songs at the very end of the play queue. */
    fun addToEnd(tracks: List<Track>) {
        val items = tracks.map { repository.requestItem(it) }
        withController { c ->
            c.addMediaItems(items)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
        }
        if (shuffled) setShuffle(true, unshuffled + tracks.map { it.id })
    }

    /**
     * Take these songs out of the play queue. The song playing stays unless `skipIfPlaying`,
     * when it's skipped first (as pressing Next would).
     */
    fun removeFromQueue(ids: Set<String>, skipIfPlaying: Boolean = false) = withController { c ->
        if (skipIfPlaying && c.currentMediaItem?.mediaId in ids && c.hasNextMediaItem()) c.seekToNext()
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (i != c.currentMediaItemIndex && c.getMediaItemAt(i).mediaId in ids) c.removeMediaItem(i)
        }
        if (shuffled) setShuffle(true, unshuffled.filter { it !in ids })
    }

    /**
     * Play `order` instead of the current list without stopping the song playing (when
     * it's in `order`; otherwise `order` starts from the top). Shuffle goes off.
     */
    fun replaceAround(order: List<Track>) = withController { c ->
        val currentId = c.currentMediaItem?.mediaId
        if (currentId == null || order.none { it.id == currentId }) { start(order, 0, null); return@withController }
        setShuffle(false, emptyList())
        rearrange(c, order.map { it.id }, currentId, c.currentMediaItemIndex)
    }

    fun pause() = withController { it.pause() }

    private var playWhenRestored = false

    /** Headphones or Bluetooth connected (Settings → Playback): carry on playing. */
    fun playFromDevice() = withController { c ->
        if (c.mediaItemCount == 0) {
            playWhenRestored = true
            restore(c)
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun togglePlay() = withController { c ->
        if (c.wantsToPlay) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }       // restarts the song after 3 s
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun skipToQueueItem(index: Int) = withController { it.seekToDefaultPosition(index); it.play() }

    // ---- Shuffle --------------------------------------------------------------------------
    // The app shuffles the list itself (LuxTunes' shuffle, or by albums), so the
    // player's own shuffle mode stays off. `unshuffled` is the order to go back to.

    private val shuffleMemory = context.getSharedPreferences("shuffle", Context.MODE_PRIVATE)
    private var shuffled = false
    private var unshuffled: List<String> = emptyList()

    init { loadShuffle() }

    private fun setShuffle(on: Boolean, original: List<String>) {
        shuffled = on
        unshuffled = if (on) original else emptyList()
        shuffleMemory.edit()
            .putBoolean("on", shuffled)
            .putString("original", unshuffled.joinToString(","))
            .apply()
        update()
    }

    private fun loadShuffle() {
        shuffled = shuffleMemory.getBoolean("on", false)
        unshuffled = shuffleMemory.getString("original", null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        update()
    }

    /**
     * Shuffle on: the current song keeps playing and everything else is shuffled after it.
     * Shuffle off: back to the order from before shuffling, carrying on from this song.
     */
    fun toggleShuffle() = withController { c ->
        if (_busy.value != null) return@withController
        val count = c.mediaItemCount
        val now = c.currentMediaItemIndex
        val ids = (0 until count).map { c.getMediaItemAt(it).mediaId }
        if (count == 0 || now !in ids.indices) { setShuffle(!shuffled, ids); return@withController }
        val library = repository.state.value
        val currentId = ids[now]
        if (!shuffled) {
            // Worked out away from the screen's thread (a long list takes a moment).
            if (count >= BIG_LIST) _busy.value = "Shuffling ${"%,d".format(count)} songs…"
            scope.launch {
                val rest = ids.filterIndexed { i, _ -> i != now }.mapNotNull { library.byId[it] }
                val order = withContext(Dispatchers.Default) { Shuffle.order(rest, library.allTracks).map { it.id } }
                _busy.value = null
                if (c.currentMediaItem?.mediaId != currentId || c.mediaItemCount != count) return@launch   // the list changed meanwhile
                setShuffle(true, ids)
                rearrange(c, listOf(currentId) + order, currentId, c.currentMediaItemIndex)
            }
        } else {
            // Songs added while shuffled are already at the end of `unshuffled`.
            val original = unshuffled.filter { library.byId[it] != null }.ifEmpty { ids }
            setShuffle(false, emptyList())
            rearrange(c, if (currentId in original) original else listOf(currentId) + original, currentId, now)
        }
    }

    /** Swap everything around the playing song (at `now`) for `newOrder`, so it doesn't stop. */
    private fun rearrange(c: MediaController, newOrder: List<String>, currentId: String, now: Int) {
        val library = repository.state.value
        val count = c.mediaItemCount
        val at = newOrder.indexOf(currentId)
        val before = newOrder.take(at).mapNotNull { id -> library.byId[id]?.let { repository.requestItem(it) } }
        val after = newOrder.drop(at + 1).mapNotNull { id -> library.byId[id]?.let { repository.requestItem(it) } }
        if (now + 1 < count) c.removeMediaItems(now + 1, count)
        if (now > 0) c.removeMediaItems(0, now)
        c.addMediaItems(0, before)
        c.addMediaItems(after)
        upNextEnd = 0
    }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    companion object {
        /** From this many songs, a "Shuffling…" / "Loading…" note may show while the player gets them ready. */
        const val BIG_LIST = 500
    }
}
