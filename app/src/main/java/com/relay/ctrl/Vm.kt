package com.relay.ctrl
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import java.util.concurrent.ConcurrentHashMap

data class Dev(val base: String, val relays: List<Relay>, val online: Boolean, val locked: String? = null, val info: Info? = null)
data class Ui(val devices: List<Dev> = emptyList(), val scanning: Boolean = false, val msg: String? = null, val busy: Boolean = false,
              val orders: Map<String, List<Int>> = emptyMap(),
              val appUpdate: AppRelease? = null, val appChecking: Boolean = false, val appUpdateNote: String? = null,
              val appUpdateProgress: Float? = null)

class Vm(private val app: Application) : AndroidViewModel(app) {
    private val _s = MutableStateFlow(Ui()); val s = _s.asStateFlow()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val lastTry = ConcurrentHashMap<String, Long>()
    private val infoAt = ConcurrentHashMap<String, Long>()
    private val active = MutableStateFlow(true)
    private val prefs = app.getSharedPreferences("p", 0)
    private fun lock(b: String) = locks.getOrPut(b) { Mutex() }
    suspend fun <T> withDevice(b: String, f: suspend () -> T): T = lock(b).withLock { f() }
    fun setActive(a: Boolean) { active.value = a }   // no network traffic while the app is in the background

    init {
        Tokens.init(app)
        prefs.getStringSet("known", emptySet())!!.forEach { put(it, null, false) }
        viewModelScope.launch {   // one GitHub request a day at most, silent on failure
            delay(4000)
            if (autoUpdateEnabled() && System.currentTimeMillis() - prefs.getLong("update_checked", 0L) > 24 * 3600 * 1000L) checkAppUpdate(false)
        }
        viewModelScope.launch {
            var rounds = 0; var nextDiscover = 0L
            while (true) {
                active.first { it }
                poll()
                val none = _s.value.devices.none { it.online }
                val now = System.currentTimeMillis()
                if (!none) { rounds = 0; nextDiscover = 0L }
                else if (now >= nextDiscover) {
                    // full sweep only the very first time with nothing known; otherwise a light check
                    doScan(full = rounds == 0 && _s.value.devices.isEmpty(), manual = false)
                    rounds++
                    nextDiscover = System.currentTimeMillis() + minOf(15_000L shl minOf(rounds, 5), 300_000L)
                }
                Shortcuts.publish(app, _s.value)
                delay(3000)
            }
        }
    }

    private fun loadOrder(b: String): List<Int> =
        prefs.getString("order:$b", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.sorted() == listOf(0, 1, 2, 3) }
            ?: listOf(0, 1, 2, 3)

    private fun put(b: String, r: List<Relay>?, online: Boolean, locked: String? = null, info: Info? = null) = _s.update { u ->
        val i = u.devices.indexOfFirst { it.base == b }
        val old = u.devices.getOrNull(i)
        val d = Dev(b, r ?: old?.relays ?: emptyList(), online, locked, info ?: old?.info)
        u.copy(devices = if (i < 0) (u.devices + d).sortedBy { it.base } else u.devices.toMutableList().also { it[i] = d },
            orders = if (u.orders.containsKey(b)) u.orders else u.orders + (b to loadOrder(b)))
    }

    /** UI-only display order; relay indices sent to the device are unchanged. */
    fun move(b: String, pos: Int, delta: Int) {
        val o = (_s.value.orders[b] ?: loadOrder(b)).toMutableList()
        val j = pos + delta
        if (pos !in o.indices || j !in o.indices) return
        o.add(j, o.removeAt(pos))
        prefs.edit().putString("order:$b", o.joinToString(",")).apply()
        _s.update { it.copy(orders = it.orders + (b to o)) }
    }

    /** Saves the display order after a drag (UI-only; relay indices sent to the device never change). */
    fun setOrder(b: String, o: List<Int>) {
        if (o.sorted() != listOf(0, 1, 2, 3)) return
        prefs.edit().putString("order:$b", o.joinToString(",")).apply()
        _s.update { it.copy(orders = it.orders + (b to o)) }
    }

    private fun save(b: String) {
        val k = prefs.getStringSet("known", emptySet())!!.toMutableSet()
        if (k.add(b)) prefs.edit().putStringSet("known", k).apply()
    }
    fun notify(m: String) = say(m)
    private fun say(m: String) {
        _s.update { it.copy(msg = m) }
        viewModelScope.launch { delay(4000); _s.update { u -> if (u.msg == m) u.copy(msg = null) else u } }
    }

