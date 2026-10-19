package com.relay.ctrl
import android.app.TimePickerDialog
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

@Composable
fun Modifier.tap(onClick: () -> Unit) = this.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

@Composable
fun NeuButton(label: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }; val down by src.collectIsPressedAsState()
    Box(modifier.neu(14.dp, 4.dp, down).clickable(src, null, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), Alignment.Center) {
        Text(label, color = if (accent) p.accent else p.txt, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
fun PowerGlyph(on: Boolean, sz: Dp = 22.dp) {
    val p = LocalPal.current; val col = if (on) p.accent else p.dim
    Canvas(Modifier.size(sz)) {
        val w = size.width * 0.1f
        drawArc(col, -55f, 290f, false, Offset(w, w), Size(size.width - 2 * w, size.height - 2 * w), style = Stroke(w, cap = StrokeCap.Round))
        drawLine(col, Offset(size.width / 2, w), Offset(size.width / 2, size.height * 0.45f), w, StrokeCap.Round)
    }
}

/** Radar icon for network scanning; sweeps while scanning. */
@Composable
fun ScanIcon(spinning: Boolean, color: Color) {
    val rot = if (spinning) rememberInfiniteTransition(label = "scan")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "a").value else 0f
    Canvas(Modifier.size(22.dp).rotate(rot)) {
        val c = center; val r = size.minDimension / 2; val w = r * 0.15f
        drawCircle(color, r - w / 2, c, style = Stroke(w))
        drawCircle(color, r * 0.5f, c, style = Stroke(w * 0.8f))
        drawCircle(color, w, c)
        drawLine(color, c, Offset(c.x + r * 0.66f, c.y - r * 0.66f), w * 0.8f, StrokeCap.Round)
    }
}

@Composable
fun TopBtn(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) =
    Box(Modifier.size(42.dp).neuCircle(depth = 4.dp).tap(onClick), Alignment.Center, content = content)

@Composable
fun ReorderIcon(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height; val sw = w * 0.11f; val hd = w * 0.16f
        val x1 = w * 0.3f; val x2 = w * 0.7f
        drawLine(color, Offset(x1, h * 0.9f), Offset(x1, h * 0.1f), sw, StrokeCap.Round)
        drawLine(color, Offset(x1, h * 0.1f), Offset(x1 - hd, h * 0.1f + hd), sw, StrokeCap.Round)
        drawLine(color, Offset(x1, h * 0.1f), Offset(x1 + hd, h * 0.1f + hd), sw, StrokeCap.Round)
        drawLine(color, Offset(x2, h * 0.1f), Offset(x2, h * 0.9f), sw, StrokeCap.Round)
        drawLine(color, Offset(x2, h * 0.9f), Offset(x2 - hd, h * 0.9f - hd), sw, StrokeCap.Round)
        drawLine(color, Offset(x2, h * 0.9f), Offset(x2 + hd, h * 0.9f - hd), sw, StrokeCap.Round)
    }
}

@Composable
fun MoveBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(Modifier.size(38.dp).neuCircle(depth = 4.dp).tap { if (enabled) onClick() }, Alignment.Center) {
        Text(label, color = if (enabled) p.accent else p.dim.copy(alpha = .4f), fontSize = 14.sp)
    }
}

