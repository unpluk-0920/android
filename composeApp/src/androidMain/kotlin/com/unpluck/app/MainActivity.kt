package com.unpluck.app

import SpaceListScreen
import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.unpluck.app.services.BleService
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.unpluck.app.data.AppDatabase
import com.unpluck.app.defs.BleDevice
import com.unpluck.app.defs.LauncherType
import com.unpluck.app.ui.ActiveSpaceUI
import com.unpluck.app.ui.AppSelectionScreen
import com.unpluck.app.ui.CreateSpaceScreen
import com.unpluck.app.ui.OnboardingFlow
import com.unpluck.app.ui.SpaceSettingScreen
import com.unpluck.app.ui.theme.UnplukTheme

enum class AppMode {
    NORMAL_MODE,
    FOCUS_MODE
}

class MainActivity : ComponentActivity() {

    private val TAG = "MAIN_ACTIVITY"
   // --- SharedPreferences KEYS ---
    private val KEY_REAL_LAUNCHER_PACKAGE = "RealLauncherPackage"
    private val KEY_REAL_LAUNCHER_ACTIVITY = "RealLauncherActivity"

    private val KEY_SELECTED_LAUNCHER_MODULE = "SELECTED_LAUNCHER_MODULE"
    private val KEY_ONBOARDING_COMPLETE = "OnboardingComplete"


    private val PREFS_NAME = "UnpluckPrefs"
    private val KEY_APP_MODE = "APP_MODE_KEY"

    private var isInitialLaunch = true
    private var isReceiverRegistered = false

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(applicationContext)
                return MainViewModel(db.spaceDao()) as T
            }
        }
    }
    private lateinit var notificationManager: NotificationManager

    private val requestBlePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                viewModel.blePermissionsGranted.value = true
                startBleService()
            } else {
                Toast.makeText(this, "Permissions are required for the app to function.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                viewModel.locationPermissionGranted.value = true
            } else {
                Toast.makeText(this, "Precise Location is needed to find the case.", Toast.LENGTH_LONG).show()
            }
        }

    // NEW: Launcher for Phone & Contacts
    private val requestPhonePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                viewModel.phonePermissionsGranted.value = true
            } else {
                Toast.makeText(this, "Call and Contact permissions are needed for call screening.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                viewModel.notificationPermissionGranted.value = true
            }
        }

    private val requestDndPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                viewModel.dndPermissionGranted.value = true
                Toast.makeText(this, "DND Permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission is required to block notifications.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Call Screening enabled!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Call Screening is required to block calls.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestOverlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (Settings.canDrawOverlays(this)) {
                    viewModel.overlayPermissionGranted.value = true
                    Toast.makeText(this, "Permission Granted!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "This permission is needed for Focus Mode to appear automatically.", Toast.LENGTH_LONG).show()
                }
            }
        }

    // --- BROADCAST RECEIVER ---
    private val bleUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
//            Log.d(TAG, "Broadcast received with action: ${intent.action}")
            when (intent.action) {
                BleService.ACTION_DEVICE_FOUND -> {
                    val name = intent.getStringExtra(BleService.EXTRA_DEVICE_NAME) ?: "Unnamed"
                    val address = intent.getStringExtra(BleService.EXTRA_DEVICE_ADDRESS)
                    if (address != null) {
                        viewModel.addFoundDevice(BleDevice(name, address))
                    }
                }
                BleService.ACTION_STATUS_UPDATE -> {
                    val status = intent.getStringExtra(BleService.EXTRA_STATUS_MESSAGE) ?: "Unknown status"
                    val isConnected = intent.getBooleanExtra(BleService.EXTRA_IS_CONNECTED, false)
                    viewModel.updateConnectionStatus(status, isConnected)
                }
                "com.unpluck.app.ACTION_MODE_CHANGED" -> {
                    val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    val newModeName = prefs.getString(KEY_APP_MODE, AppMode.NORMAL_MODE.name)
                    viewModel.appMode.value = AppMode.valueOf(newModeName ?: AppMode.NORMAL_MODE.name)
                }
            }
        }
    }

    // --- LIFECYCLE METHODS ---
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        viewModel.onboardingCompleted.value = prefs.getBoolean("OnboardingComplete", false)
        viewModel.launcherSelected.value = prefs.contains("RealLauncherPackage") // This might need review, but let's keep it for now.
        viewModel.appMode.value = AppMode.valueOf(
            prefs.getString("APP_MODE_KEY", AppMode.NORMAL_MODE.name) ?: AppMode.NORMAL_MODE.name
        )
        viewModel.updatePermissionStates(this)
        viewModel.loadSpaces(this)
        viewModel.loadInitialSpace(this)

        // Handle jumping to launcher selection if requested by HomeRouterActivity
        if (!viewModel.onboardingCompleted.value && intent.getBooleanExtra("start_on_launcher_selection", false)) {
            viewModel.currentOnboardingStep.value = OnboardingStep.SELECT_LAUNCHER_MODULE
            Log.d(TAG, "Jumping directly to Launcher Module Selection step.")
        }

        registerBleUpdateReceiver()

        // 5. Lifecycle-aware actions
        // This part stays mostly the same for starting BLE service, it's independent of UI rendering.
        if (viewModel.onboardingCompleted.value) {
            checkBlePermissionsAndStartService()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.appMode.value == AppMode.FOCUS_MODE) {
                // In Focus Mode, back button is disabled
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        setContent {
            UnplukTheme {
                val onboardingCompleted by viewModel.onboardingCompleted

                if (onboardingCompleted) {
                    MainAppUI()
                } else {
                    // This will be shown if onboardingCompleted is false AND HomeRouterActivity launched MainActivity without the "jump" flag.
                    // Or if it did have the jump flag, OnboardingFlow will handle the step change internally.
                    OnboardingFlow(
                        viewModel = viewModel,
                        onRequestBle = { requestBlePermissionsLauncher.launch(getRequiredBlePermissions()) },
                        onRequestOverlay = { checkAndRequestOverlayPermission() },
                        onRequestDnd = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            requestDndPermissionLauncher.launch(intent)
                        },
                        onRequestNotification = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestLocation = {
                            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        onRequestPhone = {
                            requestPhonePermissionsLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS))
                        }
                    )
                }
            }
        }
