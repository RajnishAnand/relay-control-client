package com.relay.ctrl
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class Relay(val name: String, val state: Boolean, val sched: Boolean, val on: String, val off: String)
data class Info(val firmware: String, val synced: Boolean, val rssi: Int, val ip: String)
data class Found(val base: String, val relays: List<Relay>, val locked: String? = null)
open class ApiAuthException(msg: String) : Exception(msg)                 // 401 / 503
class ApiRateLimitException(msg: String) : ApiAuthException(msg)          // 429: guest login paused
class ApiForbiddenException(msg: String) : Exception(msg)                 // 403: admin endpoint, guest credential
data class UpdateStatus(val current: String, val configured: Boolean, val state: String, val latest: String,
                        val progress: Int, val message: String, val pendingConfirm: Boolean, val lastCheckSecondsAgo: Int)
data class WifiNet(val ssid: String, val rssi: Int, val secure: Boolean, val channel: Int)
data class WifiScan(val state: String, val nets: List<WifiNet>)
data class WifiStatus(val configured: Boolean, val ssid: String, val connected: Boolean, val ip: String, val rssi: Int,
                      val apActive: Boolean, val changeState: String, val changeMessage: String)

object Api {
    /** Role reported by the device on the last /api/status ("admin" or "guest"). */
    private val roles = ConcurrentHashMap<String, String>()
    fun roleOf(b: String): String? = roles[b]

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun JSONObject.str(k: String) = if (isNull(k)) "" else optString(k)

