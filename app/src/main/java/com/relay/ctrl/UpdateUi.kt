package com.relay.ctrl
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BUSY = setOf("checking", "downloading", "verifying", "rebooting")
private val RESTARTING = setOf("downloading", "verifying", "rebooting")

private fun stateText(s: String) = when (s) {
    "idle" -> "Idle"; "checking" -> "Checking…"; "up_to_date" -> "Up to date"; "available" -> "Update available"
    "downloading" -> "Downloading…"; "verifying" -> "Verifying…"; "rebooting" -> "Restarting…"; "error" -> "Error"
    else -> s.ifBlank { "Unknown" }
}
private fun ago(s: Int) = when { s < 60 -> "just now"; s < 3600 -> "${s / 60} min ago"; else -> "${s / 3600} h ago" }

/** Firmware status, manual check, and a confirmed install. Polls only while this dialog is open. */
@Composable
fun UpdateDialog(d: Dev, vm: Vm, close: () -> Unit) {
    val p = LocalPal.current
    var st by remember { mutableStateOf<UpdateStatus?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try { st = vm.withDevice(d.base) { Api.updateStatus(d.base) }; err = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { err = if (st?.state in RESTARTING) "Device is restarting, reconnecting…" else e.message }
    }
    fun act(f: suspend () -> Unit) {
        scope.launch {
            try { vm.withDevice(d.base) { f() }; err = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { err = e.message }
            refresh()
        }
    }
    LaunchedEffect(Unit) { while (true) { refresh(); delay(if (st?.state in BUSY) 1500L else 5000L) } }

    @Composable fun row(label: String, value: String) = Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = p.dim, fontSize = 12.sp)
        Text(value, color = p.txt, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }

    AlertDialog(onDismissRequest = close, containerColor = p.bg,
        title = { Text("Firmware · ${d.base.removePrefix("http://")}", color = p.txt) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(10.dp)) {
            val s = st
            if (s == null) { if (err == null) CircularProgressIndicator(color = p.txt) }
            else {
                row("Installed", s.current)
                d.info?.let { if (it.rssi != 0) row("Wi-Fi signal", "${it.rssi} dBm") }
                if (s.latest.isNotBlank()) row("Latest", s.latest)
                row("Status", stateText(s.state))
                if (s.state == "downloading" || s.state == "verifying")
                    LinearProgressIndicator(progress = { (s.progress / 100f).coerceIn(0f, 1f) }, color = p.txt, trackColor = p.lo)
                if (s.message.isNotBlank()) Text(s.message, color = p.dim, fontSize = 12.sp)
                if (s.pendingConfirm) Text("The new firmware is confirming itself after the restart (about 90 s).", color = p.dim, fontSize = 12.sp)
                if (s.lastCheckSecondsAgo >= 0) Text("Last checked ${ago(s.lastCheckSecondsAgo)}", color = p.dim, fontSize = 11.sp)
                if (!s.configured) Text("Updates are not configured on this device.", color = Deep, fontSize = 12.sp)
                else if (s.state !in BUSY) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeuButton("Check for updates", Modifier.weight(1f)) { act { Api.updateCheck(d.base) } }
                    if (s.state == "available") NeuButton("Install ${s.latest}", Modifier.weight(1f), true) { confirm = true }
                }
            }
            err?.let { Text(it, color = Deep, fontSize = 12.sp) }
        } },
        confirmButton = { TextButton(close) { Text("Close", color = p.txt) } })

    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = p.bg,
        title = { Text("Install update?", color = p.txt) },
        text = { Text("The device will restart and every relay will switch off for several seconds. Make sure the connected loads can handle that, and don't unplug the device during the update.", color = p.dim) },
        confirmButton = { TextButton({ confirm = false; act { Api.updateApply(d.base) } }) { Text("Install", color = p.txt) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", color = p.dim) } })
}
