package io.github.metricambient.luxtunes.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * The player's sound stage for the equalizer, ReplayGain and mono (see SoundFilter).
 * Works on 16-bit audio, which is what the phone's decoders give; anything else
 * passes through. With nothing to do, the samples are copied untouched.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class LuxAudioProcessor : BaseAudioProcessor() {
    /** Set from the main thread; picked up by the next block of audio. */
    @Volatile var settings = SoundFilter.Settings()
    @Volatile private var resetPending = false
    private val filter = SoundFilter()
    private var floats = FloatArray(0)

    /** A new song started without a seek (gapless): don't let the last one ring into it. */
    fun songChanged() {
        resetPending = true
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioFormat.NOT_SET

    override fun onFlush() {
        // A seek, or a new format: start clean.
        filter.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        filter.reset()
    }

    override fun onReset() {
        floats = FloatArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        if (resetPending) {
            resetPending = false
            filter.reset()
        }
        filter.update(settings)
        val out = replaceOutputBuffer(bytes)
        if (filter.isNeutral) {
            out.put(inputBuffer)
        } else {
            inputBuffer.order(ByteOrder.nativeOrder())
            val samples = bytes / 2
            if (floats.size < samples) floats = FloatArray(samples)
            for (i in 0 until samples) floats[i] = inputBuffer.getShort() / 32768f
            val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
            filter.process(floats, samples / channels)
            for (i in 0 until samples) {
                // Clip cleanly instead of wrapping round.
                out.putShort((floats[i] * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            if (bytes % 2 != 0) inputBuffer.position(inputBuffer.limit())
        }
        out.flip()
    }
}