    /** Secrets always travel in a POST form body, never in a URL. */
    private fun get(base: String, path: String, t: Int, method: String = "GET", form: Map<String, String>? = null): String {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.connectTimeout = t; c.readTimeout = t
        Tokens.forBase(base)?.let { c.setRequestProperty("X-API-Key", it) }
        if (method == "POST") {
            val body = form?.entries?.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }?.toByteArray() ?: ByteArray(0)
            c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(body.size)
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body) }
        }
        try {
            val code = c.responseCode
            val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            val msg = body.trim()
            if (code == 401 && msg == "unauthorized") throw ApiAuthException("Access key missing or wrong")
            if (code == 403 && msg == "admin access required") throw ApiForbiddenException("Admin access required")
            if (code == 429 && msg.startsWith("too many failed attempts")) throw ApiRateLimitException("Too many failed attempts. Guest login is paused for about a minute.")
            if (code == 503 && msg == "device not provisioned") throw ApiAuthException("Device has no admin token set (not provisioned)")
            if (code >= 400) throw IllegalStateException(body.ifBlank { "HTTP $code" })
            return body
        } finally { c.disconnect() }
    }

    private fun parseStatus(b: String, s: String): List<Relay> {
        val o = JSONObject(s)
        roles[b] = o.optString("role", "admin").lowercase()      // older firmware has no role: only the token existed
        val a = o.getJSONArray("relays")
        require(a.length() == 4)
        return (0 until 4).map { a.getJSONObject(it).let { r ->
            Relay(r.getString("name"), r.getBoolean("state"), r.getBoolean("schedEnabled"), r.getString("onTime"), r.getString("offTime")) } }
    }

    // A busy controller (update check, Wi-Fi scan) can answer slowly, so these timeouts are generous.
    suspend fun status(b: String, t: Int = 4000) = withContext(Dispatchers.IO) { parseStatus(b, get(b, "/api/status", t)) }
    suspend fun info(b: String): Info = withContext(Dispatchers.IO) {
        val o = JSONObject(get(b, "/api/info", 6000)); val w = o.optJSONObject("wifi"); val t = o.optJSONObject("time")
        Info(o.optString("firmware"), t?.optBoolean("synced") ?: false, w?.optInt("rssi") ?: 0, w?.optString("ip").orEmpty())
    }
    private suspend fun call(b: String, p: String) = withContext(Dispatchers.IO) { get(b, p, 4000) }
    private suspend fun post(b: String, p: String, form: Map<String, String>? = null, t: Int = 10_000) =
        withContext(Dispatchers.IO) { get(b, p, t, "POST", form) }

    private suspend fun verify(b: String, ok: (List<Relay>) -> Boolean): List<Relay> {
        val r = status(b); if (!ok(r)) throw IllegalStateException("Device did not confirm the change"); return r
    }

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

    // ---- relays (guest or admin) ----
    suspend fun set(b: String, i: Int, on: Boolean): List<Relay> {
        call(b, "/api/set?relay=$i&state=${if (on) 1 else 0}"); return verify(b) { it[i].state == on }
    }
    suspend fun allOff(b: String): List<Relay> { call(b, "/api/all?state=0"); return verify(b) { r -> r.none { it.state } } }
    /** The firmware staggers switch-on itself (400 ms apart); status reports the commanded state immediately. */
    suspend fun allOn(b: String): List<Relay> { call(b, "/api/all?state=1"); return verify(b) { r -> r.all { it.state } } }

    // ---- admin only ----
    suspend fun rename(b: String, i: Int, name: String): List<Relay> {
        val n = clampName(name.filter { !it.isISOControl() }).trim().ifBlank { "Relay ${i + 1}" }
        call(b, "/api/rename?relay=$i&name=${enc(n).replace("+", "%20")}")
        return verify(b) { it[i].name == n }
    }
    suspend fun schedule(b: String, i: Int, en: Boolean, on: String, off: String): List<Relay> {
        call(b, "/api/schedule?relay=$i&enabled=${if (en) 1 else 0}&on=$on&off=$off")
        return verify(b) { it[i].sched == en && it[i].on == on && it[i].off == off }
    }

    suspend fun guestEnabled(b: String): Boolean = withContext(Dispatchers.IO) { JSONObject(get(b, "/api/guest", 6000)).optBoolean("enabled") }
    suspend fun guestSet(b: String, password: String) { post(b, "/api/guest/password", mapOf("password" to password)) }
    suspend fun guestDisable(b: String) { post(b, "/api/guest/disable") }

    suspend fun wifiScanStart(b: String) { post(b, "/api/wifi/scan") }
    suspend fun wifiScanResult(b: String): WifiScan = withContext(Dispatchers.IO) {
        val o = JSONObject(get(b, "/api/wifi/scan", 8000)); val a = o.optJSONArray("networks")
        WifiScan(o.str("state"), (0 until (a?.length() ?: 0)).map { i ->
            val n = a!!.getJSONObject(i); WifiNet(n.str("ssid"), n.optInt("rssi"), n.optBoolean("secure"), n.optInt("channel")) })
    }
    suspend fun wifiStatus(b: String): WifiStatus = withContext(Dispatchers.IO) {
        val o = JSONObject(get(b, "/api/wifi/status", 8000)); val c = o.optJSONObject("change")
        WifiStatus(o.optBoolean("configured"), o.str("ssid"), o.optBoolean("connected"), o.str("ip"), o.optInt("rssi"),
            o.optBoolean("apActive"), c?.str("state").orEmpty(), c?.str("message").orEmpty())
    }
    /** Returns immediately; the device switches networks afterwards and may become unreachable from here. */
    suspend fun wifiConnect(b: String, ssid: String, password: String) {
        post(b, "/api/wifi/connect", buildMap { put("ssid", ssid); if (password.isNotEmpty()) put("password", password) })
    }

    // ---- firmware update (admin) ----
    suspend fun updateStatus(b: String): UpdateStatus = withContext(Dispatchers.IO) {
        val o = JSONObject(get(b, "/api/update/status", 8000))
        UpdateStatus(o.str("current"), o.optBoolean("configured"), o.str("state"), o.str("latest"), o.optInt("progress"),
            o.str("message"), o.optBoolean("pendingConfirm"), o.optInt("lastCheckSecondsAgo", -1))
    }
    suspend fun updateCheck(b: String) { post(b, "/api/update/check") }
    /** Reboots the device: every relay drops for several seconds. Only call after explicit user confirmation. */
    suspend fun updateApply(b: String) { post(b, "/api/update/apply") }

    // ---- discovery ----
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
        return try { Found(b, parseStatus(b, get(b, "/api/status", 1500))) }
        catch (e: ApiAuthException) { Found(b, emptyList(), e.message) }   // a relay controller that needs a key (or paused guest login)
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
