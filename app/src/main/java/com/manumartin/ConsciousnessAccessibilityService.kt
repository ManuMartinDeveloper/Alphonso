package com.manumartin

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
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

    // --- STRIKE LOGIC VARIABLES ---
    private var strikeCount = 0
    private var lastStrikeTime = 0L
    private var lastStrikePackage = ""
    private var isLockedOut = false

    // --- SETTINGS VARIABLES ---
    private var remoteDisabledUntil = 0L
    private var isCensorViewEnabled = true // Controls GLOBAL blackout only

    private val STRIKE_DEBOUNCE_MS = 8000L      // 8 Seconds delay between strikes
    private val STRIKE_RESET_WINDOW_MS = 300000L // 5 Minutes to reset strikes

    // Audio
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val PRAYER_TEXT = "Hail Mary, full of grace, the Lord is with thee. Blessed art thou among women, and blessed is the fruit of thy womb, Jesus. Holy Mary, Mother of God, pray for us sinners, now and at the hour of our death. Amen."

    // Thresholds
    private val labelThresholds = mutableMapOf<Int, Float>().apply {
        put(2, 0.60f); put(3, 0.55f); put(4, 0.50f); put(6, 0.50f); put(14, 0.50f)
    }
    private val DEFAULT_THRESHOLD = 0.65f

    companion object {
        private const val TAG = "ConsciousnessService"
        var instance: ConsciousnessAccessibilityService? = null

        val ALL_CLASSES = listOf(
            "FEMALE_GENITALIA_COVERED", "FACE_FEMALE", "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED",
            "FEMALE_GENITALIA_EXPOSED", "MALE_BREAST_EXPOSED", "ANUS_EXPOSED", "FEET_EXPOSED",
            "BELLY_COVERED", "FEET_COVERED", "ARMPITS_COVERED", "ARMPITS_EXPOSED", "FACE_MALE",
            "BELLY_EXPOSED", "MALE_GENITALIA_EXPOSED", "ANUS_COVERED", "FEMALE_BREAST_COVERED",
            "BUTTOCKS_COVERED"
        )
        val SENSITIVE_INDICES = setOf(2, 3, 4, 6, 14)

        fun flagEventAsFalsePositive(logId: Int) {
            instance?.let { service ->
                service.inferenceScope.launch {
                    service.eventLogDao.markAsFalsePositive(logId)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, ">>> SERVICE STARTED <<<")

        prefs = getSharedPreferences("AlphonsoPrefs", Context.MODE_PRIVATE)
        isCensorViewEnabled = prefs.getBoolean("censor_view_enabled", true)

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
        // 1. Check for Remote Disable
        firebaseDb?.getReference("remote_settings/filtering_disabled_until")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                remoteDisabledUntil = snapshot.getValue(Long::class.java) ?: 0L
                Log.d(TAG, "Remote Filter Disabled Until: $remoteDisabledUntil")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 2. Sync Thresholds
        firebaseDb?.getReference("config/thresholds")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                labelThresholds.clear()
                for (child in snapshot.children) {
                    val index = child.key?.toIntOrNull()
                    val value = child.getValue(Float::class.java)
                    if (index != null && value != null) labelThresholds[index] = value
                }
                Log.d(TAG, "Updated Thresholds from Firebase: $labelThresholds")
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.US)
            isTtsReady = true
        }
    }

    private fun initializeCensorView() {
        try {
            censorView = CensorView(this)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            windowManager?.addView(censorView, params)
        } catch (e: Exception) { }
    }

    private fun initializeAI() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = assets.open("nudenet_320n.onnx").readBytes()
            ortSession = ortEnv?.createSession(modelBytes)
        } catch (e: Exception) { }
    }

    private fun startScreenCapture() {
        screenCaptureScope.launch {
            while (isActive) {
                // 1. Check Remote Disable
                if (System.currentTimeMillis() < remoteDisabledUntil) {
                    withContext(Dispatchers.Main) { censorView?.clear() }
                    delay(5000)
                    continue
                }

                // 2. Check Local Lockout
                if (isLockedOut) { delay(1000); continue }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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
                }
                delay(600)
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        inferenceScope.launch {
            try {
                if (ortSession == null) return@launch

                val resized = Bitmap.createScaledBitmap(bitmap, 320, 320, true)
                val floatBuffer = preprocessBitmap(resized)
                val inputName = ortSession?.inputNames?.iterator()?.next() ?: return@launch
                val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, 320, 320))

                val results = ortSession?.run(Collections.singletonMap(inputName, inputTensor))
                val rawOutput = results?.get(0)?.value as Array<Array<FloatArray>>
                val output = rawOutput[0]

                val detectedBoxes = mutableListOf<Rect>()
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                val scaleX = screenWidth / 320f
                val scaleY = screenHeight / 320f

                var detectedLabelName = ""
                var detectedConfidence = 0f

                for (i in 0 until output[0].size) {
                    var maxScore = 0f
                    var classIndex = -1
                    for (c in 0 until 18) {
                        val score = output[c + 4][i]
                        if (score > maxScore) { maxScore = score; classIndex = c }
                    }

                    val threshold = labelThresholds[classIndex] ?: DEFAULT_THRESHOLD

                    if (maxScore > threshold && SENSITIVE_INDICES.contains(classIndex)) {
                        val cx = output[0][i]; val cy = output[1][i]; val w = output[2][i]; val h = output[3][i]
                        val left = ((cx - w / 2) * scaleX).toInt()
                        val top = ((cy - h / 2) * scaleY).toInt()
                        val right = ((cx + w / 2) * scaleX).toInt()
                        val bottom = ((cy + h / 2) * scaleY).toInt()
                        detectedBoxes.add(Rect(left, top, right, bottom))

                        detectedLabelName = ALL_CLASSES.getOrElse(classIndex) { "Unknown" }
                        detectedConfidence = maxScore
                    }
                }

                withContext(Dispatchers.Main) {
                    handleDetections(detectedBoxes, detectedLabelName, detectedConfidence)
                }

                inputTensor.close()
                if (bitmap != resized) resized.recycle()
            } catch (e: Exception) { Log.e(TAG, "Inference Failed", e) }
        }
    }

    private fun handleDetections(boxes: List<Rect>, label: String, confidence: Float) {
        if (boxes.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val currentPackage = rootInActiveWindow?.packageName?.toString() ?: "unknown"

            // ALWAYS CENSOR SMALL BOXES
            censorView?.censorAreas(boxes)

            // A. Debounce (Ignore strikes if < 8 seconds from last one)
            if (now - lastStrikeTime < STRIKE_DEBOUNCE_MS) {
                return
            }

            // B. Reset Logic (New App OR > 5 Minutes)
            if (currentPackage != lastStrikePackage || (now - lastStrikeTime > STRIKE_RESET_WINDOW_MS)) {
                strikeCount = 0 // Reset to fresh start
                Log.i(TAG, "Strike Count Reset (New App or Time Expired)")
            }

            // C. Valid Strike
            strikeCount++
            lastStrikeTime = now
            lastStrikePackage = currentPackage

            Toast.makeText(this, "Strike $strikeCount/5: $label", Toast.LENGTH_SHORT).show()

            // Log to DB and Firebase
            logToFirebase(label, confidence)
            inferenceScope.launch {
                logEvent(LogEventType.DETECTION, "Detected: $label", confidence)
            }

            // D. Action (Prayer on EVERY strike)
            speakPrayer()

            if (strikeCount >= 5) {
                initiateLockdown()
            }
        } else {
            censorView?.clear()
        }
    }

    private fun initiateLockdown() {
        isLockedOut = true
        strikeCount = 0

        // Only trigger global blackout if enabled in Settings
        if (isCensorViewEnabled) {
            censorView?.triggerLockdown()
        } else {
            // If global blackout disabled, we must clear the small boxes so they don't get stuck on home screen
            censorView?.clear()
        }

        speakPrayer()

        // Force Home
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)

        GlobalScope.launch(Dispatchers.Main) {
            delay(3 * 60 * 1000)
            isLockedOut = false
            censorView?.clear()
            stopAudio()
            Toast.makeText(applicationContext, "Penalty Lifted.", Toast.LENGTH_LONG).show()
        }
    }

    private fun speakPrayer() {
        if (isTtsReady) {
            tts?.speak(PRAYER_TEXT, TextToSpeech.QUEUE_FLUSH, null, "PrayerID")
        }
    }

    private fun stopAudio() {
        try { tts?.stop() } catch (e: Exception) {}
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
        val log = EventLogEntity(eventType = type.name, packageName = rootInActiveWindow?.packageName?.toString() ?: "unknown", details = details, confidenceScore = confidence)
        eventLogDao.insert(log)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}