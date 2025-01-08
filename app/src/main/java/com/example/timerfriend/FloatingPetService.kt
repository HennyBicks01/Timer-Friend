package com.example.timerfriend

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.view.WindowManager.LayoutParams
import android.view.animation.AccelerateInterpolator
import android.widget.TextView
import kotlin.math.hypot

class FloatingPetService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var dismissTarget: View
    private lateinit var timerClockView: TimerClockView
    private lateinit var timerTextView: TextView
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var remainingTimeMillis: Long = 0
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var isDragging = false
    private var dismissTargetShowing = false
    private var isViewAdded = false
    private lateinit var floatingViewParams: WindowManager.LayoutParams
    private lateinit var dismissTargetParams: WindowManager.LayoutParams
    private var startDragY = 0f

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Initialize floating view
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_pet, null)
        timerClockView = floatingView.findViewById(R.id.pet_image)
        timerTextView = floatingView.findViewById(R.id.timer_text)

        // Initialize dismiss target
        dismissTarget = LayoutInflater.from(this).inflate(R.layout.layout_dismiss_target, null)
        dismissTarget.alpha = 0f

        // Set up the WindowManager LayoutParams for floating view
        floatingViewParams = WindowManager.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        floatingViewParams.gravity = Gravity.TOP or Gravity.START
        floatingViewParams.x = 0
        floatingViewParams.y = 100

        // Set up the WindowManager LayoutParams for dismiss target
        dismissTargetParams = WindowManager.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        dismissTargetParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        dismissTargetParams.y = 150 // Distance from bottom

        // Set up touch listener
        setupTouchListener()
    }

    private fun setupTouchListener() {
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
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
                    
                    // Show dismiss target when dragging starts
                    if (!dismissTargetShowing && hypot(deltaX, deltaY) > 10) {
                        showDismissTarget()
                        isDragging = true
                    }

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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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

    private fun showDismissTarget() {
        if (!dismissTargetShowing) {
            windowManager.addView(dismissTarget, dismissTargetParams)
            dismissTargetShowing = true
        }
    }

    private fun hideDismissTarget() {
        if (dismissTargetShowing) {
            windowManager.removeView(dismissTarget)
            dismissTargetShowing = false
        }
    }

    private fun showFloatingView() {
        if (!isViewAdded) {
            windowManager.addView(floatingView, floatingViewParams)
            isViewAdded = true
        }
    }

    private fun removeFloatingView() {
        if (isViewAdded) {
            windowManager.removeView(floatingView)
            isViewAdded = false
        }
        hideDismissTarget()
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        remainingTimeMillis = 0
        timerClockView.setProgress(0f)
        timerTextView.visibility = View.GONE
        removeFloatingView()
    }

    private fun startTimer(minutes: Int) {
        // Cancel any existing timer
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        
        remainingTimeMillis = minutes * 60 * 1000L
        timerTextView.visibility = View.VISIBLE
        timerClockView.setTotalMinutes(minutes)
        showFloatingView() // Show the view when timer starts
        startCountdown()
    }

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
                    val timeStr = String.format("%d:%02d", minutes, seconds)
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

    fun handleTimerCommand(command: String) {
        if (command.contains("timer") && command.contains("minute")) {
            val minutes = command.split(" ").find { it.matches(Regex("\\d+")) }?.toIntOrNull()
            minutes?.let { startTimer(it) }
        }
    }

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

    override fun onDestroy() {
        super.onDestroy()
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        if (::windowManager.isInitialized) {
            removeFloatingView()
        }
    }
}
