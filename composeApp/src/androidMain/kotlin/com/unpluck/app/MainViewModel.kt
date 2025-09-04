// In MainViewModel.kt
package com.unpluck.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.core.content.edit

class MainViewModel : ViewModel() {

    // All your state variables move here from MainActivity
    val appMode = mutableStateOf(AppMode.NORMAL_MODE)
    val launcherSelected = mutableStateOf(false)
    val onboardingCompleted = mutableStateOf(false)

    val blePermissionsGranted = mutableStateOf(false)
    val overlayPermissionGranted = mutableStateOf(false)
    val dndPermissionGranted = mutableStateOf(false)

    // Add functions to handle logic
    fun updatePermissionStates(context: Context) {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        blePermissionsGranted.value = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            overlayPermissionGranted.value = Settings.canDrawOverlays(context)
        } else {
            overlayPermissionGranted.value = true
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        dndPermissionGranted.value = notificationManager.isNotificationPolicyAccessGranted
    }

    fun onFinishOnboarding(context: Context) {
        val prefs = context.getSharedPreferences("UnpluckPrefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("OnboardingComplete", true) }
        onboardingCompleted.value = true
    }
}