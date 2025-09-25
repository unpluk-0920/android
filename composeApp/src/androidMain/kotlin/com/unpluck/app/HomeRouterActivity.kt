// HomeRouterActivity.kt
package com.unpluck.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.unpluck.app.data.AppDatabase
import com.unpluck.app.defs.LauncherType // Import your LauncherType enum
import androidx.core.content.edit

class HomeRouterActivity : ComponentActivity() {

    private val TAG = "HomeRouterActivity"
    private val PREFS_NAME = "UnpluckPrefs"
    private val KEY_ONBOARDING_COMPLETE = "OnboardingComplete"
    private val KEY_SELECTED_LAUNCHER_MODULE = "SELECTED_LAUNCHER_MODULE"
    private val KEY_REAL_LAUNCHER_PACKAGE = "RealLauncherPackage" // Re-used key from MainActivity
    private val KEY_REAL_LAUNCHER_ACTIVITY = "RealLauncherActivity" // Re-used key from MainActivity

    // We'll instantiate the ViewModel directly as done in MainActivity (though not strictly needed here)
    // private lateinit var viewModel: MainViewModel // Not needed in HomeRouterActivity after all.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "HomeRouterActivity onCreate() called.")

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        val selectedLauncherTypeString = prefs.getString(KEY_SELECTED_LAUNCHER_MODULE, null)
        val selectedLauncherType = selectedLauncherTypeString?.let { LauncherType.valueOf(it) }

        Log.d(TAG, "OnboardingComplete: $onboardingCompleted, SelectedLauncherModule: $selectedLauncherType")

        if (!onboardingCompleted) {
            Log.i(TAG, "Routing to MainActivity for Onboarding (Onboarding not completed).")
            // Launch MainActivity, which will display OnboardingFlow because viewModel.onboardingCompleted will be false
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // Onboarding is complete, decide what to launch based on selected launcher type
            when (selectedLauncherType) {
                LauncherType.ORIGINAL_PROXY -> {
                    Log.i(TAG, "Selected ORIGINAL_PROXY. Attempting to launch system's default home.")
                    // This is where we launch the *original* launcher that was saved during setup.
                    val realLauncherPkg = prefs.getString(KEY_REAL_LAUNCHER_PACKAGE, null)
                    val realLauncherAct = prefs.getString(KEY_REAL_LAUNCHER_ACTIVITY, null)

                    if (realLauncherPkg != null) {
                        launchExternalAppAsLauncher(realLauncherPkg, realLauncherAct)
                    } else {
                        Log.e(TAG, "ORIGINAL_PROXY selected but no real launcher package saved. Launching Unpluck's own MainActivity.")
                        // Fallback: if data is missing, just show Unpluck's main UI.
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                }
                LauncherType.KISS, LauncherType.LAWNCHAIR -> {
                    Log.i(TAG, "Selected $selectedLauncherType. Launching Unpluck's own MainActivity (placeholder for future module).")
                    // For KISS/Lawnchair, Unpluck itself will act as the home.
                    // When modules are ready, this will launch a specific internal activity or service.
                    // For now, it means MainActivity will show Unpluck's UI.
                    startActivity(Intent(this, MainActivity::class.java))
                }
                null -> {
                    Log.w(TAG, "No launcher module selected (even after onboarding). Routing to MainActivity for Onboarding to re-select.")
                    // If onboarding is complete but no launcher module is selected (e.g., preference cleared),
                    // force user back to launcher selection within onboarding.
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra("start_on_launcher_selection", true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            }
        }

        // HomeRouterActivity always finishes itself after routing to the appropriate activity.
        // This prevents it from being on the back stack and avoids loops with itself.
        finish()
    }

    /**
     * Helper to launch an external app as a launcher.
     * This is used when ORIGINAL_PROXY is selected.
     */
    private fun launchExternalAppAsLauncher(packageName: String, activityName: String?) {
        val launchIntent = if (activityName != null) {
            Intent().setComponent(ComponentName(packageName, activityName))
        } else {
            packageManager.getLaunchIntentForPackage(packageName)
        }

        if (launchIntent != null) {
            // These flags are crucial for making the launched app the new "home"
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or // Clears the current task stack
                        Intent.FLAG_ACTIVITY_TASK_ON_HOME // Ensures it lands on the home screen
            )
            try {
                startActivity(launchIntent)
                Log.d(TAG, "Successfully launched external app: $packageName (component: $activityName)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch external app $packageName: ${e.message}")
                Toast.makeText(this, "Could not launch selected launcher. Please re-select a launcher.", Toast.LENGTH_LONG).show()
                // Clear the problematic choice and direct to Unpluck's home as a fallback
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                    remove(KEY_REAL_LAUNCHER_PACKAGE)
                        .remove(KEY_REAL_LAUNCHER_ACTIVITY)
                }
                // Fallback to Unpluck's main activity
                startActivity(Intent(this, MainActivity::class.java))
            }
        } else {
            Log.e(TAG, "Could not find launch intent for external app: $packageName")
            Toast.makeText(this, "Selected launcher app not found. Please re-select.", Toast.LENGTH_LONG).show()
            // Clear the problematic choice and direct to Unpluck's home as a fallback
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                remove(KEY_REAL_LAUNCHER_PACKAGE)
                    .remove(KEY_REAL_LAUNCHER_ACTIVITY)
            }
            // Fallback to Unpluck's main activity
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}