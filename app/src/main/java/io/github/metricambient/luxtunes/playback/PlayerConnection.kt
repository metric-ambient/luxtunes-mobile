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
import io.github.metricambient.luxtunes.data.artistKey
import io.github.metricambient.luxtunes.data.spreadShuffle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
     * Nothing loaded (the app was closed): put back the Now Playing list, the
     * song and the position from last time, paused, as soon as the library is in.
     */
    private fun restore(c: MediaController) {
        val saved = PlaybackMemory(context).load() ?: return
        restoring?.cancel()
        restoring = scope.launch {
            val library = repository.state.first { it.loaded && it.folder != null && it.allTracks.isNotEmpty() }
            if (controller !== c || c.mediaItemCount > 0) return@launch      // something else started meanwhile
            val tracks = saved.queue.mapNotNull { library.byId[it] }
            if (tracks.isEmpty()) return@launch
            var index = saved.index
            if (index !in tracks.indices || tracks[index].id != saved.currentId) {
                index = tracks.indexOfFirst { it.id == saved.currentId }.coerceAtLeast(0)
            }
            val position = if (tracks[index].id == saved.currentId) saved.positionMs else 0L
            c.setMediaItems(tracks.map { repository.requestItem(it) }, index, position)
            c.shuffleModeEnabled = saved.shuffle
            c.repeatMode = saved.repeatMode
            c.prepare()
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
        val ids = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        _state.value = PlayerState(
            connected = true,
            currentId = c.currentMediaItem?.mediaId,
            isPlaying = c.wantsToPlay,
            shuffle = c.shuffleModeEnabled,
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

    /** Play these songs in this order, starting with the one at `start`. */
    fun play(tracks: List<Track>, start: Int = 0) {
        if (tracks.isEmpty()) return
        val items = tracks.map { repository.requestItem(it) }
        withController { c ->
            c.shuffleModeEnabled = false
            upNextEnd = 0
            c.setMediaItems(items, start.coerceIn(0, items.size - 1), 0L)
            c.prepare()
            c.play()
        }
    }

    /** Play in LuxTunes' spread-shuffle order (the same artist rarely twice in a row). */
    fun shuffle(tracks: List<Track>) = play(spreadShuffle(tracks, key = ::artistKey))

    /** Play this list in random order, starting with `first`. */
    fun shuffleFrom(tracks: List<Track>, first: Track) =
        play(listOf(first) + spreadShuffle(tracks.filter { it.id != first.id }, key = ::artistKey))

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
    }

    /** Add these songs at the very end of the play queue. */
    fun addToEnd(tracks: List<Track>) {
        val items = tracks.map { repository.requestItem(it) }
        withController { c ->
            c.addMediaItems(items)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
        }
    }

    fun pause() = withController { it.pause() }

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
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
