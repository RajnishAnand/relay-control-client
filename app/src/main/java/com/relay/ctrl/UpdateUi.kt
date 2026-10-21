package com.relay.ctrl
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().background(p.bg, shape).border(1.dp, p.hi.copy(alpha = .35f), shape)
            .verticalScroll(rememberScrollState()).padding(24.dp)) {

            Text("Firmware", color = p.txt, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(d.base.removePrefix("http://"), color = p.dim, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            val s = st
            if (s == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), Alignment.Center) {
                    if (err == null) CircularProgressIndicator(color = p.dim) else Text(err!!, color = Deep, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            } else {
                // details card
                Column(Modifier.fillMaxWidth().neu(18.dp, 3.dp, true).padding(horizontal = 18.dp, vertical = 8.dp)) {
                    @Composable fun row(label: String, value: String, last: Boolean = false, warn: Boolean = false) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), color = p.dim, fontSize = 13.sp)
                            Text(value, color = if (warn) Deep else p.txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                        }
                        if (!last) HorizontalDivider(color = p.dim.copy(alpha = .2f))
                    }
                    row("Installed", s.current.ifBlank { "–" })
                    if (s.latest.isNotBlank()) row("Latest", s.latest)
                    d.info?.let { if (it.rssi != 0) row("Wi-Fi signal", "${it.rssi} dBm") }
                    if (s.lastCheckSecondsAgo >= 0) row("Last checked", ago(s.lastCheckSecondsAgo))
                    row("Status", stateText(s.state), last = true, warn = s.state == "error")
                }

                if (s.state == "downloading" || s.state == "verifying") {
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(progress = { (s.progress / 100f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp),
                        color = p.txt, trackColor = p.lo)
                }
                val notes = buildList {
                    if (s.message.isNotBlank()) add(s.message to false)
                    if (s.pendingConfirm) add("The new firmware is confirming itself after the restart (about 90 s)." to false)
                    if (!s.configured) add("Updates are not configured on this device." to true)
                }
                notes.forEach { (t, warn) ->
                    Spacer(Modifier.height(14.dp))
                    Text(t, color = if (warn) Deep else p.dim, fontSize = 13.sp, lineHeight = 18.sp)
                }
                if (s.configured && s.state !in BUSY) {
                    Spacer(Modifier.height(22.dp))
                    NeuButton("Check for updates", Modifier.fillMaxWidth()) { act { Api.updateCheck(d.base) } }
                    if (s.state == "available") {
                        Spacer(Modifier.height(14.dp))
                        NeuButton("Install ${s.latest}", Modifier.fillMaxWidth(), true) { confirm = true }
                    }
                }
            }
            err?.takeIf { s != null }?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = Deep, fontSize = 13.sp, lineHeight = 18.sp)
            }

            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), Alignment.CenterEnd) {
                TextButton(close) { Text("Close", color = p.txt, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = p.bg,
        title = { Text("Install update?", color = p.txt) },
        text = { Text("The device will restart and every relay will switch off for several seconds. Make sure the connected loads can handle that, and don't unplug the device during the update.", color = p.dim) },
        confirmButton = { TextButton({ confirm = false; act { Api.updateApply(d.base) } }) { Text("Install", color = p.txt, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", color = p.dim) } })
}
