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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DialogShell(title: String, subtitle: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().background(p.bg, shape).border(1.dp, p.hi.copy(alpha = .35f), shape)
            .verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(title, color = p.txt, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = p.dim, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), Alignment.CenterEnd) { TextButton(close) { Text("Close", color = p.txt, fontWeight = FontWeight.Bold) } }
        }
    }
}

@Composable
fun InfoCard(rows: List<Pair<String, String>>) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().neu(18.dp, 3.dp, true).padding(horizontal = 18.dp, vertical = 8.dp)) {
        rows.forEachIndexed { i, (l, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l, Modifier.weight(1f), color = p.dim, fontSize = 13.sp)
                Text(v, color = p.txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            }
            if (i < rows.lastIndex) HorizontalDivider(color = p.dim.copy(alpha = .2f))
        }
    }
}

@Composable
fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    val p = LocalPal.current
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { Text(if (reveal) "hide" else "show", Modifier.tap { reveal = !reveal }.padding(8.dp), color = p.dim, fontSize = 12.sp) },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.txt, focusedLabelColor = p.txt, cursorColor = p.txt))
}

// ---------------------------------------------------------------- guest access (admin)

@Composable
fun GuestDialog(d: Dev, vm: Vm, close: () -> Unit) {
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var pw by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<String?>(null) }
    var confirmOff by remember { mutableStateOf(false) }

    suspend fun load() {
        try { enabled = vm.withDevice(d.base) { Api.guestEnabled(d.base) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { msg = e.message }
    }
    fun run(f: suspend () -> Unit, ok: () -> Unit) {
        scope.launch {
            busy = true
            try { vm.withDevice(d.base) { f() }; ok(); msg = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { msg = e.message }
            busy = false; load()
        }
    }
    LaunchedEffect(Unit) { load() }

    val valid = pw.toByteArray().size in 8..64 && pw == pw.trim() && pw.none { it.isISOControl() }
    DialogShell("Guest access", d.base.removePrefix("http://"), close) {
        Text(when (enabled) { null -> "Checking…"; true -> "Guest access is on"; false -> "Guest access is off" },
            color = p.txt, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Guests can see the switches and turn them on and off. They can't rename, schedule or change any settings.",
            color = p.dim, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(18.dp))
        SecretField(pw, { pw = it; msg = null }, "Guest password")
        Spacer(Modifier.height(6.dp))
        Text("8 to 64 characters, no space at the start or end, different from the admin token.", color = p.dim, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        NeuButton(if (enabled == true) "Change guest password" else "Turn on with this password", Modifier.fillMaxWidth(), true) {
            if (busy) return@NeuButton
            if (!valid) msg = "Use 8 to 64 characters, with no space at the start or end."
            else { val uri = pairingUri(d.base, pw); val v = pw; run({ Api.guestSet(d.base, v) }) { qr = uri; pw = "" } }
        }
        if (enabled == true) {
            Spacer(Modifier.height(14.dp))
            NeuButton("Turn off guest access", Modifier.fillMaxWidth()) { confirmOff = true }
        }
        qr?.let { uri ->
            Spacer(Modifier.height(20.dp))
            Text("Guest QR code", color = p.txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Let the guest scan this in Relay Control (Scan QR). Anyone who scans it can switch your relays. It disappears when you close this window.",
                color = p.dim, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), Alignment.Center) { QrCode(uri) }
        }
        msg?.let { Spacer(Modifier.height(14.dp)); Text(it, color = Deep, fontSize = 13.sp, lineHeight = 18.sp) }
    }
    if (confirmOff) AlertDialog(onDismissRequest = { confirmOff = false }, containerColor = p.bg,
        title = { Text("Turn off guest access?", color = p.txt) },
        text = { Text("The guest password stops working immediately.", color = p.dim) },
        confirmButton = { TextButton({ confirmOff = false; run({ Api.guestDisable(d.base) }) { qr = null } }) { Text("Turn off", color = p.txt, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ confirmOff = false }) { Text("Cancel", color = p.dim) } })
}

// ---------------------------------------------------------------- Wi-Fi (admin)

@Composable
fun WifiDialog(d: Dev, vm: Vm, close: () -> Unit) {
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<WifiStatus?>(null) }
    var nets by remember { mutableStateOf<List<WifiNet>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var scanNote by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<WifiNet?>(null) }
    var custom by remember { mutableStateOf(false) }
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf<Long?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        try { st = vm.withDevice(d.base) { Api.wifiStatus(d.base) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (joining == null) msg = e.message }   // while switching, silence is expected
    }
    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            joining?.let { t0 ->
                val s = st
                when {
                    s != null && s.changeState == "succeeded" -> {
                        msg = "Connected. The controller is now on ${s.ssid} at ${s.ip}. Connect your phone to that network, then choose Scan network from the menu."
                        joining = null
                    }
                    s != null && s.changeState == "failed" -> {
                        msg = "Could not join the network: ${s.changeMessage.ifBlank { "unknown reason" }}. The controller went back to its previous network."
                        joining = null
                    }
                    System.currentTimeMillis() - t0 > 120_000 -> {
                        msg = "No answer from the controller. If it joined a different network, connect your phone to that network and scan again."
                        joining = null
                    }
                }
            }
            delay(if (joining != null) 2000L else 5000L)
        }
    }

    fun startScan() {
        scope.launch {
            scanning = true; scanNote = null; nets = emptyList()
            try {
                vm.withDevice(d.base) { Api.wifiScanStart(d.base) }
                val t0 = System.currentTimeMillis(); var done = false
                while (System.currentTimeMillis() - t0 < 45_000) {      // a scan can take a while on a busy device
                    delay(1500)
                    val r = vm.withDevice(d.base) { Api.wifiScanResult(d.base) }
                    if (r.state == "done") { nets = r.nets; done = true; break }
                }
                scanNote = if (!done) "The scan is taking longer than expected. Try again in a moment."
                           else if (nets.isEmpty()) "No networks found." else null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { scanNote = e.message }
            scanning = false
        }
    }

    val target = if (custom) ssid.trim() else pick?.ssid.orEmpty()
    val openOk = custom || pick?.secure == false
    val pb = pass.toByteArray().size
    val passOk = (pass.isEmpty() && openOk) || pb in 8..63
    val ssidOk = target.toByteArray().size in 1..32

    DialogShell("Wi-Fi", d.base.removePrefix("http://"), close) {
        val s = st
        if (s == null) { if (msg == null) Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) { CircularProgressIndicator(color = p.dim) } }
        else InfoCard(buildList {
            add("Network" to s.ssid.ifBlank { "–" })
            add("Connection" to if (s.connected) "Connected" else "Not connected")
            add("IP address" to s.ip.ifBlank { "–" })
            add("Signal" to if (s.rssi != 0) "${s.rssi} dBm" else "–")
            if (s.apActive) add("Own access point" to "Active")
        })

        if (joining != null) {
            Spacer(Modifier.height(18.dp))
            Text("Switching network… this can take a minute or two. The controller may disappear from this network while it does.",
                color = p.dim, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp), color = p.txt, trackColor = p.lo)
        } else {
            Spacer(Modifier.height(20.dp))
            NeuButton(if (scanning) "Scanning… up to 45 s" else "Scan for networks", Modifier.fillMaxWidth()) { if (!scanning) startScan() }
            if (scanning) { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp), color = p.dim, trackColor = p.lo) }
            scanNote?.let { Spacer(Modifier.height(10.dp)); Text(it, color = p.dim, fontSize = 13.sp) }

            nets.forEach { n ->
                val sel = !custom && pick?.ssid == n.ssid
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().neu(14.dp, 3.dp, sel).tap { custom = false; pick = n; pass = ""; ssid = n.ssid }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(n.ssid, Modifier.weight(1f), color = p.txt, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    Text((if (n.secure) "secured · " else "open · ") + "${n.rssi} dBm", color = p.dim, fontSize = 12.sp)
                }
            }
            if (nets.isNotEmpty() || scanNote != null) {
                Spacer(Modifier.height(12.dp))
                Text("Other or hidden network…", Modifier.tap { custom = true; pick = null; ssid = ""; pass = "" }.padding(vertical = 6.dp),
                    color = p.dim, fontSize = 13.sp)
            }

            if (pick != null || custom) {
                Spacer(Modifier.height(18.dp))
                if (custom) {
                    OutlinedTextField(ssid, { ssid = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Network name (SSID)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.txt, focusedLabelColor = p.txt, cursorColor = p.txt))
                    Spacer(Modifier.height(12.dp))
                } else Text("Join “${pick?.ssid}”", color = p.txt, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (custom || pick?.secure == true) {
                    Spacer(Modifier.height(10.dp))
                    SecretField(pass, { pass = it }, if (custom) "Password (empty for an open network)" else "Password")
                }
                Spacer(Modifier.height(14.dp))
                NeuButton("Connect…", Modifier.fillMaxWidth(), true) {
                    if (!ssidOk) msg = "Enter the network name (up to 32 characters)."
                    else if (!passOk) msg = "The password needs 8 to 63 characters."
                    else { msg = null; confirm = true }
                }
            }
        }
        msg?.let { Spacer(Modifier.height(14.dp)); Text(it, color = if (it.startsWith("Connected")) p.dim else Deep, fontSize = 13.sp, lineHeight = 18.sp) }
    }

    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = p.bg,
        title = { Text("Switch to “$target”?", color = p.txt) },
        text = { Text("The controller leaves its current network and tries the new one. If that works, its address changes to the new network's .250 and you must connect this phone to that network to reach it. If it fails, the controller goes back to the old network and nothing is saved.", color = p.dim) },
        confirmButton = { TextButton({
            confirm = false
            val t = target; val pw = pass
            scope.launch {
                try { vm.withDevice(d.base) { Api.wifiConnect(d.base, t, pw) }; joining = System.currentTimeMillis(); msg = null; pass = ""; pick = null; custom = false }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { msg = e.message }
            }
        }) { Text("Switch", color = p.txt, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", color = p.dim) } })
}
