package com.manumartin

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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DebugActivity : ComponentActivity() {

    private lateinit var database: FirebaseDatabase
    private lateinit var eventLogDao: EventLogDao
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            database = FirebaseDatabase.getInstance("https://alphonso-c7f69-default-rtdb.asia-southeast1.firebasedatabase.app")
        } catch (e: Exception) {}

        val db = Room.databaseBuilder(
            applicationContext,
            EventLogDatabase::class.java, "event-log-database"
        ).build()
        eventLogDao = db.eventLogDao()

        setContent {
            val logs = remember { mutableStateOf<List<EventLogEntity>>(emptyList()) }
            val selectedFilters = remember { mutableStateOf(emptySet<LogEventType>()) }

            val workManager = WorkManager.getInstance(applicationContext)
            val workInfo by workManager.getWorkInfosForUniqueWorkLiveData("NightlyBatchWork").observeAsState()

            val currentWorkInfo = workInfo?.firstOrNull()
            val trainingState = currentWorkInfo?.state
            val trainingProgress = currentWorkInfo?.progress?.getString("KEY_PROGRESS")

            fun refreshLogs() {
                activityScope.launch {
                    val allLogs = withContext(Dispatchers.IO) { eventLogDao.getAll() }
                    logs.value = allLogs
                }
            }

            LaunchedEffect(Unit, trainingState) {
                if (trainingState == WorkInfo.State.SUCCEEDED) {
                    refreshLogs()
                }
                refreshLogs()
            }

            val filteredLogs = if (selectedFilters.value.isEmpty()) {
                logs.value
            } else {
                logs.value.filter {
                    try {
                        selectedFilters.value.contains(LogEventType.valueOf(it.eventType))
                    } catch (e: Exception) { false }
                }
            }

            DebugScreen(
                eventLog = filteredLogs,
                selectedFilters = selectedFilters.value,
                onFilterChanged = { selectedFilters.value = it },
                onClearLog = {
                    activityScope.launch(Dispatchers.IO) {
                        eventLogDao.clearAll()
                        try { database.getReference("incidents").removeValue() } catch (e: Exception) {}
                        withContext(Dispatchers.Main) { logs.value = emptyList() }
                    }
                },
                onFlagFalsePositive = { logId ->
                    activityScope.launch(Dispatchers.IO) {
                        eventLogDao.markAsFalsePositive(logId)
                        withContext(Dispatchers.Main) { refreshLogs() }
                    }
                },
                onUndoFalsePositive = { logId ->
                    activityScope.launch(Dispatchers.IO) {
                        eventLogDao.unmarkAsFalsePositive(logId)
                        withContext(Dispatchers.Main) { refreshLogs() }
                    }
                },
                onRetrain = {
                    // Manual trigger logic would go here
                },
                trainingState = trainingState,
                trainingProgress = trainingProgress
            )
        }
    }
}

@Composable
fun DebugScreen(
    eventLog: List<EventLogEntity>,
    selectedFilters: Set<LogEventType>,
    onFilterChanged: (Set<LogEventType>) -> Unit,
    onClearLog: () -> Unit,
    onFlagFalsePositive: (Int) -> Unit,
    onUndoFalsePositive: (Int) -> Unit,
    onRetrain: () -> Unit,
    trainingState: WorkInfo.State?,
    trainingProgress: String?
) {
    val context = LocalContext.current

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                onClearLog()
                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
            }) {
                Text("Clear Logs")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trainingState == WorkInfo.State.RUNNING || trainingState == WorkInfo.State.ENQUEUED) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).height(24.dp))
                }
                Button(onClick = {
                    onRetrain()
                    Toast.makeText(context, "Training job enqueued...", Toast.LENGTH_SHORT).show()
                }, enabled = trainingState != WorkInfo.State.RUNNING && trainingState != WorkInfo.State.ENQUEUED) {
                    Text("Retrain AI")
                }
            }
        }

        if (trainingState == WorkInfo.State.RUNNING || trainingState == WorkInfo.State.ENQUEUED) {
            val progressText = trainingProgress ?: if (trainingState == WorkInfo.State.ENQUEUED) "Waiting for conditions..." else "Starting..."
            Text(progressText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp)
        }

        LazyRow(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            items(LogEventType.values()) { type ->
                FilterChip(
                    selected = selectedFilters.contains(type),
                    onClick = {
                        val newFilters = selectedFilters.toMutableSet()
                        if (newFilters.contains(type)) newFilters.remove(type) else newFilters.add(type)
                        onFilterChanged(newFilters)
                    },
                    label = { Text(type.name) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(eventLog) { log ->
                EventLogItem(log, onFlagFalsePositive, onUndoFalsePositive)
            }
        }
    }
}

@Composable
fun EventLogItem(
    log: EventLogEntity,
    onFlagFalsePositive: (Int) -> Unit,
    onUndoFalsePositive: (Int) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss dd-MM", Locale.getDefault())
    val eventType = try { LogEventType.valueOf(log.eventType) } catch (e: Exception) { LogEventType.SERVICE_EVENT }

    val color = when {
        log.isFalsePositive -> Color.Yellow.copy(alpha=0.4f)
        eventType == LogEventType.AI_CANDIDATE -> Color.Cyan.copy(alpha=0.15f)
        eventType == LogEventType.WARNING -> Color.Yellow.copy(alpha=0.2f)
        eventType == LogEventType.APP_BLOCKED -> Color.Red.copy(alpha=0.3f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(3f)) {
            Text("[${log.packageName}]", fontSize = 12.sp, color = Color.Blue, fontWeight = FontWeight.Bold)
            Text(log.details, fontSize = 14.sp)

            if (log.confidenceScore > 0) {
                Text(
                    text = "Confidence: ${(log.confidenceScore * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = if(log.confidenceScore > 0.5) Color.Magenta else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(timeFormat.format(Date(log.timestamp)), fontSize = 10.sp, color = Color.Gray)
        }

        // *** LOGIC TO SHOW/HIDE BUTTONS ***
        if (eventType == LogEventType.AI_CANDIDATE || eventType == LogEventType.WARNING || eventType == LogEventType.APP_BLOCKED) {
            Spacer(modifier = Modifier.width(8.dp))

            if (log.isFalsePositive) {
                Button(
                    onClick = { onUndoFalsePositive(log.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Undo")
                }
            } else {
                Button(onClick = { onFlagFalsePositive(log.id) }) {
                    Text("Flag Mistake")
                }
            }
        }
    }
}
