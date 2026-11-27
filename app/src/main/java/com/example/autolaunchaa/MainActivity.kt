package com.example.autolaunchaa

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var refreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val hud by HudBus.state.collectAsStateWithLifecycle()
                    if (hud.phase != Phase.IDLE) {
                        HudStatus(hud) { openAndroidAuto() }
                    } else {
                        SettingsScreen(refreshTick)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++ // re-check Wi-Fi / location / permissions after returning from system settings
    }

    private fun openAndroidAuto() {
        val i = packageManager.getLaunchIntentForPackage(AutoLauncher.PKG)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${AutoLauncher.PKG}"))
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ---------- HUD status screen: big text, high contrast, 64dp+ targets, no scrolling ----------

@Composable
fun HudStatus(state: HudState, onOpen: () -> Unit) {
    val color = when (state.phase) {
        Phase.READY, Phase.LAUNCHED -> Color(0xFF4CAF50)
        Phase.CONNECTING -> Color(0xFFFFC107)
        Phase.ACTION_NEEDED -> Color(0xFFF44336)
        Phase.IDLE -> Color.Gray
    }
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(state.phase.label, fontSize = 48.sp, color = color, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Icon(Icons.Default.Bluetooth, null, Modifier.size(72.dp), tint = okColor(state.btOk))
            Icon(Icons.Default.Wifi, null, Modifier.size(72.dp), tint = okColor(state.wifiOk))
        }
        Button(onClick = onOpen, modifier = Modifier.height(80.dp).fillMaxWidth()) {
            Text("Open Android Auto", fontSize = 28.sp)
        }
    }
}

private fun okColor(ok: Boolean) = if (ok) Color(0xFF4CAF50) else Color(0xFFF44336)

// ---------- Settings screen ----------

@Composable
fun SettingsScreen(refreshTick: Int) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var direct by remember { mutableStateOf(prefs.directLaunch) }
    var delay by remember { mutableFloatStateOf(prefs.delaySec.toFloat()) }
    var carName by remember { mutableStateOf(prefs.name) }
    var showPicker by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    val permsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }

    fun requestPerms() {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) p += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        permsLauncher.launch(p.toTypedArray())
    }

    // refreshTick / tick are read so the readiness rows recompute
    val recompute = refreshTick + tick
    val btPerm = Readiness.btPermission(ctx)
    val wifiOn = Readiness.wifiOn(ctx)
    val fiveG = Readiness.fiveGhz(ctx)
    val locOn = Readiness.locationOn(ctx)

    fun applyService() {
        if (enabled && prefs.mac != null && Readiness.btPermission(ctx)) ConnectionService.start(ctx)
        else ConnectionService.stop(ctx)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Auto Launch AA", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Detects your car's Bluetooth and prompts you to open Android Auto. " +
                "Android does not let any app force-start Android Auto, so on Android 10+ " +
                "expect a notification to tap.",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))

        Button(onClick = { requestPerms() }, Modifier.fillMaxWidth()) { Text("Grant permissions") }
        Spacer(Modifier.height(12.dp))

        Text("Car device", fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = { if (btPerm) showPicker = true else requestPerms() },
            Modifier.fillMaxWidth()
        ) { Text(carName ?: "Pick paired Bluetooth device") }

        if (showPicker) {
            val devices = remember(tick) {
                try {
                    ctx.getSystemService(BluetoothManager::class.java)
                        .adapter?.bondedDevices?.toList().orEmpty()
                } catch (_: SecurityException) { emptyList() }
            }
            AlertDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
                title = { Text("Paired devices") },
                text = {
                    Column {
                        if (devices.isEmpty()) Text("No paired devices. Pair your car first.")
                        devices.forEach { d ->
                            val label = try { d.name ?: d.address } catch (_: SecurityException) { d.address }
                            TextButton(onClick = {
                                prefs.mac = d.address; prefs.name = label
                                carName = label; showPicker = false; applyService()
                            }) { Text(label) }
                        }
                    }
                }
            )
        }

        SwitchRow("Prompt to open Android Auto when car connects", enabled) {
            enabled = it; prefs.enabled = it; applyService()
        }
        SwitchRow("Also try direct launch (usually blocked on Android 10+)", direct) {
            direct = it; prefs.directLaunch = it
        }
        Text("Delay before prompt: ${delay.roundToInt()} s")
        Slider(
            value = delay, onValueChange = { delay = it },
            onValueChangeFinished = { prefs.delaySec = delay.roundToInt() },
            valueRange = 0f..15f, steps = 14
        )

        Spacer(Modifier.height(8.dp))
        Text("Readiness", fontWeight = FontWeight.SemiBold)
        ReadyRow("Bluetooth permission", btPerm) { requestPerms() }
        ReadyRow("Wi-Fi on", wifiOn) { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        ReadyRow("5 GHz supported", fiveG, fixable = false) {}
        ReadyRow("Location on", locOn) { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { CarEvents.onCarConnected(ctx) }, Modifier.fillMaxWidth()) {
            Text("Test connection")
        }
        OutlinedButton(
            onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            Modifier.fillMaxWidth()
        ) { Text("Battery optimization settings") }
        Text(
            "Test connection needs 'Prompt' switched on. Exempt this app from battery " +
                "optimization so detection keeps running.",
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ReadyRow(label: String, ok: Boolean, fixable: Boolean = true, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle, null,
            tint = if (ok) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
        Spacer(Modifier.width(8.dp))
        Text(if (!ok && !fixable) "$label (not available on this phone)" else label, Modifier.weight(1f))
        if (!ok && fixable) TextButton(onClick = onFix) { Text("Fix", color = Color(0xFFF44336)) }
    }
}
