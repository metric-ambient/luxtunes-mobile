package io.github.metricambient.luxtunes.playback

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import io.github.metricambient.luxtunes.data.AppSettings

/**
 * Settings → Sound → Audio Priority: what to do when another app or a phone call
 * wants the sound. The app asks Android for "audio focus" itself (instead of the
 * player's own handling) so these choices can be followed.
 *
 * With Ignore Other Apps, it doesn't ask for focus at all, so other apps neither stop
 * LuxTunes nor get stopped by it; calls are then noticed by watching the phone's
 * audio mode (ringing, in a call) every second while playing.
 */
class AudioFocusKeeper(
    context: Context,
    private val player: Player,
    private val settings: () -> AppSettings,
    private val duck: (Boolean) -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var has = false
    private var pausedForCall = false
    private var pausedForOther = false

    private val request: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener({ onChange(it) }, Handler(Looper.getMainLooper()))
        .build()

    private val handler = Handler(Looper.getMainLooper())

    /** The player was told to play or to stop. */
    fun playingChanged(play: Boolean) {
        if (play) {
            if (pausedByUs()) return
            pausedForCall = false
            pausedForOther = false
            if (settings().ignoreOtherApps) release() else take()
            watchCalls()
        } else if (!pausedByUs()) {
            release()
            if (!pausedForCall) handler.removeCallbacks(callWatch)
        }
    }

    /** Ignore Other Apps was switched: hand back focus, or take it. */
    fun settingsChanged() {
        if (!player.playWhenReady) return
        if (settings().ignoreOtherApps) release() else take()
        watchCalls()
    }

    private fun watchCalls() {
        handler.removeCallbacks(callWatch)
        if (settings().ignoreOtherApps) handler.post(callWatch)
    }

    /** Without focus: pause for a call and carry on after it, from the phone's audio mode. */
    private val callWatch = object : Runnable {
        override fun run() {
            val s = settings()
            if (!s.ignoreOtherApps) return
            if (inCall()) {
                if (s.pauseOnCall && player.playWhenReady) pause(call = true)
            } else if (pausedForCall) {
                pausedForCall = false
                if (s.resumeAfterCall) player.play()
            }
            if (player.playWhenReady || pausedForCall) handler.postDelayed(this, 1000)
        }
    }

    /** True while a pause we made ourselves is in progress (we keep focus to hear when to resume). */
    private var pausing = false
    private fun pausedByUs() = pausing

    private fun take() {
        if (has || settings().ignoreOtherApps) return
        has = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // Refused (a call is on): stop, unless other apps are to be ignored.
        if (!has && !settings().ignoreOtherApps && inCall() && settings().pauseOnCall) pause(call = true)
    }

    fun release() {
        if (has) audio.abandonAudioFocusRequest(request)
        has = false
        duck(false)
    }

    /** The service is closing. */
    fun close() {
        handler.removeCallbacks(callWatch)
        release()
    }

    private fun inCall(): Boolean = when (audio.mode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
        else -> false
    }

    private fun pause(call: Boolean) {
        if (!player.playWhenReady) return
        if (call) pausedForCall = true else pausedForOther = true
        pausing = true
        try {
            player.pause()
        } finally {
            pausing = false
        }
    }

    private fun onChange(change: Int) {
        val s = settings()
        val call = inCall()
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                has = false
                // Another app took over for good: no coming back by itself.
                if (call && s.pauseOnCall) pause(call = true)
                else if (!call && !s.ignoreOtherApps && s.pauseOnFocusLoss) {
                    pause(call = false)
                    pausedForOther = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (call) { if (s.pauseOnCall) pause(call = true) }
                else if (!s.ignoreOtherApps && s.pauseOnFocusLoss) pause(call = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (call) { if (s.pauseOnCall) pause(call = true) }
                else if (!s.ignoreOtherApps) duck(true)        // quieter while the other app talks
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                has = true
                duck(false)
                val resume = (pausedForCall && s.resumeAfterCall) || pausedForOther
                pausedForCall = false
                pausedForOther = false
                if (resume) player.play()
            }
        }
    }
}
