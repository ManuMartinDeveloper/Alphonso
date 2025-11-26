package com.manumartin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val deviceAdminSample by lazy {
        ComponentName(this, ConsciousnessDeviceAdminReceiver::class.java)
    }
    
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        var disableUntil by remember { mutableStateOf(0L) }
        var timeRemaining by remember { mutableStateOf("") }
        var highConfidenceThreshold by remember { mutableStateOf(0.75f) } // Add this

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

                // Add this listener
                configRef.child("highConfidenceThreshold").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val value = snapshot.getValue(Double::class.java)?.toFloat()
                        if (value != null && value in 0f..1f) {
                            highConfidenceThreshold = value
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.w("MainActivity", "Failed to read highConfidenceThreshold.", error.toException())
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
                    timeRemaining = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    timeRemaining = ""
                }
                delay(1000)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
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
                // Manual Fetch Button
                if (::database.isInitialized) {
                     database.getReference("config").get().addOnSuccessListener { snapshot ->
                         val disableValue = snapshot.child("disableFilteringUntil").getValue(Long::class.java)
                         if (disableValue != null) {
                             disableUntil = disableValue
                         }
                         val thresholdValue = snapshot.child("highConfidenceThreshold").getValue(Double::class.java)?.toFloat()
                         if (thresholdValue != null && thresholdValue in 0f..1f) {
                             highConfidenceThreshold = thresholdValue
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

            // Add this text
            Spacer(modifier = Modifier.height(16.dp))
            Text(String.format("High Confidence Detection Threshold: %.2f", highConfidenceThreshold*100))
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun enableDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
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
}
