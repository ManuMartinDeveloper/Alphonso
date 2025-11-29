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

            val configRef = db.getReference("config")
            val threshListener = configRef.child("thresholds").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    thresholds.clear()
                    snapshot.children.forEach { child ->
                        val labelName = child.key
                        val rawValue = child.value
                        val value = when (rawValue) {
                            is Long -> rawValue.toFloat(); is Double -> rawValue.toFloat(); else -> null
                        }
                        if (labelName != null && value != null) { thresholds[labelName] = "${(value * 100).toInt()}%" }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            val behaviorListener = configRef.child("behavior").addValueEventListener(object : ValueEventListener {
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

            val blocklistListener = configRef.child("blocklist").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    blocklist.clear()
                    snapshot.children.forEach { child ->
                        child.getValue(String::class.java)?.let { blocklist.add(it) }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })


            onDispose {
                remoteSettingsRef.child("filtering_disabled_until").removeEventListener(disableListener)
                remoteSettingsRef.child("censor_globally_disabled").removeEventListener(censorListener)
                remoteSettingsRef.child("global_lockout_enabled").removeEventListener(lockoutListener)
                requestsRef.child("service_status_request").removeEventListener(serviceRequestListener)
                configRef.child("thresholds").removeEventListener(threshListener)
                configRef.child("behavior").removeEventListener(behaviorListener)
                configRef.child("blocklist").removeEventListener(blocklistListener)
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
                        buttonText = if (serviceRequestPending) "Request Sent" else if (timeRemaining == "System Active") "Request Pause" else "Request Resume",
                        buttonEnabled = !serviceRequestPending,
                        onButtonClick = {
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
                            buttonText = if (isCensorGloballyDisabled) "Enable" else "Disable",
                            onButtonClick = {
                                val newValue = !isCensorGloballyDisabled
                                db.getReference("remote_settings/censor_globally_disabled").setValue(newValue)
                                Toast.makeText(context, "Censoring ${if(newValue) "disabled" else "enabled"}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        StatusCard(
                            title = "Global Lockout",
                            status = if(isGlobalLockoutEnabled) "ACTIVE" else "Inactive",
                            isNormal = !isGlobalLockoutEnabled,
                            modifier = Modifier.weight(1f),
                            buttonText = if (isGlobalLockoutEnabled) "End Lockout" else "Start Lockout",
                            onButtonClick = {
                                val newValue = !isGlobalLockoutEnabled
                                db.getReference("remote_settings/global_lockout_enabled").setValue(newValue)
                                Toast.makeText(context, "Global Lockout ${if(newValue) "started" else "ended"}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item { BlocklistSection(blocklist.sorted()) }
                item { Spacer(modifier = Modifier.height(24.dp)); HorizontalDivider(); Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    Text("Remote Behavior (Read-Only)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(behaviorSettings.toList().sortedBy { it.first }) { (label, value) ->
                    SettingsRow(label, value)
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)); HorizontalDivider(); Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    Text("Remote Sensitivity (Read-Only)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(thresholds.toList().sortedBy { it.first }) { (label, value) ->
                    SettingsRow(label, value)
                }
            }
        }
    }

    @Composable
    private fun StatusCard(
        title: String,
        status: String,
        isNormal: Boolean,
        modifier: Modifier = Modifier,
        buttonText: String,
        buttonEnabled: Boolean = true,
        onButtonClick: () -> Unit
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
                    onClick = onButtonClick,
                    enabled = buttonEnabled,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(buttonText)
                }
            }
        }
    }

    @Composable
    private fun SettingsRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 14.sp)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start=8.dp))
        }
    }

    @Composable
    fun BlocklistSection(blocklist: List<String>) {
        var isExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Keyword Blocklist (${blocklist.size} items)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    if (blocklist.isEmpty()) {
                        Text("No keywords in blocklist.", modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(blocklist) { keyword ->
                                Text(text = keyword, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
