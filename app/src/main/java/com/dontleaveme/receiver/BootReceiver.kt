// v2 — uncomment the <receiver> block in AndroidManifest.xml to activate.
// Restarts WatchdogService after a device reboot or app update.

package com.dontleaveme.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dontleaveme.service.WatchdogService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            context.startForegroundService(Intent(context, WatchdogService::class.java))
        }
    }
}
