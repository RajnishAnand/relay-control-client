package com.relay.ctrl
import android.content.Context
import android.content.SharedPreferences

/** API tokens: one optional default plus optional per-device overrides. Stored in app-private prefs; never logged or put in URLs. */
object Tokens {
    private var p: SharedPreferences? = null
    fun init(ctx: Context) { if (p == null) p = ctx.applicationContext.getSharedPreferences("tok", Context.MODE_PRIVATE) }
    fun own(b: String): String? = p?.getString("d:$b", null)?.takeIf { it.isNotBlank() }
    fun default(): String? = p?.getString("default", null)?.takeIf { it.isNotBlank() }
    fun forBase(b: String): String? = own(b) ?: default()
    fun set(b: String?, token: String) {
        val k = if (b == null) "default" else "d:$b"
        p?.edit()?.apply { if (token.isBlank()) remove(k) else putString(k, token.trim()) }?.apply()
    }
}
