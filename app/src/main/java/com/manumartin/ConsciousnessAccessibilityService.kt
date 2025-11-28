package com.manumartin

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.graphics.scale
import androidx.room.Room
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.*
import java.nio.FloatBuffer
import java.util.Collections

class ConsciousnessAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var censorView: CensorView? = null
    private var screenCaptureScope = CoroutineScope(Dispatchers.Default)
    private var inferenceScope = CoroutineScope(Dispatchers.Default)

    // ONNX Runtime
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Database
    private lateinit var eventLogDao: EventLogDao

    companion object {
        private const val TAG = "ConsciousnessService"
        var instance: ConsciousnessAccessibilityService? = null

        val SENSITIVE_LABELS = setOf("FEMALE_GENITALIA", "BUTTOCKS", "FEMALE_BREAST", "ANUS")

        // Static method to allow DebugActivity to flag false positives
        fun flagEventAsFalsePositive(logId: Int) {
            instance?.let { service ->
                service.inferenceScope.launch {
                    service.eventLogDao.markAsFalsePositive(logId)
                    Log.d(TAG, "Flagged log $logId as False Positive")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Service Connected")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Initialize Database
        val db = Room.databaseBuilder(
            applicationContext,
            EventLogDatabase::class.java, "event-log-database"
        ).build()
        eventLogDao = db.eventLogDao()

        initializeCensorView()
        initializeAI()
        startScreenCapture()
    }

    private fun initializeCensorView() {
        censorView = CensorView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(censorView, params)
    }

    private fun initializeAI() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            // Load the model from assets
            val modelBytes = assets.open("nudenet_320n.onnx").readBytes()
            ortSession = ortEnv?.createSession(modelBytes)
            Log.d(TAG, "AI Model Loaded Successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading AI model", e)
        }
    }

    private fun startScreenCapture() {
        // Note: In a real implementation, you need a way to get the MediaProjection token.
        // This usually requires an Activity to request permission first.
        // For this code to work, we assume MediaProjection is handled or we use AccessibilityService's
        // takeScreenshot API (available in Android 11+).

        // Using AccessibilityService's takeScreenshot (simpler for this context)
        screenCaptureScope.launch {
            while (isActive) {
                takeScreenshotAndAnalyze()
                delay(1000) // Analyze every 1 second to save battery
            }
        }
    }

    private fun takeScreenshotAndAnalyze() {
        takeScreenshot(
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    bitmap?.let { processImage(it) }
                    screenshot.hardwareBuffer.close()
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Screenshot failed: $errorCode")
                }
            }
        )
    }

    private fun processImage(bitmap: Bitmap) {
        inferenceScope.launch {
            try {
                // 1. Preprocess Bitmap to FloatBuffer (320x320 for this model)
                val resized = bitmap.scale(320, 320)
                val floatBuffer = preprocessBitmap(resized)

                // 2. Run Inference
                val inputName = ortSession?.inputNames?.iterator()?.next() ?: return@launch
                val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, 320, 320))
                ortSession?.run(Collections.singletonMap(inputName, inputTensor))?.use {
                    // 3. Parse Output (Simplified logic for detections)
                    // Assuming output[0] is boxes and scores.
                    // You would parse the specific output format of nudenet_320n.onnx here.

                    val hasAdultContent = false // Replace with actual parsing logic

                    if (hasAdultContent) {
                        MainScope().launch {
                            censorView?.visibility = View.VISIBLE
                        }
                        logEvent(LogEventType.DETECTION, "Adult content detected", 0.85f)
                    } else {
                        MainScope().launch {
                            censorView?.visibility = View.GONE
                        }
                    }
                }

                inputTensor.close()
            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
            }
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        val buffer = FloatBuffer.allocate(1 * 3 * 320 * 320)
        val intValues = IntArray(320 * 320)
        bitmap.getPixels(intValues, 0, 320, 0, 0, 320, 320)

        // Normalize 0-255 to 0-1 (or whatever the model expects)
        for (pixel in intValues) {
            buffer.put(((pixel shr 16) and 0xFF) / 255.0f) // R
            buffer.put(((pixel shr 8) and 0xFF) / 255.0f)  // G
            buffer.put((pixel and 0xFF) / 255.0f)          // B
        }
        buffer.rewind()
        return buffer
    }

    private suspend fun logEvent(type: LogEventType, details: String, confidence: Float) {
        val log = EventLogEntity(
            eventType = type.name,
            packageName = rootInActiveWindow?.packageName?.toString() ?: "unknown",
            details = details,
            confidenceScore = confidence
        )
        eventLogDao.insert(log)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Optional: Listen for specific app opens to trigger/pause scanning
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }
}