@Composable
fun RelayTile(base: String, i: Int, r: Relay, vm: Vm, live: Boolean, reorder: Boolean,
              canUp: Boolean, canDown: Boolean, onMove: (Int) -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    val haptic = LocalHapticFeedback.current
    var cfg by remember { mutableStateOf(false) }
    val toggle = { if (live && !reorder) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.set(base, i, !r.state) } }
    Row(modifier.neu(20.dp, 5.dp).tap { toggle() }.heightIn(min = 84.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(r.name, Modifier.weight(1f), color = p.txt, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp, maxLines = 3)
                if (!reorder) Text("⚙", Modifier.tap { cfg = true }.padding(start = 8.dp, end = 4.dp), color = p.dim, fontSize = 18.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("IN${i + 1}", color = p.dim, fontSize = 10.sp)
                if (r.sched) Text("▲ ${r.on}   ▼ ${r.off}", Modifier.neu(8.dp, 2.dp, true).padding(horizontal = 8.dp, vertical = 2.dp),
                    color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(14.dp))
        if (reorder) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MoveBtn("▲", canUp) { onMove(-1) }
            MoveBtn("▼", canDown) { onMove(1) }
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(60.dp)
                .then(if (r.state) Modifier.drawBehind {
                    drawCircle(Brush.radialGradient(listOf(p.accent.copy(.4f), Color.Transparent)), size.minDimension * .75f)
                } else Modifier)
                .neuCircle(r.state, 6.dp), Alignment.Center) { PowerGlyph(r.state && live, 30.dp) }
            Text(if (r.state) "ON" else "OFF", color = if (r.state) p.accent else p.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (cfg) ConfigDialog(base, i, r, vm) { cfg = false }
}

@Composable
fun ConfigDialog(base: String, i: Int, r: Relay, vm: Vm, close: () -> Unit) {
    val p = LocalPal.current
    var name by remember { mutableStateOf(r.name) }; var en by remember { mutableStateOf(r.sched) }
    var on by remember { mutableStateOf(r.on) }; var off by remember { mutableStateOf(r.off) }
    val ctx = LocalContext.current
    fun pick(cur: String, set: (String) -> Unit) = TimePickerDialog(ctx, { _, h, m -> set("%02d:%02d".format(h, m)) },
        cur.substringBefore(':').toInt(), cur.substringAfter(':').toInt(), true).show()
    AlertDialog(onDismissRequest = close, containerColor = p.bg,
        title = { Text("Relay ${i + 1} settings", color = p.txt) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(name, { v -> name = Api.clampName(v.filter { !it.isISOControl() }) },
                label = { Text("Name") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, focusedLabelColor = p.accent, cursorColor = p.accent))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily schedule", color = p.txt, modifier = Modifier.weight(1f))
                Switch(en, { en = it }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.bg))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton("ON  $on", Modifier.weight(1f), true) { pick(on) { on = it } }
                NeuButton("OFF  $off", Modifier.weight(1f)) { pick(off) { off = it } }
            }
            Text("Needs the device to have NTP time (internet). Runs daily, device local time.", color = p.dim, fontSize = 11.sp)
        } },
        confirmButton = { TextButton({
            if (name.isNotBlank() && name != r.name) vm.rename(base, i, name)
            if (en != r.sched || on != r.on || off != r.off) vm.schedule(base, i, en, on, off)
            close() }) { Text("Save", color = p.accent) } },
        dismissButton = { TextButton(close) { Text("Cancel", color = p.dim) } })
}

@Composable
fun DevicePage(d: Dev, order: List<Int>, vm: Vm, askAllOn: (String) -> Unit) {
    val p = LocalPal.current
    var reorder by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(false) }
    val w = Modifier.widthIn(max = 600.dp).fillMaxWidth()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
        Arrangement.spacedBy(14.dp), Alignment.CenterHorizontally) {
        Row(w, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (d.online) p.accent else p.dim, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(d.base.removePrefix("http://"), Modifier.weight(1f), color = p.dim, fontSize = 13.sp)
            Box(Modifier.size(36.dp).neuCircle(depth = 4.dp).tap { token = true }, Alignment.Center) { Text("🔑", fontSize = 15.sp) }
            if (d.relays.isNotEmpty() && d.locked == null) {
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(36.dp).neuCircle(reorder, 4.dp).tap { reorder = !reorder }, Alignment.Center) {
                    ReorderIcon(if (reorder) p.accent else p.dim)
                }
            }
        }
        if (d.online) Row(w, Arrangement.spacedBy(14.dp)) {
            NeuButton("All ON", Modifier.weight(1f), true) { askAllOn(d.base) }
            NeuButton("All OFF", Modifier.weight(1f)) { vm.allOff(d.base) }
        } else if (d.locked == null) Text("offline  ✕", Modifier.tap { vm.forget(d.base) }.padding(8.dp), color = p.dim, fontSize = 12.sp)
        d.locked?.let {
            Text("$it. Tap 🔑 to scan or enter the token.", w.padding(horizontal = 8.dp), color = Deep, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        d.info?.let { i ->
            val line = "fw ${i.firmware}" + (if (i.rssi != 0) " · ${i.rssi} dBm" else "") + (if (!i.synced) "  ⚠ clock not synced, schedules inactive" else "")
            Text(line, color = if (i.synced) p.dim else Deep, fontSize = 10.sp)
        }
        if (d.relays.isEmpty() && d.locked == null) Text("Connecting…", Modifier.padding(32.dp), color = p.dim)
        if (d.locked == null) order.forEachIndexed { pos, idx ->
            val r = d.relays.getOrNull(idx) ?: return@forEachIndexed
            RelayTile(d.base, idx, r, vm, d.online, reorder, pos > 0, pos < order.lastIndex, { vm.move(d.base, pos, it) }, w)
        }
        Spacer(Modifier.height(8.dp))
    }
    if (token) TokenDialog(d.base, vm) { token = false }
}

