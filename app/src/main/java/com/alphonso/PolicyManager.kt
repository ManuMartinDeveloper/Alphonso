package com.alphonso

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
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
            // 1. Prevent Uninstall & App Control
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            // Optional: Prevent user from clearing data or force-stopping
            // dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)

            // 2. Lock DNS (Network Filter)
            val currentDns = dpm.getGlobalPrivateDnsMode(adminComponent)
            if (currentDns != DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME) {
                dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, CLOUDFLARE_DOT_HOSTNAME)
            }
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)

            // 3. Accessibility "Lock" (whitelist)
            // We CANNOT force-enable it (SecurityException), but we CAN prevent other apps from using it.
            // This ensures that IF accessibility is on, it must be US.
            dpm.setPermittedAccessibilityServices(adminComponent, listOf(context.packageName))

            // REMOVED: setSecureSetting calls that cause the crash.

            Log.i("AlphonsoPolicy", "All Policies Enforced Successfully")

        } catch (e: Exception) {
            Log.e("AlphonsoPolicy", "Policy Enforcement Failed", e)
        }
    }
}