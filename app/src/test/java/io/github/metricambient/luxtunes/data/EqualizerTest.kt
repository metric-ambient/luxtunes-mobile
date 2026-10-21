package io.github.metricambient.luxtunes.data

import io.github.metricambient.luxtunes.playback.ReplayGainTags
import io.github.metricambient.luxtunes.playback.SoundFilter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class EqualizerTest {
    private fun preset(name: String) = Eq.BUILT_IN.first { it.name == name }

    // LuxTunes' reference values: total gain (dB) at each band's centre.
    private val reference = mapOf(
        ("Bass Booster" to 44100) to listOf(6.44, 6.03, 5.05, 3.58, 1.83, 0.35, 0.07, 0.02, 0.00, 0.00),
        ("Bass Booster" to 48000) to listOf(6.44, 6.03, 5.05, 3.58, 1.83, 0.35, 0.07, 0.02, 0.00, 0.00),
        ("Rock" to 44100) to listOf(5.87, 5.58, 4.24, 2.13, -0.21, -0.81, 0.87, 3.11, 4.10, 4.70),
        ("Rock" to 48000) to listOf(5.87, 5.58, 4.24, 2.13, -0.21, -0.80, 0.88, 3.15, 4.19, 4.77),
        ("Vocal Booster" to 44100) to listOf(-2.14, -3.77, -3.20, 1.69, 4.76, 5.12, 4.09, 2.17, 0.26, -1.48),
        ("Vocal Booster" to 48000) to listOf(-2.14, -3.77, -3.20, 1.69, 4.76, 5.12, 4.09, 2.17, 0.24, -1.47),
    )

    @Test fun filterMatchesLuxTunes() {
        for ((key, want) in reference) {
            val (name, rate) = key
            for (i in Eq.BANDS.indices) {
                val got = preset(name).gainAt(Eq.BANDS[i].toDouble(), rate)
                assertEquals("$name at ${Eq.BANDS[i]} Hz, $rate", want[i], got, 0.05)
            }
        }
    }

    /** Run a sine through the filter and measure its level once it has settled. */
    private fun measured(settings: SoundFilter.Settings, freq: Double, rate: Int = 44100): Double {
        val f = SoundFilter()
        f.configure(rate, 1)
        f.update(settings)
        val n = rate
        val samples = FloatArray(n) { (0.25 * sin(2 * PI * freq * it / rate)).toFloat() }
        val input = samples.copyOf()
        f.process(samples, n)
        var a = 0.0
        var b = 0.0
        for (i in n / 2 until n) { a += input[i] * input[i].toDouble(); b += samples[i] * samples[i].toDouble() }
        return 20 * log10(sqrt(b / a))
    }

    @Test fun soundMatchesReference() {
        assertEquals(-0.81, measured(SoundFilter.Settings(preset("Rock")), 1000.0), 0.05)
        assertEquals(6.03, measured(SoundFilter.Settings(preset("Bass Booster")), 64.0), 0.05)
        assertEquals(5.12, measured(SoundFilter.Settings(preset("Vocal Booster")), 1000.0), 0.05)
    }

    @Test fun preampAloneIsPlainGain() {
        val flatMinus6 = EqPreset("x", -6.0, List(10) { 0.0 })
        for (freq in listOf(32.0, 1000.0, 12000.0)) {
            assertEquals(-6.0, measured(SoundFilter.Settings(flatMinus6), freq), 0.01)
        }
        assertTrue(SoundFilter.Settings(preset("Flat")).neutral)
        assertFalse(SoundFilter.Settings(preset("Flat"), mono = true).neutral)
    }

    @Test fun monoMakesBothSidesTheSame() {
        val f = SoundFilter()
        f.configure(44100, 2)
        f.update(SoundFilter.Settings(mono = true))
        val s = floatArrayOf(0.5f, -0.5f, 0.2f, 0.4f)
        f.process(s, 2)
        assertEquals(0f, s[0], 1e-6f); assertEquals(0f, s[1], 1e-6f)
        assertEquals(0.3f, s[2], 1e-6f); assertEquals(0.3f, s[3], 1e-6f)
    }

    @Test fun choosingWhatPlays() {
        val eq = EqSettings(on = true, chosen = "Rock")
        assertEquals("Rock", eq.forSong(null)?.name)
        assertEquals("Jazz", eq.forSong("jazz")?.name)          // names ignore case
        assertEquals("Rock", eq.forSong("Gone")?.name)          // a missing preset counts as none
        assertNull(eq.copy(on = false).forSong("Jazz"))
    }

    @Test fun manualSaveAndDelete() {
        val moved = EqSettings(chosen = "Rock").withManual(1.04, List(10) { 13.0 })
        assertEquals(Eq.MANUAL, moved.chosen)
        assertEquals(1.0, moved.manual.preamp, 0.0)
        assertEquals(12.0, moved.manual.bands[0], 0.0)           // clamped
        val saved = moved.withSaved("Car")
        assertEquals("Car", saved.chosen)
        assertEquals(saved.presets.map { it.name.lowercase() }.sorted(), saved.presets.map { it.name.lowercase() })
        val deleted = saved.without("car")
        assertTrue(deleted.isManual)
        assertEquals(12.0, deleted.manual.bands[0], 0.0)          // the sound stays the same
        assertNull(deleted.find("Car"))
        assertNull(Eq.cleanName(" manual "))
        assertEquals("My Car", Eq.cleanName("  My   Car "))
    }

    @Test fun settingsSurviveJson() {
        val eq = EqSettings(on = true, chosen = "Rock").withManual(-2.5, List(10) { it.toDouble() }).withSaved("Mine")
        val back = EqSettings.fromJson(JSONObject(eq.toJson().toString()))
        assertEquals(eq, back)
    }

    @Test fun changesFileCarriesTheEqualizer() {
        val eq = EqSettings(on = true, chosen = "Rock")
        val text = ChangesJson.build(emptyList(), "ABC", emptyList(), eq.toJson().toString())
        val o = JSONObject(text)
        assertEquals("Rock", o.getJSONObject("equalizer").getString("preset"))
        assertTrue(o.getJSONObject("equalizer").getBoolean("on"))
        assertEquals(Eq.BUILT_IN.size, o.getJSONObject("equalizer").getJSONArray("presets").length())
    }

    @Test fun replayGainValues() {
        assertEquals(-6.54, ReplayGainTags.parse("-6.54 dB")!!, 1e-9)
        assertEquals(2.1, ReplayGainTags.parse("+2,10 dB")!!, 1e-9)
        assertNull(ReplayGainTags.parse("none"))
    }
}
