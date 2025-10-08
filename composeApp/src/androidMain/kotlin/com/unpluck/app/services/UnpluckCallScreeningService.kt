// In services/UnpluckCallScreeningService.kt
package com.unpluck.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.unpluck.app.AppMode // Import if needed
import com.unpluck.app.data.AppDatabase
import com.unpluck.app.data.SpaceDao
import com.unpluck.app.defs.CONSTANTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class UnpluckCallScreeningService : CallScreeningService() {
    private val TAG = "CallScreeningService"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob) // Use IO dispatcher for DB
    private lateinit var spaceDao: SpaceDao
    private lateinit var appContext: Context

    // Local variable to hold the currently active space's call blocking setting
    // This will be updated when the broadcast is received.
    private var isCallBlockingActiveSpaceEnabled: Boolean = false
    private var allowedContactIdsForActiveSpace: Set<String> = emptySet()

    // Assuming you have a way to get the current app mode (e.g., from SharedPreferences)
    // and the active space ID.
    private val prefs by lazy { getSharedPreferences(CONSTANTS.PREFS_NAME, Context.MODE_PRIVATE) }


    private val settingsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CONSTANTS.ACTION_CALL_BLOCKING_SETTINGS_CHANGED) {
                Log.d(TAG, "Received call blocking settings changed broadcast. Re-evaluating rules.")
                // Trigger a re-load of the active space's settings
                loadActiveSpaceCallBlockingSettings()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CallScreeningService onCreate")
        appContext = applicationContext
        spaceDao = AppDatabase.getDatabase(appContext).spaceDao()

        // Register the broadcast receiver
        val filter = IntentFilter(CONSTANTS.ACTION_CALL_BLOCKING_SETTINGS_CHANGED)
        appContext.registerReceiver(settingsChangedReceiver, filter)

        // Load initial settings
        loadActiveSpaceCallBlockingSettings()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle.toString() // e.g., tel:1234567890
        val incomingNumber = handle.substringAfter("tel:")
        Log.d(TAG, "Incoming call from: $incomingNumber")

        val response = CallResponse.Builder()

        // Get current app mode
        val currentAppMode = prefs.getString(CONSTANTS.KEY_APP_MODE, AppMode.NORMAL_MODE.name)?.let { AppMode.valueOf(it) } ?: AppMode.NORMAL_MODE

        // Only block calls if the app is in FOCUS_MODE AND call blocking is enabled for the active space
        if (currentAppMode == AppMode.FOCUS_MODE && isCallBlockingActiveSpaceEnabled) {
            Log.d(TAG, "App is in FOCUS_MODE and Call Blocking is enabled for active space.")

            // Check if the incoming number is in the allowed contacts list
            // This would require resolving the contact ID to a phone number,
            // which is more complex (ContentResolver query for phone number by contact ID).
            // For now, we'll assume a direct match or a simplified check.
            val isAllowedContact = checkNumberAgainstAllowedContacts(incomingNumber)

            if (!isAllowedContact) {
                Log.i(TAG, "Blocking call from $incomingNumber (not an allowed contact).")
                response.setDisallowCall(true)
                    .setRejectCall(true) // Hang up the call
                    .setSkipCallLog(true) // Don't show in call log
                    .setSkipNotification(true) // Don't show notification
            } else {
                Log.i(TAG, "Allowing call from $incomingNumber (is an allowed contact).")
                response.setDisallowCall(false) // Allow the call
            }
        } else {
            Log.d(TAG, "App not in FOCUS_MODE or Call Blocking not enabled. Allowing call from $incomingNumber.")
            response.setDisallowCall(false) // Always allow if not in focus mode or feature not enabled
        }

        respondToCall(callDetails, response.build())
    }

    private fun loadActiveSpaceCallBlockingSettings() {
        serviceScope.launch {
            val activeSpaceId = prefs.getString(CONSTANTS.KEY_ACTIVE_SPACE_ID, null)
            if (activeSpaceId != null) {
                val activeSpace = spaceDao.getSpaceById(activeSpaceId).firstOrNull()
                isCallBlockingActiveSpaceEnabled = activeSpace?.isCallBlockingEnabled ?: false
                allowedContactIdsForActiveSpace = activeSpace?.allowedContactIds?.toSet() ?: emptySet()
                Log.d(TAG, "Loaded active space call blocking settings: enabled=$isCallBlockingActiveSpaceEnabled, allowedContacts=${allowedContactIdsForActiveSpace.size}")
            } else {
                isCallBlockingActiveSpaceEnabled = false
                allowedContactIdsForActiveSpace = emptySet()
                Log.d(TAG, "No active space ID found. Call blocking disabled.")
            }
        }
    }

    private fun checkNumberAgainstAllowedContacts(incomingNumber: String): Boolean {
        // This is a placeholder. A real implementation needs to:
        // 1. Query Android's ContactsContract to get phone numbers for allowedContactIds.
        // 2. Compare the incomingNumber with these retrieved numbers.
        // This is complex and requires READ_CONTACTS permission.
        // For a hack, you might just return true for any number if allowedContactIds is empty
        // or always false if allowedContactIds is not empty and no number matches.

        if (allowedContactIdsForActiveSpace.isEmpty()) {
            return true // If no specific contacts are allowed, perhaps all are implicitly allowed?
            // Or, if empty, means "block all" if isCallBlockingActiveSpaceEnabled is true.
            // Adjust this logic based on your UX. Let's assume if empty, no specific ALLOW rules,
            // so it falls to the general blocking unless it's a "whitelist all" scenario.
        }

        // --- REALISTIC HACK/PLACEHOLDER ---
        // For a quick check, without full contact resolution:
        // If you are storing phone numbers directly in `allowedContactIds` instead of contact IDs:
        // return allowedContactIdsForActiveSpace.contains(incomingNumber)

        // If it's contact IDs, this needs a ContentResolver query.
        // For now, let's just make it always block if it's not explicitly empty.
        // This means if call blocking is ON and allowed contacts list is NOT empty,
        // any call not explicitly matching a *mock* allowed number would be blocked.
        // A full implementation of `checkNumberAgainstAllowedContacts` would involve:
        /*
        val contactNumbers = mutableSetOf<String>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        // This is a simplified example. For actual contact ID matching,
        // you'd need to iterate through allowedContactIdsForActiveSpace
        // and query for each, or build a complex WHERE clause.
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN (${allowedContactIdsForActiveSpace.joinToString(",") { "?" }})"
        val selectionArgs = allowedContactIdsForActiveSpace.toTypedArray()

        appContext.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberColumn)
                if (number != null) {
                    contactNumbers.add(number.normalizePhoneNumber()) // Need a normalization function
                }
            }
        }
        return contactNumbers.contains(incomingNumber.normalizePhoneNumber())
        */

        // For the "hack" without full contact resolution:
        // Let's assume for simplicity: if allowedContactIdsForActiveSpace is NOT empty,
        // we're implementing a whitelist. If the incoming number isn't explicitly in the whitelist
        // (which we can't fully check without the ContentResolver), it's blocked.
        // THIS IS VERY SIMPLIFIED AND NOT A REAL-WORLD WHITELIST CHECK.
        // For a basic test, if you were to manually put phone numbers into allowedContactIds,
        // you could do: return allowedContactIdsForActiveSpace.contains(incomingNumber)

        // For now, if allowedContactIds is NOT empty, we'll assume it's a whitelist.
        // Since we can't properly resolve numbers to IDs here without more code,
        // for demo purposes, let's just allow an arbitrary "safe" number, or block otherwise.
        return false // By default, block if whitelist is active and we can't verify.
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallScreeningService onDestroy")
        appContext.unregisterReceiver(settingsChangedReceiver)
        serviceJob.cancel() // Cancel all coroutines in this scope
    }
}