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

        // FIX: Ensure this matches the class name in your Manifest exactly
        val adminComponent = ComponentName(context, ConsciousnessDeviceAdminReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e("AlphonsoPolicy", "ERROR: Not Device Owner. Cannot enforce.")
            return
        }

        try {
            // 1. Prevent Uninstall
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

            // 2. Lock DNS
            val currentDns = dpm.getGlobalPrivateDnsMode(adminComponent)
            if (currentDns != DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME) {
                dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, CLOUDFLARE_DOT_HOSTNAME)
            }
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)

            // 3. Force Accessibility
            val serviceComponent = "${context.packageName}/.ConsciousnessAccessibilityService"
            dpm.setPermittedAccessibilityServices(adminComponent, listOf(context.packageName))
            dpm.setSecureSetting(adminComponent, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, serviceComponent)
            dpm.setSecureSetting(adminComponent, Settings.Secure.ACCESSIBILITY_ENABLED, "1")

            Log.i("AlphonsoPolicy", "All Policies Enforced Successfully")

        } catch (e: Exception) {
            Log.e("AlphonsoPolicy", "Policy Enforcement Failed", e)
        }
    }
}