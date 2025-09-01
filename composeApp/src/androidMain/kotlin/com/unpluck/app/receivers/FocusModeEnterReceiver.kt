package com.unpluck.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.unpluck.app.MainActivity
import com.unpluck.app.services.BleService

class FocusModeEnterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BleService.Companion.ACTION_ENTER_FOCUS_MODE) {
            Log.d("MainActivity", "Broadcast recieved FOCUS MODE")
            val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
                // Add a command for MainActivity to read
                putExtra(MainActivity.Companion.EXTRA_ACTION, MainActivity.Companion.ACTION_ENTER_FOCUS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(mainActivityIntent)
        }
    }
}