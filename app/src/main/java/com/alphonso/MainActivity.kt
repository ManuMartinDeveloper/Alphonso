package com.alphonso

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.alphonso.ui.theme.AlphonsoTheme
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. TRY TO FORCE POLICIES IMMEDIATELY ON LAUNCH
        enforcePolicies(this)

        // Schedule Workers
        val workRequest = PeriodicWorkRequestBuilder<NightlyBatchWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NightlyBatchWorker.UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest
        )

        // Login Check
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContent {
            AlphonsoTheme {
                MainScreen(this)
            }
        }
    }

    // --- THE "GOD MODE" FUNCTION ---
    // This writes directly to the Android System Settings
    fun enforcePolicies(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, ConsciousnessDeviceAdminReceiver::class.java)
        val accessibilityService = ComponentName(context, ConsciousnessAccessibilityService::class.java)

        // Safety Check: Are we the Device Owner?
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e("Alphonso", "Not Device Owner. Cannot force settings.")
            return
        }

        try {
            // A. FORCE ACCESSIBILITY ON
            val currentServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            // Format: com.alphonso/.ConsciousnessAccessibilityService
            val serviceString = accessibilityService.flattenToString()

            if (!currentServices.contains(serviceString)) {
                val newServices = if (currentServices.isEmpty()) serviceString else "$currentServices:$serviceString"

                // Magic Command: Writes to secure settings without user permission
                dpm.setSecureSetting(
                    adminComponent,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )

                // Ensure the master toggle is ON
                dpm.setSecureSetting(
                    adminComponent,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    "1"
                )
                Toast.makeText(context, "Accessibility Forced ON", Toast.LENGTH_SHORT).show()
            }

            // B. LOCK THE APP (Prevent Force Stop / Uninstall)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)

        } catch (e: Exception) {
            Log.e("Alphonso", "Failed to enforce policies", e)
        }
    }
}

@Composable
fun MainScreen(activity: MainActivity) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Alphonso Security", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Button 1: Manual Force Enable
        Button(onClick = {
            activity.enforcePolicies(context)
            Toast.makeText(context, "Policies Applied", Toast.LENGTH_SHORT).show()
        }) {
            Text("Force Enable Protection")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button 2: Settings
        Button(onClick = {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }) {
            Text("Settings & Status")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button 3: Debug
        Button(onClick = {
            context.startActivity(Intent(context, DebugActivity::class.java))
        }) {
            Text("Debug Console")
        }
    }
}