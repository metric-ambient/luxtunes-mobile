package io.github.metricambient.luxtunes

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object BuildConfigInfo {
    /** "0.2.0 (build 12)" */
    @Suppress("DEPRECATION")
    fun versionName(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (build ${info.versionCode})"
    }.getOrDefault("")

    /**
     * The start of the signing certificate's SHA-256 ("09:46:06:6C"). Only APKs
     * signed with the same certificate can update each other, so this shows at a
     * glance whether two builds are compatible.
     */
    @Suppress("DEPRECATION")
    fun signer(context: Context): String = runCatching {
        val pm = context.packageManager
        val signature = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return@runCatching ""
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .take(4).joinToString(":") { "%02X".format(it) }
    }.getOrDefault("")
}
