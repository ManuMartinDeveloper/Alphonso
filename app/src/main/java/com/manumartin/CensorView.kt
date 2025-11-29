package com.manumartin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

class CensorView(context: Context) : View(context) {

    private val paint = Paint()
    private val blockedAreas = mutableListOf<Rect>()
    private var isGloballyLockedDown = false

    init {
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isGloballyLockedDown) {
            canvas.drawColor(Color.BLACK)
        } else {
            for (rect in blockedAreas) {
                canvas.drawRect(rect, paint)
            }
        }
    }

    fun censorAreas(rects: List<Rect>) {
        blockedAreas.clear()
        blockedAreas.addAll(rects)
        if (rects.isNotEmpty() && !isGloballyLockedDown) {
            visibility = View.VISIBLE
            invalidate()
        } else if (rects.isEmpty() && !isGloballyLockedDown) {
            visibility = View.GONE
        }
    }

    fun triggerLockdown() {
        isGloballyLockedDown = true
        visibility = View.VISIBLE
        invalidate()
    }

    fun clearGlobalLockdown() {
        if (isGloballyLockedDown) {
            isGloballyLockedDown = false
            if (blockedAreas.isEmpty()) {
                visibility = View.GONE
            } else {
                invalidate()
            }
        }
    }

    fun clear() {
        blockedAreas.clear()
        if (!isGloballyLockedDown) {
            visibility = View.GONE
        }
    }
}
