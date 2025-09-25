package com.unpluck.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import com.unpluck.app.data.SpaceDao
import com.unpluck.app.defs.AppInfo
import com.unpluck.app.defs.BleDevice
import com.unpluck.app.defs.LauncherType
import com.unpluck.app.defs.Space
import com.unpluck.app.services.BleService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FocusScreen {
    ACTIVE_SPACE,
    SPACE_LIST,
    SPACE_SETTINGS,
    CREATE_SPACE,
    APP_SELECTION
}

enum class OnboardingStep {
    INTRO,
    PERMISSIONS,
    CONNECT_DEVICE,
    CREATE_SPACE,
    SET_LAUNCHER,
    SELECT_LAUNCHER_MODULE
}
class MainViewModel(private val dao: SpaceDao) : ViewModel() {

    private val TAG = "MAIN_VIEWMODEL"
    private val KEY_REAL_LAUNCHER_PACKAGE = "RealLauncherPackage"
    private val KEY_REAL_LAUNCHER_ACTIVITY = "RealLauncherActivity"
    // --- ONBOARDING STATE ---
    val currentOnboardingStep = mutableStateOf(OnboardingStep.INTRO)

    // --- All STATES ---
    val allSpaces: StateFlow<List<Space>> = dao.getAllSpaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSpace = mutableStateOf<Space?>(null) // The currently active space
    val currentFocusScreen = mutableStateOf(FocusScreen.ACTIVE_SPACE)
    val foundDevices = mutableStateOf<List<BleDevice>>(emptyList())
    val isScanning = mutableStateOf(false)
    val connectionStatus = mutableStateOf("Not connected")
    val isDeviceConnected = mutableStateOf(false)
    val spaces = mutableStateOf<List<Space>>(emptyList())
    val appMode = mutableStateOf(AppMode.NORMAL_MODE)
    val launcherSelected = mutableStateOf(false)
    val onboardingCompleted = mutableStateOf(false)
    val isShowingSpaceSettings = mutableStateOf(false)
    val spaceToEdit = mutableStateOf<Space?>(null)
    val allInstalledApps = mutableStateOf<List<AppInfo>>(emptyList())
    val selectedAppPackages = mutableStateOf<Set<String>>(emptySet())
    val selectedLauncher = mutableStateOf(LauncherType.ORIGINAL_PROXY)

    val isShowingAppSelection = mutableStateOf(false)


    // PERMISSIONS STATE
    val blePermissionsGranted = mutableStateOf(false)
    val overlayPermissionGranted = mutableStateOf(false)
    val dndPermissionGranted = mutableStateOf(false)
    val notificationPermissionGranted = mutableStateOf(false)
    val locationPermissionGranted = mutableStateOf(false)
    val phonePermissionsGranted = mutableStateOf(false)

    init {
        viewModelScope.launch {
            allSpaces.collect { spaces ->
                activeSpace.value?.let { currentActive ->
                    activeSpace.value = spaces.find { it.id == currentActive.id }
                }
            }
        }
    }

    fun loadInitialSpace(context: Context) {
        val prefs = context.getSharedPreferences("UnpluckPrefs", Context.MODE_PRIVATE)
        val lastActiveId = prefs.getString("LAST_ACTIVE_SPACE_ID", null)

        viewModelScope.launch {
            allSpaces.collect { spaces ->
                if (spaces.isNotEmpty()) {
                    val spaceToActivate = if (lastActiveId != null) {
                        spaces.find { it.id == lastActiveId } ?: spaces.first()
                    } else {
                        spaces.first()
                    }
                    activeSpace.value = spaceToActivate
                }
            }
        }
    }

    fun createNewSpace(context: Context, name: String) {
        Log.d("VIEW_MODEL", "createNewSpace called with name: $name")
        viewModelScope.launch {
            dao.insert(Space(name = name))
        }
    }

