package io.github.metricambient.luxtunes

import android.content.Context

object BuildConfigInfo {
    /** "0.1.0 (build 12)" */
    @Suppress("DEPRECATION")
    fun versionName(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (build ${info.versionCode})"
    }.getOrDefault("")
}
