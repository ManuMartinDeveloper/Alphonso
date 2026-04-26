package com.alphonso

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
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
    private val screenCaptureScope = CoroutineScope(Dispatchers.Default)
    private val inferenceScope = CoroutineScope(Dispatchers.Default)

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private lateinit var eventLogDao: EventLogDao
    private lateinit var activeLockoutDao: ActiveLockoutDao
    private var firebaseDb: FirebaseDatabase? = null
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private val auth = FirebaseAuth.getInstance()

    // --- State Variables ---
    private var strikeCount = 0
    private var lastStrikeTime = 0L
    private var lastStrikePackage = ""
    private var isLockedOut = false
    private var lockedOutPackage: String? = null
    private var highAlertUntil = 0L
    private var remoteDisabledUntil = 0L
    private var censorGloballyDisabled = false
    private var isGlobalLockoutActive = false

    // --- Config ---
    private val defaultBlocklist = setOf("pornhub", "xvideos", "xnxx", "chaturbate", "redtube", "youporn", "xhamster", "brazzers", "adultfriendfinder", "nude", "porn", "sexy", "xxx", "hentai")
    private val blocklist = mutableListOf<String>().apply { addAll(defaultBlocklist) }

    private val browserPackages = setOf("com.android.chrome", "org.mozilla.firefox", "com.duckduckgo.mobile.android", "com.microsoft.emmx")

    private var lockoutDurationMinutes = 3L
    private var strikeLimit = 5
    private var scanDelayNormal = 2000L
    private var scanDelayAlert = 100L
    private var strikeResetWindowMs = 300000L
    private var prayerText = "Hail Mary, full of grace, the Lord is with thee. Blessed art thou among women, and blessed is the fruit of thy womb, Jesus. Holy Mary, Mother of God, pray for us sinners, now and at the hour of our death. Amen."

    private val labelThresholds = mutableMapOf(2 to 0.40f, 3 to 0.40f, 4 to 0.40f, 6 to 0.40f, 14 to 0.40f) // Lowered thresholds for testing
    private val defaultThreshold = 0.50f
    private val lowConfidenceLogThreshold = 0.05f

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isCurrentlySpeaking = false

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
        tts = TextToSpeech(this, this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, ConsciousnessDeviceAdminReceiver::class.java)

        try {
            firebaseDb = FirebaseDatabase.getInstance()
            listenToFirebaseConfig()
        } catch (e: Exception) { Log.e(TAG, "Firebase Init Failed", e) }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Initialize Room Database for Local Logging
        val db = Room.databaseBuilder(applicationContext, EventLogDatabase::class.java, "event-log-database").fallbackToDestructiveMigration().build()
        eventLogDao = db.eventLogDao()
        activeLockoutDao = db.activeLockoutDao()

        initializeCensorView()
        initializeAI()
        checkPendingUnlock()
        startScreenCapture()
    }

    private fun listenToFirebaseConfig() {
        val dbRef = firebaseDb?.reference ?: return
        dbRef.child("remote_settings/filtering_disabled_until").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { remoteDisabledUntil = snapshot.getValue(Long::class.java) ?: 0L }
            override fun onCancelled(error: DatabaseError) {}
        })
        dbRef.child("remote_settings/censor_globally_disabled").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                censorGloballyDisabled = snapshot.getValue(Boolean::class.java) ?: false
                if (censorGloballyDisabled) {
                    mainExecutor.execute { censorView?.clear() }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        dbRef.child("config/blocklist").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                blocklist.clear()
                snapshot.children.forEach { child -> child.getValue(String::class.java)?.let { blocklist.add(it.lowercase()) } }
            }
            override fun onCancelled(error: DatabaseError) {}
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
            Log.d(TAG, "CensorView initialized and added to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "CensorView initialization failed", e)
        }
    }

    private fun initializeAI() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = assets.open("nudenet_320n.onnx").readBytes()
            ortSession = ortEnv?.createSession(modelBytes)
            Log.d(TAG, "ONNX Session created successfully. Input names: ${ortSession?.inputNames}")
        } catch (e: Exception) {
            Log.e(TAG, "AI Initialization Failed", e)
        }
    }

    private fun checkPendingUnlock() {
        inferenceScope.launch {
            val lockouts = activeLockoutDao.getAllSync()
            val now = System.currentTimeMillis()

            for (lockout in lockouts) {
                if (now >= lockout.unlockTime) {
                    unhideApplication(lockout.packageName)
                } else {
                    val delayMs = lockout.unlockTime - now
                    inferenceScope.launch {
                        delay(delayMs)
                        unhideApplication(lockout.packageName)
                    }
                }
            }
        }
    }

    private fun startScreenCapture() {
        screenCaptureScope.launch {
            Log.d(TAG, "Screen capture loop started")
            while (isActive) {
                if (System.currentTimeMillis() < remoteDisabledUntil || censorGloballyDisabled) {
                    withContext(Dispatchers.Main) { censorView?.clear() }
                    delay(5000)
                    continue
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                            bitmap?.let {
                                val softwareBitmap = it.copy(Bitmap.Config.ARGB_8888, true)
                                processImage(softwareBitmap)
                                it.recycle()
                            }
                            screenshot.hardwareBuffer.close()
                        }
                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "takeScreenshot failed with error code: $errorCode")
                        }
                    })
                } else {
                    Log.w(TAG, "takeScreenshot not supported on this Android version")
                    delay(5000)
                }
                delay(if (System.currentTimeMillis() < highAlertUntil) scanDelayAlert else scanDelayNormal)
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        inferenceScope.launch {
            try {
                if (ortSession == null) {
                    Log.w(TAG, "ortSession is null, skipping processing")
                    bitmap.recycle()
                    return@launch
                }
                val resized = resizeWithPadding(bitmap, 320, 320)
                val floatBuffer = preprocessBitmap(resized)
                val inputName = ortSession?.inputNames?.iterator()?.next() ?: return@launch
                val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, 320, 320))

                ortSession?.run(Collections.singletonMap(inputName, inputTensor))?.use { results ->
                    val outputValue = results.get(0).value
                    // YOLOv8 output is typically float[1][22][2100]
                    val output = (outputValue as? Array<Array<FloatArray>>)?.get(0)
                    
                    if (output != null) {
                        val allDetectedBoxes = mutableListOf<Rect>()
                        var highConfidenceTrigger: Pair<String, Float>? = null

                        val screenWidth = bitmap.width.toFloat()
                        val screenHeight = bitmap.height.toFloat()
                        val targetDim = 320f
                        val scale = kotlin.math.min(targetDim / screenWidth, targetDim / screenHeight)
                        val offsetX = (targetDim - screenWidth * scale) / 2
                        val offsetY = (targetDim - screenHeight * scale) / 2

                        for (i in 0 until output[0].size) {
                            var maxScore = 0f
                            var classIndex = -1
                            for (c in 0 until 18) {
                                val score = output[c + 4][i]
                                if (score > maxScore) { maxScore = score; classIndex = c }
                            }
                            
                            if (maxScore > lowConfidenceLogThreshold) {
                                // Log.v(TAG, "Box $i: class $classIndex, score $maxScore")
                            }

                            if (!SENSITIVE_INDICES.contains(classIndex)) continue
                            
                            val actionThreshold = labelThresholds[classIndex] ?: defaultThreshold
                            if (maxScore > actionThreshold) {
                                val cx = output[0][i]; val cy = output[1][i]; val w = output[2][i]; val h = output[3][i]
                                
                                val realCx = (cx - offsetX) / scale; val realCy = (cy - offsetY) / scale
                                val realW = w / scale; val realH = h / scale
                                
                                val left = (realCx - realW / 2).toInt(); val top = (realCy - realH / 2).toInt()
                                val right = (realCx + realW / 2).toInt(); val bottom = (realCy + realH / 2).toInt()
                                
                                allDetectedBoxes.add(Rect(left, top, right, bottom))
                                val label = ALL_CLASSES.getOrElse(classIndex) { "Unknown" }
                                if (highConfidenceTrigger == null || maxScore > highConfidenceTrigger!!.second) {
                                    highConfidenceTrigger = Pair(label, maxScore)
                                }
                            }
                        }

                        withContext(Dispatchers.Main) {
                            if (allDetectedBoxes.isNotEmpty()) {
                                Log.d(TAG, "Detections: ${allDetectedBoxes.size} areas found. Top: ${highConfidenceTrigger?.first} (${highConfidenceTrigger?.second})")
                                censorView?.censorAreas(allDetectedBoxes, bitmap)
                                highConfidenceTrigger?.let { handleDetections(it.first, it.second) }
                            } else {
                                censorView?.clear()
                            }
                        }
                    } else {
                        Log.w(TAG, "Model output format mismatch: ${outputValue?.javaClass?.name}")
                    }
                }
                inputTensor.close(); resized.recycle(); bitmap.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error in processImage", e)
                bitmap.recycle()
            }
        }
    }

    private fun handleDetections(label: String, confidence: Float) {
        if (isCurrentlySpeaking) return
        if (isLockedOut) return

        val detectedPackage = rootInActiveWindow?.packageName?.toString() ?: "unknown"
        if (detectedPackage == applicationContext.packageName) return

        strikeCount++
        Log.i(TAG, "Strike $strikeCount: $label ($confidence) in $detectedPackage")
        Toast.makeText(this, "Strike $strikeCount: $label", Toast.LENGTH_SHORT).show()

        logEvent(LogEventType.DETECTION, label, confidence, detectedPackage)
        speakPrayer()

        if (strikeCount >= strikeLimit) {
            initiateLockdown("Strike Limit Reached: $label", confidence, detectedPackage)
        }
    }

    private fun initiateLockdown(reason: String, confidence: Float, packageName: String) {
        if (isLockedOut) return
        Log.w(TAG, "Initiating lockdown for $packageName. Reason: $reason")
        logEvent(LogEventType.APP_BLOCKED, reason, confidence, packageName)

        try {
            dpm.setApplicationHidden(adminComponent, packageName, true)
            isLockedOut = true
            lockedOutPackage = packageName
            strikeCount = 0
            censorView?.clear()
            speakPrayer()
            performGlobalAction(GLOBAL_ACTION_HOME)

            val unlockTime = System.currentTimeMillis() + (lockoutDurationMinutes * 60 * 1000)

            inferenceScope.launch {
                activeLockoutDao.insert(ActiveLockoutEntity(packageName, unlockTime))
                delay(lockoutDurationMinutes * 60 * 1000)
                unhideApplication(packageName)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to hide application to initiate lockdown for $packageName", e)
        }
    }

    fun unhideApplication(packageName: String) {
        try {
            dpm.setApplicationHidden(adminComponent, packageName, false)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to unhide application after lockdown for $packageName", e)
        } finally {
            if (lockedOutPackage == packageName) {
                isLockedOut = false
                lockedOutPackage = null
            }
            inferenceScope.launch {
                activeLockoutDao.delete(packageName)
            }
            logEvent(LogEventType.APP_RELEASED, "Lockdown ended", 0f, packageName)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || isLockedOut) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        if (browserPackages.contains(packageName) || !packageName.contains("systemui")) {
            val rootNode = rootInActiveWindow ?: return
            val allText = getAllTextFromNode(rootNode).joinToString(" ").lowercase()

            for (keyword in blocklist) {
                if (allText.contains(keyword)) {
                    Log.i(TAG, "Keyword block: $keyword in $packageName")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    logEvent(LogEventType.DETECTION, "Text Block: $keyword", 1.0f, packageName)
                    Toast.makeText(this, "Blocked: $keyword", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }
    }

    private fun getAllTextFromNode(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        val list = mutableListOf<String>()
        node.text?.let { list.add(it.toString()) }
        for (i in 0 until node.childCount) list.addAll(getAllTextFromNode(node.getChild(i)))
        return list
    }

    private fun speakPrayer() {
        if (isTtsReady) tts?.speak(prayerText, TextToSpeech.QUEUE_FLUSH, null, "prayer")
    }

    private fun logEvent(type: LogEventType, details: String, confidence: Float, packageName: String) {
        val timestamp = System.currentTimeMillis()
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val entry = mapOf(
                "timestamp" to timestamp,
                "type" to type.name,
                "label" to details,
                "confidence" to confidence,
                "packageName" to packageName
            )
            firebaseDb?.getReference("users/$uid/incidents")?.push()?.setValue(entry)
        }
        inferenceScope.launch {
            try {
                eventLogDao.insert(EventLogEntity(
                    timestamp = timestamp,
                    eventType = type.name,
                    packageName = packageName,
                    details = details,
                    confidenceScore = confidence,
                    isFalsePositive = false
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log event locally", e)
            }
        }
    }

    private fun resizeWithPadding(original: Bitmap, w: Int, h: Int): Bitmap {
        val bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bg)
        c.drawColor(android.graphics.Color.BLACK)
        val scale = kotlin.math.min(w.toFloat() / original.width, h.toFloat() / original.height)
        val m = android.graphics.Matrix()
        m.setScale(scale, scale)
        m.postTranslate((w - original.width * scale) / 2, (h - original.height * scale) / 2)
        c.drawBitmap(original, m, android.graphics.Paint().apply { isFilterBitmap = true })
        return bg
    }

    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        val buffer = FloatBuffer.allocate(3 * 320 * 320)
        val intValues = IntArray(320 * 320)
        bitmap.getPixels(intValues, 0, 320, 0, 0, 320, 320)
        for (i in 0 until 320 * 320) {
            val px = intValues[i]
            buffer.put(i, ((px shr 16) and 0xFF) / 255.0f)
            buffer.put(i + 320 * 320, ((px shr 8) and 0xFF) / 255.0f)
            buffer.put(i + 2 * 320 * 320, (px and 0xFF) / 255.0f)
        }
        buffer.rewind()
        return buffer
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        tts?.shutdown()
        try { windowManager?.removeView(censorView) } catch (_: Exception) {}
        screenCaptureScope.cancel()
        inferenceScope.cancel()
        super.onDestroy()
    }
}