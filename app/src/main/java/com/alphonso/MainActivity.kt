package com.alphonso

import android.content.Intent
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope // REQUIRED IMPORT
import com.alphonso.ui.theme.AlphonsoTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers // REQUIRED IMPORT
import kotlinx.coroutines.launch // REQUIRED IMPORT
import kotlinx.coroutines.withContext // REQUIRED IMPORT

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. FIX: Run heavy Policy Enforcement in Background Thread
        lifecycleScope.launch(Dispatchers.IO) {
            PolicyManager.enforcePolicies(applicationContext)
            withContext(Dispatchers.Main) {
                // Optional: Toast removed to keep startup clean
            }
        }

        // 2. Start the "Unkillable" Watchdog
        startForegroundService(Intent(this, AppMonitorService::class.java))

        // 3. REMOVED: NightlyBatchWorker (As requested)
        // TODO: Re-implement NightlyBatchWorker for daily maintenance later.

        // 4. Login Check
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContent {
            AlphonsoTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Alphonso Security", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Status: Protected", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(32.dp))

        // "Force Lock" Button
        Button(
            onClick = {
                // Run in background to avoid freezing the button
                // (Note: To do this properly in Compose, you'd use a CoroutineScope,
                // but for a quick fix, this is okay or use the service)
                context.startForegroundService(Intent(context, AppMonitorService::class.java))
                Toast.makeText(context, "Policies Refreshing...", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Force Lock Policies")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }) {
            Text("Settings")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            context.startActivity(Intent(context, DebugActivity::class.java))
        }) {
            Text("Debug Console")
        }
    }
}