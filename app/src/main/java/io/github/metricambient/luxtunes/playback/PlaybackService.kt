package io.github.metricambient.luxtunes.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.metricambient.luxtunes.LuxTunesApp
import io.github.metricambient.luxtunes.ui.MainActivity

/**
 * Plays music in the background, with the notification, lock-screen and
 * Bluetooth controls Media3 provides. Also counts plays and skips the way
 * LuxTunes does on the computer: a play when a song ends, a skip when Next is
 * pressed in its first 20 seconds.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var exo: ExoPlayer
    private var currentId: String? = null

    private val repository get() = (application as LuxTunesApp).repository

    override fun onCreate() {
        super.onCreate()
        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)       // pause when headphones are unplugged
            .build()
        exo.addListener(counter)

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
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
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

    companion object {
        const val SKIP_WINDOW_MS = 20_000L
    }
}