@Composable
fun Home(s: Ui, vm: Vm) {
    val p = LocalPal.current
    var allOn by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(false) }
    var defTok by remember { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val totalOn = s.devices.sumOf { d -> d.relays.count { it.state } }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Relay Control", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.txt)
                Text(if (s.scanning) "Scanning network…" else "${s.devices.size} device${if (s.devices.size == 1) "" else "s"} · $totalOn on",
                    color = p.dim, fontSize = 12.sp)
            }
            TopBtn({ defTok = true }) { Text("🔑", fontSize = 18.sp) }
            Spacer(Modifier.width(14.dp))
            TopBtn({ manual = true }) { Text("+", color = p.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(14.dp))
            TopBtn({ vm.scan() }) { ScanIcon(s.scanning, p.accent) }
        }
        s.msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Deep, fontSize = 13.sp) }
        Spacer(Modifier.height(10.dp))
        if (s.devices.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(if (s.scanning) "Searching your network…" else "No devices found.\nTap the radar to scan again, or add an IP with +",
                color = p.dim, textAlign = TextAlign.Center)
        } else {
            val pager = rememberPagerState { s.devices.size }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { s.devices.getOrNull(it)?.base ?: it }) { page ->
                s.devices.getOrNull(page)?.let { d -> DevicePage(d, s.orders[d.base] ?: listOf(0, 1, 2, 3), vm) { allOn = it } }
            }
            if (s.devices.size > 1) Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = navBottom + 8.dp), Arrangement.Center) {
                repeat(s.devices.size) { n ->
                    Box(Modifier.padding(horizontal = 4.dp).size(if (n == pager.currentPage) 10.dp else 7.dp)
                        .background(if (n == pager.currentPage) p.accent else p.dim, CircleShape))
                }
            } else Spacer(Modifier.height(navBottom + 8.dp))
        }
    }
    if (defTok) TokenDialog(null, vm) { defTok = false }
    allOn?.let { b ->
        AlertDialog(onDismissRequest = { allOn = null }, containerColor = p.bg,
            title = { Text("Turn everything on?", color = p.txt) },
            text = { Text("The device switches relays on one at a time (about 0.4 s apart) to limit inrush. Check the connected loads are safe to power.", color = p.dim) },
            confirmButton = { TextButton({ allOn = null; vm.allOn(b) }) { Text("Turn on", color = p.accent) } },
            dismissButton = { TextButton({ allOn = null }) { Text("Cancel", color = p.dim) } })
    }
    if (manual) {
        var ip by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { manual = false }, containerColor = p.bg,
            title = { Text("Add device by IP", color = p.txt) },
            text = { OutlinedTextField(ip, { ip = it.trim() }, singleLine = true, label = { Text("e.g. 192.168.1.250") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, focusedLabelColor = p.accent, cursorColor = p.accent)) },
            confirmButton = { TextButton({ if (ip.isNotBlank()) vm.add(ip); manual = false }) { Text("Add", color = p.accent) } },
            dismissButton = { TextButton({ manual = false }) { Text("Cancel", color = p.dim) } })
    }
}
