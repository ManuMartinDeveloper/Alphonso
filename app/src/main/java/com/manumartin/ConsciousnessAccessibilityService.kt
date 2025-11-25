package com.manumartin

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.graphics.scale
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class ConsciousnessAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val devicePolicyManager by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val adminComponent by lazy { ComponentName(this, ConsciousnessDeviceAdminReceiver::class.java) }
    private val censorViews = mutableListOf<CensorView>()
    
    // Default blocklist
    private val defaultBlocklist = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "chaturbate.com", "redtube.com", "superchatlive.com", "stripchat.com",
        "youporn.com", "xhamster.com", "porn.com", "brazzers.com", "adultfriendfinder.com", "archivebate.com",
        "nude", "porn", "sexy", "adult entertainment", "erotic", "xxx", "hentai"
    )
    // Combined blocklist (Default + Remote)
    private val activeBlocklist = mutableSetOf<String>().apply { addAll(defaultBlocklist) }
    
    private val browserPackages = setOf("com.android.chrome", "org.mozilla.firefox", "com.duckduckgo.mobile.android")
    private val handler = Handler(Looper.getMainLooper())

    // Visual detection components
    private lateinit var ortEnvironment: OrtEnvironment
    private lateinit var session: OrtSession
    private lateinit var tts: TextToSpeech
    private var lastPrayerTime = 0L
    private var warningCount = 0
    private var currentPackageName: String? = null
    
    // Blocking logic
    private val blockedPackages = mutableMapOf<String, Long>()

    private val isProcessing = AtomicBoolean(false)
    private var screenshotHandler: Handler? = null
    private var screenshotRunnable: Runnable? = null

    // Remote Control
    private var isFilteringDisabled = false
    private var filteringDisabledUntil = 0L
    private lateinit var database: FirebaseDatabase

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "Consciousness Service Created")
        instance = this
        ortEnvironment = OrtEnvironment.getEnvironment()
        tts = TextToSpeech(this, this)
        try {
            val model = resources.assets.open("nudenet_320n.onnx").readBytes()
            session = ortEnvironment.createSession(model)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX model", e)
        }
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.e(TAG, "Firebase initialized manually")
            }
            database = FirebaseDatabase.getInstance("https://alphonso-c7f69-default-rtdb.asia-southeast1.firebasedatabase.app")
            setupRemoteConfig()
        } catch (e: Exception) {
             Log.e(TAG, "Firebase Critical Error", e)
        }
    }

    private fun setupRemoteConfig() {
        if (!::database.isInitialized) return
        Log.d(TAG, "Setting up remote config...")
        try {
            val configRef = database.getReference("config")
            
            // Listener for disabling/enabling filtering remotely
            configRef.child("disableFilteringUntil").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val disabledUntil = snapshot.getValue(Long::class.java)
                    if (disabledUntil != null) {
                        filteringDisabledUntil = disabledUntil
                        checkFilteringStatus()
                        Log.d(TAG, "Remote config updated: disableFilteringUntil=$disabledUntil")
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Remote config failed", error.toException())
                }
            })

            // Listener for adding blocked domains/keywords remotely
            configRef.child("blocklist").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    activeBlocklist.clear()
                    activeBlocklist.addAll(defaultBlocklist)
                    
                    for (child in snapshot.children) {
                        val item = child.getValue(String::class.java)
                        if (!item.isNullOrBlank()) {
                            activeBlocklist.add(item)
                        }
                    }
                    Log.d(TAG, "Blocklist updated from remote. Total items: ${activeBlocklist.size}")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Remote blocklist update failed", error.toException())
                }
            })
            Log.d(TAG, "Firebase listeners attached successfully")
            
        } catch (e: Exception) {
             Log.e(TAG, "Firebase init failed (make sure google-services.json is present)", e)
        }
    }
    
    private fun checkFilteringStatus() {
        val now = System.currentTimeMillis()
        if (now < filteringDisabledUntil) {
            isFilteringDisabled = true
            handler.post { Toast.makeText(applicationContext, "Filtering disabled by admin", Toast.LENGTH_LONG).show() }
        } else {
             isFilteringDisabled = false
        }
    }

    private fun logIncident(type: String, details: String, packageName: String? = currentPackageName, labels: List<String>? = null) {
        if (!::database.isInitialized) return
        Log.d(TAG, "Attempting to log incident: $type - $details")
        try {
            val incidentsRef = database.getReference("incidents")
            val incident = mutableMapOf<String, Any>(
                "timestamp" to ServerValue.TIMESTAMP,
                "type" to type,
                "details" to details,
                "packageName" to (packageName ?: "unknown")
            )
            if (labels != null) {
                incident["labels"] = labels
            }
            incidentsRef.push().setValue(incident)
                .addOnSuccessListener { Log.d(TAG, "Incident logged to Firebase successfully") }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to write to Firebase", e) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log incident", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        // We remove package filtering to detect app changes globally
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        this.serviceInfo = info
        startScreenshotLoop()
        Toast.makeText(this, "Consciousness Service Connected", Toast.LENGTH_SHORT).show()
        logIncident("service_connected", "Service enabled by user")
    }

    private fun startScreenshotLoop() {
        screenshotHandler = Handler(Looper.getMainLooper())
        screenshotRunnable = object : Runnable {
            override fun run() {
                // Logic to auto-re-enable filtering if timer expired
                val now = System.currentTimeMillis()
                val shouldBeDisabled = now < filteringDisabledUntil
                
                if (isFilteringDisabled != shouldBeDisabled) {
                    isFilteringDisabled = shouldBeDisabled
                    if (!isFilteringDisabled) {
                         handler.post { Toast.makeText(applicationContext, "Filtering re-enabled", Toast.LENGTH_SHORT).show() }
                    }
                }

                if (!isFilteringDisabled) {
                    takeScreenshot()
                }
                screenshotHandler?.postDelayed(this, 1000) 
            }
        }
        screenshotHandler?.post(screenshotRunnable!!)
    }

    @SuppressLint("WrongThread")
    private fun takeScreenshot() {
        val capturedPackage = currentPackageName
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hardwareBuffer = screenshot.hardwareBuffer
                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)

                if (bitmap != null) {
                    if (isProcessing.compareAndSet(false, true)) {
                        try {
                            processImage(bitmap, capturedPackage)
                        } finally {
                            isProcessing.set(false)
                        }
                    }
                }
                hardwareBuffer.close()
            }

            override fun onFailure(errorCode: Int) {
                // 3 is TAKE_SCREENSHOT_FAILURE_WINDOW_IS_OBSCURED
                if (errorCode != 3) {
                    Log.e(TAG, "Screenshot failed with error code: $errorCode")
                }
                clearCensorViews()
            }
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName != null) {
            val packageName = event.packageName.toString()

            // Check if this package is currently blocked
            val releaseTime = blockedPackages[packageName]
            if (releaseTime != null) {
                if (System.currentTimeMillis() < releaseTime) {
                    enforceBlock("App locked for 3 mins")
                    return
                } else {
                    blockedPackages.remove(packageName)
                }
            }

            if (packageName != currentPackageName) {
                currentPackageName = packageName
                warningCount = 0
            }
        }
        
        if (isFilteringDisabled) return

        if (event.packageName != null && browserPackages.contains(event.packageName.toString())) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                val rootNode = rootInActiveWindow ?: return
                findUrlBar(rootNode)?.text?.let { url ->
                    if (isBlocked(url.toString())) {
                        logIncident("url_blocked", url.toString(), event.packageName.toString())
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        handler.post {
                            Toast.makeText(
                                applicationContext,
                                "Incognito browsing of this content is blocked.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun isBlocked(url: String): Boolean {
        return activeBlocklist.any { url.contains(it, ignoreCase = true) }
    }

    private fun findUrlBar(nodeInfo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val browserUrlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/url_bar_title",
            "com.duckduckgo.mobile.android:id/omnibarTextInput"
        )
        browserUrlBarIds.forEach { id ->
            val nodes = nodeInfo.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) return nodes[0]
        }

        for (i in 0 until nodeInfo.childCount) {
            val child = nodeInfo.getChild(i)
            if (child != null) {
                val found = findUrlBar(child)
                if (found != null) {
                    return found
                }
            }
        }

        return null
    }

    fun addCensorView(bounds: Rect) {
        val censorView = CensorView(this)
        val params = WindowManager.LayoutParams(
            bounds.width(), bounds.height(), bounds.left, bounds.top,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(censorView, params)
        censorViews.add(censorView)
    }

    fun clearCensorViews() {
        if (censorViews.isNotEmpty()) {
            handler.post {
                censorViews.forEach { windowManager.removeView(it) }
                censorViews.clear()
            }
        }
    }

    private fun enforceBlock(message: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        // Delay the lock slightly to ensure the home screen intent has started processing,
        // reducing the chance of re-opening the blocked app upon unlock.
        handler.postDelayed({
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                devicePolicyManager.lockNow()
            }
        }, 200)
        
        handler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
    }

    // --- Merged from ScreenCaptureService ---

    private fun processImage(bitmap: Bitmap, capturedPackageName: String?) {
        val resizedBitmap = bitmap.scale(320, 320)
        val inputTensor = preProcess(resizedBitmap)
        val tensor = OnnxTensor.createTensor(ortEnvironment, inputTensor, longArrayOf(1, 3, 320, 320))
        val results = session.run(Collections.singletonMap(session.inputNames.iterator().next(), tensor))
        processOutputs(results, bitmap.width, bitmap.height, capturedPackageName)
        bitmap.recycle()
        resizedBitmap.recycle()
    }

    private fun processOutputs(results: OrtSession.Result, originalWidth: Int, originalHeight: Int, capturedPackageName: String?) {
        clearCensorViews()
        try {
            val outputTensor = results[0].value as? Array<Array<FloatArray>>
            if (outputTensor == null) {
                Log.e(TAG, "Unexpected model output type: ${results[0].value::class.java.name}")
                return
            }
            val detections = outputTensor[0]
            val numDetections = detections[0].size
            val transposedDetections = Array(numDetections) { FloatArray(22) }
            for (i in 0 until 22) {
                for (j in 0 until numDetections) {
                    transposedDetections[j][i] = detections[i][j]
                }
            }

            val boxes = mutableListOf<Detection>()
            val scaleX = originalWidth / 320f
            val scaleY = originalHeight / 320f
            var sensitiveContentInFrame = false
            val detectedLabels = mutableListOf<String>()

            for (i in 0 until numDetections) {
                val detection = transposedDetections[i]
                val scores = detection.sliceArray(4..21)
                val maxScore = scores.maxOrNull() ?: 0f
                val classIndex = scores.indexOfFirst { it == maxScore }

                if (maxScore > 0.2f && classIndex != -1) {
                    val label = LABELS[classIndex]
                    if (label == "FACE_FEMALE") continue

                    val cx = detection[0]; val cy = detection[1]; val w = detection[2]; val h = detection[3]
                    val x1 = (cx - w / 2) * scaleX; val y1 = (cy - h / 2) * scaleY
                    val x2 = (cx + w / 2) * scaleX; val y2 = (cy + h / 2) * scaleY
                    boxes.add(Detection(RectF(x1, y1, x2, y2), maxScore, classIndex))

                    if (label in SENSITIVE_LABELS) {
                        sensitiveContentInFrame = true
                        detectedLabels.add(label)
                        if (maxScore > 0.5f && (System.currentTimeMillis() - lastPrayerTime > 10000)) {
                            lastPrayerTime = System.currentTimeMillis()
                            tts.speak("Hail Mary Full of Grace, the Lord is with you. Blessed are you among women, Blessed is the fruit of thy womb Jesus", TextToSpeech.QUEUE_FLUSH, null, "prayer")
                        }
                    }
                }
            }

            if (sensitiveContentInFrame) {
                // Ensure we only warn/block if the user is still in the same app or if we want to penalize that specific app
                if (capturedPackageName == currentPackageName) {
                    
                    logIncident("visual_detection", "Sensitive content detected", capturedPackageName, detectedLabels)
                    
                    warningCount++
                    handler.post { Toast.makeText(applicationContext, "Warning ($warningCount)", Toast.LENGTH_SHORT).show() }
                    
                    if (warningCount >= 5) {
                        capturedPackageName?.let { pkg ->
                            blockedPackages[pkg] = System.currentTimeMillis() + 3 * 60 * 1000
                            logIncident("app_blocked", "Blocked for 3 mins due to repeated detections", pkg)
                        }
                        enforceBlock("Closing application due to repeated sensitive content")
                        warningCount = 0
                    }
                }
            }
            // Removed else block to prevent resetting count on clean frames

            nonMaxSuppression(boxes).forEach { addCensorView(Rect(it.box.left.toInt(), it.box.top.toInt(), it.box.right.toInt(), it.box.bottom.toInt())) }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing model outputs", e)
        }
    }

    private data class Detection(val box: RectF, val score: Float, val classIndex: Int)

    private fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float = 0.45f): List<Detection> {
        val sortedDetections = detections.sortedByDescending { it.score }
        val selectedDetections = mutableListOf<Detection>()
        val active = BooleanArray(sortedDetections.size) { true }

        for (i in sortedDetections.indices) {
            if (active[i]) {
                selectedDetections.add(sortedDetections[i])
                for (j in i + 1 until sortedDetections.size) {
                    if (active[j] && calculateIoU(sortedDetections[i].box, sortedDetections[j].box) > iouThreshold) {
                        active[j] = false
                    }
                }
            }
        }
        return selectedDetections
    }

    private fun calculateIoU(boxA: RectF, boxB: RectF): Float {
        val xA = max(boxA.left, boxB.left); val yA = max(boxA.top, boxB.top)
        val xB = min(boxA.right, boxB.right); val yB = min(boxA.bottom, boxB.bottom)
        val intersectionArea = max(0f, xB - xA) * max(0f, yB - yA)
        val boxAArea = (boxA.right - boxA.left) * (boxA.bottom - boxA.top)
        val boxBArea = (boxB.right - boxB.left) * (boxB.bottom - boxB.top)
        return intersectionArea / (boxAArea + boxBArea - intersectionArea)
    }

    private fun preProcess(bitmap: Bitmap): FloatBuffer {
        val imgData = FloatBuffer.allocate(3 * 320 * 320)
        imgData.rewind()
        val stride = 320 * 320
        val bmpData = IntArray(stride)
        bitmap.getPixels(bmpData, 0, 320, 0, 0, 320, 320)
        for (i in 0 until 320) {
            for (j in 0 until 320) {
                val idx = i * 320 + j
                val pixelValue = bmpData[idx]
                imgData.put(idx, ((pixelValue shr 16 and 0xFF) / 255.0f))
                imgData.put(idx + stride, ((pixelValue shr 8 and 0xFF) / 255.0f))
                imgData.put(idx + stride * 2, ((pixelValue and 0xFF) / 255.0f))
            }
        }
        imgData.rewind()
        return imgData
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        } else {
            Log.e(TAG, "TTS Initialization failed!")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        clearCensorViews()
        screenshotRunnable?.let { screenshotHandler?.removeCallbacks(it) }
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        if (::session.isInitialized) { session.close() }
        if (::ortEnvironment.isInitialized) { ortEnvironment.close() }
    }

    companion object {
        var instance: ConsciousnessAccessibilityService? = null
        private const val TAG = "ConsciousnessService"
        private val LABELS = arrayOf(
            "FEMALE_GENITALIA_COVERED", "FACE_FEMALE", "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED",
            "FEMALE_GENITALIA_EXPOSED", "MALE_BREAST_EXPOSED", "ANUS_EXPOSED", "FEET_EXPOSED",
            "BELLY_COVERED", "FEET_COVERED", "ARMPITS_COVERED", "ARMPITS_EXPOSED", "FACE_MALE",
            "BELLY_EXPOSED", "MALE_GENITALIA_EXPOSED", "ANUS_COVERED", "FEMALE_BREAST_COVERED",
            "BUTTOCKS_COVERED"
        )
        private val SENSITIVE_LABELS = setOf(
            "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED", "FEMALE_GENITALIA_EXPOSED",
            "ANUS_EXPOSED", "MALE_GENITALIA_EXPOSED"
        )
    }
}
