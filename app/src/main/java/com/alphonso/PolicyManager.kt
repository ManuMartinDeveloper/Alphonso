package com.alphonso

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.provider.Settings
import android.util.Log

object PolicyManager {

    fun enforcePolicies(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, ConsciousnessDeviceAdminReceiver::class.java)
        val accessibilityService = ComponentName(context, ConsciousnessAccessibilityService::class.java)

        // Safety Check
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e("AlphonsoPolicy", "Not Device Owner - Cannot enforce policies")
            return
        }

        try {
            // --- 1. FORCE ACCESSIBILITY ON ---
            val currentServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            val serviceString = accessibilityService.flattenToString()

            // Only update if it's missing (prevents constant writing)
            if (!currentServices.contains(serviceString)) {
                val newServices = if (currentServices.isEmpty()) serviceString else "$currentServices:$serviceString"

                dpm.setSecureSetting(
                    adminComponent,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )
                Log.i("AlphonsoPolicy", "Added Accessibility Service to secure settings")
            }

            // Ensure the master switch is ON
            dpm.setSecureSetting(
                adminComponent,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                "1"
            )

            // --- 2. PREVENT DEACTIVATION ---
            // Fix: Removed the invalid 'hasUserRestriction' check.
            // Just add the restriction directly. It is safe to call repeatedly.
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

            // Prevent Uninstall
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)

            Log.i("AlphonsoPolicy", "Policies Enforced Successfully")

        } catch (e: Exception) {
            Log.e("AlphonsoPolicy", "Failed to enforce policies", e)
        }
    }
}