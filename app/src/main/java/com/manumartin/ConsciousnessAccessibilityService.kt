package com.manumartin

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
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
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    // --- State & Config Variables ---
    private var strikeCount = 0
    private var lastStrikeTime = 0L
    private var lastStrikePackage = ""
    private var isLockedOut = false
    private var lockedOutPackage: String? = null
    private var highAlertUntil = 0L
    private var remoteDisabledUntil = 0L
    private var censorGloballyDisabled = false
    private var isGlobalLockoutActive = false
    private val blocklist = mutableListOf<String>()

    // --- Remotely Configurable Settings with Local Defaults ---
    private var lockoutDurationMinutes = 3L
    private var strikeLimit = 5
    private var scanDelayNormal = 2000L
    private var scanDelayAlert = 50L
    private var strikeResetWindowMs = 300000L
    private var prayerText = "Hail Mary, full of grace, the Lord is with thee. Blessed art thou among women, and blessed is the fruit of thy womb, Jesus. Holy Mary, Mother of God, pray for us sinners, now and at the hour of our death. Amen."

    private val labelThresholds = mutableMapOf<Int, Float>().apply {
        put(2, 0.60f); put(3, 0.55f); put(4, 0.50f); put(6, 0.50f); put(14, 0.50f)
    }
    private val defaultThreshold = 0.65f
    private val lowConfidenceLogThreshold = 0.15f

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
        adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)
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
            override fun onDataChange(snapshot: DataSnapshot) { remoteDisabledUntil = snapshot.getValue(Long::class.java) ?: 0L }
            override fun onCancelled(error: DatabaseError) {}
        })

        firebaseDb?.getReference("remote_settings/censor_globally_disabled")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                censorGloballyDisabled = snapshot.getValue(Boolean::class.java) ?: false
                if (censorGloballyDisabled) {
                    GlobalScope.launch(Dispatchers.Main) { censorView?.clear() }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        firebaseDb?.getReference("remote_settings/global_lockout_enabled")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isGlobalLockoutActive = snapshot.getValue(Boolean::class.java) ?: false
                GlobalScope.launch(Dispatchers.Main) {
                    if (isGlobalLockoutActive) {
                        censorView?.triggerLockdown()
                    } else {
                        censorView?.clearGlobalLockdown()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })


        firebaseDb?.getReference("config/thresholds")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val labelName = child.key
                    val rawValue = child.value
                    val thresholdValue = when (rawValue) {
                        is Long -> rawValue.toFloat()
                        is Double -> rawValue.toFloat()
                        else -> null
                    }
                    if (labelName != null && thresholdValue != null) {
                        val index = ALL_CLASSES.indexOf(labelName)
                        if (index != -1) labelThresholds[index] = thresholdValue
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        firebaseDb?.getReference("config/blocklist")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                blocklist.clear()
                snapshot.children.forEach { child -> child.getValue(String::class.java)?.let { blocklist.add(it.lowercase()) } }
                Log.i(TAG, "Updated blocklist from Firebase: $blocklist")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        firebaseDb?.getReference("config/behavior")?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.i(TAG, "Behavior settings updated from Firebase.")
                snapshot.child("lockoutDurationMinutes").getValue(Long::class.java)?.let { lockoutDurationMinutes = it }
                snapshot.child("strikeLimit").getValue(Int::class.java)?.let { strikeLimit = it }
                snapshot.child("scanDelayNormal").getValue(Long::class.java)?.let { scanDelayNormal = it }
                snapshot.child("scanDelayAlert").getValue(Long::class.java)?.let { scanDelayAlert = it }
                snapshot.child("strikeResetWindowMs").getValue(Long::class.java)?.let { strikeResetWindowMs = it }
                snapshot.child("prayerText").getValue(String::class.java)?.let { prayerText = it }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { isCurrentlySpeaking = true }
                override fun onDone(utteranceId: String?) { isCurrentlySpeaking = false }
                override fun onError(utteranceId: String?) { isCurrentlySpeaking = false }
            })
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
                if (System.currentTimeMillis() < remoteDisabledUntil || isGlobalLockoutActive) {
                    withContext(Dispatchers.Main) { censorView?.clear() }
                    delay(5000)
                    continue
                }

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
                    val output = (results.get(0).value as? Array<Array<FloatArray>>)?.get(0)
                    if (output != null) {
                        val allDetectedBoxes = mutableListOf<Rect>()
                        var highConfidenceTrigger: Pair<String, Float>? = null
                        var bestCandidate: Pair<String, Float>? = null

                        for (i in 0 until output[0].size) {
                            var maxScore = 0f
                            var classIndex = -1
                            for (c in 0 until 18) {
                                val score = output[c + 4][i]
                                if (score > maxScore) { maxScore = score; classIndex = c }
                            }
                            if (!SENSITIVE_INDICES.contains(classIndex)) continue

                            val actionThreshold = labelThresholds[classIndex] ?: defaultThreshold

                            if (maxScore > lowConfidenceLogThreshold) {
                                val screenWidth = resources.displayMetrics.widthPixels; val screenHeight = resources.displayMetrics.heightPixels
                                val scaleX = screenWidth / 320f; val scaleY = screenHeight / 320f
                                val cx = output[0][i]; val cy = output[1][i]; val w = output[2][i]; val h = output[3][i]
                                allDetectedBoxes.add(Rect(((cx - w / 2) * scaleX).toInt(), ((cy - h / 2) * scaleY).toInt(), ((cx + w / 2) * scaleX).toInt(), ((cy + h / 2) * scaleY).toInt()))
                                val label = ALL_CLASSES.getOrElse(classIndex) { "Unknown" }
                                if (maxScore > actionThreshold) {
                                    if (highConfidenceTrigger == null || maxScore > highConfidenceTrigger.second) highConfidenceTrigger = Pair(label, maxScore)
                                } else {
                                    if (bestCandidate == null || maxScore > bestCandidate.second) bestCandidate = Pair(label, maxScore)
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (isGlobalLockoutActive) {
                                censorView?.triggerLockdown()
                            } else if (censorGloballyDisabled) {
                                censorView?.clear()
                            } else if (allDetectedBoxes.isNotEmpty()) {
                                censorView?.censorAreas(allDetectedBoxes)
                            } else {
                                censorView?.clear()
                            }

                            if (allDetectedBoxes.isNotEmpty()) {
                                highConfidenceTrigger?.let { handleDetections(it.first, it.second) } ?: bestCandidate?.let {
                                    inferenceScope.launch { logEvent(LogEventType.AI_CANDIDATE, "Candidate: ${it.first}", it.second) }
                                }
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

    private fun handleDetections(label: String, confidence: Float) {
        if (isCurrentlySpeaking) return

        if (isLockedOut) {
            Log.d(TAG, "Detection ($label) during lockdown. Censoring is active.")
            inferenceScope.launch { logEvent(LogEventType.AI_CANDIDATE, "Detection during lockdown: $label", confidence) }
            return
        }

        val now = System.currentTimeMillis()
        val currentPackage = rootInActiveWindow?.packageName?.toString() ?: "unknown"
        highAlertUntil = now + 60000L

        if (currentPackage != lastStrikePackage || (now - lastStrikeTime > strikeResetWindowMs)) {
            strikeCount = 0
        }

        strikeCount++
        lastStrikeTime = now
        lastStrikePackage = currentPackage

        Toast.makeText(this, "Strike $strikeCount/$strikeLimit: $label", Toast.LENGTH_SHORT).show()

        logToFirebase(label, confidence)
        inferenceScope.launch { logEvent(LogEventType.WARNING, "Strike $strikeCount for: $label", confidence) }
        speakPrayer()

        if (strikeCount >= strikeLimit) {
            initiateLockdown("$strikeLimit Strikes: $label", confidence, currentPackage)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun initiateLockdown(reason: String, confidence: Float, packageName: String) {
        if (isLockedOut) return
        Log.e(TAG, "LOCKDOWN INITIATED for package: $packageName due to $reason")

        try {
            dpm.setApplicationHidden(adminComponent, packageName, true)
        } catch (e: SecurityException) {
            Log.e(TAG, "Not device owner, cannot hide application")
            // Fallback to old method
            isLockedOut = true
            lockedOutPackage = packageName
        }

        strikeCount = 0
        highAlertUntil = System.currentTimeMillis() + 120000L
        logToFirebase("LOCKDOWN: $reason on $packageName", confidence)
        inferenceScope.launch { logEvent(LogEventType.APP_BLOCKED, "Lockdown for $packageName due to: $reason", confidence) }
        censorView?.clear()
        speakPrayer()
        performGlobalAction(GLOBAL_ACTION_HOME)

        GlobalScope.launch(Dispatchers.Main) {
            delay(lockoutDurationMinutes * 60 * 1000)
            try {
                dpm.setApplicationHidden(adminComponent, packageName, false)
                Log.i(TAG, "Lockdown penalty lifted, un-hid package: $packageName")
            } catch (e: SecurityException) {
                Log.e(TAG, "Not device owner, cannot un-hide application")
                isLockedOut = false
                lockedOutPackage = null
            }
            Toast.makeText(applicationContext, "Penalty Lifted.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || System.currentTimeMillis() < remoteDisabledUntil || isGlobalLockoutActive) return

        // The old lockdown logic is now just a fallback.
        if (isLockedOut && lockedOutPackage != null) {
            val activePackage = rootInActiveWindow?.packageName?.toString()
            if (activePackage == lockedOutPackage || event.packageName?.toString() == lockedOutPackage) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        if (!isLockedOut && (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            if (isCurrentlySpeaking) return

            val rootNode = rootInActiveWindow ?: return
            val currentPackageName = rootNode.packageName?.toString() ?: return

            if (currentPackageName.contains("com.android.systemui")) return

            val textContent = getAllTextFromNode(rootNode).joinToString(" ").lowercase()
            if (textContent.isNotEmpty()) {
                for (keyword in blocklist) {
                    if (textContent.contains(keyword)) {
                        Log.w(TAG, "Text-based block in app: $currentPackageName for keyword: $keyword")
                        Toast.makeText(this, "Content blocked.", Toast.LENGTH_SHORT).show()
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        inferenceScope.launch { logEvent(LogEventType.APP_BLOCKED, "Text-based block: $keyword", 1.0f) }
                        return
                    }
                }
            }
        }
    }


    private fun getAllTextFromNode(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        val textList = mutableListOf<String>()
        node.text?.let { textList.add(it.toString()) }
        for (i in 0 until node.childCount) {
            textList.addAll(getAllTextFromNode(node.getChild(i)))
        }
        return textList
    }

    private fun speakPrayer() {
        if (isTtsReady) {
            val utteranceId = this.hashCode().toString()
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            tts?.speak(prayerText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
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
        try { windowManager?.removeView(censorView) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
