package com.unpluck.app.services

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.Q)
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
                .setSkipCallLog(false)
                .setSkipNotification(false)
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