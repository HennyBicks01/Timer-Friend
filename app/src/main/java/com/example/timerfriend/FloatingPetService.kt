package com.example.timerfriend

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.hypot
import java.util.Locale

/**
 * A foreground service that displays a floating timer window over other apps.
 * Features include:
 * - Draggable timer window
 * - Visual countdown with both pie chart and digital display
 * - Drag-to-dismiss functionality with animation
 * - Compatibility with different Android versions
 */
class FloatingPetService : Service() {
    // Window management
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var dismissTarget: View
    private lateinit var timerClockView: TimerClockView
    private lateinit var timerTextView: TextView
    
    // Timer management
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var remainingTimeMillis: Long = 0
    
    // Touch handling
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var isDragging = false
    private var dismissTargetShowing = false
    private var isViewAdded = false
    private var startDragY = 0f
    
    // Window layout parameters
    private lateinit var floatingViewParams: LayoutParams
    private lateinit var dismissTargetParams: LayoutParams

    /**
     * This service doesn't support binding
     */
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /**
     * Initializes the floating window and dismiss target.
     * Sets up window parameters and touch listeners.
     */
    override fun onCreate() {
        super.onCreate()
        
        // Check if we have overlay permission
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Initialize floating view with proper root
        val parent = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_pet, parent, true)
        timerClockView = floatingView.findViewById(R.id.pet_image)
        timerTextView = floatingView.findViewById(R.id.timer_text)

        // Initialize dismiss target with proper root
        val dismissParent = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dismissTarget = LayoutInflater.from(this).inflate(R.layout.layout_dismiss_target, dismissParent, true)
        dismissTarget.alpha = 0f

