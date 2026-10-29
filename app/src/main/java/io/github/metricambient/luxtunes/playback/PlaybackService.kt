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
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import android.view.KeyEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import io.github.metricambient.luxtunes.LuxTunesApp
import io.github.metricambient.luxtunes.R
import io.github.metricambient.luxtunes.data.HeardThisSession
import io.github.metricambient.luxtunes.data.Shuffle
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
    private lateinit var focus: AudioFocusKeeper
    private var ducked = false

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
                /* handleAudioFocus = */ false,         // AudioFocusKeeper does it (Audio Priority)
            )
            .setHandleAudioBecomingNoisy(main)       // pause when headphones are unplugged
            .setWakeMode(if (main) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NONE)    // keep going with the screen off
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // The status bar icon while a song is playing or paused: the app's penguin.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_luxtunes) }
        )
        memory = PlaybackMemory(this)
        exo = buildPlayer(processor, main = true)
        exo.addListener(counter)
        exo.addListener(sound)
        focus = AudioFocusKeeper(this, exo, { app.prefs.current }) { on ->
            ducked = on
            if (fadeStart == 0L && sleepFadeStart == 0L) exo.volume = volumeFor(trackOf(exo.currentMediaItem))
        }
        // The sleep timer ends here: fade out, then pause. Turned off mid-fade: full volume again.
        SleepTimer.onEnd = { sleepFade() }
        scope.launch {
            SleepTimer.endsAt.collect { if (it == null && sleepFadeStart != 0L) stopSleepFade() }
        }
        // Settings → Sound and the equalizer: apply whenever either changes.
        scope.launch {
            app.prefs.settings.combine(repository.equalizer) { s, e -> s to e }.collect {
                applySound()
                focus.settingsChanged()
            }
        }

        // Every "next" (app, notification, headset, car) passes through here.
        val player = object : ForwardingPlayer(exo) {
            override fun play() {
                if (!allowFromOutside(play = true)) return
                super.play()
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (!allowFromOutside(play = playWhenReady)) return
                super.setPlayWhenReady(playWhenReady)
            }

            override fun pause() {
                if (!allowFromOutside(play = false)) return
                super.pause()
            }

            override fun seekToPrevious() {
                if (!allowFromOutside(play = false)) return
                super.seekToPrevious()
            }

            override fun seekToNext() {
                if (!allowFromOutside(play = false)) return
                noteSkip()
                super.seekToNext()
            }

            override fun seekToNextMediaItem() {
                if (!allowFromOutside(play = false)) return
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
        stopSleepFade()
        SleepTimer.onEnd = null
        focus.close()
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

    private var lastIndex = -1

    /** A new pass with Repeat All: the song now playing stays first, the rest are shuffled again. */
    private fun reshufflePass() {
        val count = exo.mediaItemCount
        if (exo.currentMediaItemIndex != 0 || count < 3) return
        val byId = repository.state.value.byId
        val rest = (1 until count).mapNotNull { byId[exo.getMediaItemAt(it).mediaId] }
        val order = Shuffle.order(rest, repository.state.value.allTracks, keepSkipped = true)
        exo.removeMediaItems(1, count)
        exo.addMediaItems(order.mapNotNull { repository.mediaItem(it.id) })
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
            if (exo.playWhenReady) currentId?.let(HeardThisSession::add)
            // Repeat All with Shuffle on: each new pass through the list is shuffled again.
            val index = exo.currentMediaItemIndex
            if (index == 0 && lastIndex == exo.mediaItemCount - 1 && exo.mediaItemCount > 2 &&
                exo.repeatMode == Player.REPEAT_MODE_ALL && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                getSharedPreferences("shuffle", Context.MODE_PRIVATE).getBoolean("on", false)
            ) handler.post { reshufflePass() }
            lastIndex = index
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) exo.currentMediaItem?.mediaId?.let(HeardThisSession::add)
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

    /**
     * The screens send song IDs; the file and its details are filled in here. Anything
     * that isn't a song in the library is dropped: the session is open to other apps
     * (the system's media controls, Bluetooth, car displays), and none of them may make
     * LuxTunes play a web address or a file outside the LuxTunes folder.
     */
    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.mapNotNull { repository.mediaItem(it.mediaId) }.toMutableList()
            )

        @Suppress("DEPRECATION")
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean = ignoreButton(controllerInfo, intent)

        /**
         * A headset's play button with LuxTunes not playing (High Priority Mode): pick up
         * where it left off, once the library is in.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                repository.refreshAsync()
                val saved = memory.load()
                val ready = withTimeoutOrNull(15_000) {
                    repository.state.first { it.loaded && it.allTracks.isNotEmpty() }
                }
                val items = if (ready == null || saved == null) emptyList()
                else saved.queue.mapNotNull { repository.mediaItem(it) }
                if (items.isEmpty()) {
                    result.setException(IllegalStateException("nothing to resume"))
                } else {
                    val index = items.indexOfFirst { it.mediaId == saved?.currentId }
                    result.set(MediaSession.MediaItemsWithStartPosition(
                        items, index.coerceAtLeast(0), if (index >= 0) saved?.positionMs ?: 0L else 0L,
                    ))
                }
            }
            return result
        }
    }

    // ---- Settings → Headset/Bluetooth ----------------------------------------------------------
    // Commands from this app (its screens and its notification) always count. Others are
    // checked: Bluetooth devices send theirs through the system's Bluetooth service.

    private fun fromBluetooth(controller: MediaSession.ControllerInfo?): Boolean =
        controller != null && controller.packageName.contains("bluetooth", ignoreCase = true)

    /** May this command, from whoever sent it, go ahead? */
    private fun allowFromOutside(play: Boolean): Boolean {
        val controller = session?.controllerForCurrentRequest ?: return true
        if (controller.packageName == packageName) return true
        val s = app.prefs.current
        if (fromBluetooth(controller) && !s.obeyHeadset) return false
        // A car or speaker saying "play" as it connects.
        if (play && !exo.playWhenReady && s.blockReceiverPlay && DeviceWatch.justConnectedBluetooth()) return false
        return true
    }

    /** Headset buttons (wired, and Bluetooth ones sent as button presses). True: ignored. */
    private fun ignoreButton(controller: MediaSession.ControllerInfo, intent: Intent): Boolean {
        if (controller.packageName == packageName) return false          // our own notification
        val s = app.prefs.current
        if (!s.obeyHeadset) return true
        val key = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
        val isPlay = key.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || key.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        return isPlay && !exo.playWhenReady && s.blockReceiverPlay && DeviceWatch.justConnectedBluetooth()
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
        // Quieter while another app speaks (navigation and the like).
        return (1f + adjust / 100f).coerceIn(0f, 1f) * if (ducked) 0.2f else 1f
    }

    private fun speedFor(t: Track?): PlaybackParameters {
        val s = app.prefs.current
        val (speed, fix) = when {
            t?.isVideo == true -> 1f to true
            t?.mediaKind == "audiobook" -> s.audiobookSpeed to s.audiobookPitchFix
            t?.mediaKind == "podcast" -> s.podcastSpeed to s.podcastPitchFix
            else -> s.musicSpeed to s.musicPitchFix
        }
        return PlaybackParameters(speed, if (fix) 1f else speed)
    }

    /** Apply everything for the song that's playing now. */
    private fun applySound() {
        val t = trackOf(exo.currentMediaItem)
        processor.settings = filterFor(t)
        exo.pauseAtEndOfMediaItems = !app.prefs.current.autoplay
        val speed = speedFor(t)
        if (exo.playbackParameters != speed) exo.playbackParameters = speed
        if (fadeStart == 0L && sleepFadeStart == 0L) exo.volume = volumeFor(t)
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

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            focus.playingChanged(playWhenReady)
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
        if (s.transition != Transition.CROSSFADE || !s.autoplay || fadeStart != 0L || sleepFadeStart != 0L) return
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

    // ---- Sleep timer ---------------------------------------------------------------------------

    private var sleepFadeStart = 0L

    private fun sleepFade() {
        if (!exo.playWhenReady) {           // nothing playing: nothing to stop
            SleepTimer.stop()
            return
        }
        endCrossfade()
        sleepFadeStart = SystemClock.uptimeMillis()
        handler.post(sleepTick)
    }

    private val sleepTick = object : Runnable {
        override fun run() {
            if (sleepFadeStart == 0L) return
            val p = ((SystemClock.uptimeMillis() - sleepFadeStart).toFloat() / SleepTimer.FADE_MS).coerceIn(0f, 1f)
            exo.volume = volumeFor(trackOf(exo.currentMediaItem)) * (1f - p)
            if (p < 1f) {
                handler.postDelayed(this, 50)
            } else {
                // Paused, not stopped: Play carries on from here, at full volume.
                exo.pause()
                stopSleepFade()
                SleepTimer.stop()
            }
        }
    }

    private fun stopSleepFade() {
        handler.removeCallbacks(sleepTick)
        if (sleepFadeStart != 0L) {
            sleepFadeStart = 0L
            exo.volume = volumeFor(trackOf(exo.currentMediaItem))
        }
    }

    companion object {
        const val SKIP_WINDOW_MS = 20_000L
    }
}
