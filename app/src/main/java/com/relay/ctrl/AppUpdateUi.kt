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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** App self-update from GitHub releases: check, download (checksum verified), install. */
@Composable
fun AppUpdateDialog(s: Ui, vm: Vm, close: () -> Unit) {
    val p = LocalPal.current; val ctx = LocalContext.current
    var auto by remember { mutableStateOf(vm.autoUpdateEnabled()) }
    var permNote by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(28.dp)
    val u = s.appUpdate
    val prog = s.appUpdateProgress

    Dialog(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().background(p.bg, shape).border(1.dp, p.hi.copy(alpha = .35f), shape)
            .verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("App updates", color = p.txt, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Relay Control ${AppUpdater.installedVersion(ctx)}", color = p.dim, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            Column(Modifier.fillMaxWidth().neu(18.dp, 3.dp, true).padding(horizontal = 18.dp, vertical = 8.dp)) {
                @Composable fun row(label: String, value: String, last: Boolean = false) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), color = p.dim, fontSize = 13.sp)
                        Text(value, color = p.txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    }
                    if (!last) HorizontalDivider(color = p.dim.copy(alpha = .2f))
                }
                row("Installed", AppUpdater.installedVersion(ctx), last = u == null)
                if (u != null) {
                    row("Available", u.version)
                    row("Size", if (u.apkSize > 0) "%.1f MB".format(u.apkSize / 1048576.0) else "–", last = true)
                }
            }

            if (u != null && u.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("What's new", color = p.txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(u.notes.trim().take(600), color = p.dim, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }

            if (prog != null) {
                Spacer(Modifier.height(18.dp))
                if (prog >= 0f) {
                    Text("Downloading… ${(prog * 100).toInt()}%", color = p.dim, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { prog }, Modifier.fillMaxWidth().height(6.dp), color = p.txt, trackColor = p.lo)
                } else {
                    Text("Installing… confirm if Android asks", color = p.dim, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp), color = p.txt, trackColor = p.lo)
                }
            }

            (permNote ?: s.appUpdateNote)?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = if (it.startsWith("You're up to date")) p.dim else Deep, fontSize = 13.sp, lineHeight = 18.sp)
            }

            if (prog == null) {
                Spacer(Modifier.height(22.dp))
                if (u != null) NeuButton("Download & install ${u.version}", Modifier.fillMaxWidth(), true) {
                    if (!AppUpdater.canInstall(ctx)) {
                        permNote = "Allow Relay Control to install apps in the screen that just opened, then come back and tap again."
                        ctx.startActivity(AppUpdater.permissionIntent(ctx))
                    } else { permNote = null; vm.downloadAndInstall() }
                }
                if (u != null) Spacer(Modifier.height(14.dp))
                NeuButton(if (s.appChecking) "Checking…" else "Check now", Modifier.fillMaxWidth()) { permNote = null; vm.checkAppUpdate(true) }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Check automatically once a day", Modifier.weight(1f), color = p.txt, fontSize = 14.sp)
                Switch(auto, { auto = it; vm.setAutoUpdate(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = p.txt.copy(alpha = .8f), checkedThumbColor = p.bg))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), Alignment.CenterEnd) {
                TextButton(close) { Text("Close", color = p.txt, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
