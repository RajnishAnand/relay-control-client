package com.relay.ctrl
import android.app.TimePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import java.util.Collections
import kotlin.math.abs
import kotlinx.coroutines.launch

// ---------- small building blocks (orange is reserved for the ON state) ----------

@Composable
fun Modifier.tap(onClick: () -> Unit) = this.clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

@Composable
fun NeuButton(label: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }; val down by src.collectIsPressedAsState()
    Box(modifier.neu(14.dp, 4.dp, down).clickable(src, null, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), Alignment.Center) {
        Text(label, color = p.txt, fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
fun DotsIcon(color: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.width * 0.09f
        for (i in -1..1) drawCircle(color, r, Offset(center.x, center.y + this.size.width * 0.28f * i))
    }
}

@Composable
fun ClockIcon(color: Color, size: Dp = 12.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val sw = w * 0.12f
        drawCircle(color, w / 2 - sw / 2, center, style = Stroke(sw))
        drawLine(color, center, Offset(center.x, w * 0.25f), sw, StrokeCap.Round)
        drawLine(color, center, Offset(w * 0.7f, center.y), sw, StrokeCap.Round)
    }
}

/** The round power button (glows softly when on). */
@Composable
fun PowerGlyph(on: Boolean, sz: Dp = 32.dp) {
    val p = LocalPal.current; val col = if (on) p.accent else p.dim
    Canvas(Modifier.size(sz)) {
        val w = this.size.width * 0.1f
        drawArc(col, -55f, 290f, false, Offset(w, w), androidx.compose.ui.geometry.Size(this.size.width - 2 * w, this.size.height - 2 * w),
            style = Stroke(w, cap = StrokeCap.Round))
        drawLine(col, Offset(this.size.width / 2, w), Offset(this.size.width / 2, this.size.height * 0.45f), w, StrokeCap.Round)
    }
}

@Composable
fun GripIcon(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val sw = this.size.width * 0.1f
        for (i in -1..1) {
            val y = center.y + this.size.height * 0.26f * i
            drawLine(color, Offset(this.size.width * 0.2f, y), Offset(this.size.width * 0.8f, y), sw, StrokeCap.Round)
        }
    }
}

// ---------- switch tile ----------

@Composable
fun RelayTile(base: String, i: Int, r: Relay, vm: Vm, live: Boolean, reorder: Boolean,
              onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit, modifier: Modifier) {
    val p = LocalPal.current
    val haptic = LocalHapticFeedback.current
    var cfg by remember { mutableStateOf(false) }
    val startS by rememberUpdatedState(onDragStart)
    val dragS by rememberUpdatedState(onDrag)
    val endS by rememberUpdatedState(onDragEnd)
    val toggle = { if (live && !reorder) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.set(base, i, !r.state) } }
    Row(modifier.neu(22.dp, 6.dp).tap { toggle() }.heightIn(min = 92.dp).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(r.name, Modifier.weight(1f, fill = false), color = p.txt, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 21.sp, maxLines = 3)
                if (!reorder) Box(Modifier.tap { cfg = true }.padding(horizontal = 8.dp, vertical = 2.dp)) { DotsIcon(p.dim) }
            }
            Text(if (!live) "Unavailable" else if (r.state) "On" else "Off",
                color = if (live && r.state) p.accent else p.dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (r.sched) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ClockIcon(p.dim)
                Text("${r.on} – ${r.off}", color = p.dim, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        if (reorder) Box(Modifier.size(54.dp).neuCircle(depth = 4.dp).pointerInput(Unit) {
            detectDragGestures(onDragStart = { startS() }, onDragEnd = { endS() }, onDragCancel = { endS() }) { change, amt ->
                change.consume(); dragS(amt.y)
            }
        }, Alignment.Center) { GripIcon(p.txt) }
        else Box(Modifier.size(64.dp)
            .then(if (live && r.state) Modifier.drawBehind {
                drawCircle(Brush.radialGradient(listOf(p.accent.copy(.35f), Color.Transparent)), size.minDimension * .8f)
            } else Modifier)
            .neuCircle(live && r.state, 6.dp), Alignment.Center) { PowerGlyph(live && r.state, 32.dp) }
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
        title = { Text("Switch settings", color = p.txt) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(name, { v -> name = Api.clampName(v.filter { !it.isISOControl() }) },
                label = { Text("Name") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.txt, focusedLabelColor = p.txt, cursorColor = p.txt))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily schedule", color = p.txt, modifier = Modifier.weight(1f))
                Switch(en, { en = it }, colors = SwitchDefaults.colors(checkedTrackColor = p.txt.copy(alpha = .8f), checkedThumbColor = p.bg))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton("Turn on  $on", Modifier.weight(1f)) { pick(on) { on = it } }
                NeuButton("Turn off  $off", Modifier.weight(1f)) { pick(off) { off = it } }
            }
            Text("Runs every day in the controller's local time. Needs the controller to have the correct time (internet).", color = p.dim, fontSize = 11.sp)
        } },
        confirmButton = { TextButton({
            if (name.isNotBlank() && name != r.name) vm.rename(base, i, name)
            if (en != r.sched || on != r.on || off != r.off) vm.schedule(base, i, en, on, off)
            close() }) { Text("Save", color = p.txt, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(close) { Text("Cancel", color = p.dim) } })
}

