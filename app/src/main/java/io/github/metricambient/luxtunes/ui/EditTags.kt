package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.metricambient.luxtunes.data.Track
import io.github.metricambient.luxtunes.data.UNKNOWN_GENRE
import io.github.metricambient.luxtunes.data.addPills
import io.github.metricambient.luxtunes.data.joinGenres
import io.github.metricambient.luxtunes.data.joinNames
import io.github.metricambient.luxtunes.data.pillInput
import io.github.metricambient.luxtunes.data.sortText
import io.github.metricambient.luxtunes.data.splitGenres
import io.github.metricambient.luxtunes.data.splitNames
import io.github.metricambient.luxtunes.data.suggestNames
import io.github.metricambient.luxtunes.data.suggestSplit
import java.io.File

// ---- Get Info ------------------------------------------------------------------------------

/** A song's text detail as LuxTunes has it ("" if none). */
private fun textOf(t: Track, column: String): String = t.raw[column]?.toString().orEmpty()

/** A song's number detail (0 if none). */
private fun numberOf(t: Track, column: String): Long = when (val v = t.raw[column]) {
    is Number -> v.toLong()
    is String -> v.trim().toLongOrNull() ?: 0L
    else -> 0L
}

/** "3 of 12", "3", or "" */
private fun countText(n: Long, of: Long): String = when {
    n > 0 && of > 0 -> "$n of $of"
    n > 0 -> "$n"
    else -> ""
}

/** The one value all the songs share, or "Mixed". */
private fun shared(tracks: List<Track>, value: (Track) -> String): String =
    tracks.map(value).distinct().singleOrNull() ?: "Mixed"

/** Get Info: everything the app knows about a song, or what a group of songs has in common. Edit changes it. */
@Composable
internal fun InfoDialog(ids: List<String>) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val tracks = ids.mapNotNull { library.byId[it] }
    if (tracks.isEmpty()) { Dialogs.close(); return }
    val folder = library.folder
    val lines = if (tracks.size == 1) {
        val t = tracks[0]
        val file = folder?.let { File(it, t.path) }
        listOf(
            "Name" to t.name,
            "Artist" to t.artist,
            "Artists" to t.artists.joinToString("; "),
            "Featured Artist" to t.featured.joinToString("; "),
            "Album Artist" to t.albumArtist,
            "Album" to t.album,
            "Genre" to t.genre,
            "Composer" to textOf(t, "composer"),
            "Grouping" to textOf(t, "grouping"),
            "Year" to (if (t.year > 0) "${t.year}" else ""),
            "Track" to countText(numberOf(t, "track_number"), numberOf(t, "track_count")),
            "Disc" to countText(numberOf(t, "disc_number"), numberOf(t, "disc_count")),
            "Compilation" to (if (numberOf(t, "compilation") != 0L) "Yes" else ""),
            "Comments" to textOf(t, "comments"),
            "Equalizer" to textOf(t, "equalizer"),
            "Time" to formatTime(t.totalTime),
            "Rating" to ratingText(t),
            "Plays" to "${t.playCount}",
            "Last Played" to niceDate(t.playDate),
            "Skips" to "${t.skipCount}",
            "Last Skipped" to niceDate(t.skipDate),
            "Kind" to t.path.substringAfterLast('.', "").uppercase(),
            "Size" to (file?.takeIf { it.isFile }?.let { "%.1f MB".format(it.length() / 1_048_576.0) } ?: "file missing"),
            "File" to t.path,
        )
    } else {
        val bytes = tracks.sumOf { t -> folder?.let { File(it, t.path).length() } ?: 0L }
        listOf(
            "Songs" to "${tracks.size}",
            "Artist" to shared(tracks) { it.artist },
            "Album Artist" to shared(tracks) { it.albumArtist },
            "Album" to shared(tracks) { it.album },
            "Genre" to shared(tracks) { it.genre },
            "Year" to shared(tracks) { if (it.year > 0) "${it.year}" else "" },
            "Time" to formatLength(tracks.sumOf { it.totalTime }),
            "Size" to "%.1f MB".format(bytes / 1_048_576.0),
        )
    }.filter { it.second.isNotEmpty() }
    AlertDialog(
        onDismissRequest = { Dialogs.close() },
        title = { Text(if (tracks.size == 1) "Get Info" else "Get Info: ${plural(tracks.size, "song")}") },
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
        dismissButton = {
            TextButton(onClick = { Dialogs.show(DialogRequest.EditTags(tracks.map { it.id })) }) { Text("Edit") }
        },
    )
}

