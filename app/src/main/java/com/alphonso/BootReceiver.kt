package com.alphonso

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("AlphonsoBoot", "Boot Completed")
            PolicyManager.enforcePolicies(context)
            context.startForegroundService(Intent(context, AppMonitorService::class.java))
        }
    }
}