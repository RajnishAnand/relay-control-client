package com.relay.ctrl
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.*
import java.util.concurrent.atomic.AtomicLong

data class Relay(val name: String, val state: Boolean, val sched: Boolean, val on: String, val off: String)
data class Info(val firmware: String, val synced: Boolean, val rssi: Int, val ip: String)
data class Found(val base: String, val relays: List<Relay>, val locked: String? = null)
class ApiAuthException(msg: String) : Exception(msg)
data class UpdateStatus(val current: String, val configured: Boolean, val state: String, val latest: String,
                        val progress: Int, val message: String, val pendingConfirm: Boolean, val lastCheckSecondsAgo: Int)

object Api {
    private fun get(base: String, path: String, t: Int, method: String = "GET"): String {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.connectTimeout = t; c.readTimeout = t
        Tokens.forBase(base)?.let { c.setRequestProperty("X-API-Key", it) }
        if (method == "POST") { c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        try {
            val code = c.responseCode
            val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code == 401 && body.trim() == "unauthorized") throw ApiAuthException("API token missing or wrong")
            if (code == 503 && body.trim() == "device not provisioned") throw ApiAuthException("Device has no token set (not provisioned)")
            if (code >= 400) throw IllegalStateException(body.ifBlank { "HTTP $code" })
            return body
        } finally { c.disconnect() }
    }
    private fun parse(s: String): List<Relay> {
        val a = JSONObject(s).getJSONArray("relays")
        require(a.length() == 4)
        return (0 until 4).map { a.getJSONObject(it).let { o ->
            Relay(o.getString("name"), o.getBoolean("state"), o.getBoolean("schedEnabled"), o.getString("onTime"), o.getString("offTime")) } }
    }
    suspend fun status(b: String, t: Int = 2500) = withContext(Dispatchers.IO) { parse(get(b, "/api/status", t)) }
    suspend fun info(b: String): Info = withContext(Dispatchers.IO) {
        val o = JSONObject(get(b, "/api/info", 2500)); val w = o.optJSONObject("wifi"); val t = o.optJSONObject("time")
        Info(o.optString("firmware"), t?.optBoolean("synced") ?: false, w?.optInt("rssi") ?: 0, w?.optString("ip").orEmpty())
    }
    private fun JSONObject.str(k: String) = if (isNull(k)) "" else optString(k)
    suspend fun updateStatus(b: String): UpdateStatus = withContext(Dispatchers.IO) {
        val o = JSONObject(get(b, "/api/update/status", 2500))
        UpdateStatus(o.str("current"), o.optBoolean("configured"), o.str("state"), o.str("latest"), o.optInt("progress"),
            o.str("message"), o.optBoolean("pendingConfirm"), o.optInt("lastCheckSecondsAgo", -1))
    }
    suspend fun updateCheck(b: String) { withContext(Dispatchers.IO) { get(b, "/api/update/check", 2500, "POST") } }
    /** Reboots the device: every relay drops for several seconds. Only call after explicit user confirmation. */
    suspend fun updateApply(b: String) { withContext(Dispatchers.IO) { get(b, "/api/update/apply", 2500, "POST") } }

    private suspend fun verify(b: String, ok: (List<Relay>) -> Boolean): List<Relay> {
        val r = status(b); if (!ok(r)) throw IllegalStateException("Device did not confirm the change"); return r
    }
    private suspend fun call(b: String, p: String) = withContext(Dispatchers.IO) { get(b, p, 2500) }

    /** Name rules: 1-24 UTF-8 bytes, no control characters. */
    fun clampName(s: String): String {
        val out = StringBuilder(); var bytes = 0
        for (cp in s.codePoints().toArray()) {
            val ch = String(Character.toChars(cp)); val n = ch.toByteArray().size
            if (bytes + n > 24) break
            out.append(ch); bytes += n
        }
        return out.toString()
    }

    suspend fun set(b: String, i: Int, on: Boolean): List<Relay> {
        call(b, "/api/set?relay=$i&state=${if (on) 1 else 0}"); return verify(b) { it[i].state == on }
    }
    suspend fun allOff(b: String): List<Relay> { call(b, "/api/all?state=0"); return verify(b) { r -> r.none { it.state } } }
    /** The firmware itself staggers switch-on (400 ms apart); status reports the commanded state immediately. */
    suspend fun allOn(b: String): List<Relay> { call(b, "/api/all?state=1"); return verify(b) { r -> r.all { it.state } } }
    suspend fun rename(b: String, i: Int, name: String): List<Relay> {
        val n = clampName(name.filter { !it.isISOControl() }).trim().ifBlank { "Relay ${i + 1}" }
        call(b, "/api/rename?relay=$i&name=${URLEncoder.encode(n, "UTF-8").replace("+", "%20")}")
        return verify(b) { it[i].name == n }
    }
    suspend fun schedule(b: String, i: Int, en: Boolean, on: String, off: String): List<Relay> {
        call(b, "/api/schedule?relay=$i&enabled=${if (en) 1 else 0}&on=$on&off=$off")
        return verify(b) { it[i].sched == en && it[i].on == on && it[i].off == off }
    }

    enum class ScanResult { DONE, COOLDOWN, NO_WIFI }
    private val lastFull = AtomicLong(0)

    /** Returns the /24 prefix only on a private (RFC1918) Wi-Fi network, never on mobile data, VPN or public ranges. */
    private fun wifiPrefix(ctx: Context): String? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        val ip = cm.getLinkProperties(net)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address } ?: return null
        if (!ip.isSiteLocalAddress) return null
        return ip.hostAddress?.substringBeforeLast('.')
    }

    private fun probe(h: String): Found? {
        val b = "http://$h"
        return try { Found(b, parse(get(b, "/api/status", 700))) }
        catch (e: ApiAuthException) { Found(b, emptyList(), e.message) }   // it is a relay controller, but needs a token
        catch (e: Exception) { null }
    }

    /**
     * full=false: light check of known devices plus <subnet>.250 only (a handful of requests).
     * full=true: sweep of the local /24, at most once per 30 s, 12 requests in flight at a time.
     */
    suspend fun scan(ctx: Context, known: Collection<String>, full: Boolean, onFound: (Found) -> Unit): ScanResult = coroutineScope {
        Tokens.init(ctx)
        val prefix = wifiPrefix(ctx) ?: return@coroutineScope ScanResult.NO_WIFI
        if (full) {
            val now = System.currentTimeMillis()
            if (now - lastFull.get() < 30_000) return@coroutineScope ScanResult.COOLDOWN
            lastFull.set(now)
        }
        val hosts = if (full) listOf("$prefix.250") + (1..254).filter { it != 250 }.map { "$prefix.$it" }
                    else (known.map { it.removePrefix("http://") } + "$prefix.250").distinct()
        val gate = Semaphore(12)
        hosts.map { h -> async(Dispatchers.IO) { gate.withPermit { delay(10); probe(h)?.let(onFound) } } }.awaitAll()
        ScanResult.DONE
    }
}
