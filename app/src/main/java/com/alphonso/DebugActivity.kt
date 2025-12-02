package com.alphonso

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DebugActivity : ComponentActivity() {

    private lateinit var db: EventLogDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(
            applicationContext,
            EventLogDatabase::class.java, "event-log-database"
        ).build()

        setContent {
            MaterialTheme {
                DebugScreen(db)
            }
        }
    }
}

@Composable
fun DebugScreen(db: EventLogDatabase) {
    // FIX: Using produceState instead of observeAsState to avoid LiveData dependency issues
    val logs by produceState(initialValue = emptyList<EventLogEntity>()) {
        // Load logs in background
        value = withContext(Dispatchers.IO) {
            db.eventLogDao().getAll()
        }
    }

    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Debug Logs", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            // Refresh logic could go here, but produceState runs once.
            // For a debug screen, auto-refresh isn't strictly necessary.
        }) {
            Text("Logs Loaded: ${logs.size}")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(logs) { log ->
                LogItem(log)
                Divider()
            }
        }
    }
}

@Composable
fun LogItem(log: EventLogEntity) {
    val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "${dateFormat.format(Date(log.timestamp))} - ${log.eventType}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = "Pkg: ${log.packageName}", style = MaterialTheme.typography.bodySmall)
        Text(text = "Details: ${log.details}", style = MaterialTheme.typography.bodyMedium)
        if (log.confidenceScore > 0) {
            Text(text = "Confidence: ${log.confidenceScore}", style = MaterialTheme.typography.bodySmall)
        }
    }
}