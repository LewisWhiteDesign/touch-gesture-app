package com.lewisw.lockgestures

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the gesture service after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val validAction = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (validAction && Prefs(context).serviceEnabled) {
            GestureService.start(context)
        }
    }
}
