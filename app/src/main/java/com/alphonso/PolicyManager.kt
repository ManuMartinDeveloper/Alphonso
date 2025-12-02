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

            if (!currentServices.contains(serviceString)) {
                val newServices = if (currentServices.isEmpty()) serviceString else "$currentServices:$serviceString"
                dpm.setSecureSetting(
                    adminComponent,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )
            }

            dpm.setSecureSetting(
                adminComponent,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                "1"
            )

            // --- 2. FORCE ALWAYS-ON VPN (Fixes Boot Crash) ---
            // This grants VPN permission automatically and restarts it if it dies.
            try {
                if (dpm.getAlwaysOnVpnPackage(adminComponent) != context.packageName) {
                    dpm.setAlwaysOnVpnPackage(adminComponent, context.packageName, true)
                    Log.i("AlphonsoPolicy", "Always-On VPN set to Alphonso")
                }
            } catch (e: Exception) {
                Log.e("AlphonsoPolicy", "Failed to set Always-On VPN", e)
            }

            // --- 3. LOCKDOWN ---
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)

            Log.i("AlphonsoPolicy", "Policies Enforced Successfully")

        } catch (e: Exception) {
            Log.e("AlphonsoPolicy", "Failed to enforce policies", e)
        }
    }
}