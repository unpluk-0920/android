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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import com.unpluck.app.views.UnpluckApp // Your existing Focus UI Composable
import androidx.core.net.toUri

// The two states your launcher can be in.
enum class AppMode {
    NORMAL_MODE, // The mode for proxying to the OEM launcher
    FOCUS_MODE
}

class MainActivity : ComponentActivity() {

    // --- STATE MANAGEMENT ---
    private val appMode = mutableStateOf(AppMode.NORMAL_MODE)
    private val launcherSelected = mutableStateOf(false)

    // --- SharedPreferences KEYS ---
    private val PREFS_NAME = "UnpluckPrefs"
    private val KEY_APP_MODE = "APP_MODE_KEY"
    private val KEY_REAL_LAUNCHER_PACKAGE = "RealLauncherPackage"
    private val KEY_REAL_LAUNCHER_ACTIVITY = "RealLauncherActivity"

    // --- SYSTEM SERVICES & PERMISSIONS ---
    private lateinit var notificationManager: NotificationManager

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startBleService()
            } else {
                Toast.makeText(this, "Permissions are required for the app to function.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestDndPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
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
                    Toast.makeText(this, "Permission Granted!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "This permission is needed for Focus Mode to appear automatically.", Toast.LENGTH_LONG).show()
                }
            }
        }

    private fun checkAndRequestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!Settings.canDrawOverlays(this)) {
                // If permission is not granted, open the settings screen for the user
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                requestOverlayPermissionLauncher.launch(intent)
            }
        }
    }

    // --- BROADCAST RECEIVER ---
    private val modeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.unpluck.app.ACTION_MODE_CHANGED") {
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val newModeName = prefs.getString(KEY_APP_MODE, AppMode.NORMAL_MODE.name)
                appMode.value = AppMode.valueOf(newModeName ?: AppMode.NORMAL_MODE.name)
            }
        }
    }

    // --- LIFECYCLE METHODS ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // --- Initial Setup ---
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        launcherSelected.value = prefs.contains(KEY_REAL_LAUNCHER_PACKAGE)
        val initialModeName = prefs.getString(KEY_APP_MODE, AppMode.NORMAL_MODE.name)
        appMode.value = AppMode.valueOf(initialModeName ?: AppMode.NORMAL_MODE.name)

        checkPermissionsAndStartService()
        registerModeChangeReceiver()
        checkAndRequestOverlayPermission()


        // Disable back button ONLY when in Focus Mode
        onBackPressedDispatcher.addCallback(this) {
            if (appMode.value == AppMode.FOCUS_MODE) {
                Log.d("MainActivity", "Back button disabled in Focus Mode.")
                // Do nothing to disable it
            } else {
                // In normal mode, allow default back behavior (though it's unlikely to be used)
                this.isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                this.isEnabled = true
            }
        }

        // --- UI ---
        setContent {
            if (launcherSelected.value) {
                MainAppUI()
            } else {
                LauncherSelectionScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // CRITICAL: This handles the home button press from another app.
        // If we are in NORMAL_MODE, immediately proxy to the real launcher.
        if (appMode.value == AppMode.NORMAL_MODE && launcherSelected.value) {
            launchRealLauncher(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(modeChangeReceiver)
    }

    // --- COMPOSABLE UI ---
    @Composable
    private fun MainAppUI() {
        val currentMode by appMode
        when (currentMode) {
            AppMode.NORMAL_MODE -> ProxyToRealLauncher()
            AppMode.FOCUS_MODE -> FocusUI()
        }
    }

    @Composable
    private fun FocusUI() {
        // This is your UI from the old SpaceActivity
        val spaces = listOf(
            Space(id = 1, name = "Focus", appIds = emptyList()),
            Space(id = 2, name = "Family", appIds = emptyList()),
        )
        UnpluckApp(
            spaces = spaces,
            onBlockNotifications = { toggleDnd(true) },
            onAllowNotifications = { toggleDnd(false) },
            onEnableCallBlocking = { requestCallScreeningRole() },
            onCheckSettings = { /* Define action */ },
            onForceExit = {
                // Forcing exit now just means switching the mode
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                prefs.edit { putString(KEY_APP_MODE, AppMode.NORMAL_MODE.name) }
                appMode.value = AppMode.NORMAL_MODE
            }
        )
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
                    launcherSelected.value = true
                }) {
                    Text(launcherInfo.loadLabel(packageManager).toString())
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // --- HELPER FUNCTIONS ---
    private fun launchRealLauncher(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val pkg = prefs.getString(KEY_REAL_LAUNCHER_PACKAGE, null)
        val activity = prefs.getString(KEY_REAL_LAUNCHER_ACTIVITY, null)

        if (pkg != null && activity != null) {
            val launchIntent = Intent().apply {
                setClassName(pkg, activity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to launch real launcher", e)
                // Fallback: Clear saved launcher so user can re-select
                prefs.edit { clear() }
                launcherSelected.value = false
            }
        }
        // CRITICAL: Finish this activity to remove it from the back stack.
        (context as? Activity)?.finish()
    }

    private fun checkPermissionsAndStartService() {
        if (requiredPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startBleService()
        } else {
            requestPermissionsLauncher.launch(requiredPermissions)
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
    }

    // --- DND & Call Screening Logic (Moved from SpaceActivity) ---
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