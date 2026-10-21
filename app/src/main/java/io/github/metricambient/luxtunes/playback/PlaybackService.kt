package io.github.metricambient.luxtunes.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.metricambient.luxtunes.LuxTunesApp
import io.github.metricambient.luxtunes.data.ReplayGain
import io.github.metricambient.luxtunes.data.Track
import io.github.metricambient.luxtunes.data.Transition
import io.github.metricambient.luxtunes.ui.MainActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Plays music in the background, with the notification, lock-screen and
 * Bluetooth controls Media3 provides. Also counts plays and skips the way
 * LuxTunes does on the computer: a play when a song ends, a skip when Next is
 * pressed in its first 20 seconds.
 *
 * Settings → Sound is applied here: the equalizer, ReplayGain and mono (in the
 * audio processor), speed, autoplay, and crossfades between songs.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var exo: ExoPlayer
    private var currentId: String? = null
    private lateinit var memory: PlaybackMemory
    private val handler = Handler(Looper.getMainLooper())

    /** While playing, note the position every few seconds (so a crash or reboot loses little). */
    private val saveTicker = object : Runnable {
        override fun run() {
            savePosition()
            if (exo.isPlaying) handler.postDelayed(this, 5_000)
        }
    }

    private val app get() = application as LuxTunesApp
    private val repository get() = app.repository
    private val scope = MainScope()
    private val processor = LuxAudioProcessor()

    /** A player whose sound goes through `audio`. Only the main one handles audio focus. */
    private fun buildPlayer(audio: LuxAudioProcessor, main: Boolean): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(audio))
                .build()
        }
        return ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ main,
            )
            .setHandleAudioBecomingNoisy(main)       // pause when headphones are unplugged
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        memory = PlaybackMemory(this)
        exo = buildPlayer(processor, main = true)
        exo.addListener(counter)
        exo.addListener(sound)
        // Settings → Sound and the equalizer: apply whenever either changes.
        scope.launch {
            app.prefs.settings.combine(repository.equalizer) { s, e -> s to e }.collect { applySound() }
        }

        // Every "next" (app, notification, headset, car) passes through here.
        val player = object : ForwardingPlayer(exo) {
            override fun seekToNext() {
                noteSkip()
                super.seekToNext()
            }

            override fun seekToNextMediaItem() {
                noteSkip()
                super.seekToNextMediaItem()
            }
        }

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(open)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(saveTicker)
        endCrossfade()
        scope.cancel()
        savePosition()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun savePosition() {
        if (exo.mediaItemCount == 0) return
        memory.savePosition(
            exo.currentMediaItem?.mediaId, exo.currentMediaItemIndex, exo.currentPosition,
            exo.shuffleModeEnabled, exo.repeatMode,
        )
    }

    private fun saveQueue() {
        memory.saveQueue((0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId })
    }

    private fun noteSkip() {
        val id = exo.currentMediaItem?.mediaId ?: return
        if (exo.currentPosition < SKIP_WINDOW_MS) repository.recordSkip(id)
    }

    private val counter = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previous = currentId
            if (previous != null && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            ) {
                repository.recordPlay(previous)      // it played to the end
            }
            currentId = mediaItem?.mediaId
        }

        override fun onTracksChanged(tracks: Tracks) {
            // The file's own tags are read now: pass on any lyrics in them.
            val id = exo.currentMediaItem?.mediaId ?: return
            EmbeddedLyrics.find(tracks)?.let { repository.foundLyrics(id, it) }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) saveQueue()
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) savePosition()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) {
                handler.removeCallbacks(saveTicker)
                handler.postDelayed(saveTicker, 5_000)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // The last song of the list played to the end.
            if (playbackState == Player.STATE_ENDED) exo.currentMediaItem?.mediaId?.let { repository.recordPlay(it) }
        }
    }

    /** The screens send song IDs; the file and its details are filled in here. */
    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.map { repository.mediaItem(it.mediaId) ?: it }.toMutableList()
            )
    }

    // ---- Settings → Sound -----------------------------------------------------------------

    /** ReplayGain values read from each song file's tags (song, album), by song ID. */
    private val gains = HashMap<String, ReplayGainTags.Gains>()

    private fun trackOf(item: MediaItem?): Track? = item?.mediaId?.let { repository.track(it) }

    /** What a song's sound goes through: its equalizer preset, ReplayGain and mono. */
    private fun filterFor(t: Track?): SoundFilter.Settings {
        val s = app.prefs.current
        if (t == null) return SoundFilter.Settings(mono = s.forceMono)
        // Video sound isn't filtered: on the computer it would drift out of step with the picture.
        val eq = if (t.isVideo) null else repository.equalizer.value.forSong(t.raw["equalizer"] as? String)
        val g = gains[t.id]
        val gain = when (s.replayGain) {
            ReplayGain.OFF -> null
            ReplayGain.SONG -> g?.track ?: g?.album
            ReplayGain.ALBUM -> g?.album ?: g?.track
        }
        return SoundFilter.Settings(eq, if (t.isVideo) 0.0 else gain ?: 0.0, s.forceMono)
    }

    /** LuxTunes' Volume Adjustment: -100…+100 % of the playing volume, never above full. */
    private fun volumeFor(t: Track?): Float {
        val adjust = (t?.raw?.get("volume_adjustment") as? Number)?.toFloat() ?: 0f
        return (1f + adjust / 100f).coerceIn(0f, 1f)
    }

    private fun speedFor(t: Track?): PlaybackParameters {
        val s = app.prefs.current
        val spoken = t?.mediaKind == "audiobook" || t?.mediaKind == "podcast"
        val speed = if (t?.isVideo == true) 1f else if (spoken) s.spokenSpeed else s.musicSpeed
        val fix = if (spoken) s.spokenPitchFix else s.musicPitchFix
        return PlaybackParameters(speed, if (fix) 1f else speed)
    }

    /** Apply everything for the song that's playing now. */
    private fun applySound() {
        val t = trackOf(exo.currentMediaItem)
        processor.settings = filterFor(t)
        exo.pauseAtEndOfMediaItems = !app.prefs.current.autoplay
        val speed = speedFor(t)
        if (exo.playbackParameters != speed) exo.playbackParameters = speed
        if (fadeStart == 0L) exo.volume = volumeFor(t)
    }

    private val sound = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!startingFade) endCrossfade()          // the listener skipped or chose another song
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) processor.songChanged()
            applySound()
        }

        override fun onTracksChanged(tracks: Tracks) {
            val id = exo.currentMediaItem?.mediaId ?: return
            gains[id] = ReplayGainTags.find(tracks)
            applySound()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) handler.post(crossfadeWatch) else if (!exo.playWhenReady) endCrossfade()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK && !startingFade) endCrossfade()
        }
    }

    // ---- Crossfade -----------------------------------------------------------------------
    // Near the end of a song, a second player takes over its last seconds and fades
    // them out, while the main player moves on to the next song and fades it in.

    private var tail: ExoPlayer? = null
    private val tailProcessor = LuxAudioProcessor()
    private var fadeStart = 0L
    private var fadeLength = 0L
    private var tailVolume = 1f
    private var startingFade = false

    private val crossfadeWatch = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            if (!exo.isPlaying) return
            maybeStartCrossfade()
            handler.postDelayed(this, 250)
        }
    }

    private fun maybeStartCrossfade() {
        val s = app.prefs.current
        if (s.transition != Transition.CROSSFADE || !s.autoplay || fadeStart != 0L) return
        if (exo.repeatMode == Player.REPEAT_MODE_ONE || !exo.hasNextMediaItem()) return
        val duration = exo.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val now = exo.currentMediaItem ?: return
        val next = exo.getMediaItemAt(exo.nextMediaItemIndex)
        if (trackOf(now)?.isVideo == true || trackOf(next)?.isVideo == true) return
        val left = ((duration - exo.currentPosition) / exo.playbackParameters.speed).toLong()
        val wanted = s.crossfadeSeconds * 1000L
        if (left > wanted || left < 400) return
        startCrossfade(now, min(wanted, left))
    }

    private fun startCrossfade(item: MediaItem, length: Long) {
        endCrossfade()
        val t = buildPlayer(tailProcessor, main = false)
        tailProcessor.settings = processor.settings
        tailVolume = exo.volume
        t.setMediaItem(item, exo.currentPosition)
        t.playbackParameters = exo.playbackParameters
        t.volume = tailVolume
        t.prepare()
        t.play()
        tail = t
        repository.recordPlay(item.mediaId)        // it played to its fade
        fadeStart = SystemClock.uptimeMillis()
        fadeLength = length.coerceAtLeast(1)
        startingFade = true
        try {
            exo.volume = 0f
            exo.seekToNextMediaItem()
        } finally {
            startingFade = false
        }
        handler.post(fadeTick)
    }

    private val fadeTick = object : Runnable {
        override fun run() {
            if (fadeStart == 0L) return
            val p = ((SystemClock.uptimeMillis() - fadeStart).toFloat() / fadeLength).coerceIn(0f, 1f)
            exo.volume = volumeFor(trackOf(exo.currentMediaItem)) * p
            tail?.volume = tailVolume * (1f - p)
            if (p >= 1f) endCrossfade() else handler.postDelayed(this, 40)
        }
    }

    /** Stop any crossfade: the outgoing song stops, the new one plays at its own volume. */
    private fun endCrossfade() {
        handler.removeCallbacks(fadeTick)
        tail?.release()
        tail = null
        if (fadeStart != 0L) {
            fadeStart = 0L
            exo.volume = volumeFor(trackOf(exo.currentMediaItem))
        }
    }

    companion object {
        const val SKIP_WINDOW_MS = 20_000L
    }
}
