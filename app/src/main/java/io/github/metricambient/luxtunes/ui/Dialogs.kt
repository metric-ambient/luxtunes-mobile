package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.metricambient.luxtunes.data.LuxFolder
import io.github.metricambient.luxtunes.data.StorageChoice
import androidx.compose.ui.platform.LocalContext

/** The pop-ups any screen can ask for. Only one shows at a time. */
sealed interface DialogRequest {
    data class AddToPlaylist(val ids: List<String>) : DialogRequest
    data class Duplicates(val playlistId: String, val name: String, val ids: List<String>, val count: Int) : DialogRequest
    data class NewPlaylist(val ids: List<String>) : DialogRequest
    data class RenamePlaylist(val id: String, val name: String) : DialogRequest
    data class RenameLibrary(val name: String) : DialogRequest
    data class DeletePlaylist(val id: String, val name: String) : DialogRequest
    data class Info(val ids: List<String>) : DialogRequest
    data class EditTags(val ids: List<String>) : DialogRequest
    data class Delete(val ids: List<String>) : DialogRequest
    data class ChooseStorage(val firstTime: Boolean) : DialogRequest
    data class SetEq(val ids: List<String>, val title: String) : DialogRequest
    data class Choose(
        val title: String,
        val options: List<String>,
        val selected: Int,
        val onPick: (Int) -> Unit,
    ) : DialogRequest
}

object Dialogs {
    var current by mutableStateOf<DialogRequest?>(null)
    fun show(request: DialogRequest) { current = request }
    fun close() { current = null }
}

/** Shows whichever pop-up was asked for (placed once, at the root). */
@Composable
fun DialogHost() {
    val app = app()
    when (val r = Dialogs.current) {
        null -> {}
        is DialogRequest.AddToPlaylist -> AddToPlaylistDialog(r.ids)
        is DialogRequest.Duplicates -> DuplicatesDialog(r)
        is DialogRequest.NewPlaylist -> NameDialog("New Playlist", "", "Create") { name ->
            app.repository.createPlaylist(name, r.ids)
            if (r.ids.isNotEmpty()) Notice.show("${plural(r.ids.size, "song")} added to ${name.trim()}")
        }
        is DialogRequest.RenamePlaylist -> NameDialog("Rename Playlist", r.name, "Rename") { name ->
            app.repository.renamePlaylist(r.id, name)
        }
        is DialogRequest.RenameLibrary -> NameDialog("Rename Library", r.name, "Rename") { name ->
            app.repository.renameLibrary(name)
        }
        is DialogRequest.DeletePlaylist -> DeletePlaylistDialog(r)
        is DialogRequest.Info -> InfoDialog(r.ids)
        is DialogRequest.EditTags -> EditTagsDialog(r.ids)
        is DialogRequest.Delete -> DeleteDialog(r.ids)
        is DialogRequest.ChooseStorage -> StorageDialog(r.firstTime)
        is DialogRequest.Choose -> ChooseDialog(r)
        is DialogRequest.SetEq -> SetEqDialog(r)
    }
}

/**
 * Set EQ…: an equalizer preset for a song, or for every song of an album, artist or
 * playlist. It's kept in each song (as LuxTunes does) and goes to LuxTunes at the next sync.
 * None: the song follows the equalizer's own choice.
 */
@Composable
private fun SetEqDialog(r: DialogRequest.SetEq) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val eq by app.repository.equalizer.collectAsState()
    val tracks = remember(r) { r.ids.mapNotNull { library.byId[it] } }
    if (tracks.isEmpty()) { Dialogs.close(); return }
    // What they have now: one preset for all of them, or null when they differ.
    val now = remember(tracks) { tracks.map { (it.raw["equalizer"] as? String).orEmpty().trim() }.distinct().singleOrNull() }
    var choice by remember(r) { mutableStateOf(now) }
    val names = listOf("") + eq.presets.map { it.name }
    AlertDialog(
        onDismissRequest = { Dialogs.close() },
        title = { Text("Set EQ") },
        text = {
            Column {
                Text(r.title, color = Lux.dim, fontSize = 14.sp, maxLines = 2, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(names) { name ->
                        Row(
                            Modifier.fillMaxWidth().clickable { choice = name }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = choice.equals(name, ignoreCase = true), onClick = { choice = name },
                                colors = RadioButtonDefaults.colors(selectedColor = Lux.accent),
                            )
                            Text(
                                name.ifEmpty { "None (follow the equalizer)" },
                                color = if (name.isEmpty()) Lux.dim else Lux.text, fontSize = 16.sp,
                            )
                        }
                    }
                }
                Text(
                    if (tracks.size == 1) "Kept with the song and sent to LuxTunes at the next sync."
                    else "Set on all ${tracks.size} songs and sent to LuxTunes at the next sync.",
                    color = Lux.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = choice != null && choice != now, onClick = {
                val picked = choice ?: return@TextButton
                app.repository.editTags(tracks.map { it.id }, mapOf("equalizer" to picked))
                Dialogs.close()
                Notice.show(if (picked.isEmpty()) "EQ cleared" else "EQ set to $picked")
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { Dialogs.close() }) { Text("Cancel") } },
    )
}

