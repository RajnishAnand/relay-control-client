package com.relay.ctrl
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri

/** Long-press / pinnable launcher shortcuts: "All OFF" plus one toggle per switch (max 8). */
object Shortcuts {
    private var last = ""
    fun publish(ctx: Context, s: Ui) {
        val items = s.devices.filter { it.online }.flatMap { d ->
            (s.orders[d.base] ?: listOf(0, 1, 2, 3)).mapNotNull { i -> d.relays.getOrNull(i)?.let { Triple(d.base, i, it.name) } }
        }.take(8)
        val sig = items.joinToString("|") { it.first + it.third }
        if (items.isEmpty() || sig == last) return
        last = sig
        val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return
        val icon = Icon.createWithResource(ctx, R.drawable.ic_launcher_foreground)
        fun sc(id: String, label: String, uri: String) = ShortcutInfo.Builder(ctx, id)
            .setShortLabel(label).setLongLabel(label).setIcon(icon)
            .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse(uri), ctx, CommandActivity::class.java)).build()
        val list = listOf(sc("all_off", "All OFF", "relay://cmd?name=all&state=off")) + items.map { (b, i, n) ->
            sc("t_${b.removePrefix("http://")}_$i", "Toggle $n",
                "relay://cmd?name=${Uri.encode(n)}&state=toggle&device=${Uri.encode(b.removePrefix("http://"))}")
        }
        runCatching { sm.setDynamicShortcuts(list) }
    }
}
