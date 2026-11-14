package io.github.metricambient.luxtunes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** A short message that shows in the middle of the screen for a moment ("3 songs added to Car"). */
object Notice {
    internal val message = mutableStateOf<Pair<String, Long>?>(null)

    fun show(text: String) {
        message.value = text to System.nanoTime()
    }
}

/** Placed once, at the root, over everything. */
@Composable
fun NoticeHost() {
    val current by Notice.message
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(current) {
        val (t, _) = current ?: return@LaunchedEffect
        text = t
        visible = true
        delay(2000)
        visible = false
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(400))) {
            Text(
                text,
                color = Lux.text, fontSize = 17.sp, textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Lux.panel.copy(alpha = 0.95f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}

/**
 * While a long list is being shuffled or loaded (PlayerConnection.busy): after a short
 * moment, a small window says what's happening, so a wait doesn't look like a freeze.
 * Placed once, at the root.
 */
@Composable
fun BusyHost() {
    val busy by app().player.busy.collectAsState()
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        show = false
        if (busy != null) {
            delay(400)
            show = true
        }
    }
    val text = busy
    if (show && text != null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Lux.panel)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Lux.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(text, color = Lux.text, fontSize = 17.sp)
            }
        }
    }
}
