package com.example.timerfriend

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class TimerClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f // 0f to 1f
    private var totalMinutes = 60f // Default to 60 minutes
    
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val progressPaint = Paint().apply {
        color = Color.parseColor("#FF4081") // Pink color
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val bounds = Rect()

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    fun setTotalMinutes(minutes: Int) {
        totalMinutes = minutes.toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f * 0.8f

        // Draw background circle
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        // Draw progress arc
        val rect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        
        // Calculate the sweep angle based on total minutes
        val maxSweepAngle = (totalMinutes / 60f) * 360f
        val currentSweepAngle = maxSweepAngle * progress
        
        // Draw the progress arc (pie slice)
        canvas.drawArc(
            rect,
            -90f, // Start from top
            currentSweepAngle,
            true,
            progressPaint
        )

        // Draw numbers
        for (i in 0..11) {
            val number = (i * 5).toString()
            val angle = Math.toRadians(i * 30.0 - 90.0) // -90 to start at top
            
            textPaint.getTextBounds(number, 0, number.length, bounds)
            
            val x = centerX + (radius * 0.85f) * Math.cos(angle).toFloat()
            val y = centerY + (radius * 0.85f) * Math.sin(angle).toFloat() + (bounds.height() / 2)
            
            canvas.drawText(number, x, y, textPaint)
        }
    }
}
