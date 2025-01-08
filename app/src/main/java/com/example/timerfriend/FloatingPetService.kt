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
import android.widget.ImageView
import android.widget.TextView

class FloatingPetService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var timerView: View? = null
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

    private fun showTimer(minutes: Int) {
        // Remove existing timer if any
        removeTimer()

        // Inflate timer view
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        timerView = inflater.inflate(R.layout.layout_timer_pill, null)

        // Set up window parameters for timer
        val timerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Position timer above the clock
        timerParams.gravity = Gravity.TOP or Gravity.START
        timerParams.x = floatingViewParams.x
        timerParams.y = floatingViewParams.y - 100  // 100 pixels above the clock

        // Add timer view to window
        windowManager.addView(timerView, timerParams)

        // Start countdown
        remainingTimeMillis = minutes * 60 * 1000L
        startCountdown()
    }

    private fun startCountdown() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (remainingTimeMillis > 0) {
                    val minutes = remainingTimeMillis / 60000
                    val seconds = (remainingTimeMillis % 60000) / 1000
                    val timeText = String.format("%d:%02d", minutes, seconds)
                    
                    timerView?.findViewById<TextView>(R.id.timerText)?.text = timeText
                    
                    remainingTimeMillis -= 1000
                    timerHandler.postDelayed(this, 1000)
                } else {
                    removeTimer()
                }
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun removeTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // View might already be removed
            }
        }
        timerView = null
    }

    fun handleTimerCommand(command: String) {
        if (command.contains("timer") && command.contains("minute")) {
            val minutes = command.split(" ").find { it.matches(Regex("\\d+")) }?.toIntOrNull()
            minutes?.let { showTimer(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_TIMER" -> {
                val minutes = intent.getIntExtra("TIMER_MINUTES", 0)
                if (minutes > 0) {
                    showTimer(minutes)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeTimer()
        if (::floatingView.isInitialized && ::windowManager.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
