package com.alphonso

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.speech.tts.TextToSpeech
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
    private var isLockedOut = false
    private var lockedOutPackage: String? = null
    private var highAlertUntil = 0L
    private var remoteDisabledUntil = 0L
    private var censorGloballyDisabled = false

    // --- Config (Updated via Firebase) ---
    private val defaultBlocklist = setOf("pornhub", "xvideos", "xnxx", "chaturbate", "redtube", "youporn", "xhamster", "brazzers", "adultfriendfinder", "nude", "porn", "sexy", "xxx", "hentai")
    private val blocklist = mutableListOf<String>().apply { addAll(defaultBlocklist) }
    private val browserPackages = setOf("com.android.chrome", "org.mozilla.firefox", "com.duckduckgo.mobile.android", "com.microsoft.emmx")

    private var lockoutDurationMinutes = 3L
    private var strikeLimit = 5
    private var scanDelayNormal = 2000L
    private var scanDelayAlert = 100L
    private var prayerText = "Hail Mary, full of grace, the Lord is with thee. Blessed art thou among women, and blessed is the fruit of thy womb, Jesus. Holy Mary, Mother of God, pray for us sinners, now and at the hour of our death. Amen."

    private val labelThresholds = mutableMapOf<Int, Float>()
    private var defaultThreshold = 0.50f
    private val lowConfidenceLogThreshold = 0.05f

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    companion object {
        private const val TAG = "ConsciousnessService"
        private const val MODEL_INPUT_SIZE = 320
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
            initializeDatabaseStructure()
            listenToFirebaseConfig()
        } catch (e: Exception) { Log.e(TAG, "Firebase Init Failed", e) }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val db = Room.databaseBuilder(applicationContext, EventLogDatabase::class.java, "event-log-database").fallbackToDestructiveMigration().build()
        eventLogDao = db.eventLogDao()
        activeLockoutDao = db.activeLockoutDao()

        initializeCensorView()
        initializeAI()
        checkPendingUnlock()
        startScreenCapture()
    }

    private fun initializeDatabaseStructure() {
        val dbRef = firebaseDb?.reference ?: return
        
        dbRef.child("remote_settings").get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val defaults = mapOf(
                    "filtering_disabled_until" to 0L,
                    "censor_globally_disabled" to false,
                    "scan_delay_normal" to 2000L,
                    "scan_delay_alert" to 100L,
                    "strike_limit" to 5L,
                    "lockout_duration_minutes" to 3L,
                    "prayer_text" to prayerText
                )
                dbRef.child("remote_settings").setValue(defaults)
            }
        }

        dbRef.child("category_sensitivity").get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val sensitivities = mapOf(
                    "2" to mapOf("name" to "BUTTOCKS_EXPOSED", "threshold" to 0.40f),
                    "3" to mapOf("name" to "FEMALE_BREAST_EXPOSED", "threshold" to 0.40f),
                    "4" to mapOf("name" to "FEMALE_GENITALIA_EXPOSED", "threshold" to 0.40f),
                    "6" to mapOf("name" to "ANUS_EXPOSED", "threshold" to 0.40f),
                    "14" to mapOf("name" to "MALE_GENITALIA_EXPOSED", "threshold" to 0.40f),
                    "default" to 0.50f
                )
                dbRef.child("category_sensitivity").setValue(sensitivities)
            }
        }

        dbRef.child("config/blocklist").get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                dbRef.child("config/blocklist").setValue(defaultBlocklist.toList())
            }
        }
    }

    private fun listenToFirebaseConfig() {
        val dbRef = firebaseDb?.reference ?: return
        
        dbRef.child("remote_settings").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                remoteDisabledUntil = snapshot.child("filtering_disabled_until").getValue(Long::class.java) ?: 0L
                censorGloballyDisabled = snapshot.child("censor_globally_disabled").getValue(Boolean::class.java) ?: false
                scanDelayNormal = snapshot.child("scan_delay_normal").getValue(Long::class.java) ?: 2000L
                scanDelayAlert = snapshot.child("scan_delay_alert").getValue(Long::class.java) ?: 100L
                strikeLimit = snapshot.child("strike_limit").getValue(Long::class.java)?.toInt() ?: 5
                lockoutDurationMinutes = snapshot.child("lockout_duration_minutes").getValue(Long::class.java) ?: 3L
                
                val newPrayerText = snapshot.child("prayer_text").getValue(String::class.java) ?: prayerText
                if (newPrayerText != prayerText) {
                    prayerText = newPrayerText
                    mainExecutor.execute { censorView?.setPrayerText(prayerText) }
                }
                
                if (censorGloballyDisabled) mainExecutor.execute { censorView?.clear() }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        dbRef.child("category_sensitivity").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                labelThresholds.clear()
                defaultThreshold = snapshot.child("default").getValue(Double::class.java)?.toFloat() ?: 0.50f
                snapshot.children.forEach { child ->
                    val key = child.key?.toIntOrNull()
                    if (key != null) {
                        val threshold = child.child("threshold").getValue(Double::class.java)?.toFloat() ?: defaultThreshold
                        labelThresholds[key] = threshold
                    }
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
            censorView?.setPrayerText(prayerText)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, 
                WindowManager.LayoutParams.MATCH_PARENT, 
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, 
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, 
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            windowManager?.addView(censorView, params)
        } catch (e: Exception) { Log.e(TAG, "CensorView failed", e) }
    }

    private fun initializeAI() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = assets.open("yolo26n_Nudenet_Final.onnx").readBytes()
            val options = OrtSession.SessionOptions().apply {
                try {
                    addNnapi() // Use NNAPI for hardware acceleration
                    Log.i(TAG, "NNAPI acceleration enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "NNAPI not supported on this device, using CPU")
                }
            }
            ortSession = ortEnv?.createSession(modelBytes, options)
        } catch (e: Exception) {
            Log.e(TAG, "AI Init failed, falling back to CPU", e)
            try {
                ortSession = ortEnv?.createSession(assets.open("yolo26n_Nudenet_Final.onnx").readBytes())
            } catch (e2: Exception) {
                Log.e(TAG, "AI fallback failed", e2)
            }
        }
    }

    private fun checkPendingUnlock() {
        inferenceScope.launch {
            val lockouts = activeLockoutDao.getAllSync()
            val now = System.currentTimeMillis()
            for (lockout in lockouts) {
                if (now >= lockout.unlockTime) unhideApplication(lockout.packageName)
                else {
                    delay(lockout.unlockTime - now)
                    unhideApplication(lockout.packageName)
                }
            }
        }
    }

    private fun startScreenCapture() {
        screenCaptureScope.launch {
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
                        override fun onFailure(errorCode: Int) {}
                    })
                }
                delay(if (System.currentTimeMillis() < highAlertUntil) scanDelayAlert else scanDelayNormal)
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        inferenceScope.launch {
            try {
                if (ortSession == null) { bitmap.recycle(); return@launch }
                val resized = resizeWithPadding(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
                val floatBuffer = preprocessBitmap(resized)
                val inputName = ortSession?.inputNames?.iterator()?.next() ?: return@launch
                val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong()))

                ortSession?.run(Collections.singletonMap(inputName, inputTensor))?.use { results ->
                    val outputValue = results.get(0).value
                    val output = (outputValue as? Array<Array<FloatArray>>)?.get(0)
                    if (output != null) {
                        val boxesToNms = mutableListOf<Rect>()
                        val scoresToNms = mutableListOf<Float>()
                        val labelsToNms = mutableListOf<String>()

                        val screenWidth = bitmap.width.toFloat()
                        val screenHeight = bitmap.height.toFloat()
                        val targetDim = MODEL_INPUT_SIZE.toFloat()
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
                            if (!SENSITIVE_INDICES.contains(classIndex)) continue
                            val actionThreshold = labelThresholds[classIndex] ?: defaultThreshold
                            if (maxScore > actionThreshold) {
                                val cx = output[0][i]; val cy = output[1][i]; val w = output[2][i]; val h = output[3][i]
                                val realCx = (cx - offsetX) / scale; val realCy = (cy - offsetY) / scale
                                val realW = w / scale; val realH = h / scale
                                val left = (realCx - realW / 2).toInt(); val top = (realCy - realH / 2).toInt()
                                val right = (realCx + realW / 2).toInt(); val bottom = (realCy + realH / 2).toInt()
                                
                                boxesToNms.add(Rect(left, top, right, bottom))
                                scoresToNms.add(maxScore)
                                labelsToNms.add(ALL_CLASSES.getOrElse(classIndex) { "Unknown" })
                            }
                        }

                        val selectedIndices = nms(boxesToNms, scoresToNms, 0.45f)
                        val finalBoxes = selectedIndices.map { boxesToNms[it] }

                        withContext(Dispatchers.Main) {
                            if (finalBoxes.isNotEmpty()) {
                                censorView?.censorAreas(finalBoxes, bitmap)
                                val bestIdx = selectedIndices.maxByOrNull { scoresToNms[it] }
                                if (bestIdx != null) {
                                    handleDetections(labelsToNms[bestIdx], scoresToNms[bestIdx])
                                }
                            } else censorView?.clear()
                        }
                    }
                }
                inputTensor.close(); resized.recycle(); bitmap.recycle()
            } catch (e: Exception) { Log.e(TAG, "Image process error", e); bitmap.recycle() }
        }
    }

    private fun handleDetections(label: String, confidence: Float) {
        val detectedPackage = rootInActiveWindow?.packageName?.toString() ?: "unknown"
        if (isLockedOut || detectedPackage == applicationContext.packageName) return

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
        Log.w(TAG, "LOCKDOWN for $packageName ($reason)")
        logEvent(LogEventType.APP_BLOCKED, reason, confidence, packageName)
        try {
            dpm.setApplicationHidden(adminComponent, packageName, true)
            isLockedOut = true
            lockedOutPackage = packageName
            strikeCount = 0
            censorView?.clear()
            speakPrayer()
            performGlobalAction(GLOBAL_ACTION_HOME)
            val durationMs = lockoutDurationMinutes * 60 * 1000
            inferenceScope.launch {
                activeLockoutDao.insert(ActiveLockoutEntity(packageName, System.currentTimeMillis() + durationMs))
                delay(durationMs)
                unhideApplication(packageName)
            }
        } catch (e: Exception) { Log.e(TAG, "Lockdown failed", e) }
    }

    fun unhideApplication(packageName: String) {
        try { dpm.setApplicationHidden(adminComponent, packageName, false) } catch (e: Exception) {}
        finally {
            if (lockedOutPackage == packageName) { isLockedOut = false; lockedOutPackage = null }
            inferenceScope.launch { activeLockoutDao.delete(packageName) }
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
                    Log.i(TAG, "Keyword block: $keyword")
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

    private fun speakPrayer() { if (isTtsReady) tts?.speak(prayerText, TextToSpeech.QUEUE_FLUSH, null, "prayer") }

    private fun logEvent(type: LogEventType, details: String, confidence: Float, packageName: String) {
        val timestamp = System.currentTimeMillis()
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val entry = mapOf("timestamp" to timestamp, "type" to type.name, "label" to details, "confidence" to confidence, "packageName" to packageName)
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
            } catch (_: Exception) {}
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
        val buffer = FloatBuffer.allocate(3 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        val intValues = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        bitmap.getPixels(intValues, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        for (i in 0 until MODEL_INPUT_SIZE * MODEL_INPUT_SIZE) {
            val px = intValues[i]
            buffer.put(i, ((px shr 16) and 0xFF) / 255.0f)
            buffer.put(i + MODEL_INPUT_SIZE * MODEL_INPUT_SIZE, ((px shr 8) and 0xFF) / 255.0f)
            buffer.put(i + 2 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE, (px and 0xFF) / 255.0f)
        }
        buffer.rewind()
        return buffer
    }

    private fun nms(boxes: List<Rect>, scores: List<Float>, iouThreshold: Float): List<Int> {
        if (boxes.isEmpty()) return emptyList()
        val indices = scores.indices.sortedByDescending { scores[it] }.toMutableList()
        val selected = mutableListOf<Int>()
        while (indices.isNotEmpty()) {
            val current = indices.removeAt(0)
            selected.add(current)
            val iterator = indices.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (calculateIou(boxes[current], boxes[next]) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selected
    }

    private fun calculateIou(a: Rect, b: Rect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val intersectionArea = maxOf(0, right - left) * maxOf(0, bottom - top)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        return intersectionArea.toFloat() / (areaA + areaB - intersectionArea).toFloat()
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