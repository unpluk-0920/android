package com.unpluck.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unpluck.app.MainActivity
import com.unpluck.app.services.BleService

class FocusModeExitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BleService.Companion.ACTION_EXIT_FOCUS_MODE) {
            val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
                // Add a different command
                putExtra(MainActivity.Companion.EXTRA_ACTION, MainActivity.Companion.ACTION_EXIT_FOCUS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(mainActivityIntent)
        }
    }
}