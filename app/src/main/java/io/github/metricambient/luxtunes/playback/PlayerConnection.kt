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
import kotlinx.coroutines.flow.asStateFlow

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
            waiting?.invoke(c)
            waiting = null
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
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
            isPlaying = c.isPlaying,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = ids,
            queueIndex = c.currentMediaItemIndex,
        )
    }

    // ---- Position (polled by the screens that show it) --------------------------------

    val position: Long get() = controller?.currentPosition ?: 0L
    val duration: Long get() = controller?.duration?.takeIf { it > 0 } ?: 0L

    // ---- Commands --------------------------------------------------------------------------

    /** Play these songs in this order, starting with the one at `start`. */
    fun play(tracks: List<Track>, start: Int = 0) {
        if (tracks.isEmpty()) return
        val items = tracks.map { repository.requestItem(it) }
        withController { c ->
            c.shuffleModeEnabled = false
            c.setMediaItems(items, start.coerceIn(0, items.size - 1), 0L)
            c.prepare()
            c.play()
        }
    }

    /** Play in LuxTunes' spread-shuffle order (the same artist rarely twice in a row). */
    fun shuffle(tracks: List<Track>) = play(spreadShuffle(tracks, key = ::artistKey))

    fun addToQueue(tracks: List<Track>) {
        val items = tracks.map { repository.requestItem(it) }
        withController { c ->
            c.addMediaItems(items)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
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
