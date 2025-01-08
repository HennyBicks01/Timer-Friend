package com.example.timerfriend

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class TimerClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val circlePaint = Paint().apply {
        color = Color.WHITE
        alpha = 128  // 50% opacity
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val bounds = Rect()

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val innerRadius = (width.coerceAtMost(height) / 2f) * 0.8f  // Inner circle radius (smaller)
        val outerRadius = innerRadius * 1.05f  // Outer circle radius for text (larger ratio)

        // Draw translucent outer circle
        canvas.drawCircle(centerX, centerY, outerRadius, circlePaint)

        // Calculate scale and translation to center the drawable
        val scale = (innerRadius * 2) / width.coerceAtMost(height)
        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)
        
        // Draw the timer drawable
        super.onDraw(canvas)
        canvas.restore()

        // Draw numbers
        for (i in 0..11) {
            val number = (i * 5).toString()
            val angle = Math.toRadians(i * 30.0 - 90.0) // -90 to start at top
            
            textPaint.getTextBounds(number, 0, number.length, bounds)
            
            val x = centerX + (outerRadius * 0.85f) * Math.cos(angle).toFloat()
            val y = centerY + (outerRadius * 0.85f) * Math.sin(angle).toFloat() + (bounds.height() / 2)
            
            canvas.drawText(number, x, y, textPaint)
        }
    }
}
