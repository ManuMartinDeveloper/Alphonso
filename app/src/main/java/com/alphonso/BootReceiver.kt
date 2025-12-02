package com.alphonso

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("AlphonsoBoot", "Boot Completed - Enforcing Policies")
            PolicyManager.enforcePolicies(context)

            // Optional: Start the main VPN service on boot too
            context.startForegroundService(Intent(context, CloudflareDnsService::class.java))
        }
    }
}