    fun navigateToSpaceList() { currentFocusScreen.value = FocusScreen.SPACE_LIST }
    fun navigateToCreateSpace() { currentFocusScreen.value = FocusScreen.CREATE_SPACE }
    fun navigateToAppSelection() { currentFocusScreen.value = FocusScreen.APP_SELECTION }
    fun navigateToSettings(space: Space) {
        spaceToEdit.value = space
        selectedAppPackages.value = space.appIds.toSet()
        currentFocusScreen.value = FocusScreen.SPACE_SETTINGS
    }
    fun navigateBack() {
        Log.d("VIEW_MODEL", "navigateBack called. Current screen is ${currentFocusScreen.value}") // <-- ADD THIS
        // Simple back navigation logic
        when(currentFocusScreen.value) {
            FocusScreen.SPACE_LIST -> currentFocusScreen.value = FocusScreen.ACTIVE_SPACE
            FocusScreen.CREATE_SPACE -> currentFocusScreen.value = FocusScreen.SPACE_LIST
            FocusScreen.SPACE_SETTINGS -> currentFocusScreen.value = FocusScreen.SPACE_LIST
            FocusScreen.APP_SELECTION -> currentFocusScreen.value = FocusScreen.SPACE_SETTINGS
            else -> {}
        }
    }

    fun setActiveSpace(context: Context, space: Space) {
        val prefs = context.getSharedPreferences("UnpluckPrefs", Context.MODE_PRIVATE)
        prefs.edit { putString("LAST_ACTIVE_SPACE_ID", space.id) }

        activeSpace.value = space
        currentFocusScreen.value = FocusScreen.ACTIVE_SPACE // Go back to the active screen
    }

    fun updateSpace(space: Space) {
        viewModelScope.launch {
            dao.update(space)
        }
    }

    fun startScan(context: Context) {
        Log.d(TAG, "UI requested a scan.")
        // Clear old list and start scanning
        foundDevices.value = emptyList()
        isScanning.value = true
        context.startService(Intent(context, BleService::class.java).apply { action = BleService.ACTION_START_SCAN })
    }

    fun connectToDevice(context: Context, address: String) {
        Log.d(TAG, "UI requested connection to address: $address")
        isScanning.value = false // Stop showing scan results
        connectionStatus.value = "Connecting..."
        context.startService(Intent(context, BleService::class.java).apply {
            action = BleService.ACTION_CONNECT
            putExtra(BleService.EXTRA_DEVICE_ADDRESS, address)
        })
    }

    fun addFoundDevice(device: BleDevice) {
//        Log.d(TAG, "Adding found device to list: ${device.name}")
        // Add device to the list only if it's not already there
        if (foundDevices.value.none { it.address == device.address }) {
            foundDevices.value = foundDevices.value + device
        }
    }

    fun updateConnectionStatus(status: String, isConnected: Boolean) {
        Log.d(TAG, "Updating connection status: '$status', isConnected: $isConnected")
        connectionStatus.value = status
        isDeviceConnected.value = isConnected
        when {
            status.contains("Scanning for devices...") -> {
                isScanning.value = true
            }
            status.contains("Scan finished.") -> {
                isScanning.value = false
            }
            status.contains("Connecting...") -> {
                isScanning.value = false // We are no longer scanning when trying to connect
            }
        }
    }

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

    fun onDeviceConnected() {
        currentOnboardingStep.value = OnboardingStep.CREATE_SPACE
    }

    fun onSpaceCreated() {
        currentOnboardingStep.value = OnboardingStep.SET_LAUNCHER
    }

    fun onSpaceCreationDone() {
        currentOnboardingStep.value = OnboardingStep.SELECT_LAUNCHER_MODULE
    }

    fun onLauncherSelectedDone() {
        launcherSelected.value = true
        currentOnboardingStep.value = OnboardingStep.SET_LAUNCHER
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionGranted.value = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            notificationPermissionGranted.value = true // Not needed on older versions
        }

        locationPermissionGranted.value = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        phonePermissionsGranted.value = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
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

    fun showSpaceSettings(space: Space) {
        spaceToEdit.value = space
        selectedAppPackages.value = space.appIds.toSet() // Load current selection
        isShowingSpaceSettings.value = true
    }

    fun hideSpaceSettings() {
        isShowingSpaceSettings.value = false
    }