//        setContent {
//            UnplukTheme {
//                val onboardingCompleted by viewModel.onboardingCompleted
//
//                if (onboardingCompleted) {
//                    MainAppUI()
//                } else {
//                    OnboardingFlow(
//                        viewModel = viewModel,
//                        onRequestBle = { requestBlePermissionsLauncher.launch(getRequiredBlePermissions()) },
//                        onRequestOverlay = { checkAndRequestOverlayPermission() },
//                        onRequestDnd = {
//                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
//                            requestDndPermissionLauncher.launch(intent)
//                        },
//                        onRequestNotification = {
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
//                            }
//                        },
//                        onRequestLocation = {
//                            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
//                        },
//                        onRequestPhone = {
//                            requestPhonePermissionsLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS))
//                        }
//                    )
//                }
//            }
//        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    private fun MainAppUI() {
        val currentMode by viewModel.appMode
        val currentFocusScreen by viewModel.currentFocusScreen
        val selectedLauncherType by viewModel.selectedLauncher // Still useful for displaying info or internal logic

        // Local state to control the Focus Mode animation
        var showFocusUI by remember { mutableStateOf(false) }

        LaunchedEffect(currentMode) {
            showFocusUI = (currentMode == AppMode.FOCUS_MODE)
        }

        // When in NORMAL_MODE, display Unpluck's regular UI
        // The HomeRouterActivity has already decided that MainActivity should be showing.
        // It's up to MainActivity to decide what to show internally based on selectedLauncherType.
        // For ORIGINAL_PROXY, HomeRouterActivity would have launched the external app.
        // For KISS/LAWNCHAIR, HomeRouterActivity would have launched MainActivity, and here we display Unpluck's internal representation.
        if (currentMode == AppMode.NORMAL_MODE) {
            when (selectedLauncherType) {
                LauncherType.KISS, LauncherType.LAWNCHAIR -> {
                    // For these, Unpluck is acting as the home. Display its internal UI.
                    // This is where your custom KISS/Lawnchair *internal UI* would go when implemented as modules.
                    // For now, it's a placeholder.
                    UnpluckHomeDisplay(
                        viewModel = viewModel,
                        message = "Welcome to Unpluck's ${selectedLauncherType.name} Home!",
                        showDevButtons = true
                    )
                }
                LauncherType.ORIGINAL_PROXY -> {
                    // This case *should* generally not be reached if HomeRouterActivity successfully launched the original proxy.
                    // However, as a fallback, if HomeRouterActivity couldn't launch it, it would launch MainActivity,
                    // in which case we display Unpluck's default home.
                    UnpluckHomeDisplay(
                        viewModel = viewModel,
                        message = "Welcome to Unpluck's Home (Fallback for Original Proxy)!",
                        showDevButtons = true
                    )
                }
                null -> {
                    // This means onboarding is done but somehow no launcher was selected.
                    // HomeRouterActivity should ideally catch this and restart onboarding.
                    // As a final fallback, display Unpluck's home.
                    UnpluckHomeDisplay(
                        viewModel = viewModel,
                        message = "Welcome to Unpluck's Home (No Launcher Selected Fallback)!",
                        showDevButtons = true
                    )
                }
            }
        }

        // The FocusUI will always animate in/out based on showFocusUI state,
        // overlaying whatever is beneath it.
        AnimatedVisibility(
            visible = showFocusUI,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 400),
                initialOffsetY = { fullHeight -> -fullHeight }
            ) + fadeIn(animationSpec = tween(durationMillis = 400)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 400),
                targetOffsetY = { fullHeight -> -fullHeight }
            ) + fadeOut(animationSpec = tween(durationMillis = 400))
        ) {
            FocusUI(viewModel = viewModel, currentScreen = currentFocusScreen)
        }
    }

    @Composable
    private fun UnpluckHomeDisplay(viewModel: MainViewModel, message: String, showDevButtons: Boolean = false) {
        val context = LocalContext.current // Get context here
        val activity = context as? Activity // Cast to Activity if possible

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(message, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            if (showDevButtons) {
                Button(onClick = { viewModel.appMode.value = AppMode.FOCUS_MODE }) {
                    Text("Enter Focus Mode (for testing)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    // For demonstration, if you want to allow re-selecting launchers
                    val prefs = context.getSharedPreferences("UnpluckPrefs", Context.MODE_PRIVATE)
                    prefs.edit { remove(KEY_SELECTED_LAUNCHER_MODULE) }
                    prefs.edit { remove(KEY_ONBOARDING_COMPLETE) } // Reset onboarding

                    // These ViewModel updates are internal and fine
                    viewModel.onboardingCompleted.value = false
                    viewModel.currentOnboardingStep.value = OnboardingStep.SELECT_LAUNCHER_MODULE

                    // Finish current activity to let HomeRouterActivity pick up the change
                    // This call needs to happen on an Activity context.
                    activity?.finish()

                    // The system will now naturally look for the CATEGORY_HOME activity again,
                    // which is HomeRouterActivity, and it will see onboarding is reset.
                    // We don't need to manually start HomeRouterActivity here.
                    // Removing: LocalContext.current.startActivity(Intent(LocalContext.current, HomeRouterActivity::class.java))
                }) {
                    Text("Re-select Launcher (Dev Only)")
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    private fun FocusUI(viewModel: MainViewModel, currentScreen: FocusScreen) {
//        val currentScreen by viewModel.currentFocusScreen
        val activeSpace by viewModel.activeSpace
        Log.d("FOCUS_UI", "Recomposing. Current screen state: $currentScreen") // <-- ADD THIS

        when (currentScreen) {
            FocusScreen.ACTIVE_SPACE -> {
                activeSpace?.let {
                    ActiveSpaceUI(
                        space = it,
                        onSettingsClicked = { viewModel.navigateToSpaceList() },
                        onForceExit = {
                            val prefs = getSharedPreferences("UnpluckPrefs", MODE_PRIVATE)
                            prefs.edit { putString("APP_MODE_KEY", AppMode.NORMAL_MODE.name) }
                            viewModel.appMode.value = AppMode.NORMAL_MODE
                        }
                    )
                } ?: Box(modifier=Modifier.fillMaxSize(), contentAlignment=Alignment.Center){ Text("Create a Space to get started!")}
            }
            FocusScreen.SPACE_LIST -> SpaceListScreen(viewModel)
            FocusScreen.CREATE_SPACE -> CreateSpaceScreen(
                onCreate = {
                    name -> viewModel.createNewSpace(this, name)
                    viewModel.navigateBack()
                },
                onClose = {
                    viewModel.navigateBack()
                }
            )
            FocusScreen.SPACE_SETTINGS -> SpaceSettingScreen(viewModel)
            FocusScreen.APP_SELECTION -> {
                AppSelectionScreen(viewModel = viewModel)
            }
        }
    }

    private fun getRequiredBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            requestOverlayPermissionLauncher.launch(intent)
        }
    }

    private fun checkBlePermissionsAndStartService() {
        if (getRequiredBlePermissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startBleService()
        }
    }

    private fun startBleService() {
        if (!BleService.isServiceRunning) {
            val intent = Intent(this, BleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun registerBleUpdateReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(BleService.ACTION_DEVICE_FOUND)
            addAction(BleService.ACTION_STATUS_UPDATE)
            addAction("com.unpluck.app.ACTION_MODE_CHANGED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bleUpdateReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bleUpdateReceiver, intentFilter)
        }
        Log.d(TAG, "BLE Update Receiver registered.")
        isReceiverRegistered = true
    }

    private fun hasDndPermission(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    private fun toggleDnd(enable: Boolean) {
        if (!hasDndPermission()) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            Toast.makeText(this, "Please enable Do Not Disturb access.", Toast.LENGTH_LONG).show()
            requestDndPermissionLauncher.launch(intent)
            return
        }
        notificationManager.setInterruptionFilter(
            if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.updatePermissionStates(this)
        // CRITICAL: This handles the home button press from another app.
        // If we are in NORMAL_MODE, immediately proxy to the real launcher.
        Log.d(TAG, "MainActivity onResume: Displaying Unpluck's UI.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            unregisterReceiver(bleUpdateReceiver)
            isReceiverRegistered = false
            Log.d(TAG, "BLE Update Receiver unregistered.")
        }
    }

    override fun onPause() {
        super.onPause()
        // After the first time the activity is paused, it's no longer the "initial launch"
        isInitialLaunch = false
    }
}