package com.manumartin

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class ScreenCaptureService : Service(), TextToSpeech.OnInitListener {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var imageReader: ImageReader
    private lateinit var ortEnvironment: OrtEnvironment
    private lateinit var session: OrtSession
    private lateinit var tts: TextToSpeech
    private var lastPrayerTime = 0L
    private var warningCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private val isProcessing = AtomicBoolean(false)

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            stopScreenCapture()
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        ortEnvironment = OrtEnvironment.getEnvironment()
        tts = TextToSpeech(this, this)
        try {
            val model = resources.assets.open("nudenet_320n.onnx").readBytes()
            session = ortEnvironment.createSession(model)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        createNotificationChannel()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "The Language specified is not supported!")
            }
        } else {
            Log.e(TAG, "TTS Initialization failed!")
        }
    }

    @SuppressLint("NewApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)

            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
            if (resultCode == Activity.RESULT_OK && data != null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                mediaProjection?.registerCallback(mediaProjectionCallback, Handler())
                startScreenCapture()
            }
        } else if (intent?.action == ACTION_STOP) {
            stopScreenCapture()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startScreenCapture() {
        val metrics = resources.displayMetrics
        val density = metrics.densityDpi
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                if (isProcessing.compareAndSet(false, true)) {
                    try {
                        processImage(image)
                    } finally {
                        image.close()
                        isProcessing.set(false)
                    }
                } else {
                    image.close()
                }
            }
        }, null)
    }

    private fun processImage(image: Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 320, 320, true)
        val inputTensor = preProcess(resizedBitmap)
        val tensor = OnnxTensor.createTensor(ortEnvironment, inputTensor, longArrayOf(1, 3, 320, 320))
        val results = session.run(Collections.singletonMap(session.inputNames.iterator().next(), tensor))
        processOutputs(results, image.width, image.height)
    }

    private fun processOutputs(results: OrtSession.Result, originalWidth: Int, originalHeight: Int) {
        val accessibilityService = ConsciousnessAccessibilityService.instance
        accessibilityService?.clearCensorViews()

        if (accessibilityService == null) {
            Log.w(TAG, "AccessibilityService is not running, cannot draw boxes.")
            return
        }

        try {
            val outputTensor = results[0].value as Array<Array<FloatArray>> // Shape is [1, 22, 8400]
            val detections = outputTensor[0] // Shape is [22, 8400]

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

            for (i in 0 until numDetections) {
                val detection = transposedDetections[i]
                val scores = detection.sliceArray(4..21)
                val maxScore = scores.maxOrNull() ?: 0f
                val classIndex = scores.indexOfFirst { it == maxScore }

                if (maxScore > 0.2f && classIndex != -1) {
                    val label = LABELS[classIndex]

                    if (label == "FACE_FEMALE") {
                        continue // Skip female faces
                    }

                    val cx = detection[0]
                    val cy = detection[1]
                    val w = detection[2]
                    val h = detection[3]
                    val x1 = (cx - w / 2) * scaleX
                    val y1 = (cy - h / 2) * scaleY
                    val x2 = (cx + w / 2) * scaleX
                    val y2 = (cy + h / 2) * scaleY

                    boxes.add(
                        Detection(RectF(x1, y1, x2, y2), maxScore, classIndex)
                    )

                    if (label in SENSITIVE_LABELS) {
                        sensitiveContentInFrame = true
                        if (maxScore > 0.75f) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastPrayerTime > 5000) { // 5-second cooldown
                                lastPrayerTime = currentTime
                                tts.speak("Hail Mary Full of Grace, the Lord is with you, Blessed are you amongst women, Blessed is the fruit of thy womb Jesus.", TextToSpeech.QUEUE_FLUSH, null, "prayer")
                                Log.d(TAG, "Reciting prayer due to detection of $label")
                            }
                        }
                    }
                }
            }

            if (sensitiveContentInFrame) {
                warningCount++
                handler.post {
                    Toast.makeText(applicationContext, "Warning: Adult content detected ($warningCount)", Toast.LENGTH_SHORT).show()
                }
            }

            val finalBoxes = nonMaxSuppression(boxes, 0.45f)

            finalBoxes.forEach {
                accessibilityService.addCensorView(
                    Rect(it.box.left.toInt(), it.box.top.toInt(), it.box.right.toInt(), it.box.bottom.toInt())
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing model outputs: ${e.message}", e)
        }
    }
    private data class Detection(val box: RectF, val score: Float, val classIndex: Int)

    private fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sortedDetections = detections.sortedByDescending { it.score }
        val selectedDetections = mutableListOf<Detection>()

        val active = BooleanArray(sortedDetections.size) { true }
        var numActive = active.size

        for (i in sortedDetections.indices) {
            if (active[i]) {
                val boxA = sortedDetections[i]
                selectedDetections.add(boxA)

                for (j in i + 1 until sortedDetections.size) {
                    if (active[j]) {
                        val boxB = sortedDetections[j]
                        if (calculateIoU(boxA.box, boxB.box) > iouThreshold) {
                            active[j] = false
                            numActive--
                        }
                    }
                }
            }
        }
        return selectedDetections
    }

    private fun calculateIoU(boxA: RectF, boxB: RectF): Float {
        val xA = max(boxA.left, boxB.left)
        val yA = max(boxA.top, boxB.top)
        val xB = min(boxA.right, boxB.right)
        val yB = min(boxA.bottom, boxB.bottom)

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

    private fun stopScreenCapture() {
        virtualDisplay?.release()
        imageReader.close()
        mediaProjection?.stop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Consciousness")
            .setContentText("Monitoring your screen.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        session.close()
        ortEnvironment.close()
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ScreenCaptureServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
        private val LABELS = arrayOf(
            "FEMALE_GENITALIA_COVERED", "FACE_FEMALE", "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED",
            "FEMALE_GENITALIA_EXPOSED", "MALE_BREAST_EXPOSED", "ANUS_EXPOSED", "FEET_EXPOSED",
            "BELLY_COVERED", "FEET_COVERED", "ARMPITS_COVERED", "ARMPITS_EXPOSED", "FACE_MALE",
            "BELLY_EXPOSED", "MALE_GENITALIA_EXPOSED", "ANUS_COVERED", "FEMALE_BREAST_COVERED",
            "BUTTOCKS_COVERED"
        )
        private val SENSITIVE_LABELS = setOf(
            "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED", "FEMALE_GENITALIA_EXPOSED",
            "MALE_BREAST_EXPOSED", "ANUS_EXPOSED", "MALE_GENITALIA_EXPOSED"
        )
    }
}
