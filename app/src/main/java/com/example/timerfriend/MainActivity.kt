package com.example.timerfriend

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.view.LayoutInflater
import android.widget.TextView
import java.util.regex.Pattern
import android.util.Log

class MainActivity : AppCompatActivity() {
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1
        private const val EXTRA_LENGTH = "android.intent.extra.alarm.LENGTH"
        private val TIMER_PATTERN = Pattern.compile("/timer/(\\d+)")
    }

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.startButton).setOnClickListener {
            startFloatingPet()
        }

        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            startFloatingService()
        }

        // Handle intent if activity was launched with one
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    val duration = extractDurationFromUri(uri)
                    if (duration != null) {
                        showTimerBottomSheet(duration)
                    }
                }
            }
            "android.intent.action.SET_TIMER" -> {
                val duration = intent.getSerializableExtra("android.intent.extra.alarm.LENGTH")
                if (duration != null) {
                    try {
                        val durationLong = when (duration) {
                            is Int -> duration.toLong()
                            is Long -> duration
                            else -> null
                        }
                        if (durationLong != null) {
                            // Convert to minutes (divide by 60)
                            val minutes = (durationLong / 60).toInt()
                            Log.d(TAG, "Received timer duration: $durationLong seconds, converting to $minutes minutes")

                            // Send command to floating service
                            val serviceIntent = Intent(this, FloatingPetService::class.java)
                            serviceIntent.action = "START_TIMER"
                            serviceIntent.putExtra("TIMER_MINUTES", minutes)
                            startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling timer intent", e)
                    }
                }
            }
        }
    }

    private fun extractDurationFromUri(uri: Uri): Int? {
        val path = uri.path ?: return null
        val matcher = TIMER_PATTERN.matcher(path)
        return if (matcher.find()) {
            matcher.group(1)?.toIntOrNull()
        } else null
    }

    private fun showTimerBottomSheet(durationSeconds: Int) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.timer_bottom_sheet, null)
        
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        
        view.findViewById<TextView>(R.id.timerText).text = 
            String.format("%02d:%02d", minutes, seconds)
        
        view.findViewById<Button>(R.id.startTimerButton).setOnClickListener {
            startFloatingPet()
            // TODO: Set the timer duration in the floating service
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun startFloatingPet() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        } else {
            startService(Intent(this, FloatingPetService::class.java))
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            }
        }
    }

    private fun startFloatingService() {
        val serviceIntent = Intent(this, FloatingPetService::class.java)
        startService(serviceIntent)
    }
}
