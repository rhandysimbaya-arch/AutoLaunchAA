package com.example.autolaunchaa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Prefs(c).enabled && Prefs(c).mac != null && Readiness.btPermission(c)) {
            try {
                ConnectionService.start(c)
            } catch (_: Exception) { /* start restrictions: user can open the app once */ }
        }
    }
}