private fun ratingText(t: Track): String {
    if (t.rating <= 0) return "none"
    val stars = "★".repeat((t.rating + 10) / 20)
    return if (t.ratingComputed) "$stars (album)" else stars
}

// ---- Edit ----------------------------------------------------------------------------------

/**
 * Edit a song's details, or several songs' at once (then only the details you
 * change are changed, and Name and Track number aren't offered). The changes show
 * straight away and go to LuxTunes at the next sync, which writes them into the files.
 */
@Composable
internal fun EditTagsDialog(ids: List<String>) {
    val app = app()
    val library by app.repository.state.collectAsState()
    // The songs as they were when the dialog opened.
    val tracks = remember(ids) { ids.mapNotNull { library.byId[it] } }
    if (tracks.isEmpty()) { Dialogs.close(); return }
    val single = tracks.size == 1

    // What each detail starts as: the shared value, or null when the songs differ.
    val start: Map<String, String?> = remember(tracks) {
        fun common(value: (Track) -> String) = tracks.map(value).distinct().singleOrNull()
        fun num(t: Track, c: String) = numberOf(t, c).let { if (it > 0) "$it" else "" }
        buildMap<String, String?> {
            for (c in TEXT_FIELDS.map { it.first }) put(c, common { textOf(it, c) })
            for (c in NUMBER_FIELDS) put(c, common { num(it, c) })
        }
    }
    val values = remember(tracks) {
        mutableStateMapOf<String, String>().apply { start.forEach { (k, v) -> put(k, if (k in PILL_FIELDS) "" else v ?: "") } }
    }
    // Names already in the library, for suggestions.
    val artistNames = remember(library) {
        (library.trackArtists.map { it.name } + library.artists.map { it.name } + library.tracks.flatMap { it.featured })
            .distinctBy { it.lowercase() }.sortedBy { sortText(it) }
    }
    val genreNames = remember(library) { library.genres.map { it.name }.filter { it != UNKNOWN_GENRE } }
    // One song by "Lümen & Grayfield" without Artists, both artists of their own: suggest them.
    val suggested = remember(tracks) {
        if (!single || !start["artists"].isNullOrBlank()) emptyList()
        else suggestSplit(start["artist"], (library.trackArtists.map { it.name } + library.artists.map { it.name }).map { it.lowercase() }.toSet())
    }
    // Genre, Artists and Featured Artist as pills; what's typed in their boxes stays in `values`.
    val pills = remember(tracks) {
        mutableStateMapOf<String, List<String>>().apply {
            for (c in PILL_FIELDS) put(c, when {
                c == "artists" && suggested.isNotEmpty() -> suggested
                c == "genre" -> splitGenres(start[c])
                else -> splitNames(start[c])
            })
        }
    }
    val edited = remember(tracks) { mutableStateMapOf<String, Boolean>() }      // several songs: pill boxes changed
    /** A pill box's value as saved: its pills and whatever is still typed in the box. */
    fun pillValue(c: String): String {
        val all = addPills(pills[c].orEmpty(), listOf(values[c].orEmpty()), c == "genre")
        return if (c == "genre") joinGenres(all) else joinNames(all)
    }
    val startCompilation = remember(tracks) { tracks.map { numberOf(it, "compilation") != 0L }.distinct().singleOrNull() }
    var compilation by remember(tracks) { mutableStateOf(startCompilation) }

    /** Only what was changed: a field the songs differ on changes once something is typed in it. */
    fun changes(): Map<String, Any?> = buildMap<String, Any?> {
        for (c in PILL_FIELDS) {
            val now = pillValue(c)
            val was = start[c]?.let { if (c == "genre") joinGenres(splitGenres(it)) else joinNames(splitNames(it)) }
            val changed = if (single) now != was.orEmpty() else edited[c] == true || values[c].orEmpty().isNotBlank()
            if (changed) put(c, now)
        }
        for ((c, v) in values) {
            if (c in PILL_FIELDS) continue
            if (!single && (c == "name" || c == "track_number")) continue
            val was = start[c]
            val changed = if (was == null) v.isNotEmpty() else v != was
            if (changed) put(c, when (c) {
                in NUMBER_FIELDS -> v.toLongOrNull() ?: 0L
                else -> v.trim()
            })
        }
        if (compilation != null && compilation != startCompilation) put("compilation", if (compilation == true) 1L else 0L)
    }
    val canSave = changes().isNotEmpty() && !(single && values["name"].isNullOrBlank())

    @Composable
    fun field(column: String, label: String, modifier: Modifier, number: Boolean) = TagField(
        value = values[column].orEmpty(),
        onChange = { values[column] = if (number) it.filter(Char::isDigit).take(4) else it },
        label = label, mixed = start[column] == null, number = number, modifier = modifier,
    )

    AlertDialog(
        onDismissRequest = { Dialogs.close() },
        title = { Text(if (single) "Edit Details" else "Edit ${plural(tracks.size, "song")}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for ((c, label) in TEXT_FIELDS) {
                    if (!single && c == "name") continue
                    if (c == "comments") continue
                    if (c in PILL_FIELDS) {
                        val genre = c == "genre"
                        val shownSuggested = c == "artists" && suggested.isNotEmpty() && pills[c] == suggested && values[c].isNullOrEmpty()
                        PillField(
                            label = label,
                            pills = pills[c].orEmpty(),
                            text = values[c].orEmpty(),
                            genre = genre,
                            mixed = start[c] == null && edited[c] != true,
                            hint = if (c == "artists") "Only for a song by several artists" else null,
                            note = if (shownSuggested) "Suggested from the Artist (${if (suggested.size == 2) "both have" else "each has"} songs " +
                                "of their own). Save keeps it; remove the names to leave them out." else null,
                            choices = if (genre) genreNames else artistNames,
                        ) { newPills, newText ->
                            if (newPills != pills[c]) edited[c] = true
                            pills[c] = newPills
                            values[c] = newText
                        }
                    } else {
                        field(c, label, Modifier.fillMaxWidth(), number = false)
                    }
                }
                field("year", "Year", Modifier.fillMaxWidth(0.5f), number = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (single) field("track_number", "Track", Modifier.weight(1f), number = true)
                    field("track_count", if (single) "of" else "Tracks on album", Modifier.weight(1f), number = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    field("disc_number", "Disc", Modifier.weight(1f), number = true)
                    field("disc_count", "of", Modifier.weight(1f), number = true)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { compilation = !(compilation ?: false) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TriStateCheckbox(
                        state = when (compilation) {
                            true -> ToggleableState.On
                            false -> ToggleableState.Off
                            null -> ToggleableState.Indeterminate
                        },
                        onClick = { compilation = !(compilation ?: false) },
                        colors = CheckboxDefaults.colors(checkedColor = Lux.accent, checkmarkColor = Lux.onAccent),
                    )
                    Text("Part of a compilation", color = Lux.text, fontSize = 16.sp)
                }
                field("comments", "Comments", Modifier.fillMaxWidth(), number = false)
                Text(
                    "The changes show here now and go to LuxTunes on your computer at the next sync, " +
                        "which writes them into the song files." +
                        if (library.takesTagEdits) "" else "\n\nThe LuxTunes on your computer doesn't take " +
                            "edits from the phone yet. They're kept here and sent again at each sync until it does.",
                    color = Lux.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                app.repository.editTags(tracks.map { it.id }, changes())
                Dialogs.close()
                Notice.show(if (single) "Details saved" else "${plural(tracks.size, "song")} changed")
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { Dialogs.close() }) { Text("Cancel") } },
    )
}

/** One box in Edit; "Mixed" shows in it when the songs differ. */
@Composable
private fun TagField(value: String, onChange: (String) -> Unit, label: String, mixed: Boolean, number: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (mixed) Text("Mixed", color = Lux.faint) },
        singleLine = label != "Comments",
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier.padding(vertical = 2.dp),
    )
}

