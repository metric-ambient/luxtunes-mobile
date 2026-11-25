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

    /**
     * folder/relative, or null if the path is absolute or has a ".." in it. Paths in
     * library.db come from shared storage, which other apps can write, so every file
     * the app opens or plays from a library path goes through here. Cheap enough for
     * list rows and long queues: it only looks at the text.
     */
    fun inside(folder: File, relative: String?): File? {
        if (relative.isNullOrEmpty() || relative.startsWith("/")) return null
        if (relative.split('/').any { it == ".." }) return null
        return File(folder, relative)
    }

    /** [inside], and links are followed too: for deleting, where a mistake can't be undone. */
    fun insideStrict(folder: File, relative: String?): File? {
        val file = inside(folder, relative) ?: return null
        return try {
            val base = folder.canonicalFile
            val real = file.canonicalFile
            if (real.path.startsWith(base.path + File.separator)) real else null
        } catch (e: java.io.IOException) {
            null
        }
    }

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
     * Where the library is. `folder`: the LuxTunes folder to use (null: none found).
     * `cardMissing`: the user chose the SD card but it isn't in the phone; `folder` is
     * then the library on internal storage, if there is one (the app switches to it).
     */
    data class Found(val folder: File?, val cardMissing: Boolean)

    /** The library on the storage the user chose (see [locateIn]). */
    fun locate(context: Context, choice: StorageChoice?): Found = locateIn(storageRoots(context), choice)

    /** The LuxTunes folder to use (as [locate] finds it), or null. */
    fun find(context: Context, choice: StorageChoice?): File? = locate(context, choice).folder

    /**
     * `roots`: internal storage first, then SD cards. The LuxTunes folder with a library.db
     * on the chosen storage (with no choice yet, on any: the most recently synced). If the
     * chosen SD card isn't in the phone, the library on internal storage instead.
     */
    fun locateIn(roots: List<File>, choice: StorageChoice?): Found {
        val internal = roots.take(1)
        val removable = roots.drop(1)
        val chosen = when (choice) {
            StorageChoice.INTERNAL -> internal
            StorageChoice.REMOVABLE -> removable
            null -> roots
        }
        libraryOn(chosen)?.let { return Found(it, cardMissing = false) }
        if (choice == StorageChoice.REMOVABLE && removable.isEmpty()) return Found(libraryOn(internal), cardMissing = true)
        return Found(null, cardMissing = false)
    }

    /** The LuxTunes folder with a library.db on these storages (the newest library.db if several). */
    fun libraryOn(roots: List<File>): File? = roots
        .map { File(it, FOLDER) }
        .filter { File(it, DATABASE).isFile }
        .maxByOrNull { File(it, DATABASE).lastModified() }

    /** An SD card with a LuxTunes library on it is in the phone. */
    fun libraryOnCard(context: Context): Boolean = libraryOn(storageRoots(context).drop(1)) != null
}
