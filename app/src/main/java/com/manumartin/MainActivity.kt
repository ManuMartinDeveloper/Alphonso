package com.manumartin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import java.util.*

class MainActivity : ComponentActivity() {

    private val deviceAdminSample by lazy {
        ComponentName(this, ConsciousnessDeviceAdminReceiver::class.java)
    }
    
    private lateinit var database: FirebaseDatabase
    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = getSharedPreferences("AlphonsoPrefs", MODE_PRIVATE)
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            database = FirebaseDatabase.getInstance("https://alphonso-c7f69-default-rtdb.asia-southeast1.firebasedatabase.app")
        } catch (e: Exception) {
            Log.e("MainActivity", "Firebase Init Error", e)
            Toast.makeText(this, "Firebase Init Failed", Toast.LENGTH_SHORT).show()
        }

        setContent {
            MainScreen()
        }
    }
    
    @Composable
    fun MainScreen() {
        var disableUntil by remember { mutableLongStateOf(0L) }
        var timeRemaining by remember { mutableStateOf("") }
        val labelThresholds = remember { mutableStateMapOf<String, Float>() }
        var allowMobileData by remember { mutableStateOf(sharedPrefs.getBoolean("allowMobileDataBackup", false)) }

        LaunchedEffect(Unit) {
            if (::database.isInitialized) {
                val configRef = database.getReference("config")

                configRef.child("disableFilteringUntil").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val value = snapshot.getValue(Long::class.java)
                        if (value != null) {
                            disableUntil = value
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.w("MainActivity", "Failed to read disableFilteringUntil.", error.toException())
                    }
                })

                configRef.child("labelThresholds").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val remoteThresholds = snapshot.value as? Map<*, *>
                        remoteThresholds?.let {
                            for ((label, value) in it) {
                                if (label is String) {
                                    val floatValue = (value as? Double)?.toFloat()
                                    if (floatValue != null) {
                                        labelThresholds[label] = floatValue
                                    }
                                }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.w("MainActivity", "Failed to read labelThresholds.", error.toException())
                    }
                })
            }
        }
        
        LaunchedEffect(disableUntil) {
            while (true) {
                val now = System.currentTimeMillis()
                if (disableUntil > now) {
                    val diff = disableUntil - now
                    val hours = diff / (1000 * 60 * 60)
                    val minutes = (diff % (1000 * 60 * 60)) / (1000 * 60)
                    val seconds = (diff % (1000 * 60)) / 1000
                    timeRemaining = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    timeRemaining = ""
                }
                delay(1000)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { openAccessibilitySettings() }) {
                Text("Enable Accessibility Service")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { enableDeviceAdmin() }) {
                Text("Enable Device Admin")
            }
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(onClick = { requestDisableFilter() }) {
                Text("Request Disable Filter (1 Hour)")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { 
                if (::database.isInitialized) {
                     database.getReference("config").get().addOnSuccessListener { snapshot ->
                         val disableValue = snapshot.child("disableFilteringUntil").getValue(Long::class.java)
                         if (disableValue != null) {
                             disableUntil = disableValue
                         }
                        val remoteThresholds = snapshot.child("labelThresholds").value as? Map<*, *>
                        remoteThresholds?.let {
                            for ((label, value) in it) {
                                if (label is String) {
                                    val floatValue = (value as? Double)?.toFloat()
                                    if (floatValue != null) {
                                        labelThresholds[label] = floatValue
                                    }
                                }
                            }
                        }
                         Toast.makeText(this@MainActivity, "Updated status", Toast.LENGTH_SHORT).show()
                     }
                }
            }) {
                Text("Refresh Status")
            }
            
            if (timeRemaining.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Filter Disabled For: $timeRemaining")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow Backup on Mobile Data")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = allowMobileData,
                    onCheckedChange = {
                        allowMobileData = it
                        sharedPrefs.edit().putBoolean("allowMobileDataBackup", it).apply()
                        NightlyBatchWorker.schedule(applicationContext, it)
                        Toast.makeText(applicationContext, "Settings updated", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                item { 
                    Text("Label Sensitivities:", modifier = Modifier.padding(bottom = 8.dp))
                }
                items(ConsciousnessAccessibilityService.SENSITIVE_LABELS.toList()) { label ->
                    val threshold = labelThresholds[label] ?: 0.75f
                    Text(String.format(Locale.getDefault(), "  %s: %.0f%%", label, threshold * 100))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { openDebugLog() }) {
                Text("Show Debug Log")
            }
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun enableDeviceAdmin() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isAdminActive(deviceAdminSample)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminSample)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable Device Admin to protect your device.")
            startActivity(intent)
        } else {
            Toast.makeText(this, "Device Admin is already enabled.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestDisableFilter() {
        if (!::database.isInitialized) {
            Toast.makeText(this, "Database not connected", Toast.LENGTH_SHORT).show()
            return
        }
        
        val requestsRef = database.getReference("filter_requests")
        val request = mapOf(
            "timestamp" to ServerValue.TIMESTAMP,
            "type" to "disable_filter",
            "device" to (Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown")
        )
        requestsRef.push().setValue(request)
            .addOnSuccessListener { 
                Toast.makeText(this, "Request sent to admin", Toast.LENGTH_SHORT).show() 
            }
            .addOnFailureListener { 
                Toast.makeText(this, "Failed to send request", Toast.LENGTH_SHORT).show() 
            }
    }

    private fun openDebugLog() {
        val intent = Intent(this, DebugActivity::class.java)
        startActivity(intent)
    }
}
