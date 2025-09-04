package com.unpluck.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import com.unpluck.app.defs.Space
import com.unpluck.app.services.BleService
import com.unpluck.app.views.UnpluckApp
import androidx.core.net.toUri
import com.unpluck.app.ui.ActiveSpaceUI
import com.unpluck.app.ui.OnboardingFlow
import com.unpluck.app.ui.theme.UnplukTheme

// The two states your launcher can be in.
enum class AppMode {
    NORMAL_MODE,
    FOCUS_MODE
}

class MainActivity : ComponentActivity() {

   // --- SharedPreferences KEYS ---
    private val KEY_REAL_LAUNCHER_PACKAGE = "RealLauncherPackage"
    private val KEY_REAL_LAUNCHER_ACTIVITY = "RealLauncherActivity"

    private val PREFS_NAME = "UnpluckPrefs"
    private val KEY_APP_MODE = "APP_MODE_KEY"

    private var isInitialLaunch = true
    private var isReceiverRegistered = false

    private val viewModel: MainViewModel by viewModels()
    private lateinit var notificationManager: NotificationManager

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                viewModel.blePermissionsGranted.value = true
                startBleService()
            } else {
                Toast.makeText(this, "Permissions are required for the app to function.", Toast.LENGTH_LONG).show()
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
    private val modeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.unpluck.app.ACTION_MODE_CHANGED") {
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val newModeName = prefs.getString(KEY_APP_MODE, AppMode.NORMAL_MODE.name)
                viewModel.appMode.value = AppMode.valueOf(newModeName ?: AppMode.NORMAL_MODE.name)
            }
        }
    }

    // --- LIFECYCLE METHODS ---
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

        // 5. Lifecycle-aware actions
        if (viewModel.onboardingCompleted.value) {
            checkBlePermissionsAndStartService()
            registerModeChangeReceiver()
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
                val launcherSelected by viewModel.launcherSelected

                if (onboardingCompleted) {
                    if (launcherSelected) {
                        MainAppUI()
                    } else {
                        LauncherSelectionScreen()
                    }
                } else {
                    OnboardingFlow(
                        viewModel = viewModel,
                        onRequestBle = { requestPermissionsLauncher.launch(getRequiredBlePermissions()) },
                        onRequestOverlay = { checkAndRequestOverlayPermission() },
                        onRequestDnd = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            requestDndPermissionLauncher.launch(intent)
                        }
                    )
                }
            }
        }
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
            unregisterReceiver(modeChangeReceiver)
        }
    }

    override fun onPause() {
        super.onPause()
        // After the first time the activity is paused, it's no longer the "initial launch"
        isInitialLaunch = false
    }

    // --- COMPOSABLE UI ---
    @Composable
    private fun MainAppUI() {
        val currentMode by viewModel.appMode
        when (currentMode) {
            AppMode.NORMAL_MODE -> ProxyToRealLauncher()
            AppMode.FOCUS_MODE -> FocusUI(viewModel)
        }
    }
    @Composable
    private fun LauncherSelectionScreen() {
        val context = LocalContext.current
        val packageManager = context.packageManager
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val launchers = remember {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != context.packageName }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Setup: Please Select Your Main Launcher")
            Spacer(modifier = Modifier.height(24.dp))
            launchers.forEach { launcherInfo ->
                Button(onClick = {
                    prefs.edit {
                        putString(KEY_REAL_LAUNCHER_PACKAGE, launcherInfo.activityInfo.packageName)
                        putString(KEY_REAL_LAUNCHER_ACTIVITY, launcherInfo.activityInfo.name)
                    }
                    viewModel.launcherSelected.value = true
                }) {
                    Text(launcherInfo.loadLabel(packageManager).toString())
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    @Composable
    private fun FocusUI(viewModel: MainViewModel) {
        // This is your UI from the old SpaceActivity
        val spaces by viewModel.spaces
        // Find the first (and only) space created during onboarding
        val defaultSpace = spaces.firstOrNull()

        if (defaultSpace != null) {
            // If we found the space, display the ActiveSpaceUI
            ActiveSpaceUI (
                space = defaultSpace,
                onBlockNotifications = { toggleDnd(true) },
                onAllowNotifications = { toggleDnd(false) },
                onForceExit = {
                    val prefs = getSharedPreferences("UnpluckPrefs", MODE_PRIVATE)
                    prefs.edit { putString("APP_MODE_KEY", AppMode.NORMAL_MODE.name) }
                    viewModel.appMode.value = AppMode.NORMAL_MODE
                }
            )
        } else {
            // Show a fallback message if something went wrong and no space was found
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: No default space found. Please restart the app.")
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

    private fun registerModeChangeReceiver() {
        val intentFilter = IntentFilter("com.unpluck.app.ACTION_MODE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(modeChangeReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(modeChangeReceiver, intentFilter)
        }
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

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                requestRoleLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Call Screening is already active or unavailable.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}