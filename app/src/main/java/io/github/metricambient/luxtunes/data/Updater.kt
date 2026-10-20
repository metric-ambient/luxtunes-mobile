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

/**
 * Settings → Check for Updates: looks at the latest GitHub release of the app.
 * Nothing here runs until the user has said yes to going online.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/metric-ambient/luxtunes-mobile/releases/latest"

    data class Release(val tag: String, val build: Int, val apkUrl: String, val notes: String)

    /** This copy's build number (the GitHub run that built it). */
    @Suppress("DEPRECATION")
    fun installedBuild(context: Context): Int =
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode

    /** The latest release, or null if there's none. Throws IOException when GitHub can't be reached. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            when (conn.responseCode) {
                200 -> {}
                404 -> return@withContext null       // no releases yet (or the repository is private)
                else -> throw IOException("GitHub answered ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
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
            val folder = File(context.cacheDir, "updates").apply { mkdirs() }
            folder.listFiles()?.forEach { it.delete() }
            val target = File(folder, "LuxTunes-Mobile-update.apk")
            val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode != 200) throw IOException("download failed (${conn.responseCode})")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    target.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            if (total > 0) progress(done.toFloat() / total)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            // Make sure it's really this app before handing it to Android.
            val info = context.packageManager.getPackageArchiveInfo(target.path, 0)
            if (info?.packageName != context.packageName) {
                target.delete()
                throw IOException("the download isn't a LuxTunes Mobile APK")
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
