package com.relay.ctrl
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR payload: relay://pair?key=<token>[&host=<ip>]. A bare key (8+ chars) is accepted too. Returns (host, token). */
fun parsePairing(raw: String): Pair<String?, String>? {
    val t = raw.trim()
    if (t.startsWith("relay://pair")) {
        val u = Uri.parse(t)
        val k = u.getQueryParameter("key")?.trim() ?: return null
        return if (k.length >= 8) u.getQueryParameter("host")?.takeIf { it.isNotBlank() } to k else null
    }
    return if (t.length >= 8 && t.none { it.isWhitespace() }) null to t else null
}

fun pairingUri(base: String?, token: String) =
    "relay://pair?key=${Uri.encode(token)}" + (base?.let { "&host=${Uri.encode(it.removePrefix("http://"))}" } ?: "")

/** Uses Google's code scanner (Play services): no camera permission needed. */
fun scanQr(ctx: Context, onError: (String) -> Unit, onText: (String) -> Unit) {
    val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
    GmsBarcodeScanning.getClient(ctx, opts).startScan()
        .addOnSuccessListener { b -> b.rawValue?.let(onText) ?: onError("Empty QR code") }
        .addOnFailureListener { onError(it.message ?: "Scanner unavailable (needs Google Play services)") }
}

@Composable
fun QrCode(text: String, size: Dp = 210.dp) {
    val m = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    }
    Canvas(Modifier.size(size).background(Color.White, RoundedCornerShape(10.dp)).padding(8.dp)) {
        val n = m.width; val cell = this.size.minDimension / n
        for (y in 0 until n) for (x in 0 until n) if (m.get(x, y))
            drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
    }
}

/** base == null edits the default token (used for every device without its own). */
@Composable
fun TokenDialog(base: String?, vm: Vm, close: () -> Unit) {
    val p = LocalPal.current; val ctx = LocalContext.current
    var token by remember { mutableStateOf((if (base != null) Tokens.own(base) else Tokens.default()).orEmpty()) }
    var reveal by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val valid = token.trim().length >= 8
    AlertDialog(onDismissRequest = close, containerColor = p.bg,
        title = { Text(if (base == null) "Access key (default)" else "Access key · ${base.removePrefix("http://")}", color = p.txt) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(token, { token = it.trim(); err = null }, singleLine = true, label = { Text("Access key") },
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { Text(if (reveal) "hide" else "show", Modifier.tap { reveal = !reveal }.padding(8.dp), color = p.txt, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.txt, focusedLabelColor = p.txt, cursorColor = p.txt))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuButton("Scan QR", Modifier.weight(1f), true) {
                    scanQr(ctx, { err = it }) { raw ->
                        val pr = parsePairing(raw)
                        if (pr == null) err = "That QR code isn't an access key."
                        else { vm.applyPairing(base, pr.first, pr.second); close() }
                    }
                }
                NeuButton(if (showQr) "Hide QR" else "Share QR", Modifier.weight(1f)) {
                    if (valid) showQr = !showQr else err = "Enter an access key of at least 8 characters first."
                }
            }
            if (showQr && valid) {
                Box(Modifier.fillMaxWidth(), Alignment.Center) { QrCode(pairingUri(base, token.trim())) }
                Text("Anyone who scans this can control your relays. Show it only to people you trust.", color = Deep, fontSize = 11.sp)
            }
            err?.let { Text(it, color = Deep, fontSize = 12.sp) }
            Text("Admin token or guest password. Sent as an X-API-Key header over plain HTTP. Save with an empty field to remove it.", color = p.dim, fontSize = 11.sp)
        } },
        confirmButton = { TextButton({ vm.setToken(base, token); close() }) { Text("Save", color = p.txt) } },
        dismissButton = { TextButton(close) { Text("Cancel", color = p.dim) } })
}
