package com.alphonso

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
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

            val auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid
            if (uid == null) {
                Log.w(TAG, "No user logged in, skipping nightly job.")
                return@withContext Result.success()
            }

            val firebase = FirebaseDatabase.getInstance()
            val incidentsRef = firebase.getReference("users/$uid/incidents")
            val historyRef = firebase.getReference("users/$uid/retraining_history")

            val snapshot = incidentsRef.get().await()
            val incidentCount = snapshot.childrenCount

            if (incidentCount == 0L) {
                setProgress(workDataOf(KEY_PROGRESS to "No new incidents to process."))
                Log.d(TAG, "No new incidents to process.")
                return@withContext Result.success()
            }

            Log.d(TAG, "Processing $incidentCount incidents...")
            // NOTE: Actual processing logic would go here.
            // This worker will now only log that it ran and will NOT delete data.

            val historyEntry = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                "items_processed" to incidentCount,
                "status" to "SUCCESS (Dry Run - Data not deleted)"
            )
            historyRef.push().setValue(historyEntry).await()

            Log.i(TAG, "Nightly batch job finished. Processed $incidentCount incidents.")
            return@withContext Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Nightly batch job failed", e)
            return@withContext Result.failure()
        }
    }
}