// ---------- one controller ----------

@Composable
fun DevicePage(d: Dev, order: List<Int>, vm: Vm, reorder: Boolean, onDone: () -> Unit,
               askAllOn: (String) -> Unit, askToken: (String) -> Unit) {
    val p = LocalPal.current; val ctx = LocalContext.current
    val w = Modifier.widthIn(max = 600.dp).fillMaxWidth()
    // drag-to-reorder state: `work` is the order shown while dragging, saved when the finger lifts
    var work by remember(order) { mutableStateOf(order) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableStateOf(0f) }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    val gap = with(LocalDensity.current) { 16.dp.toPx() }

    fun onDrag(idx: Int, dy: Float) {
        dragY += dy
        var pos = work.indexOf(idx)
        while (pos >= 0) {
            val down = dragY > 0
            val next = work.getOrNull(if (down) pos + 1 else pos - 1) ?: break
            val h = (heights[next] ?: 0) + gap
            if (h <= gap) break
            if (abs(dragY) <= 0.6f * h) break
            val l = work.toMutableList(); Collections.swap(l, pos, if (down) pos + 1 else pos - 1); work = l
            dragY += if (down) -h else h
            pos += if (down) 1 else -1
        }
    }
    fun endDrag() { if (work != order) vm.setOrder(d.base, work); dragging = null; dragY = 0f }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {

        Column(w.neu(22.dp, 6.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                d.locked != null -> {
                    Text("This controller needs its API token", color = p.txt, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(d.locked, color = p.dim, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeuButton("Scan QR", Modifier.weight(1f), true) {
                            scanQr(ctx, { vm.notify(it) }) { raw ->
                                val pr = parsePairing(raw)
                                if (pr == null) vm.notify("That QR code isn't an API token.") else vm.applyPairing(d.base, pr.first, pr.second)
                            }
                        }
                        NeuButton("Enter token", Modifier.weight(1f)) { askToken(d.base) }
                    }
                }
                !d.online -> {
                    Text("Controller offline", color = p.txt, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${d.base.removePrefix("http://")} · trying again automatically", color = p.dim, fontSize = 12.sp)
                    NeuButton("Remove this device") { vm.forget(d.base) }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${d.relays.count { it.state }} of ${d.relays.size} on", color = p.txt, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(d.base.removePrefix("http://"), color = p.dim, fontSize = 12.sp)
                    }
                    NeuButton("All off") { vm.allOff(d.base) }
                    Spacer(Modifier.width(10.dp))
                    NeuButton("All on") { askAllOn(d.base) }
                }
            }
        }

        if (reorder) Row(w.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Hold the ≡ handle and drag a switch up or down", Modifier.weight(1f), color = p.dim, fontSize = 13.sp)
            NeuButton("Done", accent = true) { onDone() }
        }
        if (d.online) d.info?.let { i ->
            if (!i.synced && d.relays.any { it.sched })
                Text("Clock not synced: schedules stay inactive until the controller gets the time.", w.padding(horizontal = 6.dp),
                    color = Deep, fontSize = 12.sp)
        }

        if (d.locked == null) work.forEach { idx ->
            val r = d.relays.getOrNull(idx) ?: return@forEach
            key(idx) {
                val isDrag = dragging == idx
                RelayTile(d.base, idx, r, vm, d.online, reorder,
                    onDragStart = { dragging = idx; dragY = 0f },
                    onDrag = { dy -> onDrag(idx, dy) },
                    onDragEnd = { endDrag() },
                    modifier = w.onSizeChanged { heights[idx] = it.height }
                        .zIndex(if (isDrag) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDrag) dragY else 0f
                            scaleX = if (isDrag) 1.03f else 1f; scaleY = if (isDrag) 1.03f else 1f
                        })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- home ----------

@Composable
fun MenuItem(text: String, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text, color = LocalPal.current.txt) }, onClick = onClick)

@Composable
fun Home(s: Ui, vm: Vm) {
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    var allOn by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(false) }
    var defTok by remember { mutableStateOf(false) }
    var appDlg by remember { mutableStateOf(false) }
    var tokenFor by remember { mutableStateOf<String?>(null) }
    var updateFor by remember { mutableStateOf<String?>(null) }
    var reorderFor by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pager = rememberPagerState { s.devices.size.coerceAtLeast(1) }
    val cur = s.devices.getOrNull(pager.currentPage)
    val totalOn = s.devices.sumOf { d -> d.relays.count { it.state } }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Relay Control", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.txt)
                Text(if (s.scanning) "Looking for controllers…"
                     else "${s.devices.size} controller${if (s.devices.size == 1) "" else "s"} · $totalOn on", color = p.dim, fontSize = 13.sp)
            }
            Box {
                Box(Modifier.size(44.dp).neuCircle(depth = 4.dp).tap { menu = true }, Alignment.Center) { DotsIcon(p.txt) }
                DropdownMenu(menu, { menu = false }, Modifier.background(p.bg)) {
                    MenuItem("Scan network") { menu = false; vm.scan() }
                    MenuItem("Add device by IP…") { menu = false; manual = true }
                    MenuItem("API token (all devices)…") { menu = false; defTok = true }
                    MenuItem(if (s.appUpdate != null) "App updates… (new version)" else "App updates…") { menu = false; appDlg = true }
                    cur?.let { d ->
                        val ip = d.base.removePrefix("http://")
                        HorizontalDivider(color = p.dim.copy(alpha = .3f))
                        if (d.online && d.locked == null) {
                            MenuItem("Reorder switches") { menu = false; reorderFor = d.base }
                            MenuItem("Firmware & updates…") { menu = false; updateFor = d.base }
                        }
                        MenuItem("Token for $ip…") { menu = false; tokenFor = d.base }
                        if (!d.online) MenuItem("Remove $ip") { menu = false; vm.forget(d.base) }
                    }
                }
            }
        }
        if (s.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = p.dim, trackColor = Color.Transparent)
        s.msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Deep, fontSize = 13.sp) }
        s.appUpdate?.let { up ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).neu(16.dp, 4.dp).tap { appDlg = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("App update ${up.version} available", Modifier.weight(1f), color = p.txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("View ›", color = p.dim, fontSize = 13.sp)
            }
        }

        if (s.devices.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(24.dp)) {
                Text(if (s.scanning) "Looking for controllers on your Wi-Fi…" else "No controllers found",
                    color = p.txt, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (!s.scanning) {
                    Text("Make sure your phone is on the same Wi-Fi as the relay board.", color = p.dim, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeuButton("Scan again", accent = true) { vm.scan() }
                        NeuButton("Add by IP") { manual = true }
                    }
                }
            }
        } else {
            if (s.devices.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 14.dp),
                Arrangement.spacedBy(14.dp)) {
                s.devices.forEachIndexed { i, d ->
                    val sel = i == pager.currentPage
                    Box(Modifier.neu(14.dp, 3.dp, sel).tap { scope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Text("Controller ${d.base.substringAfterLast('.')}", color = if (sel) p.txt else p.dim,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { s.devices.getOrNull(it)?.base ?: it }) { page ->
                s.devices.getOrNull(page)?.let { d ->
                    DevicePage(d, s.orders[d.base] ?: listOf(0, 1, 2, 3), vm, reorderFor == d.base, { reorderFor = null },
                        { allOn = it }, { tokenFor = it })
                }
            }
            Spacer(Modifier.height(navBottom + 8.dp))
        }
    }

    if (defTok) TokenDialog(null, vm) { defTok = false }
    if (appDlg) AppUpdateDialog(s, vm) { appDlg = false }
    tokenFor?.let { b -> TokenDialog(b, vm) { tokenFor = null } }
    updateFor?.let { b -> s.devices.firstOrNull { it.base == b }?.let { d -> UpdateDialog(d, vm) { updateFor = null } } }
    allOn?.let { b ->
        AlertDialog(onDismissRequest = { allOn = null }, containerColor = p.bg,
            title = { Text("Turn everything on?", color = p.txt) },
            text = { Text("The controller switches relays on one at a time (about 0.4 s apart) to limit inrush. Check the connected loads are safe to power.", color = p.dim) },
            confirmButton = { TextButton({ allOn = null; vm.allOn(b) }) { Text("Turn on", color = p.txt, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ allOn = null }) { Text("Cancel", color = p.dim) } })
    }
    if (manual) {
        var ip by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { manual = false }, containerColor = p.bg,
            title = { Text("Add device by IP", color = p.txt) },
            text = { OutlinedTextField(ip, { ip = it.trim() }, singleLine = true, label = { Text("e.g. 192.168.1.250") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.txt, focusedLabelColor = p.txt, cursorColor = p.txt)) },
            confirmButton = { TextButton({ if (ip.isNotBlank()) vm.add(ip); manual = false }) { Text("Add", color = p.txt, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ manual = false }) { Text("Cancel", color = p.dim) } })
    }
}