    /** Online devices every cycle (3 s); offline or locked ones only every 15 s. Device info once a minute. */
    private suspend fun poll() = coroutineScope {
        val now = System.currentTimeMillis()
        _s.value.devices.filter { it.online || now - (lastTry[it.base] ?: 0L) >= 15_000L }.map { d -> async {
            lastTry[d.base] = now
            lock(d.base).withLock {
                try {
                    val r = Api.status(d.base)
                    var inf: Info? = null
                    if (now - (infoAt[d.base] ?: 0L) >= 60_000L) { infoAt[d.base] = now; inf = runCatching { Api.info(d.base) }.getOrNull() }
                    put(d.base, r, true, null, inf)
                } catch (e: CancellationException) { throw e }
                catch (e: ApiAuthException) { put(d.base, null, false, e.message) }
                catch (e: Exception) { put(d.base, null, false) }
            } } }.awaitAll()
    }

    fun setToken(base: String?, token: String) {
        Tokens.set(base, token)
        if (base == null) lastTry.clear() else lastTry.remove(base)
        viewModelScope.launch { poll() }
    }
    /** From a scanned QR: token (and optionally the device it belongs to). */
    fun applyPairing(scope: String?, host: String?, key: String) {
        val target = host?.let { if (it.startsWith("http")) it else "http://$it" } ?: scope
        setToken(target, key)
        if (host != null) add(host)
    }

    fun autoUpdateEnabled() = prefs.getBoolean("auto_update", true)
    fun setAutoUpdate(on: Boolean) { prefs.edit().putBoolean("auto_update", on).apply() }

    fun checkAppUpdate(manual: Boolean) {
        if (_s.value.appChecking) return
        _s.update { it.copy(appChecking = true, appUpdateNote = null) }
        viewModelScope.launch {
            try {
                val r = AppUpdater.latest(app)
                prefs.edit().putLong("update_checked", System.currentTimeMillis()).apply()
                val cur = AppUpdater.installedVersion(app)
                val newer = r != null && AppUpdater.isNewer(r.version, cur)
                _s.update { it.copy(appUpdate = if (newer) r else null,
                    appUpdateNote = if (!newer && manual) "You're up to date ($cur)." else null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (manual) _s.update { it.copy(appUpdateNote = e.message ?: "Couldn't check for updates") } }
            _s.update { it.copy(appChecking = false) }
        }
    }

    fun downloadAndInstall() {
        val r = _s.value.appUpdate ?: return
        if (_s.value.appUpdateProgress != null) return
        _s.update { it.copy(appUpdateProgress = 0f, appUpdateNote = null) }
        viewModelScope.launch {
            try {
                val f = AppUpdater.download(app, r) { p -> _s.update { it.copy(appUpdateProgress = p) } }
                _s.update { it.copy(appUpdateProgress = -1f) }
                AppUpdater.install(app, f)
                delay(30_000)   // if the installer never came up, let the user try again
                _s.update { it.copy(appUpdateProgress = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _s.update { it.copy(appUpdateProgress = null, appUpdateNote = e.message ?: "Update failed") } }
        }
    }

    fun scan() { viewModelScope.launch { doScan(full = true, manual = true) } }

    private suspend fun doScan(full: Boolean, manual: Boolean) {
        if (_s.value.scanning) return
        _s.update { it.copy(scanning = true) }
        val known = prefs.getStringSet("known", emptySet())!!.toList()
        val r = runCatching { Api.scan(app, known, full) { f -> save(f.base); put(f.base, f.relays.takeIf { it.isNotEmpty() }, f.locked == null, f.locked) } }.getOrNull()
        _s.update { it.copy(scanning = false) }
        if (manual) when (r) {
            Api.ScanResult.COOLDOWN -> say("Scanned moments ago. Try again in a few seconds.")
            Api.ScanResult.NO_WIFI -> say("Connect to your home Wi-Fi to scan.")
            else -> {}
        }
    }

    fun add(ip: String) {
        val b = ip.trim().let { if (it.startsWith("http")) it else "http://$it" }
        viewModelScope.launch {
            runCatching { Api.status(b) }.onSuccess { save(b); put(b, it, true) }.onFailure { say("Can't reach $ip") }
        }
    }

    fun forget(b: String) {
        prefs.edit().putStringSet("known", prefs.getStringSet("known", emptySet())!!.toMutableSet().apply { remove(b) }).apply()
        _s.update { u -> u.copy(devices = u.devices.filter { it.base != b }) }
    }

    private fun act(b: String, f: suspend () -> List<Relay>) {
        viewModelScope.launch { lock(b).withLock {
            _s.update { it.copy(busy = true) }
            runCatching { f() }.onSuccess { put(b, it, true) }.onFailure { say(it.message ?: "Request failed") }
            _s.update { it.copy(busy = false) }
        } }
    }

    fun set(b: String, i: Int, on: Boolean) = act(b) { Api.set(b, i, on) }
    fun allOn(b: String) = act(b) { Api.allOn(b) }
    fun allOff(b: String) = act(b) { Api.allOff(b) }
    fun rename(b: String, i: Int, n: String) = act(b) { Api.rename(b, i, n) }
    fun schedule(b: String, i: Int, en: Boolean, on: String, off: String) = act(b) { Api.schedule(b, i, en, on, off) }
}