        setupWindowParameters()
        setupTouchListener()
    }

    /**
     * Sets up window parameters for both the floating view and dismiss target,
     * handling Android version compatibility.
     */
    private fun setupWindowParameters() {
        // Set up the WindowManager LayoutParams for floating view
        floatingViewParams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.TYPE_APPLICATION_OVERLAY,
                LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.TYPE_SYSTEM_ALERT,
                LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }

        floatingViewParams.gravity = Gravity.TOP or Gravity.START
        floatingViewParams.x = 0
        floatingViewParams.y = 100

        // Set up the WindowManager LayoutParams for dismiss target
        dismissTargetParams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.TYPE_APPLICATION_OVERLAY,
                LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.TYPE_SYSTEM_ALERT,
                LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }

        dismissTargetParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        dismissTargetParams.y = 150 // Distance from bottom
    }

    /**
     * Sets up touch handling for the floating view, including:
     * - Click detection
     * - Drag movement
     * - Dismiss target interaction
     */
    private fun setupTouchListener() {
        var initialTouchX = 0f
        var initialTouchY = 0f
        var wasClick = false

        floatingView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    wasClick = true
                    initialX = floatingViewParams.x
                    initialY = floatingViewParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    startDragY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    // If moved more than slight threshold, it's not a click
                    if (hypot(deltaX, deltaY) > 10) {
                        wasClick = false
                    }

                    // Show dismiss target when dragging starts
                    if (!dismissTargetShowing && !wasClick) {
                        showDismissTarget()
                        isDragging = true
                    }

                    // Update floating view position
                    floatingViewParams.x = initialX + deltaX.toInt()
                    floatingViewParams.y = initialY + deltaY.toInt()
                    windowManager.updateViewLayout(floatingView, floatingViewParams)

                    if (isDragging) {
                        // Check if over dismiss target
                        val dismissBounds = getRectForView(dismissTarget)
                        val floatingBounds = getRectForView(floatingView)
                        
                        if (floatingBounds.intersect(dismissBounds)) {
                            animateAndRemove()
                            return@setOnTouchListener true
                        }

                        // Fade dismiss target based on vertical drag
                        val dragProgress = (event.rawY - startDragY) / 300f // 300dp travel distance
                        val alpha = (dragProgress).coerceIn(0f, 1f)
                        dismissTarget.alpha = alpha
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (wasClick) {
                        view.performClick()
                    }
                    if (isDragging) {
                        isDragging = false
                        hideDismissTarget()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        hideDismissTarget()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Animates the floating view when being dismissed.
     * Scales down and moves towards the dismiss target.
     */
    private fun animateAndRemove() {
        // Get the location of the dismiss target
        val targetLocation = IntArray(2)
        dismissTarget.getLocationOnScreen(targetLocation)
        val targetCenterX = targetLocation[0] + dismissTarget.width / 2
        val targetCenterY = targetLocation[1] + dismissTarget.height / 2

        // Get the current location of the floating view
        val currentLocation = IntArray(2)
        floatingView.getLocationOnScreen(currentLocation)
        val startX = currentLocation[0]
        val startY = currentLocation[1]

        // Create scale animation
        val scaleAnimator = ValueAnimator.ofFloat(1f, 0f)
        scaleAnimator.duration = 300
        scaleAnimator.interpolator = AccelerateInterpolator()

        scaleAnimator.addUpdateListener { animator ->
            val scale = animator.animatedValue as Float
            floatingView.scaleX = scale
            floatingView.scaleY = scale

            // Move towards target center
            floatingViewParams.x = (startX + (targetCenterX - startX) * (1 - scale)).toInt()
            floatingViewParams.y = (startY + (targetCenterY - startY) * (1 - scale)).toInt()
            try {
                windowManager.updateViewLayout(floatingView, floatingViewParams)
            } catch (e: IllegalArgumentException) {
                // View might have been removed
            }
        }

        scaleAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                removeFloatingView()
                stopSelf()
            }
        })

        scaleAnimator.start()
    }

    /**
     * Gets the screen bounds of a view.
     */
    private fun getRectForView(view: View): android.graphics.Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return android.graphics.Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )
    }

    /**
     * Shows the dismiss target with proper window parameters
     */
    private fun showDismissTarget() {
        if (!dismissTargetShowing) {
            windowManager.addView(dismissTarget, dismissTargetParams)
            dismissTargetShowing = true
        }
    }

    /**
     * Hides and removes the dismiss target from the window
     */
    private fun hideDismissTarget() {
        if (dismissTargetShowing) {
            windowManager.removeView(dismissTarget)
            dismissTargetShowing = false
        }
    }

    /**
     * Shows the floating view with proper window parameters
     */
    private fun showFloatingView() {
        if (!isViewAdded) {
            windowManager.addView(floatingView, floatingViewParams)
            isViewAdded = true
        }
    }

    /**
     * Removes the floating view and dismiss target from the window
     */
    private fun removeFloatingView() {
        if (isViewAdded) {
            windowManager.removeView(floatingView)
            isViewAdded = false
        }
        hideDismissTarget()
    }

    /**
     * Starts a new timer with the specified duration
     * @param minutes Duration in minutes
     */
    private fun startTimer(minutes: Int) {
        // Cancel any existing timer
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        
        remainingTimeMillis = minutes * 60 * 1000L
        timerTextView.visibility = View.VISIBLE
        timerClockView.setTotalMinutes(minutes)
        showFloatingView() // Show the view when timer starts
        startCountdown()
    }

    /**
     * Starts the countdown process, updating both visual elements
     * (pie chart and digital display) every second
     */
    private fun startCountdown() {
        val startTime = remainingTimeMillis

        timerRunnable = object : Runnable {
            override fun run() {
                if (remainingTimeMillis > 0) {
                    // Update pie timer progress (1.0 to 0.0)
                    val progress = remainingTimeMillis.toFloat() / startTime.toFloat()
                    timerClockView.setProgress(progress)

                    // Update digital display
                    val minutes = remainingTimeMillis / 1000 / 60
                    val seconds = (remainingTimeMillis / 1000) % 60
                    val timeStr = String.format(Locale.US, "%d:%02d", minutes, seconds)
                    timerTextView.text = timeStr

                    remainingTimeMillis -= 1000
                    timerHandler.postDelayed(this, 1000)
                } else {
                    timerClockView.setProgress(0f)
                    timerTextView.visibility = View.GONE
                    removeFloatingView()
                }
            }
        }

        timerHandler.post(timerRunnable!!)
    }

    /**
     * Handles commands sent to the service
     * Currently supports:
     * - START_TIMER: Starts a new timer with specified minutes
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_TIMER" -> {
                val minutes = intent.getIntExtra("TIMER_MINUTES", 0)
                if (minutes > 0) {
                    startTimer(minutes)
                }
            }
        }
        return START_STICKY
    }

    /**
     * Cleans up resources when the service is destroyed
     */
    override fun onDestroy() {
        super.onDestroy()
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        if (::windowManager.isInitialized) {
            removeFloatingView()
        }
    }
}
