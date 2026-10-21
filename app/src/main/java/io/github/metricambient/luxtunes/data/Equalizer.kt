package io.github.metricambient.luxtunes.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The equalizer, as LuxTunes has it: ten bands (32 Hz to 16 kHz, one octave apart)
 * and a preamp, each -12 to +12 dB in steps of 0.1. Each band is a peaking filter one
 * octave wide (Q 1.41), so presets sound the same here and on the computer.
 */
object Eq {
    val BANDS = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val LABELS = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    const val LIMIT = 12.0
    const val Q = 1.41
    const val MANUAL = "Manual"
    private const val NAME_MAX = 60

    fun clamp(db: Double): Double = (max(-LIMIT, min(LIMIT, db)) * 10).roundToInt() / 10.0

    /** A preset name as LuxTunes keeps it, or null if it can't be used. */
    fun cleanName(name: String): String? {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(NAME_MAX).trim()
        if (clean.isEmpty() || clean.equals(MANUAL, true) || clean.equals("None", true)) return null
        return clean
    }

    /** The presets of a new LuxTunes library (until the first sync brings the real list). */
    val BUILT_IN: List<EqPreset> = listOf(
        "Acoustic" to listOf(5.0, 4.9, 4.0, 1.1, 2.1, 1.8, 3.5, 4.1, 3.5, 2.1),
        "Bass Booster" to listOf(5.5, 4.2, 3.5, 2.5, 1.2, 0.0, 0.0, 0.0, 0.0, 0.0),
        "Bass Reducer" to listOf(-5.5, -4.2, -3.5, -2.5, -1.2, 0.0, 0.0, 0.0, 0.0, 0.0),
        "Classical" to listOf(4.8, 3.8, 3.0, 2.5, -1.8, -1.8, 0.0, 2.2, 3.2, 3.8),
        "Dance" to listOf(3.6, 6.5, 5.0, 0.0, 1.9, 3.6, 5.2, 4.5, 3.6, 0.0),
        "Deep" to listOf(5.0, 3.5, 1.8, 1.0, 2.9, 2.5, 1.4, -2.1, -3.5, -4.6),
        "Electronic" to listOf(4.2, 3.8, 1.2, 0.0, -2.1, 2.2, 0.8, 1.2, 4.0, 4.8),
        "Flat" to List(10) { 0.0 },
        "Hip-Hop" to listOf(5.0, 4.2, 1.5, 3.0, -1.0, -1.0, 1.5, -0.5, 2.0, 3.0),
        "Jazz" to listOf(4.0, 3.0, 1.5, 2.2, -1.5, -1.5, 0.0, 1.5, 3.0, 3.8),
        "Latin" to listOf(4.5, 3.0, 0.0, 0.0, -1.5, -1.5, -1.5, 0.0, 3.0, 4.5),
        "Loudness" to listOf(6.0, 4.0, 0.0, 0.0, -2.0, 0.0, -1.0, -5.0, 5.0, 1.0),
        "Lounge" to listOf(-3.0, -1.5, -0.5, 1.5, 4.0, 2.5, 0.0, -1.5, 2.0, 1.0),
        "Piano" to listOf(3.0, 2.0, 0.0, 2.5, 3.0, 1.5, 3.5, 4.5, 3.0, 3.5),
        "Pop" to listOf(-1.5, -1.0, 0.0, 2.0, 4.0, 4.0, 2.0, 0.0, -1.0, -1.5),
        "R&B" to listOf(2.6, 6.9, 5.7, 1.3, -2.2, -1.5, 2.3, 2.6, 3.0, 3.8),
        "Rock" to listOf(5.0, 4.0, 3.0, 1.5, -0.5, -1.0, 0.5, 2.5, 3.5, 4.5),
        "Small Speakers" to listOf(5.5, 4.2, 3.5, 2.5, 1.2, 0.0, -1.2, -2.5, -3.5, -4.2),
        "Spoken Word" to listOf(-3.5, -0.5, 0.0, 0.7, 3.5, 4.6, 4.8, 4.3, 2.5, 0.0),
        "Treble Booster" to listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.2, 2.5, 3.5, 4.2, 5.5),
        "Treble Reducer" to listOf(0.0, 0.0, 0.0, 0.0, 0.0, -1.2, -2.5, -3.5, -4.2, -5.5),
        "Vocal Booster" to listOf(-1.5, -3.0, -3.0, 1.5, 3.8, 3.8, 3.0, 1.5, 0.0, -1.5),
    ).map { (name, bands) -> EqPreset(name, 0.0, bands) }

    /**
     * One peaking band's filter (Audio EQ Cookbook), divided through by a0:
     * b0, b1, b2, a1, a2. Null when the band does nothing or is too close to
     * half the sample rate.
     */
    fun peaking(f0: Double, gainDb: Double, sampleRate: Int): DoubleArray? {
        if (gainDb == 0.0 || f0 >= 0.45 * sampleRate) return null
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * f0 / sampleRate
        val alpha = sin(w0) / (2 * Q)
        val a0 = 1 + alpha / a
        return doubleArrayOf(
            (1 + alpha * a) / a0, (-2 * cos(w0)) / a0, (1 - alpha * a) / a0,
            (-2 * cos(w0)) / a0, (1 - alpha / a) / a0,
        )
    }
}

