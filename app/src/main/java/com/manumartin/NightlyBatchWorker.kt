package com.manumartin

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.work.*
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.training.CheckpointState
import ai.onnxruntime.training.TrainingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class NightlyBatchWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val tag = "NightlyBatchWorker"

    companion object {
        const val UNIQUE_WORK_NAME = "NightlyBatchWork"
        const val KEY_PROGRESS = "progress"

        fun schedule(context: Context, allowMobileData: Boolean) {
            val workManager = WorkManager.getInstance(context)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<NightlyBatchWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            Log.d("NightlyBatchWorker", "Work scheduled with allowMobileData: $allowMobileData")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setProgress(workDataOf(KEY_PROGRESS to "Starting..."))
        Log.d(tag, "Nightly batch job started")

        // 1. Initialize Database
        val db = Room.databaseBuilder(
            applicationContext,
            EventLogDatabase::class.java, "event-log-database"
        ).build()
        val dao = db.eventLogDao()

        // 2. Fetch Data (False Positives only for now)
        val logs = dao.getAll()
        val falsePositives = logs.filter { it.isFalsePositive }

        if (falsePositives.isEmpty()) {
            Log.d(tag, "No false positives to train on. Skipping.")
            return@withContext Result.success()
        }

        // 3. Load ONNX Training Artifacts
        val filesDir = applicationContext.filesDir.absolutePath
        val trainingModelPath = "$filesDir/training_model.onnx"
        val checkpointPath = "$filesDir/checkpoint.ckpt"
        if (!File(trainingModelPath).exists()) {
            Log.e(tag, "Training artifacts not found.")
            return@withContext Result.failure()
        }

        try {
            val env = OrtEnvironment.getEnvironment()
            val state = CheckpointState.loadCheckpoint(checkpointPath)
            val trainingSession = TrainingSession.create(env, trainingModelPath, state)

            Log.d(tag, "Training session created. Processing ${falsePositives.size} items...")

            // 4. (Placeholder) Run Training Loop
            // Here you would convert the false positive images into tensors and feed them
            // into trainingSession.trainStep(...)

            // 5. Save updated weights
            trainingSession.saveCheckpoint(checkpointPath, true)
            Log.d(tag, "Model updated and saved.")

            // 6. Backup to GitHub (Placeholder)
            // uploadToGitHub(checkpointPath)

            return@withContext Result.success()

        } catch (e: Exception) {
            Log.e(tag, "Training failed", e)
            return@withContext Result.failure()
        }
    }
}