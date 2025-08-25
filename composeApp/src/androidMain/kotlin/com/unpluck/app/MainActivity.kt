package com.unpluck.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private val PREFS_NAME = "UnpluckPrefs"
    private val KEY_LAUNCHER_PACKAGE = "RealLauncherPackage"
    private val KEY_LAUNCHER_ACTIVITY = "RealLauncherActivity"

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
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        checkPermissionsAndProceed()

//        val savedPackage = prefs.getString(KEY_LAUNCHER_PACKAGE, null)
//        val savedActivity = prefs.getString(KEY_LAUNCHER_ACTIVITY, null)
//
//        if (savedPackage != null && savedActivity != null) {
//            // We have a saved choice, launch it directly
//            launchLauncher(savedPackage, savedActivity)
//        } else {
//            // First time setup: show the selection screen
//            showLauncherSelection()
//        }
    }

    private fun checkPermissionsAndProceed() {
        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            startBleService()
            proceedWithLauncherLogic()
        } else {
            requestMultiplePermissions.launch(requiredPermissions)
        }
    }


    private fun startBleService() {
        val intent = Intent(this, BleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
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
}