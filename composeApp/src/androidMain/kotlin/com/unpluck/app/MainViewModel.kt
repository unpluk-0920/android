package com.unpluck.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unpluck.app.data.SpaceDao
import com.unpluck.app.defs.AppInfo
import com.unpluck.app.defs.BleDevice
import com.unpluck.app.defs.LauncherType
import com.unpluck.app.defs.Space
import com.unpluck.app.services.BleService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
class MainViewModel(
    application: Application,
    private val dao: SpaceDao
) : AndroidViewModel(application) {
    private val TAG = "MAIN_VIEWMODEL"
    private val PREFS_NAME = "UnpluckPrefs"
    private val KEY_REAL_LAUNCHER_PACKAGE = "RealLauncherPackage"
    private val KEY_REAL_LAUNCHER_ACTIVITY = "RealLauncherActivity"
    private val KEY_ONBOARDING_COMPLETE = "OnboardingComplete" // Added from HomeRouterActivity
    private val KEY_SELECTED_LAUNCHER_MODULE = "SELECTED_LAUNCHER_MODULE" // Added from HomeRouterActivity
    private val KEY_APP_MODE = "APP_MODE_KEY" // Added for appMode persistence

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
    private val _appMode = MutableStateFlow(AppMode.NORMAL_MODE)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()
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
        val appContext = getApplication<Application>().applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load onboarding status and launcher type from preferences
        onboardingCompleted.value = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        val savedLauncherTypeString = prefs.getString(KEY_SELECTED_LAUNCHER_MODULE, null)
        selectedLauncher.value = savedLauncherTypeString?.let { LauncherType.valueOf(it) } ?: LauncherType.ORIGINAL_PROXY

        // Load appMode from preferences
        val savedAppModeString = prefs.getString(KEY_APP_MODE, AppMode.NORMAL_MODE.name)
        _appMode.value = AppMode.valueOf(savedAppModeString ?: AppMode.NORMAL_MODE.name)

        // Observe and save appMode changes
        _appMode.onEach { mode ->
            prefs.edit { putString(KEY_APP_MODE, mode.name) }
            Log.d(TAG, "AppMode changed and saved: ${mode.name}")
        }.launchIn(viewModelScope)

        // Existing init logic for spaces
        viewModelScope.launch {
            allSpaces.collect { spaces ->
                activeSpace.value?.let { currentActive ->
                    activeSpace.value = spaces.find { it.id == currentActive.id }
                }
            }
        }

        if (!onboardingCompleted.value) { // Only if onboarding is not yet complete
            // Skip directly to the next step after permissions
            currentOnboardingStep.value = OnboardingStep.CONNECT_DEVICE // Or whatever step comes next
            Log.d(TAG, "Permissions step temporarily bypassed for testing.")
            // You might also want to set permissions as "granted" for testing if they cause crashes later
            blePermissionsGranted.value = true
            overlayPermissionGranted.value = true
            dndPermissionGranted.value = true
            notificationPermissionGranted.value = true
            locationPermissionGranted.value = true
            phonePermissionsGranted.value = true
        } else {
            // If onboarding is complete, ensure it starts at INTRO (or relevant step)
            // or if it was already on some step, it remains there.
            // For existing users, this path is not critical, as onboardingCompleted.value is true.
        }

        updatePermissionStates(appContext)
        loadSpaces(appContext)
        loadInitialSpace(appContext)
    }

    fun setAppMode(mode: AppMode) {
        _appMode.value = mode
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
    fun navigateToAppSelection() {
        currentFocusScreen.value = FocusScreen.APP_SELECTION
        loadAllInstalledApps(getApplication<Application>().applicationContext)
    }
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

            // --- CHANGED LOGIC HERE ---
            // Get a list of ALL installed packages (ApplicationInfo)
            val installedApplicationInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION") // For older API levels
                packageManager.getInstalledApplications(0)
            }

            val apps = installedApplicationInfos
                .mapNotNull { appInfo ->
                    try {
                        // Optional: Filter out system apps that don't have a launcher icon
                        // This will prevent showing many background system services.
                        // Remove this 'if' block if you truly want *every single* installed package.
                        val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        val hasLauncher = packageManager.getLaunchIntentForPackage(appInfo.packageName) != null

                        if (!isSystemApp || hasLauncher) { // Keep user apps OR system apps with a launcher
                            AppInfo(
                                name = packageManager.getApplicationLabel(appInfo).toString(),
                                packageName = appInfo.packageName,
                                icon = packageManager.getApplicationIcon(appInfo)
                            )
                        } else {
                            null // Filter out system apps without a launcher
                        }

                    } catch (e: Exception) {
                        // Log errors for specific packages, but don't crash the entire list loading
                        Log.e("MainViewModel", "Error loading app info for ${appInfo.packageName}: ${e.message}")
                        null
                    }
                }
                .sortedBy { it.name.lowercase() } // Sort by app name alphabetically

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