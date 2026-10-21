package io.github.metricambient.luxtunes.playback

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.metricambient.luxtunes.LuxTunesApp

/**
 * Settings → Headset/Bluetooth: notices headphones and Bluetooth audio being
 * connected (while LuxTunes is running) to start playing, and remembers when
 * Bluetooth connected, for blocking a car's or speaker's own "play".
 */
object DeviceWatch {
    /** When a Bluetooth audio device last connected (uptime ms), 0 if never. */
    @Volatile var bluetoothConnectedAt = 0L
        private set

    /** A receiver's automatic "play" comes within this long of connecting. */
    const val RECEIVER_PLAY_WINDOW_MS = 10_000L

    fun justConnectedBluetooth(): Boolean =
        bluetoothConnectedAt != 0L && SystemClock.uptimeMillis() - bluetoothConnectedAt < RECEIVER_PLAY_WINDOW_MS

    private val wired = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
    )

    fun start(app: LuxTunesApp) {
        val audio = app.getSystemService(AudioManager::class.java)
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            private var ready = false       // the first call lists what's already connected

            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                if (!ready) { ready = true; return }
                val sinks = added.filter { it.isSink }
                val s = app.prefs.current
                if (sinks.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }) {
                    bluetoothConnectedAt = SystemClock.uptimeMillis()
                    if (s.playOnBluetooth) app.player.playFromDevice()
                }
                if (sinks.any { it.type in wired } && s.playOnWired) app.player.playFromDevice()
            }
        }, Handler(Looper.getMainLooper()))
    }

    /** High Priority Mode: let headset buttons start LuxTunes even when it isn't playing. */
    fun setHighPriority(context: Context, on: Boolean) {
        val receiver = ComponentName(context, "androidx.media3.session.MediaButtonReceiver")
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (context.packageManager.getComponentEnabledSetting(receiver) != state) {
            context.packageManager.setComponentEnabledSetting(receiver, state, PackageManager.DONT_KILL_APP)
        }
    }
}
