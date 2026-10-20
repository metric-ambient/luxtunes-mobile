package io.github.metricambient.luxtunes.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Watches for USB file-transfer (MTP) mode, when LuxTunes may be syncing.
 * Android announces the USB state with this (sticky) broadcast; "connected"
 * and "mtp" are its extras.
 */
object UsbState {
    private const val ACTION = "android.hardware.usb.action.USB_STATE"

    fun watch(context: Context, onChange: (transferMode: Boolean) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val connected = intent.getBooleanExtra("connected", false)
                val mtp = intent.getBooleanExtra("mtp", false)
                val transfer = connected && mtp
                Log.i("LuxTunes", "USB: connected=$connected mtp=$mtp")
                onChange(transfer)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
    }
}
