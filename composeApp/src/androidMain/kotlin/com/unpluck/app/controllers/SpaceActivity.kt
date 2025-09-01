package com.unpluck.app.controllers

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.unpluck.app.defs.Space
import com.unpluck.app.services.BleService
import com.unpluck.app.views.UnpluckApp

class SpaceActivity : ComponentActivity() {

    private lateinit var notificationManager: NotificationManager
    private var isExitingCleanly = false

    private val requestDndPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
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

    private fun forceOpenDefaultAppSettings() {
        Log.d("CallScreening", "Forcing open default app settings.")
        Toast.makeText(this, "Check for 'unpluck' in the list", Toast.LENGTH_LONG).show()
        try {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("CallScreening", "Could not open default app settings", e)
            Toast.makeText(this, "Could not open settings.", Toast.LENGTH_SHORT).show()
        }
    }
    // 1. Ensure the 'closeReceiver' is defined here, inside the class
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BleService.Companion.ACTION_CLOSE_SPACE_ACTIVITY) {
                Log.d("SpaceActivity", "Close broadcast received. Finishing activity.")
                if(hasDndPermission()) toggleDnd(false)
                isExitingCleanly = true
                finish()
            }
        }
    }

    private fun requestCallScreeningRole() {
        Log.d("CallScreening", "Button clicked. Requesting role...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    Log.d("CallScreening", "Role not held. Creating intent...")
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    requestRoleLauncher.launch(intent)
                } else {
                    Log.d("CallScreening", "Role is already held.")
                    Toast.makeText(this, "Call Screening is already active.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("CallScreening", "Role not available")
            }
        } else {
            Log.d("CallScreening", "SDK version too old.")
            Toast.makeText(this, "Feature requires Android 10 or higher.", Toast.LENGTH_SHORT).show()
        }
    }
    // 2. This is the 'onCreate' function you posted, which is correct
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            // This callback is now enabled.
            // By leaving this block empty, we are overriding the
            // default back button behavior and doing nothing.
            Log.d("SpaceActivity", "Back button pressed, but action is disabled.")
        }
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intentFilter = IntentFilter(BleService.Companion.ACTION_CLOSE_SPACE_ACTIVITY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, intentFilter)
        }
        Log.d("SpaceActivity", "BroadcastReceiver registered.")

        setContent {
            val spaces = listOf(
                Space(id = 1, name = "Focus", appIds = emptyList()),
                Space(id = 2, name = "Family", appIds = emptyList()),
                Space(id = 3, name = "Meetings", appIds = emptyList())
            )

            UnpluckApp(
                spaces = spaces,
                onBlockNotifications = { toggleDnd(true) },
                onAllowNotifications = { toggleDnd(false) },
                onEnableCallBlocking = { requestCallScreeningRole() },
                onCheckSettings = { forceOpenDefaultAppSettings() },
                onForceExit = {
                    isExitingCleanly = true
                    finish()
                }
            )
        }
    }

    // 3. Ensure you have the 'onDestroy' method to unregister the receiver
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(closeReceiver)
        Log.d("SpaceActivity", "BroadcastReceiver unregistered.")
    }

    private fun hasDndPermission(): Boolean {
        return notificationManager.isNotificationPolicyAccessGranted
    }

    private fun requestDndPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        Toast.makeText(this, "Please enable Do Not Disturb access for unpluck.", Toast.LENGTH_LONG).show()
        requestDndPermissionLauncher.launch(intent)
    }

    private fun toggleDnd(enable: Boolean) {
        if (!hasDndPermission()) {
            requestDndPermission()
            return
        }

        if (enable) {
            // Turn ON Do Not Disturb (blocks all notifications and sounds)
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        } else {
            // Turn OFF Do Not Disturb (allows all notifications)
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

//    override fun onResume() {
//        super.onResume()
//        isResumed = true
//    }
//
//    override fun onPause() {
//        super.onPause()
//        // If the activity is paused and it was previously resumed (i.e., not just starting),
//        // it's likely due to a home button press. Re-launch the activity.
//        if (!isExitingCleanly && isResumed) {
//            val intent = Intent(this, SpaceActivity::class.java).apply {
//                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
//            }
//            startActivity(intent)
//        }
//    }
}