/** From Now Playing and the ⋮ menus: the queue, a new playlist, or one of the phone's playlists. */
@Composable
private fun AddToPlaylistDialog(ids: List<String>) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val tracks = ids.mapNotNull { library.byId[it] }
    val mine = library.playlists.filter { !it.isSmart && library.canEdit(it) }
    AlertDialog(
        onDismissRequest = { Dialogs.close() },
        title = { Text("Add to Playlist") },
        text = {
          // Arrows at the top and bottom while there's more to scroll that way.
          val list = rememberLazyListState()
          Box {
            LazyColumn(Modifier.heightIn(max = 420.dp), state = list) {
                item {
                    PickRow("Play Queue") {
                        app.player.addToQueue(tracks)
                        Dialogs.close()
                        Notice.show("${plural(tracks.size, "song")} added to the queue")
                    }
                }
                item { PickRow("New…") { Dialogs.show(DialogRequest.NewPlaylist(ids)) } }
                items(mine, key = { it.id }) { p ->
                    PickRow(p.name) {
                        // Like LuxTunes: ask before adding songs the playlist already has.
                        val dupes = app.repository.duplicatesIn(p.id, ids)
                        if (dupes > 0) Dialogs.show(DialogRequest.Duplicates(p.id, p.name, ids, dupes))
                        else {
                            app.repository.addToPlaylist(p.id, ids)
                            Dialogs.close()
                            Notice.show("${plural(ids.size, "song")} added to ${p.name}")
                        }
                    }
                }
                if (library.formatVersion < 2 && library.playlists.any { !it.onPhone }) {
                    item {
                        Text(
                            "Playlists from LuxTunes can be changed on the phone with LuxTunes 0.6.0 or newer.",
                            color = Lux.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            if (list.canScrollBackward) ScrollHint(up = true, Modifier.align(Alignment.TopCenter))
            if (list.canScrollForward) ScrollHint(up = false, Modifier.align(Alignment.BottomCenter))
          }
        },
        confirmButton = { TextButton(onClick = { Dialogs.close() }) { Text("Cancel") } },
    )
}

/** "Some of these songs are already in the playlist": Skip (add only the new ones) / Add / Cancel. */
@Composable
private fun DuplicatesDialog(r: DialogRequest.Duplicates) {
    val app = app()
    val all = r.count == r.ids.size
    AlertDialog(
        onDismissRequest = { Dialogs.close() },
        title = { Text("Add to “${r.name}”") },
        text = {
            Text(
                if (r.ids.size == 1) "This song is already in the playlist. Do you want to add it again?"
                else if (all) "These songs are already in the playlist. Do you want to add them again?"
                else "${plural(r.count, "song")} of ${r.ids.size} ${if (r.count == 1) "is" else "are"} already in the playlist. " +
                    "Do you want to add ${if (r.count == 1) "it" else "them"} again, or skip ${if (r.count == 1) "it" else "them"}?"
            )
        },
        confirmButton = {
            TextButton(onClick = {
                app.repository.addToPlaylist(r.playlistId, r.ids)
                Dialogs.close()
                Notice.show("${plural(r.ids.size, "song")} added to ${r.name}")
            }) { Text("Add") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Dialogs.close() }) { Text("Cancel") }
                if (!all) {
                    TextButton(onClick = {
                        val have = app.repository.state.value.playlists.firstOrNull { it.id == r.playlistId }?.trackIds?.toSet().orEmpty()
                        val adding = r.ids.filter { it !in have }.distinct()
                        app.repository.addToPlaylist(r.playlistId, adding)
                        Dialogs.close()
                        Notice.show("${plural(adding.size, "song")} added to ${r.name}")
                    }) { Text("Skip") }
                }
            }
        },
    )
}

@Composable
private fun PickRow(label: String, onClick: () -> Unit) {
    Column {
        Text(
            label, fontSize = 18.sp, color = Lux.text,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        )
        HorizontalDivider(color = Lux.panel)
    }
}

@Composable
private fun NameDialog(title: String, start: String, button: String, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = { Dialogs.close() },
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onDone(name); Dialogs.close() }) { Text(button) }
        },
        dismissButton = { TextButton(onClick = { Dialogs.close() }) { Text("Cancel") } },
    )
}

