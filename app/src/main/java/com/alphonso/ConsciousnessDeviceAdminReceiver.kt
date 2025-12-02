package com.alphonso

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class ConsciousnessDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("AlphonsoAdmin", "Device Admin Enabled")
        Toast.makeText(context, "Alphonso Security Active", Toast.LENGTH_SHORT).show()

        // Use the shared helper
        PolicyManager.enforcePolicies(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        PolicyManager.enforcePolicies(context)
    }
}