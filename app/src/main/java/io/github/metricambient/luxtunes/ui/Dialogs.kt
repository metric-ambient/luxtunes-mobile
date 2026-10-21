package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.clickable
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
import io.github.metricambient.luxtunes.data.StorageChoice
import io.github.metricambient.luxtunes.data.Track
import java.io.File

/** The pop-ups any screen can ask for. Only one shows at a time. */
sealed interface DialogRequest {
    data class AddToPlaylist(val ids: List<String>) : DialogRequest
    data class Duplicates(val playlistId: String, val name: String, val ids: List<String>, val count: Int) : DialogRequest
    data class NewPlaylist(val ids: List<String>) : DialogRequest
    data class RenamePlaylist(val id: String, val name: String) : DialogRequest
    data class DeletePlaylist(val id: String, val name: String) : DialogRequest
    data class Info(val id: String) : DialogRequest
    data class Delete(val ids: List<String>) : DialogRequest
    data class ChooseStorage(val firstTime: Boolean) : DialogRequest
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
        is DialogRequest.NewPlaylist -> NameDialog("New playlist", "", "Create") { name ->
            app.repository.createPlaylist(name, r.ids)
        }
        is DialogRequest.RenamePlaylist -> NameDialog("Rename playlist", r.name, "Rename") { name ->
            app.repository.renamePlaylist(r.id, name)
        }
        is DialogRequest.DeletePlaylist -> DeletePlaylistDialog(r)
        is DialogRequest.Info -> InfoDialog(r.id)
        is DialogRequest.Delete -> DeleteDialog(r.ids)
        is DialogRequest.ChooseStorage -> StorageDialog(r.firstTime)
        is DialogRequest.Choose -> ChooseDialog(r)
    }
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
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { PickRow("Play Queue") { app.player.addToQueue(tracks); Dialogs.close() } }
                item { PickRow("New…") { Dialogs.show(DialogRequest.NewPlaylist(ids)) } }
                items(mine, key = { it.id }) { p ->
                    PickRow(p.name) {
                        // Like LuxTunes: ask before adding songs the playlist already has.
                        val dupes = app.repository.duplicatesIn(p.id, ids)
                        if (dupes > 0) Dialogs.show(DialogRequest.Duplicates(p.id, p.name, ids, dupes))
                        else { app.repository.addToPlaylist(p.id, ids); Dialogs.close() }
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
            TextButton(onClick = { app.repository.addToPlaylist(r.playlistId, r.ids); Dialogs.close() }) { Text("Add") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { Dialogs.close() }) { Text("Cancel") }
                if (!all) {
                    TextButton(onClick = {
                        val have = app.repository.state.value.playlists.firstOrNull { it.id == r.playlistId }?.trackIds?.toSet().orEmpty()
                        app.repository.addToPlaylist(r.playlistId, r.ids.filter { it !in have }.distinct())
                        Dialogs.close()
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

/** Get Info: everything the app knows about a song. */
@Composable
private fun InfoDialog(id: String) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val t: Track = library.byId[id] ?: run { Dialogs.close(); return }
    val file = library.folder?.let { File(it, t.path) }
    val lines = listOf(
        "Name" to t.name,
        "Artist" to t.artist,
        "Album Artist" to t.albumArtist,
        "Album" to t.album,
        "Genre" to t.genre,
        "Year" to (if (t.year > 0) "${t.year}" else ""),
        "Track" to (if (t.trackNumber > 0) "${t.trackNumber}" else ""),
        "Disc" to (if (t.discNumber > 0) "${t.discNumber}" else ""),
        "Time" to formatTime(t.totalTime),
        "Rating" to ratingText(t),
        "Plays" to "${t.playCount}",
        "Last Played" to niceDate(t.playDate),
        "Skips" to "${t.skipCount}",
        "Last Skipped" to niceDate(t.skipDate),
        "Kind" to t.path.substringAfterLast('.', "").uppercase(),
        "Size" to (file?.takeIf { it.isFile }?.let { "%.1f MB".format(it.length() / 1_048_576.0) } ?: "file missing"),
        "File" to t.path,
    ).filter { it.second.isNotEmpty() }
    AlertDialog(
        onDismissRequest = { Dialogs.close() },
        title = { Text("Get Info") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for ((label, value) in lines) {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(label, color = Lux.dim, fontSize = 14.sp, modifier = Modifier.weight(0.38f))
                        Text(value, color = Lux.text, fontSize = 14.sp, modifier = Modifier.weight(0.62f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { Dialogs.close() }) { Text("OK") } },
    )
}

private fun ratingText(t: Track): String {
    if (t.rating <= 0) return "none"
    val stars = "★".repeat((t.rating + 10) / 20)
    return if (t.ratingComputed) "$stars (album)" else stars
}

/** "2026-10-18T21:04:00Z" -> "2026-10-18 21:04 UTC" */
fun niceDate(iso: String?): String =
    iso?.replace('T', ' ')?.removeSuffix("Z")?.let { if (it.length >= 16) it.substring(0, 16) + " UTC" else it } ?: ""

/** Internal storage or SD card (asked on first start when there's an SD card). */
@Composable
private fun StorageDialog(firstTime: Boolean) {
    val app = app()
    val settings by app.prefs.settings.collectAsState()
    var choice by remember { mutableStateOf(settings.storage ?: StorageChoice.INTERNAL) }
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
                    Row(
                        Modifier.fillMaxWidth().clickable { choice = option }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == option, onClick = { choice = option })
                        Text(label, fontSize = 18.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                app.prefs.update { it.copy(storage = choice) }
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
