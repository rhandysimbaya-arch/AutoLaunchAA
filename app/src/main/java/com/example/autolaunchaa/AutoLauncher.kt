package com.example.autolaunchaa

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat

/**
 * Android cannot force-start Android Auto. On Android 10+ background activity starts are
 * blocked, so the reliable path is a high-priority notification with a full-screen intent.
 */
object AutoLauncher {
    const val PKG = "com.google.android.projection.gearhead"
    private const val CH = "auto_launch"
    private const val NOTIF_ID = 2

    fun trigger(c: Context): Phase {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return Phase.ACTION_NEEDED

        val launch = c.packageManager.getLaunchIntentForPackage(PKG)
        val intent = (launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PKG")))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        var phase = Phase.READY
        if (launch != null && Prefs(c).directLaunch) {
            try {
                c.startActivity(intent)
                phase = Phase.LAUNCHED
            } catch (_: Exception) { /* blocked: the notification below is the fallback */ }
        }

        nm.createNotificationChannel(
            NotificationChannel(CH, "Car connection", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            c, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            NOTIF_ID,
            NotificationCompat.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_car)
                .setContentTitle("Car connected")
                .setContentText(
                    if (launch != null) "Tap to open Android Auto"
                    else "Android Auto is not installed. Tap to get it."
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .build()
        )
        return phase
    }

    fun cancel(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}
