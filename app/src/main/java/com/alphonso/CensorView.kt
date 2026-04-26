package com.alphonso

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

class CensorView(context: Context) : View(context) {

    private val paint = Paint()
    // We store the blurred image patches here
    private val blurredBitmaps = mutableListOf<Bitmap>()
    // We keep the rects to know where to draw them
    private val drawPositions = mutableListOf<Rect>()

    private var isGloballyLockedDown = false

    init {
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isGloballyLockedDown) {
            // Full screen blackout for lockdown
            canvas.drawColor(Color.BLACK)
        } else {
            // Draw each blurred patch at its correct position
            for (i in drawPositions.indices) {
                if (i < blurredBitmaps.size && !blurredBitmaps[i].isRecycled) {
                    val rect = drawPositions[i]
                    canvas.drawBitmap(blurredBitmaps[i], rect.left.toFloat(), rect.top.toFloat(), paint)
                }
            }
        }
    }

    /**
     * New function that takes the source image (screenshot) to create blurs.
     */
    fun censorAreas(rects: List<Rect>, sourceBitmap: Bitmap?) {
        // Clean up old bitmaps
        for (bmp in blurredBitmaps) {
            if (!bmp.isRecycled) bmp.recycle()
        }
        blurredBitmaps.clear()
        drawPositions.clear()

        if (rects.isEmpty() || sourceBitmap == null || sourceBitmap.isRecycled) {
            if (!isGloballyLockedDown) visibility = View.GONE
            return
        }

        drawPositions.addAll(rects)

        for (rect in rects) {
            // Safety check: ensure rect fits inside image
            val safeLeft = rect.left.coerceIn(0, sourceBitmap.width - 1)
            val safeTop = rect.top.coerceIn(0, sourceBitmap.height - 1)
            val safeRight = rect.right.coerceIn(safeLeft + 1, sourceBitmap.width)
            val safeBottom = rect.bottom.coerceIn(safeTop + 1, sourceBitmap.height)

            val safeWidth = safeRight - safeLeft
            val safeHeight = safeBottom - safeTop

            if (safeWidth > 0 && safeHeight > 0) {
                try {
                    // 1. Crop the sensitive area
                    val cropped = Bitmap.createBitmap(sourceBitmap, safeLeft, safeTop, safeWidth, safeHeight)

                    // 2. PIXELATION EFFECT (Granular Blur)
                    // We shrink it aggressively.
                    // 0.04f means a 100px wide object becomes just 4 giant pixels.
                    val pixelationFactor = 0.04f

                    val smallW = (safeWidth * pixelationFactor).toInt().coerceAtLeast(2)
                    val smallH = (safeHeight * pixelationFactor).toInt().coerceAtLeast(2)

                    val small = Bitmap.createScaledBitmap(cropped, smallW, smallH, true)

                    // 3. Scale back up WITHOUT filtering
                    // 'filter = false' is the key! It keeps the pixels sharp and blocky (granular).
                    val pixelated = Bitmap.createScaledBitmap(small, safeWidth, safeHeight, false)

                    blurredBitmaps.add(pixelated)

                    cropped.recycle()
                    small.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (drawPositions.isNotEmpty() && !isGloballyLockedDown) {
            visibility = View.VISIBLE
            invalidate()
        }
    }

    fun triggerLockdown() {
        isGloballyLockedDown = true
        visibility = View.VISIBLE
        invalidate()
    }

    fun clearGlobalLockdown() {
        isGloballyLockedDown = false
        if (drawPositions.isEmpty()) {
            visibility = View.GONE
        }
        invalidate()
    }

    fun clear() {
        // Clean up
        for (bmp in blurredBitmaps) {
            if (!bmp.isRecycled) bmp.recycle()
        }
        blurredBitmaps.clear()
        drawPositions.clear()

        if (!isGloballyLockedDown) {
            visibility = View.GONE
        }
    }
}