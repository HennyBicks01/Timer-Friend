package com.example.timerfriend

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.view.WindowManager.LayoutParams
import android.widget.TextView

class FloatingPetService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var timerClockView: TimerClockView
    private lateinit var timerTextView: TextView
    private var timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var remainingTimeMillis: Long = 0
    private var initialX: Int = 0
    private var initialY: Int = 0
    private lateinit var floatingViewParams: WindowManager.LayoutParams

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

        // Set up the WindowManager LayoutParams
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

        // Add the view to the window
        windowManager.addView(floatingView, floatingViewParams)

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
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingViewParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    floatingViewParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, floatingViewParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun startTimer(minutes: Int) {
        // Cancel any existing timer
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        
        remainingTimeMillis = minutes * 60 * 1000L
        timerTextView.visibility = View.VISIBLE
        timerClockView.setTotalMinutes(minutes)
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
        if (::windowManager.isInitialized && ::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
