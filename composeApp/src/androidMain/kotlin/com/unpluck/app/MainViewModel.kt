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
import androidx.lifecycle.viewModelScope
import com.unpluck.app.defs.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class OnboardingStep {
    INTRO,
    PERMISSIONS,
    CONNECT_DEVICE,
    CREATE_SPACE,
    SET_LAUNCHER
}

class MainViewModel : ViewModel() {

    // --- ONBOARDING STATE ---
    val currentOnboardingStep = mutableStateOf(OnboardingStep.INTRO)

    // --- BLE CONNECTION STATE ---
    val connectionStatus = mutableStateOf("Waiting to start...")
    val isDeviceConnected = mutableStateOf(false)

    val spaces = mutableStateOf<List<Space>>(emptyList())

    val appMode = mutableStateOf(AppMode.NORMAL_MODE)
    val launcherSelected = mutableStateOf(false)
    val onboardingCompleted = mutableStateOf(false)

    val blePermissionsGranted = mutableStateOf(false)
    val overlayPermissionGranted = mutableStateOf(false)
    val dndPermissionGranted = mutableStateOf(false)

    fun loadSpaces(context: Context) {
        val prefs = context.getSharedPreferences("UnpluckPrefs", Context.MODE_PRIVATE)
        val savedString = prefs.getString("SPACES_LIST_KEY", null)
        spaces.value = parseSpacesFromString(savedString) // Uses the helper function we already wrote
    }

    fun onIntroFinished() {
        currentOnboardingStep.value = OnboardingStep.PERMISSIONS
    }

    fun onPermissionsFinished() {
        currentOnboardingStep.value = OnboardingStep.CONNECT_DEVICE
    }

    fun startDeviceConnection(context: Context) {
        viewModelScope.launch {
            connectionStatus.value = "Starting BLE Service..."
            // In a real app, you would start the service and listen for a broadcast.
            // For this example, we'll simulate the process.
            delay(1000)
            connectionStatus.value = "Scanning for your SmartCase..."
            delay(2000)
            connectionStatus.value = "Connecting..."
            delay(1500)
            connectionStatus.value = "Connected! You're all set."
            isDeviceConnected.value = true
        }
    }

    fun onDeviceConnected() {
        currentOnboardingStep.value = OnboardingStep.CREATE_SPACE
    }

    fun onSpaceCreated() {
        currentOnboardingStep.value = OnboardingStep.SET_LAUNCHER
    }

    // This will be called when the final button is clicked.
    // MainActivity will observe a change and launch the settings intent.
    fun onSetLauncher() {
        // We'll handle this with a callback or event to the Activity
    }

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

    fun saveSpace(context: Context, spaceName: String) {
        val newSpace = Space(name = spaceName)

        val prefs = context.getSharedPreferences("UnpluckPrefs", Context.MODE_PRIVATE)
        val savedString = prefs.getString("SPACES_LIST_KEY", null)

        // Get the current list of spaces
        val spaces = parseSpacesFromString(savedString).toMutableList()

        // Add the new one
        spaces.add(newSpace)

        // Convert the updated list back to a string
        val updatedString = convertSpacesToString(spaces)

        // Save the new string
        prefs.edit { putString("SPACES_LIST_KEY", updatedString) }

        // After saving, move to the next step
        onSpaceCreated()
    }

    private fun convertSpacesToString(spaces: List<Space>): String {
        // Converts each space to "id|name" and joins them with "##"
        return spaces.joinToString("##") { "${it.id}|${it.name}" }
    }

    private fun parseSpacesFromString(savedString: String?): List<Space> {
        if (savedString.isNullOrBlank()) {
            return emptyList()
        }
        // Splits the string by "##" to get individual space strings
        return savedString.split("##").mapNotNull { spaceString ->
            // Splits each space string by "|" to get id and name
            val parts = spaceString.split("|", limit = 2)
            if (parts.size == 2) {
                Space(id = parts[0], name = parts[1])
            } else {
                null // Ignore malformed entries
            }
        }
    }
}