package io.github.metricambient.luxtunes.playback

import io.github.metricambient.luxtunes.data.Eq
import io.github.metricambient.luxtunes.data.EqPreset
import kotlin.math.pow

/**
 * What a song's sound goes through, in order: a gain (preamp plus ReplayGain), the
 * equalizer's ten bands, then mono if asked. Runs on interleaved float samples
 * (-1..1). Plain Kotlin, so it can be tested without a phone.
 */
class SoundFilter {
    /** What to apply. `eq` null: no equalizer (off, or a video). */
    data class Settings(val eq: EqPreset? = null, val gainDb: Double = 0.0, val mono: Boolean = false) {
        val neutral: Boolean get() = (eq == null || eq.isFlat) && gainDb == 0.0 && !mono
    }

    private var settings = Settings()
    private var sampleRate = 0
    private var channels = 0
    private var gain = 1f
    // Per band: b0, b1, b2, a1, a2 (null: the band is skipped).
    private var coeffs: Array<FloatArray?> = arrayOfNulls(10)
    // Per band and channel: the filter's two state values.
    private var state: Array<FloatArray> = emptyArray()

    val isNeutral: Boolean get() = settings.neutral

    fun configure(sampleRate: Int, channels: Int) {
        if (sampleRate == this.sampleRate && channels == this.channels) return
        this.sampleRate = sampleRate
        this.channels = channels
        rebuild(keepState = false)
    }

    /** New settings take effect at once; the filters' memory is kept, so there's no click. */
    fun update(s: Settings) {
        if (s == settings) return
        settings = s
        rebuild(keepState = true)
    }

    /** A seek or a new song: forget what was playing, so it doesn't ring into what's next. */
    fun reset() {
        for (st in state) st.fill(0f)
    }

    private fun rebuild(keepState: Boolean) {
        val eq = settings.eq
        val preamp = eq?.preamp ?: 0.0
        gain = 10.0.pow((preamp + settings.gainDb) / 20).toFloat()
        val ch = channels.coerceAtLeast(1)
        if (!keepState || state.size != 10 * ch) state = Array(10 * ch) { FloatArray(2) }
        val old = coeffs
        coeffs = Array(10) { i ->
            val c = if (eq == null || sampleRate == 0) null
            else Eq.peaking(Eq.BANDS[i].toDouble(), eq.bands[i], sampleRate)
            // A band coming back on starts from silence, not from old values.
            if (c != null && old[i] == null) for (k in 0 until ch) state[i * ch + k].fill(0f)
            c?.let { d -> FloatArray(5) { k -> d[k].toFloat() } }
        }
    }

    /** Filter `frames` frames of interleaved samples in place. */
    fun process(samples: FloatArray, frames: Int) {
        val ch = channels.coerceAtLeast(1)
        val g = gain
        val bands = coeffs
        val mono = settings.mono && ch >= 2
        for (f in 0 until frames) {
            val base = f * ch
            for (c in 0 until ch) {
                var x = samples[base + c] * g
                for (b in 0 until 10) {
                    // Transposed direct form II
                    val k = bands[b] ?: continue
                    val z = state[b * ch + c]
                    val y = k[0] * x + z[0]
                    z[0] = k[1] * x - k[3] * y + z[1]
                    z[1] = k[2] * x - k[4] * y
                    x = y
                }
                samples[base + c] = x
            }
            if (mono) {
                var sum = 0f
                for (c in 0 until ch) sum += samples[base + c]
                val m = sum / ch
                for (c in 0 until ch) samples[base + c] = m
            }
        }
    }
}
