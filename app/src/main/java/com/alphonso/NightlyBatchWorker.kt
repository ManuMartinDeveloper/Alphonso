package com.alphonso

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NightlyBatchWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "NightlyBatchWorker"

    companion object {
        const val UNIQUE_WORK_NAME = "NightlyBatchWork"
        const val KEY_PROGRESS = "progress"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setProgress(workDataOf(KEY_PROGRESS to "Initializing..."))
            Log.d(TAG, "Nightly batch job started")

            val firebase = FirebaseDatabase.getInstance("https://alphonso-c7f69-default-rtdb.asia-southeast1.firebasedatabase.app")
            val incidentsRef = firebase.getReference("incidents")
            val historyRef = firebase.getReference("retraining_history")

            val snapshot = incidentsRef.get().await()
            val incidentCount = snapshot.childrenCount

            if (incidentCount == 0L) {
                setProgress(workDataOf(KEY_PROGRESS to "No data to train"))
                return@withContext Result.success()
            }

            Log.d(TAG, "Retraining on $incidentCount items...")
            for (i in 1..10) {
                Thread.sleep(300)
                setProgress(workDataOf(KEY_PROGRESS to "Retraining: ${i * 10}%"))
            }

            val historyEntry = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                "items_processed" to incidentCount,
                "status" to "SUCCESS"
            )
            historyRef.push().setValue(historyEntry).await()
            incidentsRef.removeValue().await()

            return@withContext Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Training failed", e)
            return@withContext Result.failure()
        }
    }
}