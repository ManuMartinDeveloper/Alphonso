import re

# We will create a PermissionsActivity
activity_code = """package com.alphonso

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class PermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PermissionsScreen()
            }
        }
    }
}

@Composable
fun PermissionsScreen() {
    val context = LocalContext.current
    var isAccessibilityGranted by remember { mutableStateOf(checkAccessibilityPermission(context)) }
    var isDeviceAdminGranted by remember { mutableStateOf(checkDeviceAdminPermission(context)) }
    var isOverlayGranted by remember { mutableStateOf(checkOverlayPermission(context)) }

    // Periodically re-check permissions
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityGranted = checkAccessibilityPermission(context)
            isDeviceAdminGranted = checkDeviceAdminPermission(context)
            isOverlayGranted = checkOverlayPermission(context)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Required Permissions", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

        PermissionRow(
            title = "Accessibility Service",
            description = "Required to monitor screen text and activity.",
            isGranted = isAccessibilityGranted,
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionRow(
            title = "Device Admin (Device Owner)",
            description = "Required to enforce lockouts and policies. Must be set via ADB.",
            isGranted = isDeviceAdminGranted,
            onClick = { /* Device owner usually cannot be set via simple intent, user must use adb */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionRow(
            title = "Display Over Other Apps",
            description = "Required to show the censor overlay.",
            isGranted = isOverlayGranted,
            onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
        )
    }
}

@Composable
fun PermissionRow(title: String, description: String, isGranted: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (isGranted) "GRANTED" else "MISSING", color = if (isGranted) Color.Green else Color.Red, fontWeight = FontWeight.Bold)
            }
            Text(description, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            if (!isGranted) {
                Button(onClick = onClick) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

fun checkAccessibilityPermission(context: Context): Boolean {
    var isEnabled = false
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    for (service in enabledServices) {
        if (service.resolveInfo.serviceInfo.packageName == context.packageName) {
            isEnabled = true
            break
        }
    }
    return isEnabled
}

fun checkDeviceAdminPermission(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, ConsciousnessDeviceAdminReceiver::class.java)
    return dpm.isAdminActive(adminComponent)
}

fun checkOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}
"""

with open('app/src/main/java/com/alphonso/PermissionsActivity.kt', 'w') as f:
    f.write(activity_code)

# Register in AndroidManifest.xml
with open('app/src/main/AndroidManifest.xml', 'r') as f:
    manifest = f.read()

manifest = manifest.replace(
    '<activity android:name=".DebugActivity" android:exported="false" />',
    '<activity android:name=".DebugActivity" android:exported="false" />\n        <activity android:name=".PermissionsActivity" android:exported="false" />'
)

with open('app/src/main/AndroidManifest.xml', 'w') as f:
    f.write(manifest)

# Add button in MainActivity to go to permissions
with open('app/src/main/java/com/alphonso/MainActivity.kt', 'r') as f:
    main_activity = f.read()

button_code = """        Button(onClick = {
            try { context.startActivity(Intent(context, PermissionsActivity::class.java)) } catch (e: Exception) { e.printStackTrace() }
        }) {
            Text("Permissions Status")
        }

        Spacer(modifier = Modifier.height(16.dp))"""

main_activity = main_activity.replace(
    'Button(onClick = {\n            try { context.startActivity(Intent(context, SettingsActivity::class.java)) } catch (e: Exception) { e.printStackTrace() }\n        }) {\n            Text("Settings")\n        }',
    button_code + '\n\n        Button(onClick = {\n            try { context.startActivity(Intent(context, SettingsActivity::class.java)) } catch (e: Exception) { e.printStackTrace() }\n        }) {\n            Text("Settings")\n        }'
)

with open('app/src/main/java/com/alphonso/MainActivity.kt', 'w') as f:
    f.write(main_activity)
