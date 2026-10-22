package com.relay.ctrl
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppRelease(val version: String, val tag: String, val notes: String, val pageUrl: String,
                      val apkUrl: String, val apkSize: Long, val sha256: String?)

/** Self-update from the latest GitHub release of a public repository. */
object AppUpdater {
    private const val UA = "RelayControl-Android"
    const val ACTION_RESULT = "com.relay.ctrl.INSTALL_RESULT"

    fun repo(ctx: Context): String = ctx.getString(R.string.github_repo)

    @Suppress("DEPRECATION")
    fun installedVersion(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    fun isNewer(latest: String, current: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").split('.', '-').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Returns the latest release that has an .apk asset, or null if there is none. */
    suspend fun latest(ctx: Context): AppRelease? = withContext(Dispatchers.IO) {
        val repo = repo(ctx)
        if (repo.startsWith("OWNER")) throw IllegalStateException("Set github_repo in res/values/update.xml")
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("User-Agent", UA)
        try {
            if (c.responseCode == 404) return@withContext null
            if (c.responseCode != 200) throw IllegalStateException("GitHub answered ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val assets = o.getJSONArray("assets")
            var apk: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { apk = a; break }
            }
            if (apk == null) return@withContext null
            val tag = o.getString("tag_name")
            AppRelease(tag.removePrefix("v"), tag, o.optString("body"), o.optString("html_url"),
                apk.getString("browser_download_url"), apk.optLong("size"),
                apk.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 })
        } finally { c.disconnect() }
    }

    /** Downloads to the app cache, checks size and (when GitHub provides one) the SHA-256 digest. */
    suspend fun download(ctx: Context, r: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, "app.apk")
        val c = URL(r.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 20000; c.setRequestProperty("User-Agent", UA)
        try {
            if (c.responseCode != 200) throw IllegalStateException("Download failed (HTTP ${c.responseCode})")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.apkSize
            val md = MessageDigest.getInstance("SHA-256")
            c.inputStream.use { ins ->
                f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var n = 0; var done = 0L
                    while (ins.read(buf).also { n = it } >= 0) {
                        out.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        ensureActive()
                    }
                }
            }
            if (r.apkSize > 0 && f.length() != r.apkSize) { f.delete(); throw IllegalStateException("Download incomplete, please try again") }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (r.sha256 != null && !hex.equals(r.sha256, ignoreCase = true)) { f.delete(); throw IllegalStateException("Checksum mismatch, update rejected") }
            f
        } finally { c.disconnect() }
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()
    fun permissionIntent(ctx: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Installs through PackageInstaller. On Android 12+ the update needs no confirmation when this app is updating itself
     * (REQUEST_INSTALL_PACKAGES + UPDATE_PACKAGES_WITHOUT_USER_ACTION + USER_ACTION_NOT_REQUIRED); otherwise Android asks once.
     */
    fun install(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            apk.inputStream().use { ins -> s.openWrite("app.apk", 0, apk.length()).use { out -> ins.copyTo(out); s.fsync(out) } }
            val intent = Intent(ctx, InstallReceiver::class.java).setAction(ACTION_RESULT)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
        }
    }

    fun failureText(status: Int, msg: String): String = when {
        msg.contains("UPDATE_INCOMPATIBLE", true) || msg.contains("signatures", true) || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "The new version is signed with a different key than the installed app. Uninstall the app once and install the new version."
        status == PackageInstaller.STATUS_FAILURE_ABORTED -> "Update cancelled"
        status == PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage for the update"
        else -> msg.ifBlank { "Install failed (code $status)" }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                              else i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { ctx.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> {}   // Android restarts the app with the new version
            else -> Toast.makeText(ctx.applicationContext,
                AppUpdater.failureText(st, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()), Toast.LENGTH_LONG).show()
        }
    }
}
