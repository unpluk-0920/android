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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.unpluck.app.services.BleService
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.unpluck.app.data.AppDatabase
import com.unpluck.app.defs.BleDevice
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

        val prefs = getSharedPreferences("UnpluckPrefs", MODE_PRIVATE)
        viewModel.onboardingCompleted.value = prefs.getBoolean("OnboardingComplete", false)
        viewModel.launcherSelected.value = prefs.contains("RealLauncherPackage")
        viewModel.appMode.value = AppMode.valueOf(
            prefs.getString("APP_MODE_KEY", AppMode.NORMAL_MODE.name) ?: AppMode.NORMAL_MODE.name
        )
        viewModel.updatePermissionStates(this)
        viewModel.loadSpaces(this)
        viewModel.loadInitialSpace(this)

        registerBleUpdateReceiver()

        // 5. Lifecycle-aware actions
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
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    private fun MainAppUI() {
        val currentMode by viewModel.appMode
        val currentFocusScreen by viewModel.currentFocusScreen

        // NEW: Local state to control the animation trigger. Starts as false.
        var showFocusUI by remember { mutableStateOf(false) }

        // This effect runs when the composable first appears or when currentMode changes.
        // It syncs our local animation trigger with the real app state.
        LaunchedEffect(currentMode) {
            showFocusUI = (currentMode == AppMode.FOCUS_MODE)
        }

        // Now we can go back to a simpler structure
        if (currentMode == AppMode.NORMAL_MODE) {
            // We only show the proxy when in normal mode. It doesn't need to animate.
            ProxyToRealLauncher()
        }

        // The animation is now driven by the local 'showFocusUI' state,
        // which guarantees a false -> true transition on screen.
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

    @Composable
    private fun ProxyToRealLauncher() {
        val context = LocalContext.current
        // This effect runs once to launch the real launcher and then finish this activity
        LaunchedEffect(Unit) {
            launchRealLauncher(context)
        }
        // You can show a blank screen or a loading indicator here while it switches
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

    private fun launchRealLauncher(context: Context) {
        val prefs = context.getSharedPreferences("UnpluckPrefs", MODE_PRIVATE)
        val pkg = prefs.getString("RealLauncherPackage", null)
        val activity = prefs.getString("RealLauncherActivity", null)
        if (pkg != null && activity != null) {
            val launchIntent = Intent().setClassName(pkg, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                // Handle case where launcher was uninstalled
                prefs.edit { clear() }
                viewModel.launcherSelected.value = false
            }
        }
        (context as? Activity)?.finish()
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
        // CRITICAL: This handles the home button press from another app.
        // If we are in NORMAL_MODE, immediately proxy to the real launcher.
        if (viewModel.onboardingCompleted.value && viewModel.appMode.value == AppMode.NORMAL_MODE && viewModel.launcherSelected.value) {
            launchRealLauncher(this)
        }
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