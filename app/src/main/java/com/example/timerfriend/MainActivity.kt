package com.example.timerfriend

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import java.util.regex.Pattern

class MainActivity : Activity() {
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1
        private const val EXTRA_LENGTH = "android.intent.extra.alarm.LENGTH"
        private val TIMER_PATTERN = Pattern.compile("/timer/(\\d+)")
    }

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            startFloatingService()
            handleIntent(intent)
            finish() // Close activity immediately
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish() // Close activity immediately
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    val duration = extractDurationFromUri(uri)
                    if (duration != null) {
                        startTimer(duration / 60) // Convert seconds to minutes
                    }
                }
            }
            "android.intent.action.SET_TIMER" -> {
                val duration = intent.getSerializableExtra(EXTRA_LENGTH)
                if (duration != null) {
                    try {
                        val durationLong = when (duration) {
                            is Int -> duration.toLong()
                            is Long -> duration
                            else -> null
                        }
                        if (durationLong != null) {
                            val minutes = (durationLong / 60).toInt()
                            Log.d(TAG, "Received timer duration: $durationLong seconds, converting to $minutes minutes")
                            startTimer(minutes)
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

    private fun startTimer(minutes: Int) {
        val serviceIntent = Intent(this, FloatingPetService::class.java)
        serviceIntent.action = "START_TIMER"
        serviceIntent.putExtra("TIMER_MINUTES", minutes)
        startService(serviceIntent)
    }

    private fun startFloatingService() {
        val serviceIntent = Intent(this, FloatingPetService::class.java)
        startService(serviceIntent)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
                handleIntent(intent)
            }
            finish() // Close activity after permission result
        }
    }
}
