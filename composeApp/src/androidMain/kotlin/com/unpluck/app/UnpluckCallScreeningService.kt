// In composeApp/src/androidMain/kotlin/com/unpluck/app/UnpluckCallScreeningService.kt
package com.unpluck.app

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.Q) // <-- ADD THIS ANNOTATION
class UnpluckCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle.schemeSpecificPart
        Log.d("CallScreeningService", "Incoming call from: $phoneNumber")

        // In the future, we will check a list of allowed contacts.
        // For now, let's block all calls as a test.
        val isBlocked = true // Placeholder logic

        val response = if (isBlocked) {
            Log.d("CallScreeningService", "Blocking call.")
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false) // You can change this to true to hide from call log
                .setSkipNotification(false) // You can change this to true to hide the notification
                .build()
        } else {
            Log.d("CallScreeningService", "Allowing call.")
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .build()
        }
        respondToCall(callDetails, response)
    }
}