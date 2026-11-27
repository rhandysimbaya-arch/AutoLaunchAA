package com.example.autolaunchaa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow

/** Simple persisted settings. */
class Prefs(c: Context) {
    private val sp = c.applicationContext.getSharedPreferences("car", Context.MODE_PRIVATE)
    var mac: String?
        get() = sp.getString("mac", null)
        set(v) = sp.edit().putString("mac", v).apply()
    var name: String?
        get() = sp.getString("name", null)
        set(v) = sp.edit().putString("name", v).apply()
    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()
    /** Try startActivity directly as well as notifying. Usually blocked on Android 10+. */
    var directLaunch: Boolean
        get() = sp.getBoolean("directLaunch", false)
        set(v) = sp.edit().putBoolean("directLaunch", v).apply()
    var delaySec: Int
        get() = sp.getInt("delaySec", 2)
        set(v) = sp.edit().putInt("delaySec", v).apply()
}

enum class Phase(val label: String) {
    IDLE("WAITING FOR CAR"),
    CONNECTING("CONNECTING"),
    READY("READY"),
    LAUNCHED("LAUNCHED"),
    ACTION_NEEDED("ACTION NEEDED")
}

data class HudState(
    val phase: Phase = Phase.IDLE,
    val btOk: Boolean = false,
    val wifiOk: Boolean = false
)

object HudBus {
    val state = MutableStateFlow(HudState())
}

object Readiness {
    fun wifiOn(c: Context): Boolean =
        c.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true

    fun fiveGhz(c: Context): Boolean =
        c.applicationContext.getSystemService(WifiManager::class.java)?.is5GHzBandSupported == true

    fun locationOn(c: Context): Boolean =
        c.getSystemService(LocationManager::class.java)?.isLocationEnabled == true

    fun wifiReady(c: Context) = wifiOn(c) && fiveGhz(c) && locationOn(c)

    fun btPermission(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 31
}

/** Shared logic used by the Bluetooth receiver and the "Test connection" button. */
object CarEvents {
    private val handler = Handler(Looper.getMainLooper())

    fun onCarConnected(c: Context) {
        val app = c.applicationContext
        val p = Prefs(app)
        if (!p.enabled) return
        handler.removeCallbacksAndMessages(null) // debounce rapid connect/disconnect
        HudBus.state.value = HudState(Phase.CONNECTING, btOk = true, wifiOk = Readiness.wifiReady(app))
        handler.postDelayed({
            val wifiOk = Readiness.wifiReady(app)
            val phase = if (wifiOk) AutoLauncher.trigger(app) else Phase.ACTION_NEEDED
            HudBus.state.value = HudState(phase, btOk = true, wifiOk = wifiOk)
        }, p.delaySec * 1000L)
    }

    fun onCarDisconnected(c: Context) {
        handler.removeCallbacksAndMessages(null)
        AutoLauncher.cancel(c.applicationContext)
        HudBus.state.value = HudState()
    }
}
