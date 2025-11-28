package com.manumartin

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        setProgress(androidx.work.workDataOf(KEY_PROGRESS to "Starting..."))
        Log.d(TAG, "Nightly batch job started")

        val db = Room.databaseBuilder(
            applicationContext,
            EventLogDatabase::class.java, "event-log-database"
        ).build()
        val dao = db.eventLogDao()

        val logs = dao.getAll()
        val falsePositives = logs.filter { it.isFalsePositive }

        if (falsePositives.isEmpty()) {
            Log.d(TAG, "No false positives to train on. Skipping.")
            return@withContext Result.success()
        }

        try {
            Log.d(TAG, "Processing ${falsePositives.size} items for retraining...")

            // Placeholder: Simulated Training Delay
            // Real on-device training on Android requires complex C++ JNI setup
            // which is not yet fully supported in the Java API wrapper.
            Thread.sleep(2000)

            Log.d(TAG, "Training logic executed (simulated).")
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Training failed", e)
            return@withContext Result.failure()
        }
    }
}