/** The text details Edit offers, in order, with their labels (library.db columns). */
private val TEXT_FIELDS = listOf(
    "name" to "Name",
    "artist" to "Artist",
    "artists" to "Artists",
    "featured_artist" to "Featured Artist",
    "album_artist" to "Album Artist",
    "album" to "Album",
    "genre" to "Genre",
    "composer" to "Composer",
    "grouping" to "Grouping",
    "comments" to "Comments",
)

/** Shown as pills: names (split at ";") and genres (at "," and ";"). */
private val PILL_FIELDS = setOf("artists", "featured_artist", "genre")

/**
 * A pill box (Genre, Artists, Featured Artist): each name a pill with ✕ to remove it. Type a
 * name and press Enter, pick a suggestion or choose from the library (▾); typing ";" (or ","
 * for genres) also makes a pill, and pasting "A; B" makes two. Tapping a pill puts it back in
 * the box to change; Backspace in an empty box does that with the last one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillField(
    label: String,
    pills: List<String>,
    text: String,
    genre: Boolean,
    mixed: Boolean,
    hint: String?,
    note: String?,
    choices: List<String>,
    onChange: (pills: List<String>, text: String) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    fun add(names: List<String>, rest: String = "") = onChange(addPills(pills, names, genre), rest)
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (pills.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pills.forEachIndexed { i, p ->
                    val others = pills.filterIndexed { j, _ -> j != i }
                    InputChip(
                        selected = false,
                        // Back into the box to change (what was typed there becomes a pill first).
                        onClick = { onChange(addPills(others, listOf(text), genre), p) },
                        label = { Text(p) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, "Remove $p", Modifier.size(18.dp).clickable { onChange(others, text) })
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                val (done, rest) = pillInput(text, new, genre)
                if (done.isEmpty()) onChange(pills, rest) else add(done, rest)
            },
            label = { Text(label) },
            placeholder = { if (mixed) Text("Mixed", color = Lux.faint) else if (hint != null) Text(hint, color = Lux.faint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) add(listOf(text)) }),
            trailingIcon = {
                Box {
                    IconButton(onClick = { choosing = true }) { Icon(Icons.Filled.ArrowDropDown, "Choose from the library") }
                    DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                        val taken = pills.map { it.lowercase() }.toSet()
                        val listed = if (text.isNotBlank()) suggestNames(text, choices, taken, limit = 150)
                            else choices.filter { it.lowercase() !in taken }.take(150)
                        if (listed.isEmpty()) DropdownMenuItem(text = { Text("Nothing to add", color = Lux.dim) }, onClick = { choosing = false })
                        for (name in listed) DropdownMenuItem(text = { Text(name) }, onClick = { add(listOf(name)); choosing = false })
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                // Backspace in an empty box: the last pill back in it, to change.
                if (e.key == Key.Backspace && e.type == KeyEventType.KeyDown && text.isEmpty() && pills.isNotEmpty()) {
                    onChange(pills.dropLast(1), pills.last()); true
                } else false
            },
        )
        val suggestions = if (text.isBlank()) emptyList() else suggestNames(text, choices, pills.map { it.lowercase() }.toSet(), limit = 6)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (name in suggestions) SuggestionChip(onClick = { add(listOf(name)) }, label = { Text(name) })
            }
        }
        if (note != null) Text(note, color = Lux.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

private val NUMBER_FIELDS = listOf("year", "track_number", "track_count", "disc_number", "disc_count")
