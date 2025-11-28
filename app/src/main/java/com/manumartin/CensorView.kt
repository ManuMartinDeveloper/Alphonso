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
    private var isLockedDown = false

    init {
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isLockedDown) {
            canvas.drawColor(Color.BLACK)
        } else {
            for (rect in blockedAreas) {
                canvas.drawRect(rect, paint)
            }
        }
    }

    fun censorAreas(rects: List<Rect>) {
        isLockedDown = false
        blockedAreas.clear()
        blockedAreas.addAll(rects)
        if (rects.isNotEmpty()) {
            visibility = View.VISIBLE
            invalidate()
        } else {
            visibility = View.GONE
        }
    }

    fun triggerLockdown() {
        isLockedDown = true
        blockedAreas.clear()
        visibility = View.VISIBLE
        invalidate()
    }

    fun clear() {
        isLockedDown = false
        blockedAreas.clear()
        visibility = View.GONE
    }
}