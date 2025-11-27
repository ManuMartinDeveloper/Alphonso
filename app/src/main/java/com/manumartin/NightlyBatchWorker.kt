package com.manumartin

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.training.CheckpointState
import ai.onnxruntime.training.TrainingSession
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class NightlyBatchWorker(val appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private val eventLogDao: EventLogDao by lazy {
        Room.databaseBuilder(
            appContext,
            EventLogDatabase::class.java, "event-log-database"
        ).build().eventLogDao()
    }

    private val firebaseStorage: FirebaseStorage by lazy {
        FirebaseStorage.getInstance()
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "On-device training batch starting...")
        setProgress(Data.Builder().putString(KEY_PROGRESS, "Starting training...").build())

        try {
            val logs = eventLogDao.getAll()
            val trainingLogs = logs.filter { it.eventType == LogEventType.DETECTION.name }

            if (trainingLogs.isEmpty()) {
                Log.d(TAG, "No training data available. Skipping.")
                setProgress(Data.Builder().putString(KEY_PROGRESS, "No new data to train on.").build())
                return Result.success()
            }
            
            setProgress(Data.Builder().putString(KEY_PROGRESS, "Processing ${trainingLogs.size} logs...").build())

            val ortEnv = OrtEnvironment.getEnvironment()

            // 1. Define paths for all training artifacts
            val checkpointFile = getArtifactFile("checkpoint.ckpt")
            val trainingModelFile = getArtifactFile("training_model.onnx")
            val optimizerModelFile = getArtifactFile("optimizer_model.onnx")

            // 2. Create the TrainingSession
            val checkpointState = CheckpointState.load(checkpointFile.path)
            val session = TrainingSession(
                ortEnv,
                checkpointState,
                trainingModelFile.path,
                optimizerModelFile.path,
            )
            
            setProgress(Data.Builder().putString(KEY_PROGRESS, "Running training step...").build())

            // 3. TODO: Prepare training data
            // val trainingInputs: Map<String, OnnxTensor> = prepareTrainingData(trainingLogs)
            
            // 4. TODO: Run a training step
            // session.trainStep(trainingInputs)
            Log.d(TAG, "Training step completed (placeholder). You need to implement data preparation.")

            // 5. Save the updated weights back to the checkpoint
            session.saveCheckpoint(checkpointFile.path, true)
            Log.d(TAG, "Saved updated weights to checkpoint.")

            // 6. Export the newly refined model for inference
            val newInferenceModelFile = File(appContext.filesDir, "refined_model.onnx")
            session.exportModelForInference(newInferenceModelFile.path, listOf("output")) 
            Log.d(TAG, "Exported refined inference model to ${newInferenceModelFile.path}")

            // 7. Backup to Firebase
            setProgress(Data.Builder().putString(KEY_PROGRESS, "Backing up new model...").build())
            backupArtifactToFirebase(checkpointFile)
            backupArtifactToFirebase(newInferenceModelFile, "refined_model.onnx")

            // 8. Log the results
            val positives = trainingLogs.count { !it.isFalsePositive }
            val negatives = trainingLogs.count { it.isFalsePositive }
            val modelSizeMb = newInferenceModelFile.length() / (1024.0 * 1024.0)
            val logDetails = "Training complete. Positives: $positives, Negatives: $negatives, New Model Size: ${String.format("%.2f", modelSizeMb)} MB"
            logEvent(LogEventType.RETRAINING, "system", logDetails)

            // 9. Clear old logs
            eventLogDao.clearAll()

            session.close()
            setProgress(Data.Builder().putString(KEY_PROGRESS, "Finished successfully.").build())
            Log.d(TAG, "On-device training batch finished successfully.")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "On-device training failed", e)
            setProgress(Data.Builder().putString(KEY_PROGRESS, "Training failed!").build())
            return Result.failure()
        }
    }

    private suspend fun logEvent(type: LogEventType, packageName: String, details: String) {
        val logEntry = EventLogEntity(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            eventType = type.name,
            packageName = packageName,
            details = details,
            isFalsePositive = false
        )
        eventLogDao.insert(logEntry)
    }

    private fun getArtifactFile(filename: String): File {
        val file = File(appContext.filesDir, filename)
        if (!file.exists()) {
            Log.d(TAG, "Copying $filename from assets to internal storage.")
            appContext.assets.open(filename).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file
    }

     private fun getModelFile(context: Context): File {
        val refinedModel = File(context.filesDir, "refined_model.onnx")
        if (refinedModel.exists()) {
            Log.d(TAG, "Loading refined model for training.")
            return refinedModel
        }
        return getArtifactFile("nudenet_320n.onnx")
    } 

    private fun backupArtifactToFirebase(file: File, remoteFilename: String = file.name) {
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId.isNullOrEmpty()) {
            Log.w(TAG, "Could not get Android ID. Skipping artifact backup.")
            return
        }

        val storageRef = firebaseStorage.reference
        val modelRef = storageRef.child("user_models/$androidId/$remoteFilename")

        modelRef.putFile(android.net.Uri.fromFile(file))
            .addOnSuccessListener { 
                Log.d(TAG, "Successfully backed up ${file.name} to Firebase Storage.") 
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Failed to back up ${file.name}.", e) 
            }
    }

    companion object {
        const val KEY_PROGRESS = "Progress"
        const val UNIQUE_WORK_NAME = "OnDeviceTrainingWorker"

        fun schedule(context: Context, allowMobileData: Boolean) {
            val networkType = if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<NightlyBatchWorker>(
                24, TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest
            )
            Log.d(TAG, "On-device training worker scheduled with network constraint: $networkType")
        }

        fun runManually(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<NightlyBatchWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Manually triggered on-device training.")
        }
    }
}
