package io.github.metricambient.luxtunes.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Settings → Check for Updates: looks at the latest GitHub release of the app.
 * Nothing here runs until the user has said yes to going online. It only talks
 * HTTPS to GitHub (checked again after redirects), and a download must match the
 * size and SHA-256 digest GitHub publishes for it before Android is handed it.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/metric-ambient/luxtunes-mobile/releases/latest"
    private val HOSTS = listOf("github.com", "githubusercontent.com")
    private const val MAX_ANSWER = 4L * 1024 * 1024          // the release's JSON
    private const val MAX_APK = 200L * 1024 * 1024

    data class Release(
        val tag: String, val build: Int, val apkUrl: String, val notes: String,
        val size: Long = 0, val sha256: String = "",
    )

    /** An https:// address on GitHub. */
    fun allowed(url: URL): Boolean {
        val host = url.host.lowercase().trimEnd('.')
        return url.protocol == "https" && HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /** GET `url` from GitHub; the connection is open once this returns (status 200 or 404 checked by the caller). */
    private fun open(url: String, accept: String, readTimeout: Int): HttpURLConnection {
        val address = URL(url)
        if (!allowed(address)) throw IOException("not a GitHub address: $url")
        val conn = (address.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "LuxTunes-Mobile")      // GitHub refuses requests without one
            connectTimeout = 15_000
            this.readTimeout = readTimeout
            instanceFollowRedirects = true      // GitHub sends files from another address
        }
        conn.responseCode                       // follows the redirects
        if (!allowed(conn.url)) {
            conn.disconnect()
            throw IOException("GitHub redirected to an unexpected address")
        }
        return conn
    }

    /** Copy at most `limit` bytes of `input` to `out`; IOException if there's more. */
    private fun copyLimited(input: java.io.InputStream, out: java.io.OutputStream, limit: Long,
                            each: (ByteArray, Int, Long) -> Unit = { _, _, _ -> }): Long {
        val buffer = ByteArray(64 * 1024)
        var done = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return done
            done += n
            if (done > limit) throw IOException("the download was bigger than expected")
            out.write(buffer, 0, n)
            each(buffer, n, done)
        }
    }

    /** This copy's build number (the GitHub run that built it). */
    @Suppress("DEPRECATION")
    fun installedBuild(context: Context): Int =
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode

    /** The latest release, or null if there's none. Throws IOException when GitHub can't be reached. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val conn = open(LATEST, "application/vnd.github+json", 15_000)
        try {
            when (conn.responseCode) {
                200 -> {}
                404 -> return@withContext null       // no releases yet (or the repository is private)
                else -> throw IOException("GitHub answered ${conn.responseCode}")
            }
            val bytes = java.io.ByteArrayOutputStream()
            conn.inputStream.use { copyLimited(it, bytes, MAX_ANSWER) }
            val json = JSONObject(bytes.toString("UTF-8"))
            val assets = json.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                // LuxTunes-Mobile-v0.2.0-17.apk: 17 is the build number.
                val build = Regex("-(\\d+)\\.apk$").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                return@withContext Release(
                    tag = json.optString("tag_name"),
                    build = build,
                    apkUrl = asset.optString("browser_download_url"),
                    notes = json.optString("body"),
                    size = asset.optLong("size"),
                    sha256 = asset.optString("digest").removePrefix("sha256:").lowercase(),
                )
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Download the release's APK into the app's cache. progress(0..1) while downloading. */
    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (!Regex("^[0-9a-f]{64}$").matches(release.sha256))
                throw IOException("GitHub didn't give a checksum for this download")
            val folder = File(context.cacheDir, "updates").apply { mkdirs() }
            folder.listFiles()?.forEach { it.delete() }
            val target = File(folder, "LuxTunes-Mobile-update.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            val conn = open(release.apkUrl, "application/octet-stream", 30_000)
            try {
                if (conn.responseCode != 200) throw IOException("download failed (${conn.responseCode})")
                val total = if (release.size > 0) release.size else conn.contentLengthLong
                conn.inputStream.use { input ->
                    target.outputStream().use { out ->
                        copyLimited(input, out, if (release.size > 0) release.size else MAX_APK) { buffer, n, done ->
                            digest.update(buffer, 0, n)
                            if (total > 0) progress(done.toFloat() / total)
                        }
                    }
                }
            } catch (e: IOException) {
                target.delete()
                throw e
            } finally {
                conn.disconnect()
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!MessageDigest.isEqual(actual.toByteArray(), release.sha256.toByteArray())) {
                target.delete()
                throw IOException("the download doesn't match GitHub's checksum")
            }
            // Make sure it's really this app before handing it to Android.
            val info = context.packageManager.getPackageArchiveInfo(target.path, 0)
            if (info?.packageName != context.packageName) {
                target.delete()
                throw IOException("the download isn't a LuxTunes APK")
            }
            target
        }

    /** True if Android lets this app install updates; otherwise the user must allow it first. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Android's "Install unknown apps" page for this app. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Hand the APK to Android's installer, which asks the user to confirm. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
