package com.alphonso

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class CensorView(context: Context) : View(context) {

    private val paint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }
    
    private val overlayPaint = Paint().apply {
        color = Color.BLACK
        alpha = 0
    }

    private val blurredBitmaps = mutableListOf<Bitmap>()
    private val drawPositions = mutableListOf<Rect>()

    private var isGloballyLockedDown = false
    private var globalOverlayAlpha = 0

    private var fadeAnimator: ValueAnimator? = null

    init {
        visibility = View.GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isGloballyLockedDown) {
            canvas.drawColor(Color.argb(globalOverlayAlpha, 0, 0, 0))
        } else {
            // Draw each blurred patch
            for (i in drawPositions.indices) {
                if (i < blurredBitmaps.size && !blurredBitmaps[i].isRecycled) {
                    val rect = drawPositions[i]
                    canvas.drawBitmap(blurredBitmaps[i], null, rect, paint)
                }
            }
        }
    }

    fun censorAreas(rects: List<Rect>, sourceBitmap: Bitmap?) {
        if (rects.isEmpty() || sourceBitmap == null || sourceBitmap.isRecycled) {
            if (!isGloballyLockedDown) {
                smoothHide()
            }
            return
        }

        // Clean up old bitmaps
        clearBitmaps()
        drawPositions.addAll(rects)

        for (rect in rects) {
            val safeLeft = rect.left.coerceIn(0, sourceBitmap.width - 1)
            val safeTop = rect.top.coerceIn(0, sourceBitmap.height - 1)
            val safeRight = rect.right.coerceIn(safeLeft + 1, sourceBitmap.width)
            val safeBottom = rect.bottom.coerceIn(safeTop + 1, sourceBitmap.height)

            val safeWidth = safeRight - safeLeft
            val safeHeight = safeBottom - safeTop

            if (safeWidth > 0 && safeHeight > 0) {
                try {
                    val cropped = Bitmap.createBitmap(sourceBitmap, safeLeft, safeTop, safeWidth, safeHeight)

                    // Smoother pixelation factor
                    val pixelationFactor = 0.15f 
                    val smallW = (safeWidth * pixelationFactor).toInt().coerceAtLeast(8)
                    val smallH = (safeHeight * pixelationFactor).toInt().coerceAtLeast(8)

                    val small = Bitmap.createScaledBitmap(cropped, smallW, smallH, true)
                    // We don't need to scale back up here, we can use drawBitmap(small, null, rect, paint)
                    blurredBitmaps.add(small)
                    cropped.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (drawPositions.isNotEmpty() && !isGloballyLockedDown) {
            if (visibility != View.VISIBLE) {
                visibility = View.VISIBLE
                alpha = 0f
                animate().alpha(1f).setDuration(200).start()
            }
            invalidate()
        }
    }

    private fun smoothHide() {
        if (visibility == View.VISIBLE) {
            animate().alpha(0f).setDuration(300).withEndAction {
                visibility = View.GONE
                clearBitmaps()
                drawPositions.clear()
            }.start()
        }
    }

    fun triggerLockdown() {
        isGloballyLockedDown = true
        visibility = View.VISIBLE
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofInt(0, 255).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                globalOverlayAlpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    fun clearGlobalLockdown() {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofInt(globalOverlayAlpha, 0).apply {
            duration = 500
            addUpdateListener {
                globalOverlayAlpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
        isGloballyLockedDown = false
        if (drawPositions.isEmpty()) {
            smoothHide()
        }
    }

    private fun clearBitmaps() {
        for (bmp in blurredBitmaps) {
            if (!bmp.isRecycled) bmp.recycle()
        }
        blurredBitmaps.clear()
    }

    fun clear() {
        if (isGloballyLockedDown) return
        smoothHide()
    }
}