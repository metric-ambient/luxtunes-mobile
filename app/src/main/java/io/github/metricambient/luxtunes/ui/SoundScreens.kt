package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.data.Eq
import io.github.metricambient.luxtunes.data.EqSettings
import io.github.metricambient.luxtunes.data.ReplayGain
import io.github.metricambient.luxtunes.data.Transition
import kotlin.math.roundToInt

private fun speedLabel(speed: Float, pitchFix: Boolean): String =
    "%.2f×".format(speed).replace(".00×", ".0×") + if (speed != 1f && !pitchFix) " · pitch changes with speed" else ""

/** The equalizer preset pop-up: Manual first, then the presets. */
private fun choosePreset(eq: EqSettings, set: (EqSettings) -> Unit) {
    val names = listOf(Eq.MANUAL) + eq.presets.map { it.name }
    val selected = if (eq.isManual) 0 else 1 + eq.presets.indexOf(eq.current)
    Dialogs.show(DialogRequest.Choose("Equalizer Preset", names, selected) { i -> set(eq.copy(chosen = names[i])) })
}

/** Settings → Sound. */
@Composable
fun SoundSettingsScreen(nav: NavHostController) {
    val app = app()
    val prefs = app.prefs
    val s by prefs.settings.collectAsState()
    val eq by app.repository.equalizer.collectAsState()
    val eqHere by app.repository.equalizerChangedHere.collectAsState()
    var speedFor by remember { mutableStateOf<Boolean?>(null) }       // true: music, false: audiobooks and podcasts

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Sound")
        ToggleRow(
            "Autoplay", "Play the next song when one ends. Off: stop after every song.", s.autoplay,
        ) { on -> prefs.update { it.copy(autoplay = on) } }
        ChoiceRow("Track Transitions", s.transition.label) {
            Dialogs.show(DialogRequest.Choose("Track Transitions", Transition.entries.map { it.label }, s.transition.ordinal) { i ->
                prefs.update { it.copy(transition = Transition.entries[i]) }
            })
        }
        val fading = s.transition == Transition.CROSSFADE
        Column(Modifier.fillMaxWidth().alpha(if (fading) 1f else 0.4f).padding(horizontal = 16.dp)) {
            Text("Crossfade time: ${s.crossfadeSeconds} s", color = Lux.text, fontSize = 16.sp)
            Slider(
                value = s.crossfadeSeconds.toFloat(),
                onValueChange = { v -> prefs.update { it.copy(crossfadeSeconds = v.roundToInt().coerceIn(1, 10)) } },
                valueRange = 1f..10f,
                steps = 8,
                enabled = fading,
                colors = SliderDefaults.colors(thumbColor = Lux.accent, activeTrackColor = Lux.accent, inactiveTrackColor = Lux.track),
            )
        }
        HorizontalDivider(color = Lux.panel)

        ToggleRow(
            "Equalizer", "LuxTunes' ten-band equalizer, with the same presets as the computer.", eq.on,
        ) { on -> app.repository.setEqualizer(eq.copy(on = on)) }
        ChoiceRow("Equalizer Preset", if (eq.isManual) Eq.MANUAL else eq.current.name) {
            choosePreset(eq) { app.repository.setEqualizer(it) }
        }
        ChoiceRow("Adjust Equalizer", "Preamp and the ten bands; save your own presets") { nav.go(Routes.SETTINGS_EQUALIZER) }
        if (eqHere) {
            Text(
                "Changed on this phone: goes to LuxTunes at the next sync.",
                color = Lux.dim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp),
            )
            TextButton(onClick = { app.repository.useComputerEqualizer() }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Use the computer's equalizer", color = Lux.accent)
            }
        }
        HorizontalDivider(color = Lux.panel)

        ChoiceRow("Music Playback Speed", speedLabel(s.musicSpeed, s.musicPitchFix)) { speedFor = true }
        ChoiceRow("Audiobook & Podcast Speed", speedLabel(s.spokenSpeed, s.spokenPitchFix)) { speedFor = false }
        ChoiceRow("Replay Gain", s.replayGain.label) {
            Dialogs.show(DialogRequest.Choose("Replay Gain", ReplayGain.entries.map { it.label }, s.replayGain.ordinal) { i ->
                prefs.update { it.copy(replayGain = ReplayGain.entries[i]) }
            })
        }
        ToggleRow(
            "Force Mono", "Play everything in mono: both sides get the same sound.", s.forceMono,
        ) { on -> prefs.update { it.copy(forceMono = on) } }
        Spacer(Modifier.height(24.dp))
    }

    speedFor?.let { music ->
        SpeedDialog(
            title = if (music) "Music Playback Speed" else "Audiobook & Podcast Speed",
            speed = if (music) s.musicSpeed else s.spokenSpeed,
            pitchFix = if (music) s.musicPitchFix else s.spokenPitchFix,
            onDismiss = { speedFor = null },
            onDone = { speed, fix ->
                speedFor = null
                prefs.update {
                    if (music) it.copy(musicSpeed = speed, musicPitchFix = fix)
                    else it.copy(spokenSpeed = speed, spokenPitchFix = fix)
                }
            },
        )
    }
}

