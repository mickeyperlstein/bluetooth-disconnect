package com.dontleaveme.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dontleaveme.service.WatchdogService

/**
 * Manifest-registered receiver that catches BT ACL events even when the app is closed.
 * Forwards them to WatchdogService which does all the state logic.
 */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_DISCONNECTED &&
            action != BluetoothDevice.ACTION_ACL_CONNECTED
        ) return

        val serviceIntent = Intent(context, WatchdogService::class.java).apply {
            this.action = action
            // Pass the BluetoothDevice parcel straight through
            intent.extras?.let { putExtras(it) }
        }
        context.startForegroundService(serviceIntent)
    }
}