@Composable
private fun DeletePlaylistDialog(r: DialogRequest.DeletePlaylist) {
    val app = app()
    AlertDialog(
        onDismissRequest = { Dialogs.close() },
        title = { Text("Delete “${r.name}”?") },
        text = {
            Text(
                "The playlist is deleted on this phone. Its songs stay.\n\n" +
                    "If it came from LuxTunes, LuxTunes asks you at the next sync before deleting it there too."
            )
        },
        confirmButton = {
            TextButton(onClick = { app.repository.deletePlaylist(r.id); Dialogs.close() }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { Dialogs.close() }) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(ids: List<String>) {
    val app = app()
    val count = ids.size
    AlertDialog(
        onDismissRequest = { Dialogs.close() },
        title = { Text("Delete ${plural(count, "song")} from this phone?") },
        text = {
            Text(
                "The ${if (count == 1) "file is" else "files are"} deleted from the phone now.\n\n" +
                    "LuxTunes decides what's on the phone: if ${if (count == 1) "this song is" else "these songs are"} " +
                    "still chosen in LuxTunes' sync settings, the next sync copies ${if (count == 1) "it" else "them"} back. " +
                    "Nothing is deleted on the computer, and plays and ratings not yet sent are kept."
            )
        },
        confirmButton = {
            TextButton(onClick = { app.repository.deleteFromPhone(ids); Dialogs.close() }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { Dialogs.close() }) { Text("Cancel") } },
    )
}

/** "2026-10-18T21:04:00Z" -> "2026-10-18 21:04 UTC" */
fun niceDate(iso: String?): String =
    iso?.replace('T', ' ')?.removeSuffix("Z")?.let { if (it.length >= 16) it.substring(0, 16) + " UTC" else it } ?: ""

/** Internal storage or SD card (asked on first start when there's an SD card). */
@Composable
private fun StorageDialog(firstTime: Boolean) {
    val app = app()
    val context = LocalContext.current
    val settings by app.prefs.settings.collectAsState()
    // Each storage, whether it's in the phone, and whether it has a LuxTunes library.
    val roots = remember { LuxFolder.storageRoots(context) }
    val cardIn = roots.size > 1
    fun libraryHere(option: StorageChoice) =
        LuxFolder.libraryOn(if (option == StorageChoice.INTERNAL) roots.take(1) else roots.drop(1)) != null
    var choice by remember {
        mutableStateOf(settings.storage?.takeIf { it == StorageChoice.INTERNAL || cardIn } ?: StorageChoice.INTERNAL)
    }
    AlertDialog(
        onDismissRequest = { if (!firstTime) Dialogs.close() },
        title = { Text("Where is your LuxTunes music?") },
        text = {
            Column {
                Text(
                    "Choose the storage LuxTunes syncs to (the \"Sync to\" choice on the phone's page in LuxTunes).",
                    color = Lux.dim, fontSize = 14.sp,
                )
                for ((option, label) in listOf(StorageChoice.INTERNAL to "Internal storage", StorageChoice.REMOVABLE to "SD card")) {
                    val present = option == StorageChoice.INTERNAL || cardIn
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = present) { choice = option }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == option, onClick = { choice = option }, enabled = present)
                        Column {
                            Text(label, fontSize = 18.sp, color = if (present) Lux.text else Lux.dim)
                            Text(
                                when {
                                    !present -> "Not in the phone"
                                    libraryHere(option) -> "LuxTunes library found"
                                    else -> "No LuxTunes library here yet"
                                },
                                fontSize = 13.sp, color = Lux.dim,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Choosing the same one again just looks for the library again.
                app.prefs.update { it.copy(storage = choice, leftSdCard = false) }
                app.repository.refreshAsync()
                Dialogs.close()
            }) { Text("OK") }
        },
        dismissButton = {
            if (!firstTime) TextButton(onClick = { Dialogs.close() }) { Text("Cancel") }
        },
    )
}

/** A list of choices with radio buttons (the default actions, grid size…). */
@Composable
private fun ChooseDialog(r: DialogRequest.Choose) {
    AlertDialog(
        onDismissRequest = { Dialogs.close() },
        title = { Text(r.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {      // long lists scroll
                r.options.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable { r.onPick(i); Dialogs.close() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == r.selected, onClick = { r.onPick(i); Dialogs.close() })
                        Text(label, fontSize = 18.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { Dialogs.close() }) { Text("Cancel") } },
    )
}

/** A small arrow on a fade at the top or bottom of a list: there's more that way. */
@Composable
private fun ScrollHint(up: Boolean, modifier: Modifier) {
    val solid = Lux.panel
    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Brush.verticalGradient(if (up) listOf(solid, solid.copy(alpha = 0f)) else listOf(solid.copy(alpha = 0f), solid))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (up) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            if (up) "More above" else "More below", tint = Lux.text, modifier = Modifier.size(24.dp),
        )
    }
}
