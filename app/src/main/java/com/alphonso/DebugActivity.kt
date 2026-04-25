package com.alphonso

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.room.Room
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DebugActivity : ComponentActivity() {

    private lateinit var database: FirebaseDatabase
    private lateinit var eventLogDao: EventLogDao
    private lateinit var activeLockoutDao: ActiveLockoutDao
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init Firebase safely
        try { database = FirebaseDatabase.getInstance() } catch (e: Exception) {}

        // Init Room
        val db = Room.databaseBuilder(applicationContext, EventLogDatabase::class.java, "event-log-database").fallbackToDestructiveMigration().build()
        eventLogDao = db.eventLogDao()
        activeLockoutDao = db.activeLockoutDao()

        setContent {
            MaterialTheme {
                // 1. LIVE LOGS: Collect the Flow directly from DAO
                val allLogs by eventLogDao.getAll().collectAsState(initial = emptyList())
                val activeLockouts by activeLockoutDao.getAll().collectAsState(initial = emptyList())

                // 2. STATE: Filters
                val selectedFilters = remember { mutableStateOf(emptySet<LogEventType>()) }

                // 4. FILTER LOGIC
                val filteredLogs = if (selectedFilters.value.isEmpty()) {
                    allLogs
                } else {
                    allLogs.filter {
                        try { selectedFilters.value.contains(LogEventType.valueOf(it.eventType)) }
                        catch (e: Exception) { false }
                    }
                }

                // 5. UI RENDER
                DebugScreenContent(
                    eventLog = filteredLogs,
                    activeLockouts = activeLockouts,
                    selectedFilters = selectedFilters.value,
                    onFilterChanged = { selectedFilters.value = it },
                    onClearLog = { activityScope.launch(Dispatchers.IO) { eventLogDao.clearAll() } },
                    onFlagFalsePositive = { id -> activityScope.launch(Dispatchers.IO) { eventLogDao.markAsFalsePositive(id) } },
                    onUndoFalsePositive = { id -> activityScope.launch(Dispatchers.IO) { eventLogDao.unmarkAsFalsePositive(id) } },
                    onRetrain = { /* Trigger logic */ },
                    onEmergencyUnlock = { packageName ->
                        activityScope.launch(Dispatchers.IO) {
                            try {
                                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                                val adminComponent = ComponentName(this@DebugActivity, ConsciousnessDeviceAdminReceiver::class.java)
                                dpm.setApplicationHidden(adminComponent, packageName, false)
                            } catch (e: Exception) { e.printStackTrace() }
                            activeLockoutDao.delete(packageName)
                        }
                    },

                )
            }
        }
    }
}

@Composable
fun DebugScreenContent(
    eventLog: List<EventLogEntity>,
    activeLockouts: List<ActiveLockoutEntity>,
    selectedFilters: Set<LogEventType>,
    onFilterChanged: (Set<LogEventType>) -> Unit,
    onClearLog: () -> Unit,
    onFlagFalsePositive: (Int) -> Unit,
    onUndoFalsePositive: (Int) -> Unit,
    onRetrain: () -> Unit,
    onEmergencyUnlock: (String) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { onClearLog(); Toast.makeText(context, "Logs Cleared", Toast.LENGTH_SHORT).show() }) {
                Text("Clear Logs")
            }
            Button(onClick = { onRetrain() }) { Text("Retrain AI") }
        }

        if (activeLockouts.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFEBEE)).padding(8.dp)) {
                Text("Active Lockouts", fontWeight = FontWeight.Bold, color = Color.Red)
                activeLockouts.forEach { lockout ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(lockout.packageName, fontSize = 12.sp)
                        Button(onClick = { onEmergencyUnlock(lockout.packageName); Toast.makeText(context, "Unlocked ${lockout.packageName}", Toast.LENGTH_SHORT).show() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                            Text("Emergency Unlock", fontSize = 10.sp)
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        // --- FILTER CHIPS ---
        LazyRow(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            items(LogEventType.values()) { type ->
                FilterChip(
                    selected = selectedFilters.contains(type),
                    onClick = {
                        val new = selectedFilters.toMutableSet()
                        if (new.contains(type)) new.remove(type) else new.add(type)
                        onFilterChanged(new)
                    },
                    label = { Text(type.name, fontSize = 10.sp) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        HorizontalDivider()

        // --- LOG LIST ---
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(eventLog) { log ->
                EventLogItem(log, onFlagFalsePositive, onUndoFalsePositive)
                HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun EventLogItem(log: EventLogEntity, onFlag: (Int) -> Unit, onUndo: (Int) -> Unit) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val type = try { LogEventType.valueOf(log.eventType) } catch (e: Exception) { LogEventType.SERVICE_EVENT }

    val bgColor = when {
        log.isFalsePositive -> Color(0xFFFFF9C4) // Yellow-ish
        type == LogEventType.APP_BLOCKED -> Color(0xFFFFCDD2) // Red-ish
        type == LogEventType.WARNING -> Color(0xFFFFE0B2) // Orange-ish
        type == LogEventType.AI_CANDIDATE -> Color(0xFFE0F7FA) // Cyan-ish
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier.fillMaxWidth().background(bgColor).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(text = "[${log.packageName}] ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Blue)
                Text(text = timeFormat.format(Date(log.timestamp)), fontSize = 11.sp, color = Color.Gray)
            }
            Text(text = "${log.eventType}: ${log.details}", fontSize = 13.sp)
            if (log.confidenceScore > 0) {
                Text(text = "Confidence: ${(log.confidenceScore * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Action Buttons for False Positives
        if (type in listOf(LogEventType.AI_CANDIDATE, LogEventType.WARNING, LogEventType.APP_BLOCKED)) {
            if (log.isFalsePositive) {
                Button(onClick = { onUndo(log.id) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                    Text("Undo", fontSize = 10.sp)
                }
            } else {
                OutlinedButton(onClick = { onFlag(log.id) }) {
                    Text("Mistake?", fontSize = 10.sp)
                }
            }
        }
    }
}