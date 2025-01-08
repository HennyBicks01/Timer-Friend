package com.example.timerfriend

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A custom view that displays a circular timer with both a pie chart progress indicator
 * and numerical markers around the circumference. The view shows time progress both
 * through a filling arc and digital numbers positioned at 5-minute intervals.
 */
class TimerClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Progress from 0 (empty) to 1 (full)
    private var progress = 0f
    // Total duration of the timer in minutes
    private var totalMinutes = 60f

    // Paint object for drawing the minute numbers
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    // Paint object for the background circle
    private val backgroundPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Paint object for the progress arc
    private val progressPaint = Paint().apply {
        color = Color.parseColor("#FF4081") // Material Design pink
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Pre-allocated objects to avoid object creation during drawing
    private val bounds = Rect()                    // For measuring text
    private val arcRect = RectF()                  // For drawing the progress arc
    private val numberCache = Array(12) { (it * 5).toString() }  // Cache of minute numbers
    private val angleCache = Array(12) { Math.toRadians(it * 30.0 - 90.0) }  // Cache of number positions
    private var centerX = 0f                       // Center X coordinate
    private var centerY = 0f                       // Center Y coordinate
    private var radius = 0f                        // Radius of the clock

    /**
     * Sets the current progress of the timer.
     * @param value Progress value between 0 and 1
     */
    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    /**
     * Sets the total duration of the timer in minutes.
     * @param minutes Total number of minutes
     */
    fun setTotalMinutes(minutes: Int) {
        totalMinutes = minutes.toFloat()
        invalidate()
    }

    /**
     * Called when the size of the view changes. Updates the dimensions and
     * recalculates the drawing bounds.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = min(w, h) / 2f * 0.8f  // Use 80% of the available space
        
        // Update the rectangle used for drawing the progress arc
        arcRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
    }

    /**
     * Draws the timer view, including:
     * 1. Background circle
     * 2. Progress arc (pie slice)
     * 3. Minute numbers around the circumference
     */
    override fun onDraw(canvas: Canvas) {
        // Draw the background circle
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
        
        // Calculate and draw the progress arc
        val maxSweepAngle = (totalMinutes / 60f) * 360f  // Scale sweep angle based on total minutes
        val currentSweepAngle = maxSweepAngle * progress
        canvas.drawArc(
            arcRect,
            -90f,  // Start from top (12 o'clock position)
            currentSweepAngle,
            true,
            progressPaint
        )

        // Draw the minute numbers using cached values
        val numberRadius = radius * 0.85f  // Position numbers slightly inside the circle
        for (i in 0..11) {
            val number = numberCache[i]
            val angle = angleCache[i]
            
            // Get text bounds for vertical centering
            textPaint.getTextBounds(number, 0, number.length, bounds)
            
            // Calculate position using trigonometry
            val x = centerX + numberRadius * cos(angle).toFloat()
            val y = centerY + numberRadius * sin(angle).toFloat() + (bounds.height() / 2)
            
            canvas.drawText(number, x, y, textPaint)
        }
    }
}
