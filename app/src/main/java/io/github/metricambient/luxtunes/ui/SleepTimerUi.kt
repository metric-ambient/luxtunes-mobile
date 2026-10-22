package io.github.metricambient.luxtunes.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.metricambient.luxtunes.playback.SleepTimer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Opens the sleep timer sheet from anywhere (Now Playing, the ⋮ menus, the bottom bar). */
object SleepTimerUi {
    internal val open = mutableStateOf(false)
    fun show() {
        open.value = true
    }
}

/** "11:45 PM" (or 23:45, as the phone is set), with the weekday if it isn't today: "Tue 3:15 AM". */
fun stopTimeText(context: Context, at: Long): String {
    val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(at))
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = at }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) time else SimpleDateFormat("EEE", Locale.getDefault()).format(Date(at)) + " " + time
}

/** The time now, updated every second while shown. */
@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** The ⋮ menu items: "Sleep Timer… (stops at 11:45 PM)", and Turn Off while it runs. */
@Composable
fun sleepTimerMenu(): Menu {
    val context = LocalContext.current
    val endsAt by SleepTimer.endsAt.collectAsState()
    val at = endsAt
    return if (at == null) listOf("Sleep Timer…" to { SleepTimerUi.show() })
    else listOf(
        "Sleep Timer… (stops at ${stopTimeText(context, at)})" to { SleepTimerUi.show() },
        "Turn Off Sleep Timer" to { SleepTimer.stop() },
    )
}

/** A moon with the time left, while the timer runs. Tapping it opens the sheet (with Turn Off). */
@Composable
fun SleepTimerBadge(modifier: Modifier = Modifier, alwaysShow: Boolean = false) {
    val endsAt by SleepTimer.endsAt.collectAsState()
    val at = endsAt
    if (at == null && !alwaysShow) return
    val now = rememberNow()
    Column(
        modifier.size(48.dp).clickable { SleepTimerUi.show() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Bedtime, "Sleep timer",
            tint = if (at != null) Lux.accent else Lux.icon,
            modifier = Modifier.size(if (at != null) 20.dp else 24.dp),
        )
        if (at != null) Text(SleepTimer.leftLabel(at, now), color = Lux.accent, fontSize = 10.sp, maxLines = 1)
    }
}

/** The sheet: six lengths, a live "Music will stop at …", OK, Cancel, and Turn Off while running. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet() {
    var open by SleepTimerUi.open
    if (!open) return
    val context = LocalContext.current
    val prefs = app().prefs
    val endsAt by SleepTimer.endsAt.collectAsState()
    var minutes by remember { mutableStateOf(prefs.current.sleepMinutes) }
    val now = rememberNow()
    val close = { open = false }

    // Opened all the way, so OK is in sight without dragging the sheet up.
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = close, sheetState = sheet, containerColor = Lux.panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Sleep Timer", color = Lux.text, fontSize = 22.sp)
            endsAt?.let { at ->
                Text(
                    "On: the music stops at ${stopTimeText(context, at)} (${SleepTimer.leftLabel(at, now)} left).",
                    color = Lux.dim, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Stop playing music after:", color = Lux.text, fontSize = 17.sp)
            for (choice in SleepTimer.CHOICES) {
                Row(
                    Modifier.fillMaxWidth().clickable { minutes = choice },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = choice == minutes,
                        onClick = { minutes = choice },
                        colors = RadioButtonDefaults.colors(selectedColor = Lux.accent),
                    )
                    Text(SleepTimer.choiceLabel(choice), color = Lux.text, fontSize = 17.sp)
                }
            }
            Text(
                "Music will stop at ${stopTimeText(context, now + minutes * 60_000L)}.",
                color = Lux.dim, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (endsAt != null) {
                    TextButton(onClick = { SleepTimer.stop(); close() }) { Text("Turn Off", color = Lux.accent) }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = close) { Text("Cancel", color = Lux.accent) }
                TextButton(onClick = {
                    prefs.update { it.copy(sleepMinutes = minutes) }
                    SleepTimer.start(minutes)
                    close()
                }) { Text(if (endsAt != null) "Restart" else "OK", color = Lux.accent) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
