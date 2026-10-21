package com.relay.ctrl
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Publishes launcher shortcuts AND the App Actions inventory (needs androidx.core:core-google-shortcuts).
 * Each shortcut is bound to the OPEN_APP_FEATURE capability declared in res/xml/shortcuts.xml, so
 * "open <name> in Relay Control" resolves to that shortcut's intent.
 */
object Shortcuts {
    private const val BII = "actions.intent.OPEN_APP_FEATURE"
    private var last = ""

    fun publish(ctx: Context, s: Ui) {
        val relays = s.devices.filter { it.online }.flatMap { d ->
            val ip = d.base.removePrefix("http://")
            (s.orders[d.base] ?: listOf(0, 1, 2, 3)).mapNotNull { i -> d.relays.getOrNull(i)?.let { Triple(ip, i, it.name) } }
        }
        if (relays.isEmpty()) return
        val sig = relays.joinToString("|") { it.first + it.third }
        if (sig == last) return
        last = sig

        val icon = IconCompat.createWithResource(ctx, R.drawable.ic_launcher_foreground)
        fun sc(id: String, label: String, features: List<String>, state: String, name: String, device: String?): ShortcutInfoCompat {
            val uri = "relay://cmd?name=${Uri.encode(name)}&state=$state" + (device?.let { "&device=${Uri.encode(it)}" } ?: "")
            val intent = Intent(ctx, CommandActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(uri))
            return ShortcutInfoCompat.Builder(ctx, id).setShortLabel(label).setLongLabel(label).setIcon(icon).setIntent(intent)
                .addCapabilityBinding(BII, "feature", features).build()
        }
        // priority order: All off, then a toggle per switch, then explicit on / off per switch
        val all = mutableListOf(sc("all_off", "All OFF", listOf("all off", "everything off", "all switches off"), "off", "all", null))
        relays.forEach { (ip, i, n) -> all += sc("t_${ip}_$i", "Toggle $n", listOf(n, "toggle $n"), "toggle", n, ip) }
        relays.forEach { (ip, i, n) -> all += sc("on_${ip}_$i", "$n ON", listOf("$n on", "turn on $n"), "on", n, ip) }
        relays.forEach { (ip, i, n) -> all += sc("off_${ip}_$i", "$n OFF", listOf("$n off", "turn off $n"), "off", n, ip) }
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx).coerceAtLeast(4)
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(ctx, all.take(max)) }
    }
}
