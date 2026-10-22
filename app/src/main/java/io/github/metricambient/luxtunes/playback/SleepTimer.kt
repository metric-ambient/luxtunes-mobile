package io.github.metricambient.luxtunes.playback

import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The sleep timer, as in LuxTunes: after 15 minutes … 8 hours the music fades out over
 * 8 seconds and pauses. Lives as long as the app's process (the playback service keeps
 * that going while music plays), so closing the screens doesn't stop it.
 */
object SleepTimer {
    /** The choices, in minutes. */
    val CHOICES = listOf(15, 30, 60, 120, 240, 480)
    const val FADE_MS = 8_000L

    /** When the music stops (wall clock ms, for showing), or null when the timer is off. */
    private val _endsAt = MutableStateFlow<Long?>(null)
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    /** Set by the playback service: fade out and pause. Without it (nothing playing) the timer just ends. */
    @Volatile var onEnd: (() -> Unit)? = null

    private val scope = MainScope()
    private var countdown: Job? = null

    /** Start (or restart) the timer. */
    fun start(minutes: Int) {
        val length = minutes * 60_000L
        _endsAt.value = System.currentTimeMillis() + length
        val endUptime = SystemClock.elapsedRealtime() + length
        countdown?.cancel()
        countdown = scope.launch {
            // Measured on the clock that keeps counting while the phone sleeps.
            while (true) {
                val left = endUptime - SystemClock.elapsedRealtime()
                if (left <= 0) break
                delay(minOf(left, 30_000L))
            }
            val end = onEnd
            if (end != null) end() else stop()
        }
    }

    /** Turn the timer off (also called once the music has faded out). */
    fun stop() {
        countdown?.cancel()
        countdown = null
        _endsAt.value = null
    }

    /** "42m", "1:05" for an hour or more, "<1m" for the last minute. */
    fun leftLabel(endsAt: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = (endsAt - now).coerceAtLeast(0) / 60_000
        return when {
            minutes < 1 -> "<1m"
            minutes < 60 -> "${minutes}m"
            else -> "%d:%02d".format(minutes / 60, minutes % 60)
        }
    }

    fun choiceLabel(minutes: Int): String = when {
        minutes < 60 -> "$minutes minutes"
        minutes == 60 -> "1 hour"
        else -> "${minutes / 60} hours"
    }
}
