package com.unpluck.app

import LauncherSelectionScreen
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private val PREFS_NAME = "UnpluckPrefs"
    private val KEY_LAUNCHER_PACKAGE = "RealLauncherPackage"
    private val KEY_LAUNCHER_ACTIVITY = "RealLauncherActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedPackage = prefs.getString(KEY_LAUNCHER_PACKAGE, null)
        val savedActivity = prefs.getString(KEY_LAUNCHER_ACTIVITY, null)

        if (savedPackage != null && savedActivity != null) {
            // We have a saved choice, launch it directly
            launchLauncher(savedPackage, savedActivity)
        } else {
            // First time setup: show the selection screen
            showLauncherSelection()
        }
    }

    private fun launchLauncher(packageName: String, activityName: String) {
//        val launchIntent = Intent().apply {
//            setClassName(packageName, activityName)
//            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        }
//        startActivity(launchIntent)
        val intent = Intent(this, SpaceActivity::class.java)
        startActivity(intent)
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