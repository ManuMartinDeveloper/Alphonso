// Updated PolicyManager.kt
package com.alphonso

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.provider.Settings
import android.util.Log

object PolicyManager {

    private const val CLOUDFLARE_DOT_HOSTNAME = "3d1e280bon.cloudflare-gateway.com"

    fun enforcePolicies(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, ConsciousnessDeviceAdminReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e("AlphonsoPolicy", "ERROR: Not Device Owner. Cannot enforce.")
            return
        }

        try {
            // 1. Prevent Uninstall / Force Stop
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

            // 2. Lock DNS (System Level)
            // Checks if it's already set to avoid system lag
            val currentDns = dpm.getGlobalPrivateDnsMode(adminComponent)
            if (currentDns != DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME) {
                dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, CLOUDFLARE_DOT_HOSTNAME)
            }
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)

            // 3. FORCE-ENABLE ACCESSIBILITY (The "God Mode" Fix)
            val serviceComponent = "${context.packageName}/.ConsciousnessAccessibilityService"

            // Allow our service
            dpm.setPermittedAccessibilityServices(adminComponent, listOf(context.packageName))

            // Force turn it ON
            dpm.setSecureSetting(adminComponent, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, serviceComponent)
            dpm.setSecureSetting(adminComponent, Settings.Secure.ACCESSIBILITY_ENABLED, "1")

            Log.i("AlphonsoPolicy", "All Policies Enforced Successfully")

        } catch (e: Exception) {
            Log.e("AlphonsoPolicy", "Policy Enforcement Failed", e)
        }
    }
}