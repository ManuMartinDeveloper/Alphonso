package com.alphonso

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast

class ConsciousnessDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        enforcePolicies(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        enforcePolicies(context)
    }

    // Runs when phone finishes booting
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            enforcePolicies(context)
        }
    }

    private fun enforcePolicies(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, ConsciousnessDeviceAdminReceiver::class.java)
        val accessibilityService = ComponentName(context, ConsciousnessAccessibilityService::class.java)

        if (dpm.isDeviceOwnerApp(context.packageName)) {
            try {
                // 1. Force Accessibility
                val currentServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                val serviceString = accessibilityService.flattenToString()

                if (!currentServices.contains(serviceString)) {
                    val newServices = if (currentServices.isEmpty()) serviceString else "$currentServices:$serviceString"
                    dpm.setSecureSetting(adminComponent, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices)
                    dpm.setSecureSetting(adminComponent, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                }

                // 2. Lock App
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
                dpm.setUninstallBlocked(adminComponent, context.packageName, true)

                Log.i("AlphonsoAdmin", "Policies Enforced on Boot/Update")

            } catch (e: Exception) {
                Log.e("AlphonsoAdmin", "Error enforcing policies", e)
            }
        }
    }
}