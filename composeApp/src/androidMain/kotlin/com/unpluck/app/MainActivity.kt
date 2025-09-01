package com.unpluck.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.unpluck.app.controllers.SpaceActivity
import com.unpluck.app.defs.LauncherInfo
import com.unpluck.app.defs.Space
import com.unpluck.app.services.BleService
import com.unpluck.app.views.LauncherSelectionScreen
import com.unpluck.app.views.UnpluckApp

enum class AppMode {
    PASSTHROUGH_LAUNCHER,
    FOCUS_MODE
}
class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private val PREFS_NAME = "UnpluckPrefs"
    private val KEY_LAUNCHER_PACKAGE = "RealLauncherPackage"
    private val KEY_LAUNCHER_ACTIVITY = "RealLauncherActivity"
    private val appMode = mutableStateOf(AppMode.PASSTHROUGH_LAUNCHER)

    companion object {
        // These are the commands our receivers will send
        const val EXTRA_ACTION = "com.unpluck.app.EXTRA_ACTION"
        const val ACTION_ENTER_FOCUS = "ENTER_FOCUS_MODE"
        const val ACTION_EXIT_FOCUS = "EXIT_FOCUS_MODE"
    }

//
//    private val modeChangeReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            when (intent?.action) {
//                BleService.ACTION_ENTER_FOCUS_MODE -> {
//                    Log.d("MainActivity", "Broadcast received: Entering Focus Mode")
//                    appMode.value = AppMode.FOCUS_MODE
//                }
//                BleService.ACTION_EXIT_FOCUS_MODE -> {
//                    Log.d("MainActivity", "Broadcast received: Exiting Focus Mode")
//                    appMode.value = AppMode.PASSTHROUGH_LAUNCHER
//                }
//            }
//        }
//    }

    // --- PERMISSION HANDLING START ---
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startBleService()
                proceedWithLauncherLogic()
            } else {
                // Handle permission denial - for now, just proceed
                proceedWithLauncherLogic()
            }
        }
    // --- PERMISSION HANDLING END ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        Log.d("MainActivity", "Hittes in background on create")
        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this) {
            if (appMode.value == AppMode.FOCUS_MODE) {
                Log.d("MainActivity", "Back button disabled in Focus Mode.")
            } else {
                // In passthrough mode, the back button should have no effect
                // as we are either showing the selection or the real launcher.
            }
        }

        checkPermissionsAndProceed()

        setContent {
            val currentMode by appMode // Observe the state

            when (currentMode) {
                AppMode.PASSTHROUGH_LAUNCHER -> {
                    // This composable handles the entire passthrough flow.
                    PassthroughLauncherUI()
                }
                AppMode.FOCUS_MODE -> {
                    // This composable shows the focus mode screen.
                    val spaces = listOf(
                        Space(id = 1, name = "Focus", appIds = emptyList()),
                        Space(id = 2, name = "Family", appIds = emptyList()),
                        Space(id = 3, name = "Meetings", appIds = emptyList())
                    )
                    UnpluckApp(
                        onBlockNotifications = { null },
                        onAllowNotifications = { null },
                        spaces = spaces,
                        onEnableCallBlocking = { null },
                        onCheckSettings = { null },
                        onForceExit = { null }
                    )
                }
            }
        }

        // 5. Kick off the initial logic.
    }

    @Composable
    private fun PassthroughLauncherUI() {
        val hasSavedLauncher = prefs.contains(KEY_LAUNCHER_PACKAGE)

        if (hasSavedLauncher) {
            // If a launcher is already saved, launch it immediately and finish.
            // LaunchedEffect ensures this runs once when the composable enters the screen.
            LaunchedEffect(Unit) {
                proceedWithLauncherLogic()
            }
        } else {
            // If no launcher is saved, show the selection screen.
            ShowLauncherSelectionUI()
        }
    }

    @Composable
    private fun ShowLauncherSelectionUI() {
        val packageManager = this.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfoList = packageManager.queryIntentActivities(homeIntent, 0)
        val otherLaunchers = resolveInfoList
            .filter { it.activityInfo.packageName != packageName }
            .map {
                LauncherInfo(
                    it.loadLabel(packageManager).toString(),
                    it.activityInfo.packageName,
                    it.activityInfo.name,
                    it.loadIcon(packageManager)
                )
            }

        LauncherSelectionScreen(
            labels = otherLaunchers.map { it.label },
            onLauncherSelected = { index ->
                val selected = otherLaunchers[index]
                prefs.edit {
                    putString(KEY_LAUNCHER_PACKAGE, selected.packageName)
                    putString(KEY_LAUNCHER_ACTIVITY, selected.activityName)
                }
                // Relaunch the passthrough logic, which will now succeed.
                proceedWithLauncherLogic()
            }
        )
    }

    // This is needed to handle the case where the activity is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_ENTER_FOCUS -> appMode.value = AppMode.FOCUS_MODE
            ACTION_EXIT_FOCUS -> appMode.value = AppMode.PASSTHROUGH_LAUNCHER
        }
    }

    private fun checkPermissionsAndProceed() {
        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            startBleService()
        } else {
            requestMultiplePermissions.launch(requiredPermissions)
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

    private fun proceedWithLauncherLogic() {
        val savedPackage = prefs.getString(KEY_LAUNCHER_PACKAGE, null)
        val savedActivity = prefs.getString(KEY_LAUNCHER_ACTIVITY, null)

        if (savedPackage != null && savedActivity != null) {
            launchLauncher(savedPackage, savedActivity)
        } else {
            showLauncherSelection()
        }
    }
    private fun launchLauncher(packageName: String, activityName: String) {
        val launchIntent = Intent().apply {
            setClassName(packageName, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0)
        startActivity(launchIntent, options.toBundle())

        finish()
    }
    private fun showLauncherSelection() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfoList = packageManager.queryIntentActivities(homeIntent, 0)
        val otherLaunchers = resolveInfoList
            .filter { it.activityInfo.packageName != packageName }
            .map {
                LauncherInfo(
                    label = it.loadLabel(packageManager).toString(),
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name,
                    icon = it.loadIcon(packageManager)
                )
            }

        if (otherLaunchers.isEmpty()) {
            // This is the fallback error, just in case
            setContent { /* Show your error message UI here */ }
            return
        }

        setContent {
            LauncherSelectionScreen(
                labels = otherLaunchers.map { it.label },
                onLauncherSelected = { index ->
                    val selected = otherLaunchers[index]
                    // Save the user's choice
                    prefs.edit {
                        putString(KEY_LAUNCHER_PACKAGE, selected.packageName)
                            .putString(KEY_LAUNCHER_ACTIVITY, selected.activityName)
                    }
                    // Launch it for the first time
                    launchLauncher(selected.packageName, selected.activityName)
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
       // unregisterReceiver(modeChangeReceiver)
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "Hittes in background on pause")
        // If the activity is resumed and we are in passthrough mode,
        // it means the user has pressed Home. Re-launch the real launcher.
    }


    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "Hittes in background on resume")
        // If the activity is resumed and we are in passthrough mode,
        // it means the user has pressed Home. Re-launch the real launcher.
        if (appMode.value == AppMode.PASSTHROUGH_LAUNCHER) {
            proceedWithLauncherLogic()
        }
    }
}