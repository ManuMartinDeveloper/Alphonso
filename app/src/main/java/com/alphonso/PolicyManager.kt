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

        // 1. Safety Check: We can only do this if we are Device Owner
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e("AlphonsoPolicy", "Not Device Owner - Cannot enforce policies")
            return
        }

        try {
            // --- STEP A: FORCE ACCESSIBILITY ON ---
            val currentServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            val serviceString = accessibilityService.flattenToString()

            // If our service is not in the list, ADD IT.
            if (!currentServices.contains(serviceString)) {
                val newServices = if (currentServices.isEmpty()) serviceString else "$currentServices:$serviceString"

                dpm.setSecureSetting(
                    adminComponent,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )
                Log.i("AlphonsoPolicy", "Forced Accessibility Service ON")
            }

            // Force the Master Switch ON
            dpm.setSecureSetting(
                adminComponent,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                "1"
            )

            // --- STEP B: PREVENT TURNING IT OFF ---
            // 1. Block the user from "Force Stopping" or "Clearing Data"
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

            // 2. Block Uninstallation
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)

            Log.i("AlphonsoPolicy", "Enforced: App cannot be stopped or uninstalled")

        } catch (e: Exception) {
            Log.e("AlphonsoPolicy", "Failed to enforce policies", e)
        }
    }
}