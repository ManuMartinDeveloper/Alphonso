package com.alphonso

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ConsciousnessDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        PolicyManager.enforcePolicies(context)
        context.startForegroundService(Intent(context, AppMonitorService::class.java))
        Toast.makeText(context, "Alphonso Protection Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        PolicyManager.enforcePolicies(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PolicyManager.enforcePolicies(context)
            context.startForegroundService(Intent(context, AppMonitorService::class.java))
        }
    }
}