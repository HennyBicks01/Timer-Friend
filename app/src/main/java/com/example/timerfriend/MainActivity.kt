package com.example.timerfriend

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import java.io.Serializable
import java.util.regex.Pattern

/**
 * Compatibility extension function for getting serializable extras from Intents.
 * Handles the API level 33 (Tiramisu) deprecation of getSerializableExtra() by using
 * the new type-safe version when available, falling back to the old version for older Android versions.
 *
 * @param key The name of the extra to retrieve
 * @param clazz The Class object of the expected type
 * @return The extra as type T, or null if not found
 */
@Suppress("DEPRECATION", "UNCHECKED_CAST")
fun <T : Serializable> Intent.getSerializableExtraCompat(key: String, clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(key, clazz)
    } else {
        @Suppress("UNCHECKED_CAST")
        getSerializableExtra(key) as? T
    }
}

/**
 * Main entry point for the Timer Friend app. This activity handles incoming timer intents
 * and manages the overlay permission flow. It doesn't show a UI, instead launching a floating
 * service to display the timer.
 */
class MainActivity : Activity() {
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1
        private const val EXTRA_LENGTH = "android.intent.extra.alarm.LENGTH"
        private val TIMER_PATTERN = Pattern.compile("/timer/(\\d+)")
    }

    private val tag = "MainActivity"

    /**
     * Initializes the activity and checks for overlay permission.
     * If permission is granted, starts the floating service and handles any incoming intent.
     * If not, requests the permission from the user.
     */
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

    /**
     * Handles new intents when the activity is already running.
     * Processes the intent and immediately finishes the activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish() // Close activity immediately
    }

    /**
     * Processes incoming intents, supporting two types:
     * 1. ACTION_VIEW: Handles deep links with timer duration in the URI
     * 2. SET_TIMER: Handles system timer intents with duration in extras
     *
     * @param intent The intent to process
     */
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
                // Use the new extension function for type-safe serializable extra
                val duration = intent.getSerializableExtraCompat(EXTRA_LENGTH, Number::class.java)
                if (duration != null) {
                    try {
                        val durationLong = when (duration) {
                            is Int -> duration.toLong()
                            is Long -> duration
                            else -> null
                        }
                        if (durationLong != null) {
                            val minutes = (durationLong / 60).toInt()
                            Log.d(tag, "Received timer duration: $durationLong seconds, converting to $minutes minutes")
                            startTimer(minutes)
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Error handling timer intent", e)
                    }
                }
            }
        }
    }

    /**
     * Extracts the timer duration from a deep link URI.
     * Expects URIs in the format: .../timer/{seconds}
     *
     * @param uri The URI to parse
     * @return The duration in seconds, or null if not found/invalid
     */
    private fun extractDurationFromUri(uri: Uri): Int? {
        val path = uri.path ?: return null
        val matcher = TIMER_PATTERN.matcher(path)
        return if (matcher.find()) {
            matcher.group(1)?.toIntOrNull()
        } else null
    }

    /**
     * Starts the floating timer service with the specified duration.
     *
     * @param minutes The duration of the timer in minutes
     */
    private fun startTimer(minutes: Int) {
        val serviceIntent = Intent(this, FloatingPetService::class.java)
        serviceIntent.action = "START_TIMER"
        serviceIntent.putExtra("TIMER_MINUTES", minutes)
        startService(serviceIntent)
    }

    /**
     * Starts the floating service without a timer.
     * Used when the app is first launched or after permission is granted.
     */
    private fun startFloatingService() {
        val serviceIntent = Intent(this, FloatingPetService::class.java)
        startService(serviceIntent)
    }

    /**
     * Requests the overlay permission from the user by launching the system settings.
     */
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }

    /**
     * Handles the result of the overlay permission request.
     * If granted, starts the floating service and processes any pending intent.
     */
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
