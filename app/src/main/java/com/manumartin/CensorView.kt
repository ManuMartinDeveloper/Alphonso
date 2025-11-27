package com.manumartin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class CensorView(context: Context) : View(context) {

    private val paint = Paint()

    init {
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }
}