    fun updateSpaceName(context: Context, newName: String) {
        val spaceToUpdate = spaceToEdit.value ?: return

        // Load the current list of spaces
        val prefs = context.getSharedPreferences("UnpluckPrefs", Context.MODE_PRIVATE)
        val savedString = prefs.getString("SPACES_LIST_KEY", null)
        val currentSpaces = parseSpacesFromString(savedString).toMutableList()

        // Find the index of the space we need to update
        val index = currentSpaces.indexOfFirst { it.id == spaceToUpdate.id }

        if (index != -1) {
            // Replace the old space with a new one containing the updated name
            currentSpaces[index] = spaceToUpdate.copy(name = newName)

            // Save the modified list back to SharedPreferences
            val updatedString = convertSpacesToString(currentSpaces)
            prefs.edit { putString("SPACES_LIST_KEY", updatedString) }

            // Update the live 'spaces' state to refresh the UI immediately
            spaces.value = currentSpaces

            // Go back to the main focus screen
            hideSpaceSettings()
        }
    }

    fun loadAllInstalledApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val packageManager = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(mainIntent, 0)
                .mapNotNull {
                    try {
                        AppInfo(
                            name = it.loadLabel(packageManager).toString(),
                            packageName = it.activityInfo.packageName,
                            icon = it.loadIcon(packageManager)
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                .sortedBy { it.name.lowercase() }

            withContext(Dispatchers.Main) {
                allInstalledApps.value = apps
            }
        }
    }

    fun onAppSelectionChanged(packageName: String, isSelected: Boolean) {
        val currentSelection = selectedAppPackages.value.toMutableSet()
        if (isSelected) {
            currentSelection.add(packageName)
        } else {
            currentSelection.remove(packageName)
        }
        selectedAppPackages.value = currentSelection
    }

    // NEW: Saves the final app selection back to the Space
    fun saveAppSelection() {
        val spaceToUpdate = spaceToEdit.value ?: return

        // Create an updated copy of the space with the new app list from our selection.
        val updatedSpace = spaceToUpdate.copy(appIds = selectedAppPackages.value.toList())

        // Launch a coroutine to perform the database operation.
        viewModelScope.launch {
            dao.update(updatedSpace) // This saves the changes to the database.
        }
        // Navigate back to the main settings screen
        navigateBack()
    }

    fun saveLauncherChoice(context: Context, launcherType: LauncherType) {
        val prefs = context.getSharedPreferences("UnpluckPrefs", Context.MODE_PRIVATE)
        prefs.edit { putString("SELECTED_LAUNCHER_MODULE", launcherType.name) }
        selectedLauncher.value = launcherType
    }

    fun saveCurrentDefaultLauncherInfo(context: Context) {
        val packageManager = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        // Get all activities that can handle a HOME intent
        val resolveInfos = packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)

        // Filter out Unpluck itself and the IntentResolver
        val actualHomeLaunchers = resolveInfos.filter {
            it.activityInfo.packageName != context.packageName && // Exclude Unpluck
                    it.activityInfo.packageName != "com.android.intentresolver" // Exclude the system chooser
        }

        // Try to find the *currently preferred* or the *first non-Unpluck* launcher
        var targetPackage: String? = null
        var targetActivity: String? = null

        // Attempt to find the currently preferred launcher (if one is set)
        // This usually requires a different API or might not be directly available for 'default' in this context.
        // A simpler approach for *capturing the default Unpluck will proxy to* is to take the first non-Unpluck one.
        if (actualHomeLaunchers.isNotEmpty()) {
            val preferredHome = actualHomeLaunchers.first() // Take the first one found, assuming it's the desired default.
            targetPackage = preferredHome.activityInfo.packageName
            targetActivity = preferredHome.activityInfo.name
        }


        if (targetPackage != null && targetActivity != null) {
            val prefs = context.getSharedPreferences("UnpluckPrefs", Context.MODE_PRIVATE)
            prefs.edit {
                putString(KEY_REAL_LAUNCHER_PACKAGE, targetPackage) // Use your defined keys
                putString(KEY_REAL_LAUNCHER_ACTIVITY, targetActivity) // Use your defined keys
            }
            Log.d(TAG, "Saved real launcher: Pkg=$targetPackage, Act=$targetActivity")
        } else {
            Log.e(TAG, "Could not find a suitable non-Unpluck default launcher to save. Defaulting to system behavior.")
            // If we can't find one, it's better to NOT save anything, so HomeRouter will go to Unpluck.
            // Or, you could decide to explicitly store a known default launcher like Pixel Launcher or a safe fallback.
        }
    }
}