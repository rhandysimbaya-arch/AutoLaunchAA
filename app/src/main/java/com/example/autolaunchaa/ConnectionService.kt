package com.example.autolaunchaa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps a runtime receiver registered (ACL broadcasts can't be
 * relied on from the manifest). It stays alive across disconnects so the next connect is seen.
 */
class ConnectionService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

            val mac = Prefs(c).mac
            if (mac == null || !mac.equals(dev?.address, ignoreCase = true)) return

            when (i.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> CarEvents.onCarConnected(c)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> CarEvents.onCarDisconnected(c)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("service", "Car detection", NotificationManager.IMPORTANCE_LOW)
        )
        val n: Notification = NotificationCompat.Builder(this, "service")
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("Watching for your car")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, n)
        }
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        fun start(c: Context) {
            c.startForegroundService(Intent(c, ConnectionService::class.java))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, ConnectionService::class.java))
        }
    }
}
