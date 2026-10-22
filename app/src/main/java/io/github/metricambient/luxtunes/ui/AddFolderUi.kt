package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.metricambient.luxtunes.data.LuxFolder
import java.io.File

/** Add Folder to Library: opened from Settings → My Library and the ⋮ menus. */
object AddFolder {
    internal val open = mutableStateOf(false)
    fun show() {
        open.value = true
    }
}

/** The folder picker, then the progress and what happened. Placed once, at the root. */
@Composable
fun AddFolderHost() {
    val repo = app().repository
    val adding by repo.adding.collectAsState()
    var open by AddFolder.open

    adding?.let { a ->
        AlertDialog(
            onDismissRequest = { if (a.finished) repo.clearAdding() },
            title = { Text(if (a.finished) "Songs added" else if (a.moving) "Moving songs…" else "Copying songs…") },
            text = {
                Column {
                    if (!a.finished) {
                        Text(if (a.found == 0) "Looking for songs…" else "${a.done} of ${a.found}")
                        LinearProgressIndicator(
                            progress = { if (a.found == 0) 0f else a.done.toFloat() / a.found },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    } else {
                        Text(
                            buildString {
                                append(
                                    if (a.added == 0) "No new songs were added."
                                    else "${plural(a.added, "song")} ${if (a.moving) "moved" else "copied"} into your library."
                                )
                                if (a.skipped > 0) append("\n${plural(a.skipped, "song")} already in the library, left where they are.")
                                if (a.failed > 0) append("\n${plural(a.failed, "file")} couldn't be read or ${if (a.moving) "moved" else "copied"}.")
                                if (a.added > 0) append("\n\nThey go to LuxTunes on your computer at the next sync.")
                            }
                        )
                    }
                }
            },
            confirmButton = {
                if (a.finished) TextButton(onClick = { repo.clearAdding() }) { Text("OK", color = Lux.accent) }
            },
        )
    }

    if (open) {
        FolderPicker(
            onDismiss = { open = false },
            onPick = { dir ->
                open = false
                repo.addFolder(dir)
            },
        )
    }
}

/** Browse the phone's storage for a folder: the storage volumes first, then folders inside. */
@Composable
private fun FolderPicker(onDismiss: () -> Unit, onPick: (File) -> Unit) {
    val context = LocalContext.current
    val keep = app().prefs.current.keepOriginals
    val roots = remember { LuxFolder.storageRoots(context) }
    var dir by remember { mutableStateOf<File?>(null) }
    val here = dir
    val folders = remember(here) {
        here?.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Add Folder to Library")
                Text(
                    here?.path ?: "Choose where to look",
                    color = Lux.dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (keep) "The songs are copied; the originals stay here."
                    else "The songs are moved into the LuxTunes folder.",
                    color = Lux.dim, fontSize = 13.sp,
                )
            }
        },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                if (here == null) {
                    items(roots.withIndex().toList()) { (i, root) ->
                        PickerRow(
                            if (i == 0) Icons.Filled.Smartphone else Icons.Filled.SdStorage,
                            if (i == 0) "Phone storage" else "SD card",
                        ) { dir = root }
                    }
                } else {
                    item {
                        PickerRow(Icons.AutoMirrored.Filled.ArrowBack, "Back") {
                            dir = if (roots.any { it.absolutePath == here.absolutePath }) null else here.parentFile
                        }
                    }
                    items(folders) { f -> PickerRow(Icons.Filled.Folder, f.name) { dir = f } }
                    if (folders.isEmpty()) {
                        item { Text("No folders in here.", color = Lux.dim, modifier = Modifier.padding(vertical = 12.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = here != null, onClick = { here?.let(onPick) }) {
                Text("Add This Folder", color = if (here != null) Lux.accent else Lux.faint)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Lux.accent) } },
    )
}

@Composable
private fun PickerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Lux.icon, modifier = Modifier.size(28.dp))
            Text(label, color = Lux.text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp))
        }
        HorizontalDivider(color = Lux.panel)
    }
}