/** A speed from 0.5× to 3.0× (steps of 0.05), and whether to keep the pitch. */
@Composable
private fun SpeedDialog(title: String, speed: Float, pitchFix: Boolean, onDismiss: () -> Unit, onDone: (Float, Boolean) -> Unit) {
    var value by remember { mutableStateOf(speed) }
    var fix by remember { mutableStateOf(pitchFix) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("%.2f×".format(value), color = Lux.text, fontSize = 22.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Slider(
                    value = value,
                    onValueChange = { value = (it * 20).roundToInt() / 20f },
                    valueRange = 0.5f..3f,
                    steps = 49,
                    colors = SliderDefaults.colors(thumbColor = Lux.accent, activeTrackColor = Lux.accent, inactiveTrackColor = Lux.track),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0.5×", color = Lux.dim, fontSize = 13.sp)
                    Text("3.0×", color = Lux.dim, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fix, onCheckedChange = { fix = it }, colors = CheckboxDefaults.colors(checkedColor = Lux.accent))
                    Text("Correct pitch (voices don't go higher or lower)", color = Lux.text, fontSize = 15.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(value, fix) }) { Text("OK", color = Lux.accent) } },
        dismissButton = {
            Row {
                TextButton(onClick = { value = 1f }) { Text("Normal", color = Lux.accent) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = Lux.accent) }
            }
        },
    )
}

/** Settings → Sound → Adjust Equalizer: on/off, preset, the preamp and ten bands, saving presets. */
@Composable
fun EqualizerScreen(nav: NavHostController) {
    val app = app()
    val repo = app.repository
    val eq by repo.equalizer.collectAsState()
    val library by repo.state.collectAsState()
    val playerState by app.player.state.collectAsState()
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val shown = eq.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(nav, "Equalizer")
        ToggleRow("Equalizer", "On: songs play through these settings.", eq.on) { on -> repo.setEqualizer(eq.copy(on = on)) }
        ChoiceRow("Preset", if (eq.isManual) Eq.MANUAL else shown.name) { choosePreset(eq) { repo.setEqualizer(it) } }
        // As on the computer: a song with its own preset plays with that one.
        val playing = playerState.currentId?.let { library.byId[it] }
        val own = eq.find(playing?.raw?.get("equalizer") as? String)
        if (eq.on && playing != null && own != null) {
            Text(
                "“${playing.name}” plays with its own preset, ${own.name}.",
                color = Lux.dim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Text(
            "Moving a slider switches to Manual. Double-tap a name to set it back to 0.",
            color = Lux.dim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        BandRow("Preamp", shown.preamp) { v -> repo.setEqualizer(eq.withManual(v, shown.bands)) }
        HorizontalDivider(color = Lux.panel)
        for (i in Eq.BANDS.indices) {
            BandRow("${Eq.LABELS[i]} Hz", shown.bands[i]) { v ->
                repo.setEqualizer(eq.withManual(shown.preamp, shown.bands.toMutableList().also { it[i] = v }))
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { saving = true },
                colors = ButtonDefaults.buttonColors(containerColor = Lux.accent, contentColor = Lux.onAccent),
            ) { Text("Save as Preset…") }
            if (!eq.isManual) {
                OutlinedButton(onClick = { deleting = true }) { Text("Delete Preset", color = Lux.text) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (saving) {
        var name by remember { mutableStateOf(if (eq.isManual) "" else shown.name) }
        val clean = Eq.cleanName(name)
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Save as Preset") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true, label = { Text("Name") })
                    when {
                        name.isNotBlank() && clean == null -> Text("“Manual” and “None” can't be used.", color = Lux.dim, fontSize = 13.sp)
                        clean != null && eq.find(clean) != null -> Text("This replaces the preset “${eq.find(clean)?.name}”.", color = Lux.dim, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = clean != null, onClick = {
                    clean?.let { repo.setEqualizer(eq.withSaved(it)) }
                    saving = false
                }) { Text("Save", color = Lux.accent) }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Cancel", color = Lux.accent) } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete “${shown.name}”?") },
            text = { Text("The sound stays the same (as Manual). Songs that used this preset go back to the chosen one.") },
            confirmButton = {
                TextButton(onClick = { repo.setEqualizer(eq.without(shown.name)); deleting = false }) { Text("Delete", color = Lux.accent) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel", color = Lux.accent) } },
        )
    }
}

/** One band (or the preamp): its name, a -12…+12 dB slider and the value. */
@Composable
private fun BandRow(label: String, db: Double, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = Lux.text, fontSize = 15.sp,
            modifier = Modifier.width(72.dp).pointerInput(label) { detectTapGestures(onDoubleTap = { onChange(0.0) }) },
        )
        Slider(
            value = db.toFloat(),
            onValueChange = { onChange(Eq.clamp(it.toDouble())) },
            valueRange = -12f..12f,
            colors = SliderDefaults.colors(thumbColor = Lux.accent, activeTrackColor = Lux.accent, inactiveTrackColor = Lux.track),
            modifier = Modifier.weight(1f),
        )
        Text(
            (if (db > 0) "+" else "") + "%.1f".format(db), color = Lux.dim, fontSize = 14.sp,
            modifier = Modifier.width(52.dp).padding(start = 8.dp),
        )
    }
}
