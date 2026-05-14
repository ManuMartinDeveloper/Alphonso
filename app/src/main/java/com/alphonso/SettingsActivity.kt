package com.alphonso

import android.os.Bundle
import android.util.Log
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
        var timeRemaining by remember { mutableStateOf("Active") }

        var serviceRequestPending by remember { mutableStateOf(false) }

        val thresholds = remember { mutableStateMapOf<String, String>() }
        val behaviorSettings = remember { mutableStateMapOf<String, String>() }
        val blocklist = remember { mutableStateListOf<String>() }

        val db = FirebaseDatabase.getInstance()

        DisposableEffect(Unit) {
            val remoteSettingsRef = db.getReference("remote_settings")
            val remoteSettingsListener = remoteSettingsRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    behaviorSettings.clear()
                    
                    remoteDisabledUntil = snapshot.child("filtering_disabled_until").getValue(Long::class.java) ?: 0L
                    isCensorGloballyDisabled = snapshot.child("censor_globally_disabled").getValue(Boolean::class.java) ?: false
                    
                    snapshot.child("lockout_duration_minutes").getValue(Long::class.java)?.let { behaviorSettings["Lockout Duration"] = "$it minutes" }
                    snapshot.child("strike_limit").getValue(Long::class.java)?.let { behaviorSettings["Strike Limit"] = "$it strikes" }
                    snapshot.child("scan_delay_normal").getValue(Long::class.java)?.let { behaviorSettings["Normal Scan Speed"] = "${it}ms" }
                    snapshot.child("scan_delay_alert").getValue(Long::class.java)?.let { behaviorSettings["Alert Scan Speed"] = "${it}ms" }
                    snapshot.child("prayer_text").getValue(String::class.java)?.let { behaviorSettings["Prayer Text"] = it }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            val sensitivityRef = db.getReference("category_sensitivity")
            val sensitivityListener = sensitivityRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    thresholds.clear()
                    val defaultThresh = snapshot.child("default").getValue(Double::class.java) ?: 0.50
                    thresholds["Default Threshold"] = "${(defaultThresh * 100).toInt()}%"
                    
                    snapshot.children.forEach { child ->
                        if (child.key != "default") {
                            val name = child.child("name").getValue(String::class.java) ?: "Category ${child.key}"
                            val thresh = child.child("threshold").getValue(Double::class.java) ?: defaultThresh
                            thresholds[name] = "${(thresh * 100).toInt()}%"
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            val configRef = db.getReference("config")
            val blocklistListener = configRef.child("blocklist").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    blocklist.clear()
                    snapshot.children.forEach { child ->
                        child.getValue(String::class.java)?.let { blocklist.add(it) }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            val requestsRef = db.getReference("requests")
            val serviceRequestListener = requestsRef.child("service_status_request").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { serviceRequestPending = snapshot.exists() }
                override fun onCancelled(error: DatabaseError) {}
            })

            onDispose {
                remoteSettingsRef.removeEventListener(remoteSettingsListener)
                sensitivityRef.removeEventListener(sensitivityListener)
                configRef.child("blocklist").removeEventListener(blocklistListener)
                requestsRef.child("service_status_request").removeEventListener(serviceRequestListener)
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

        Scaffold { padding ->
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

                    StatusCard(
                        title = "Censoring Blocks",
                        status = if(isCensorGloballyDisabled) "Disabled" else "Enabled",
                        isNormal = !isCensorGloballyDisabled,
                        modifier = Modifier.fillMaxWidth(),
                        buttonText = if (isCensorGloballyDisabled) "Enable" else "Disable",
                        onButtonClick = {
                            val newValue = !isCensorGloballyDisabled
                            db.getReference("remote_settings/censor_globally_disabled").setValue(newValue)
                            Toast.makeText(context, "Censoring ${if(newValue) "disabled" else "enabled"}", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item { BlocklistSection(blocklist.sorted()) }
                
                item { 
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Remote Behavior (Live)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                
                items(behaviorSettings.toList().sortedBy { it.first }) { (label, value) ->
                    SettingsRow(label, value)
                }
                
                item { 
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Remote Sensitivity (AI Labels)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                
                items(thresholds.toList().sortedBy { it.first }) { (label, value) ->
                    SettingsRow(label, value)
                }
                
                item { Spacer(modifier = Modifier.height(40.dp)) }
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
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
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
                        Column {
                            blocklist.take(10).forEach { keyword ->
                                Text(text = keyword, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                                HorizontalDivider()
                            }
                            if (blocklist.size > 10) {
                                Text("... and ${blocklist.size - 10} more", modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
