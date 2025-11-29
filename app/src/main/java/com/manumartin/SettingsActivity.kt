package com.manumartin

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        val prefs = context.getSharedPreferences("AlphonsoPrefs", Context.MODE_PRIVATE)

        var censorEnabled by remember { mutableStateOf(prefs.getBoolean("censor_view_enabled", true)) }
        var remoteDisabledUntil by remember { mutableLongStateOf(0L) }
        var timeRemaining by remember { mutableStateOf("Active") }
        val thresholds = remember { mutableStateMapOf<String, String>() }
        val behaviorSettings = remember { mutableStateMapOf<String, String>() }

        DisposableEffect(Unit) {
            val db = FirebaseDatabase.getInstance("https://alphonso-c7f69-default-rtdb.asia-southeast1.firebasedatabase.app")

            val disableRef = db.getReference("remote_settings/filtering_disabled_until")
            val disableListener = disableRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    remoteDisabledUntil = snapshot.getValue(Long::class.java) ?: 0L
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            val threshRef = db.getReference("config/thresholds")
            val threshListener = threshRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    thresholds.clear()
                    snapshot.children.forEach { child ->
                        val labelName = child.key
                        val rawValue = child.value
                        val value = when (rawValue) {
                            is Long -> rawValue.toFloat()
                            is Double -> rawValue.toFloat()
                            else -> null
                        }
                        if (labelName != null && value != null) {
                            thresholds[labelName] = "${(value * 100).toInt()}%"
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            val behaviorRef = db.getReference("config/behavior")
            val behaviorListener = behaviorRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    behaviorSettings.clear()
                    snapshot.child("lockoutDurationMinutes").getValue(Long::class.java)?.let { behaviorSettings["Lockout Duration"] = "$it minutes" }
                    snapshot.child("strikeLimit").getValue(Int::class.java)?.let { behaviorSettings["Strike Limit"] = "$it strikes" }
                    snapshot.child("scanDelayNormal").getValue(Long::class.java)?.let { behaviorSettings["Normal Scan Speed"] = "${it}ms" }
                    snapshot.child("scanDelayAlert").getValue(Long::class.java)?.let { behaviorSettings["Alert Scan Speed"] = "${it}ms" }
                    snapshot.child("strikeResetWindowMs").getValue(Long::class.java)?.let { behaviorSettings["Strike Reset Window"] = "${it / 1000}s" }
                    snapshot.child("prayerText").getValue(String::class.java)?.let { behaviorSettings["Prayer Text"] = it }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            onDispose {
                disableRef.removeEventListener(disableListener)
                threshRef.removeEventListener(threshListener)
                behaviorRef.removeEventListener(behaviorListener)
            }
        }

        LaunchedEffect(remoteDisabledUntil) {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (remoteDisabledUntil > now) {
                    val diff = remoteDisabledUntil - now
                    val hours = TimeUnit.MILLISECONDS.toHours(diff)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60
                    timeRemaining = String.format("Paused: %02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    timeRemaining = "System Active"
                }
                delay(1000)
            }
        }

        Scaffold(
            topBar = { }
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

                item {
                    Text("System Status", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (timeRemaining == "System Active") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Operational Status", fontWeight = FontWeight.Bold)
                            Text(text = timeRemaining, fontSize = 20.sp, color = if (timeRemaining == "System Active") Color(0xFF4CAF50) else Color(0xFFF44336))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Global Lockdown Blackout", fontWeight = FontWeight.Bold)
                            Text("Black out entire screen on 5th strike.", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = censorEnabled,
                            onCheckedChange = {
                                censorEnabled = it
                                prefs.edit().putBoolean("censor_view_enabled", it).apply()
                                Toast.makeText(context, "Restart Service to apply", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Text("Remote Behavior (Read-Only)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(behaviorSettings.toList().sortedBy { it.first }) { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, fontSize = 14.sp)
                        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start=8.dp))
                    }
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)); HorizontalDivider(); Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    Text("Remote Sensitivity (Read-Only)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(thresholds.toList().sortedBy { it.first }) { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, fontSize = 14.sp)
                        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}