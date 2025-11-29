package com.manumartin

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen()
        }
    }

    @Composable
    fun SettingsScreen() {
        val context = LocalContext.current

        var remoteDisabledUntil by remember { mutableLongStateOf(0L) }
        var isCensorGloballyDisabled by remember { mutableStateOf(false) }
        var isGlobalLockoutEnabled by remember { mutableStateOf(false) }
        var timeRemaining by remember { mutableStateOf("Active") }

        var serviceRequestPending by remember { mutableStateOf(false) }
        var censorRequestPending by remember { mutableStateOf(false) }
        var lockoutRequestPending by remember { mutableStateOf(false) }

        val thresholds = remember { mutableStateMapOf<String, String>() }
        val behaviorSettings = remember { mutableStateMapOf<String, String>() }
        val blocklist = remember { mutableStateListOf<String>() }

        val db = FirebaseDatabase.getInstance("https://alphonso-c7f69-default-rtdb.asia-southeast1.firebasedatabase.app")

        DisposableEffect(Unit) {
            val remoteSettingsRef = db.getReference("remote_settings")
            val disableListener = remoteSettingsRef.child("filtering_disabled_until").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { remoteDisabledUntil = snapshot.getValue(Long::class.java) ?: 0L }
                override fun onCancelled(error: DatabaseError) {}
            })
            val censorListener = remoteSettingsRef.child("censor_globally_disabled").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { isCensorGloballyDisabled = snapshot.getValue(Boolean::class.java) ?: false }
                override fun onCancelled(error: DatabaseError) {}
            })
            val lockoutListener = remoteSettingsRef.child("global_lockout_enabled").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { isGlobalLockoutEnabled = snapshot.getValue(Boolean::class.java) ?: false }
                override fun onCancelled(error: DatabaseError) {}
            })

            val requestsRef = db.getReference("requests")
            val serviceRequestListener = requestsRef.child("service_status_request").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { serviceRequestPending = snapshot.exists() }
                override fun onCancelled(error: DatabaseError) {}
            })
            val censorRequestListener = requestsRef.child("censor_status_request").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { censorRequestPending = snapshot.exists() }
                override fun onCancelled(error: DatabaseError) {}
            })
            val lockoutRequestListener = requestsRef.child("lockout_status_request").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { lockoutRequestPending = snapshot.exists() }
                override fun onCancelled(error: DatabaseError) {}
            })

            val configRef = db.getReference("config")
            // ... (rest of the listeners are the same)

            onDispose {
                remoteSettingsRef.child("filtering_disabled_until").removeEventListener(disableListener)
                remoteSettingsRef.child("censor_globally_disabled").removeEventListener(censorListener)
                remoteSettingsRef.child("global_lockout_enabled").removeEventListener(lockoutListener)
                requestsRef.child("service_status_request").removeEventListener(serviceRequestListener)
                requestsRef.child("censor_status_request").removeEventListener(censorRequestListener)
                requestsRef.child("lockout_status_request").removeEventListener(lockoutRequestListener)
                // ...
            }
        }

        LaunchedEffect(remoteDisabledUntil) {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (remoteDisabledUntil > now) {
                    val diff = remoteDisabledUntil - now
                    val hours = TimeUnit.MILLISECONDS.toHours(diff)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                    timeRemaining = String.format("Paused (resumes in %d hr, %d min)", hours, minutes)
                } else {
                    timeRemaining = "System Active"
                }
                delay(1000)
            }
        }

        Scaffold(topBar = { }) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

                item {
                    Text("System Status", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    StatusCard(
                        title = "Operational Status",
                        status = timeRemaining,
                        isNormal = timeRemaining == "System Active",
                        modifier = Modifier.fillMaxWidth(),
                        requestButtonText = if (timeRemaining == "System Active") "Request Pause" else "Request Resume",
                        requestPending = serviceRequestPending,
                        onRequest = {
                            db.getReference("requests/service_status_request").setValue(System.currentTimeMillis())
                            Toast.makeText(context, "Request sent.", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusCard(
                            title = "Censoring Blocks",
                            status = if(isCensorGloballyDisabled) "Disabled" else "Enabled",
                            isNormal = !isCensorGloballyDisabled,
                            modifier = Modifier.weight(1f),
                            requestButtonText = if (isCensorGloballyDisabled) "Request Enable" else "Request Disable",
                            requestPending = censorRequestPending,
                            onRequest = {
                                db.getReference("requests/censor_status_request").setValue(System.currentTimeMillis())
                                Toast.makeText(context, "Request sent.", Toast.LENGTH_SHORT).show()
                            }
                        )
                        StatusCard(
                            title = "Global Lockout",
                            status = if(isGlobalLockoutEnabled) "ACTIVE" else "Inactive",
                            isNormal = !isGlobalLockoutEnabled,
                            modifier = Modifier.weight(1f),
                            requestButtonText = if (isGlobalLockoutEnabled) "Request End" else "Request Lockout",
                            requestPending = lockoutRequestPending,
                            onRequest = {
                                db.getReference("requests/lockout_status_request").setValue(System.currentTimeMillis())
                                Toast.makeText(context, "Request sent.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // ... (rest of the screen is the same)
            }
        }
    }

    @Composable
    private fun StatusCard(
        title: String, status: String, isNormal: Boolean, modifier: Modifier = Modifier,
        requestButtonText: String, requestPending: Boolean, onRequest: () -> Unit
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isNormal) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
            modifier = modifier
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(text = status, fontSize = 20.sp, color = if (isNormal) Color(0xFF388E3C) else Color(0xFFD32F2F))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequest, 
                    enabled = !requestPending,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (requestPending) "Request Sent" else requestButtonText)
                }
            }
        }
    }
    // ... (rest of the composables are the same)
}