/** A preset: a name, the preamp and the ten bands, in dB. */
data class EqPreset(val name: String, val preamp: Double, val bands: List<Double>) {
    val isFlat: Boolean get() = preamp == 0.0 && bands.all { it == 0.0 }

    fun renamed(newName: String) = copy(name = newName)

    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("preamp", preamp)
        .put("bands", JSONArray(bands))

    companion object {
        fun of(name: String, preamp: Double, bands: List<Double>) =
            EqPreset(name, Eq.clamp(preamp), List(10) { Eq.clamp(bands.getOrElse(it) { 0.0 }) })

        fun fromJson(o: JSONObject): EqPreset {
            val arr = o.optJSONArray("bands") ?: JSONArray()
            return of(o.optString("name"), o.optDouble("preamp", 0.0), List(10) { arr.optDouble(it, 0.0) })
        }
    }
}

/**
 * The equalizer's settings: on or off, the chosen preset (or Manual), the Manual
 * values and the preset list in menu order.
 */
data class EqSettings(
    val on: Boolean = false,
    val chosen: String = "Flat",
    val manual: EqPreset = EqPreset(Eq.MANUAL, 0.0, List(10) { 0.0 }),
    val presets: List<EqPreset> = Eq.BUILT_IN,
) {
    fun find(name: String?): EqPreset? =
        if (name.isNullOrBlank()) null else presets.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** What the equalizer shows: the chosen preset, or the Manual values. */
    val current: EqPreset get() = find(chosen) ?: manual

    val isManual: Boolean get() = find(chosen) == null

    /** What a song plays with: its own preset if that still exists, else the chosen one. Null while off. */
    fun forSong(songPreset: String?): EqPreset? = if (!on) null else find(songPreset) ?: current

    // ---- Changes, as on the computer -------------------------------------------------------

    /** Moving a slider: Manual, holding the new values. */
    fun withManual(preamp: Double, bands: List<Double>) =
        copy(manual = EqPreset.of(Eq.MANUAL, preamp, bands), chosen = Eq.MANUAL)

    /** Save what's shown under a name (replacing a preset of that name); it becomes the chosen one. */
    fun withSaved(name: String): EqSettings {
        val p = current.renamed(name)
        val old = find(name)
        val list = if (old != null) presets.map { if (it === old) p else it }
        else (presets + p).sortedBy { it.name.lowercase() }
        return copy(presets = list, chosen = name)
    }

    /** Delete a preset; if it was chosen, Manual keeps its values so the sound doesn't change. */
    fun without(name: String): EqSettings {
        val gone = find(name) ?: return this
        val wasChosen = gone.name.equals(chosen, ignoreCase = true)
        return copy(
            presets = presets.filter { it !== gone },
            chosen = if (wasChosen) Eq.MANUAL else chosen,
            manual = if (wasChosen) gone.renamed(Eq.MANUAL) else manual,
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("on", on)
        .put("preset", if (isManual) Eq.MANUAL else current.name)
        .put("manual", JSONObject().put("preamp", manual.preamp).put("bands", JSONArray(manual.bands)))
        .put("presets", JSONArray(presets.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject): EqSettings {
            val list = o.optJSONArray("presets")
            val presets = if (list == null) Eq.BUILT_IN
            else (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let(EqPreset::fromJson) }
                .filter { it.name.isNotBlank() }
            val m = o.optJSONObject("manual")
            val manual = if (m == null) EqPreset(Eq.MANUAL, 0.0, List(10) { 0.0 })
            else EqPreset.fromJson(m.put("name", Eq.MANUAL))
            return EqSettings(o.optBoolean("on", false), o.optString("preset", "Flat"), manual, presets)
        }
    }
}

/** Total gain (dB) of a preset's filters at a frequency: for checking the filter math. */
fun EqPreset.gainAt(freq: Double, sampleRate: Int): Double {
    var db = preamp
    val w = 2 * PI * freq / sampleRate
    for ((i, f0) in Eq.BANDS.withIndex()) {
        val c = Eq.peaking(f0.toDouble(), bands[i], sampleRate) ?: continue
        // |H(e^jw)| of b0 + b1 z^-1 + b2 z^-2 over 1 + a1 z^-1 + a2 z^-2
        val nr = c[0] + c[1] * cos(w) + c[2] * cos(2 * w)
        val ni = -(c[1] * sin(w) + c[2] * sin(2 * w))
        val dr = 1 + c[3] * cos(w) + c[4] * cos(2 * w)
        val di = -(c[3] * sin(w) + c[4] * sin(2 * w))
        val mag = kotlin.math.sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
        db += 20 * kotlin.math.log10(mag)
    }
    return if (abs(db) < 1e-9) 0.0 else db
}
