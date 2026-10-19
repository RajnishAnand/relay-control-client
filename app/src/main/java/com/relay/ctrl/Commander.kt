package com.relay.ctrl
import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/** Headless command runner used by voice/automation entry points (no UI). */
object Commander {
    data class Result(val ok: Boolean, val msg: String)
    private data class Hit(val base: String, val idx: Int, val r: Relay)
    private val ALL = setOf("all", "everything", "all relays", "all switches", "all lights")
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    private fun word(on: Boolean) = if (on) "ON" else "OFF"

    /** state: "on" | "off" | "toggle". name: relay name, or "all". device: optional IP fragment. */
    suspend fun run(ctx: Context, name: String?, state: String, device: String?): Result {
        Tokens.init(ctx)
        val prefs = ctx.getSharedPreferences("p", 0)
        var bases = prefs.getStringSet("known", emptySet())!!.toList()
        if (bases.isEmpty()) {
            val found = ConcurrentHashMap.newKeySet<String>()
            runCatching { Api.scan(ctx, emptyList(), true) { found += it.base } }
            prefs.edit().putStringSet("known", found.toSet()).apply(); bases = found.toList()
        }
        if (!device.isNullOrBlank()) bases = bases.filter { it.contains(device) }
        var authMsg: String? = null
        val live = coroutineScope {
            bases.map { b -> async {
                try { b to Api.status(b) }
                catch (e: ApiAuthException) { authMsg = e.message; null }
                catch (e: Exception) { null }
            } }.awaitAll().filterNotNull()
        }
        if (live.isEmpty()) return Result(false, authMsg ?: "No relay controller reachable")

        val q = name?.trim().orEmpty()
        if (q.isEmpty() || q.lowercase() in ALL) {
            if (state == "toggle") return Result(false, "Say on or off for all switches")
            return try {
                for ((b, _) in live) if (state == "on") Api.allOn(b) else Api.allOff(b)
                Result(true, "All switches ${word(state == "on")}")
            } catch (e: Exception) { Result(false, e.message ?: "Request failed") }
        }

        val hits = live.flatMap { (b, rs) -> rs.mapIndexed { i, r -> Hit(b, i, r) } }
        val nq = norm(q)
        val exact = hits.filter { norm(it.r.name) == nq }
        val cand = exact.ifEmpty {
            hits.filter { val n = norm(it.r.name); nq.length >= 3 && n.length >= 3 && (n.contains(nq) || nq.contains(n)) }
        }
        if (cand.isEmpty()) return Result(false, "No switch named \"$q\". Known: ${hits.joinToString { it.r.name }}")
        if (cand.size > 1) return Result(false, "\"$q\" matches ${cand.size} switches: ${cand.joinToString { it.r.name }}. Be more specific.")

        val h = cand.first()
        val want = when (state) { "on" -> true; "off" -> false; else -> !h.r.state }
        if (h.r.state == want) return Result(true, "${h.r.name} is already ${word(want)}")
        return try { Api.set(h.base, h.idx, want); Result(true, "${h.r.name} is now ${word(want)}") }
        catch (e: Exception) { Result(false, e.message ?: "Request failed") }
    }
}
