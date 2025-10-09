// In services/UnpluckCallScreeningService.kt
package com.unpluck.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.unpluck.app.AppMode // Import if needed
import com.unpluck.app.data.AppDatabase
import com.unpluck.app.data.SpaceDao
import com.unpluck.app.defs.CONSTANTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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

    private val allowedPhoneNumbersCache = ConcurrentHashMap<String, Set<String>>() // contactId -> Set<PhoneNumber>

    // Assuming you have a way to get the current app mode (e.g., from SharedPreferences)
    // and the active space ID.
    private val prefs by lazy { getSharedPreferences(CONSTANTS.PREFS_NAME, MODE_PRIVATE) }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(settingsChangedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(settingsChangedReceiver, filter)
        }

        // Load initial settings
        loadActiveSpaceCallBlockingSettings()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle.toString() // e.g., tel:1234567890
        val incomingNumber = handle.substringAfter("tel:")
        Log.d(TAG, "Incoming call from: $incomingNumber")

        val response = CallResponse.Builder()

        // Get current app mode
        Log.i(TAG,"${prefs.all}")
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
                val currentAllowedContactIds = activeSpace?.allowedContactIds?.toSet() ?: emptySet()
                allowedContactIdsForActiveSpace = currentAllowedContactIds // Update the IDs

                Log.d(TAG, "Loaded active space call blocking settings: enabled=$isCallBlockingActiveSpaceEnabled, allowedContacts=${allowedContactIdsForActiveSpace.size}")

                // Clear and re-populate the phone number cache based on new IDs
                repopulateAllowedPhoneNumbersCache(currentAllowedContactIds)

            } else {
                isCallBlockingActiveSpaceEnabled = false
                allowedContactIdsForActiveSpace = emptySet()
                allowedPhoneNumbersCache.clear() // Clear cache if no active space
                Log.d(TAG, "No active space ID found. Call blocking disabled.")
            }
        }
    }

    private fun repopulateAllowedPhoneNumbersCache(contactIds: Set<String>) {
        if (contactIds.isEmpty()) {
            allowedPhoneNumbersCache.clear()
            return
        }

        val newCache = ConcurrentHashMap<String, Set<String>>()

        val contentResolver = appContext.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        // Build a selection clause for all contact IDs
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN (${contactIds.joinToString(",") { "?" }})"
        val selectionArgs = contactIds.toTypedArray()

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val contactIdColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val contactId = cursor.getString(contactIdColumn)
                val rawNumber = cursor.getString(numberColumn)

                if (contactId != null && !rawNumber.isNullOrBlank()) {
                    val normalizedNumber = PhoneNumberUtils.normalizeNumber(rawNumber) // Use system's normalization
                    if (normalizedNumber != null) {
                        newCache.compute(contactId) { _, currentNumbers ->
                            (currentNumbers ?: emptySet()) + normalizedNumber
                        }
                    }
                }
            }
        }
        allowedPhoneNumbersCache.clear()
        allowedPhoneNumbersCache.putAll(newCache)
        Log.d(TAG, "Repopulated allowed phone numbers cache. Total contacts with numbers: ${allowedPhoneNumbersCache.size}")
    }

    private fun checkNumberAgainstAllowedContacts(incomingNumber: String): Boolean {
        if (allowedContactIdsForActiveSpace.isEmpty()) {
            // If the whitelist is empty, decide default behavior:
            // - true: Allow all calls (effectively, no whitelist applied)
            // - false: Block all calls (if call blocking is enabled)
            // Let's assume for now: if user enables call blocking but picks no contacts, it means block all.
            return false // If no contacts are allowed, then the incoming number is NOT allowed.
        }

        val normalizedIncomingNumber = PhoneNumberUtils.normalizeNumber(incomingNumber) ?: return false

        // Get the device's default country ISO
        val telephonyManager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val defaultCountryIso = telephonyManager.networkCountryIso?.uppercase() ?: "IN" // Fallback to "US" or your app's primary target country


        // Iterate through all cached allowed phone numbers and check for a match
        return allowedPhoneNumbersCache.values.any { numbers ->
            numbers.any { allowedNum ->
                // --- CHANGE THIS LINE ---
                // Use the recommended PhoneNumberUtils.arePhoneNumberIdentical(String, String, String)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PhoneNumberUtils.areSamePhoneNumber(allowedNum, normalizedIncomingNumber, defaultCountryIso)
                } else {
                    TODO("VERSION.SDK_INT < S")
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallScreeningService onDestroy")
        appContext.unregisterReceiver(settingsChangedReceiver)
        serviceJob.cancel() // Cancel all coroutines in this scope
    }
}