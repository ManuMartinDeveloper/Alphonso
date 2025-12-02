package com.alphonso

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.provider.Settings
import android.util.Log

object PolicyManager {

    // Cloudflare Zero Trust / Family DNS Endpoint
    private const val CLOUDFLARE_DOT_HOSTNAME = "3d1e280bon.cloudflare-gateway.com"

    fun enforcePolicies(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, ConsciousnessDeviceAdminReceiver::class.java)

        // Safety Check
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e("AlphonsoPolicy", "Not Device Owner - Cannot enforce policies")
            return
        }

        try {
            // --- 1. ACCESSIBILITY PROTECTION (New Method) ---
            // Instead of forcing the switch (which crashes on Android 12+),
            // we set the "Permitted List". This locks the configuration.

            // Allow ONLY our service (and system services)
            val allowedServices = listOf(context.packageName)
            dpm.setPermittedAccessibilityServices(adminComponent, allowedServices)

            // Note: The user still has to turn it on ONCE manually.
            // But once on, 'DISALLOW_APPS_CONTROL' makes it hard to kill.

            // --- 2. FORCE NATIVE DNS ---
            try {
                dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, CLOUDFLARE_DOT_HOSTNAME)
                Log.i("AlphonsoPolicy", "DNS Locked to: $CLOUDFLARE_DOT_HOSTNAME")
            } catch (e: SecurityException) {
                Log.e("AlphonsoPolicy", "DNS Security Error", e)
            }

            // --- 3. LOCKDOWN ---
            // Prevent changing DNS manually
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)

            // Prevent Force Stop / Clear Data (This keeps the service alive)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)

            // Prevent Uninstall
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)

            Log.i("AlphonsoPolicy", "Policies Enforced")

        } catch (e: Exception) {
            Log.e("AlphonsoPolicy", "Critical Failure enforcing policies", e)
        }
    }
}