package com.manumartin

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.graphics.scale
import androidx.room.Room
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.Collections
import java.util.Locale

class ConsciousnessAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private var windowManager: WindowManager? = null
    private var censorView: CensorView? = null
    private var screenCaptureScope = CoroutineScope(Dispatchers.Default)
    private var inferenceScope = CoroutineScope(Dispatchers.Default)

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private lateinit var eventLogDao: EventLogDao
    private var firebaseDb: FirebaseDatabase? = null
    private lateinit var prefs: SharedPreferences

    // --- State Variables ---
    private var strikeCount = 0
    private var lastStrikeTime = 0L
    private var lastStrikePackage = ""
    private var isLockedOut = false
    private var lockedOutPackage: String? = null // *** NEW: Stores the package to be locked
    private var highAlertUntil = 0L
    private var remoteDisabledUntil = 0L

    // --- Constants ---
    private val scanDelayNormal = 2000L
    private val scanDelayAlert = 500L
    private val strikeDebounceMs = 8000L
    private val strikeResetWindowMs = 300000L
    private val prayerText = "Hail Mary, full of grace..."

    private val labelThresholds = mutableMapOf<Int, Float>().apply {
        put(2, 0.60f); put(3, 0.55f); put(4, 0.50f); put(6, 0.50f); put(14, 0.50f)
    }
    private val defaultThreshold = 0.65f

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    companion object {
        private const val TAG = "ConsciousnessService"
        val ALL_CLASSES = listOf(
            "FEMALE_GENITALIA_COVERED", "FACE_FEMALE", "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED",
            "FEMALE_GENITALIA_EXPOSED", "MALE_BREAST_EXPOSED", "ANUS_EXPOSED", "FEET_EXPOSED",
            "BELLY_COVERED", "FEET_COVERED", "ARMPITS_COVERED", "ARMPITS_EXPOSED", "FACE_MALE",
            "BELLY_EXPOSED", "MALE_GENITALIA_EXPOSED", "ANUS_COVERED", "FEMALE_BREAST_COVERED",
            "BUTTOCKS_COVERED"
        )
        val SENSITIVE_INDICES = setOf(2, 3, 4, 6, 14)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, ">>> SERVICE STARTED <<<")

        prefs = getSharedPreferences("AlphonsoPrefs", MODE_PRIVATE)

        tts = TextToSpeech(this, this)

        try {
            firebaseDb = FirebaseDatabase.getInstance("https://alphonso-c7f69-default-rtdb.asia-southeast1.firebasedatabase.app")
            listenToFirebaseConfig()
        } catch (e: Exception) { Log.e(TAG, "Firebase Init Failed", e) }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val db = Room.databaseBuilder(applicationContext, EventLogDatabase::class.java, "event-log-database").build()
        eventLogDao = db.eventLogDao()

        initializeCensorView()
        initializeAI()
        startScreenCapture()
    }

    private fun listenToFirebaseConfig() {
        firebaseDb?.getReference("remote_settings/filtering_disabled_until")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                remoteDisabledUntil = snapshot.getValue(Long::class.java) ?: 0L
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // *** NEW LOGIC: Use Label Names as Keys in Firebase ***
        firebaseDb?.getReference("config/thresholds")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.i(TAG, "Firebase thresholds updated. Syncing...")
                for (child in snapshot.children) {
                    val labelName = child.key // e.g., "BUTTOCKS_EXPOSED"
                    val thresholdValue = child.getValue(Number::class.java)?.toFloat()

                    if (labelName != null && thresholdValue != null) {
                        val index = ALL_CLASSES.indexOf(labelName) // Find the index for that label
                        if (index != -1) {
                            // Update the threshold for the found index
                            labelThresholds[index] = thresholdValue
                            Log.d(TAG, "Remote threshold set for '$labelName' (index $index) to $thresholdValue")
                        } else {
                            Log.w(TAG, "Firebase threshold: Label '$labelName' not found in ALL_CLASSES list.")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read remote thresholds.", error.toException())
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    private fun initializeCensorView() {
        try {
            censorView = CensorView(this)
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
            params.gravity = Gravity.TOP or Gravity.START
            windowManager?.addView(censorView, params)
        } catch (_: Exception) { }
    }

    private fun initializeAI() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = assets.open("nudenet_320n.onnx").readBytes()
            ortSession = ortEnv?.createSession(modelBytes)
        } catch (_: Exception) { }
    }

    private fun startScreenCapture() {
        screenCaptureScope.launch {
            while (isActive) {
                if (System.currentTimeMillis() < remoteDisabledUntil) {
                    withContext(Dispatchers.Main) { censorView?.clear() }
                    delay(5000)
                    continue
                }

                if (!isLockedOut) {
                    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            val hardwareBitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                            hardwareBitmap?.let {
                                val softwareBitmap = it.copy(Bitmap.Config.ARGB_8888, true)
                                processImage(softwareBitmap)
                                it.recycle()
                            }
                            screenshot.hardwareBuffer.close()
                        }
                        override fun onFailure(errorCode: Int) {}
                    })
                } else {
                    delay(1000) // If locked out, just pause to save energy. The real logic is in onAccessibilityEvent.
                }

                val delayTime = if (System.currentTimeMillis() < highAlertUntil) scanDelayAlert else scanDelayNormal
                delay(delayTime)
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        inferenceScope.launch {
            try {
                if (ortSession == null) return@launch

                val resized = bitmap.scale(320, 320)
                val floatBuffer = preprocessBitmap(resized)
                val inputName = ortSession?.inputNames?.iterator()?.next() ?: return@launch
                val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, 320, 320))

                ortSession?.run(Collections.singletonMap(inputName, inputTensor))?.use { results ->
                    val rawOutput = results.get(0).value as? Array<Array<FloatArray>>
                    val output = rawOutput?.get(0)

                    if (output != null) {
                        val detectedBoxes = mutableListOf<Rect>()
                        var primaryLabel: String? = null
                        var primaryConfidence = 0f

                        // Find all sensitive items on screen, not just the first one.
                        for (i in 0 until output[0].size) {
                            var maxScore = 0f
                            var classIndex = -1
                            for (c in 0 until 18) {
                                val score = output[c + 4][i]
                                if (score > maxScore) { maxScore = score; classIndex = c }
                            }

                            val threshold = labelThresholds[classIndex] ?: defaultThreshold

                            if (maxScore > threshold && SENSITIVE_INDICES.contains(classIndex)) {
                                val screenWidth = resources.displayMetrics.widthPixels
                                val screenHeight = resources.displayMetrics.heightPixels
                                val scaleX = screenWidth / 320f
                                val scaleY = screenHeight / 320f
                                val cx = output[0][i]; val cy = output[1][i]; val w = output[2][i]; val h = output[3][i]
                                val left = ((cx - w / 2) * scaleX).toInt()
                                val top = ((cy - h / 2) * scaleY).toInt()
                                val right = ((cx + w / 2) * scaleX).toInt()
                                val bottom = ((cy + h / 2) * scaleY).toInt()
                                detectedBoxes.add(Rect(left, top, right, bottom))

                                if (primaryLabel == null) {
                                    primaryLabel = ALL_CLASSES.getOrElse(classIndex) { "Unknown" }
                                    primaryConfidence = maxScore
                                }
                            }
                        }

                        withContext(Dispatchers.Main) {
                            if (primaryLabel != null) {
                                handleDetections(detectedBoxes, primaryLabel, primaryConfidence)
                            } else {
                                censorView?.clear()
                            }
                        }
                    }
                }

                inputTensor.close()
                bitmap.recycle()
                resized.recycle()

            } catch (e: Exception) { Log.e(TAG, "Inference Failed", e) }
        }
    }

    private fun handleDetections(boxes: List<Rect>, label: String, confidence: Float) {
        val now = System.currentTimeMillis()
        val currentPackage = rootInActiveWindow?.packageName?.toString() ?: "unknown"

        highAlertUntil = now + 60000L
        censorView?.censorAreas(boxes) // Censor all detected areas

        if (now - lastStrikeTime < strikeDebounceMs) return

        if (currentPackage != lastStrikePackage || (now - lastStrikeTime > strikeResetWindowMs)) {
            strikeCount = 0
        }

        strikeCount++
        lastStrikeTime = now
        lastStrikePackage = currentPackage

        Toast.makeText(this, "Strike $strikeCount/5: $label", Toast.LENGTH_SHORT).show()

        logToFirebase(label, confidence)
        inferenceScope.launch {
            logEvent(LogEventType.WARNING, "Strike $strikeCount for: $label", confidence)
        }

        speakPrayer()

        if (strikeCount >= 5) {
            initiateLockdown(label, confidence, currentPackage)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun initiateLockdown(label: String, confidence: Float, packageName: String) {
        Log.e(TAG, "LOCKDOWN INITIATED for package: $packageName")
        isLockedOut = true
        lockedOutPackage = packageName
        strikeCount = 0

        logToFirebase("LOCKDOWN: $label on $packageName", confidence)
        inferenceScope.launch {
            logEvent(LogEventType.APP_BLOCKED, "Lockdown for $packageName due to: $label", confidence)
        }

        censorView?.clear() // Clear any lingering boxes
        speakPrayer()
        performGlobalAction(GLOBAL_ACTION_HOME) // Go home once to immediately close the app

        // Timer to end the lockout
        GlobalScope.launch(Dispatchers.Main) {
            delay(3 * 60 * 1000)
            isLockedOut = false
            lockedOutPackage = null
            Log.i(TAG, "Lockdown penalty lifted for $packageName.")
            Toast.makeText(applicationContext, "Penalty Lifted.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This is the core of the targeted app lock
        if (isLockedOut && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.packageName == lockedOutPackage) {
                Log.w(TAG, "User tried to open locked app: ${event.packageName}. Forcing home.")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun speakPrayer() {
        if (isTtsReady) {
            tts?.speak(prayerText, TextToSpeech.QUEUE_FLUSH, null, "PrayerID")
        }
    }

    private fun logToFirebase(label: String, confidence: Float) {
        val entry = mapOf("timestamp" to System.currentTimeMillis(), "label" to label, "confidence" to confidence)
        firebaseDb?.getReference("incidents")?.push()?.setValue(entry)
    }

    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        val buffer = FloatBuffer.allocate(1 * 3 * 320 * 320)
        val intValues = IntArray(320 * 320)
        bitmap.getPixels(intValues, 0, 320, 0, 0, 320, 320)
        for (i in 0 until 320 * 320) {
            val pixel = intValues[i]
            buffer.put(i, ((pixel shr 16) and 0xFF) / 255.0f)
            buffer.put(i + 320*320, ((pixel shr 8) and 0xFF) / 255.0f)
            buffer.put(i + 2*320*320, (pixel and 0xFF) / 255.0f)
        }
        buffer.rewind()
        return buffer
    }

    private suspend fun logEvent(type: LogEventType, details: String, confidence: Float) {
        val log = EventLogEntity(eventType = type.name, packageName = (rootInActiveWindow?.packageName?.toString() ?: "unknown"), details = details, confidenceScore = confidence)
        eventLogDao.insert(log)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onInterrupt() {}
}