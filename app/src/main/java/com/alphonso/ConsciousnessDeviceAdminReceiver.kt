// Updated ConsciousnessDeviceAdminReceiver.kt
package com.alphonso

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ConsciousnessDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Called when you first grant admin rights
        applyProtection(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Handle Boot
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("Alphonso", "Boot Detected - Applying Protection")
            applyProtection(context)
        }
    }

    private fun applyProtection(context: Context) {
        // Apply policies ONCE
        PolicyManager.enforcePolicies(context)
        // Start the passive monitoring service
        context.startForegroundService(Intent(context, AppMonitorService::class.java))
    }
}