package io.github.metricambient.luxtunes.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/** Finding the LuxTunes folder LuxTunes syncs to (internal storage or an SD card). */
object LuxFolder {
    const val FOLDER = "LuxTunes"
    const val DATABASE = "library.db"
    const val CHANGES = "changes.json"

    /** Can the app read and write the shared LuxTunes folder? */
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** The top of every storage volume: internal storage first, then SD cards. */
    fun storageRoots(context: Context): List<File> {
        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory()
        val roots = mutableListOf(primary)
        // An app's own folder on each volume is ".../Android/data/<package>/files";
        // the volume's top is the part before "/Android/".
        for (dir in context.getExternalFilesDirs(null)) {
            if (dir == null) continue
            val path = dir.absolutePath
            val cut = path.indexOf("/Android/")
            if (cut > 0) roots += File(path.substring(0, cut))
        }
        return roots.distinctBy { it.absolutePath }
    }

    /** True if the phone has a removable SD card mounted. */
    fun hasRemovable(context: Context): Boolean = storageRoots(context).size > 1

    /**
     * The LuxTunes folder holding a library.db, on the storage the user chose
     * (internal storage or the SD card). With no choice yet, the one synced most
     * recently.
     */
    fun find(context: Context, choice: StorageChoice?): File? {
        val roots = storageRoots(context)
        val allowed = when (choice) {
            StorageChoice.INTERNAL -> roots.take(1)
            StorageChoice.REMOVABLE -> roots.drop(1)
            null -> roots
        }
        return allowed
            .map { File(it, FOLDER) }
            .filter { File(it, DATABASE).isFile }
            .maxByOrNull { File(it, DATABASE).lastModified() }
    }
}
