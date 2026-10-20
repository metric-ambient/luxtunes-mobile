package io.github.metricambient.luxtunes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.github.metricambient.luxtunes.data.Smart
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * New or edit a smart playlist, laid out like LuxTunes' editor: match all/any of
 * the rules, limit, "match only checked items", live updating. Nested groups and
 * rules only LuxTunes knows are kept as they are (shown, removable, not editable here).
 */
@Composable
fun SmartEditorScreen(nav: NavHostController, playlistId: String?) {
    val app = app()
    val library by app.repository.state.collectAsState()
    val existing = playlistId?.let { id -> library.playlists.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: "untitled playlist") }
    var p by remember { mutableStateOf(existing?.smart ?: Smart.defaultPlaylist()) }

    val playlistChoices: List<Pair<String, String>> = remember(library) {
        library.playlists.filter { it.id != playlistId }.map { it.id.uppercase() to it.name } +
            library.references.map { it.key to it.value }
    }
    fun playlistName(id: String) = playlistChoices.firstOrNull { it.first == id.uppercase() }?.second ?: "(another playlist)"

    fun setRule(i: Int, item: Smart.Item) {
        p = p.copy(rules = p.rules.copy(rules = p.rules.rules.toMutableList().also { it[i] = item }))
    }

    // How many songs on the phone it would hold now (a preview).
    val matching = remember(p, library) {
        val songs = library.allTracks.map { t ->
            object : Smart.Song {
                override val id = t.id
                override fun value(column: String) = t.smartValue(column)
            }
        }
        val byId = library.playlists.associateBy { it.id.uppercase() }
        Smart.evaluate(p, songs, System.currentTimeMillis() / 1000, 0) { id ->
            byId[id]?.trackIds?.toSet() ?: emptySet()
        }.size
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel", tint = Lux.text) }
            Text(
                if (existing == null) "New Smart Playlist" else "Edit Smart Playlist",
                color = Lux.text, fontSize = 20.sp, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                if (existing == null) app.repository.createSmartPlaylist(name, p)
                else app.repository.setSmartRules(existing.id, name, p)
                nav.popBackStack()
            }) { Text("SAVE", color = Lux.accent) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tickbox(p.info.useRules) { p = p.copy(info = p.info.copy(useRules = it)) }
                Text("Match", color = Lux.text)
                Picker(if (p.rules.matchAny) "any" else "all", listOf("all", "any")) { i ->
                    p = p.copy(rules = p.rules.copy(matchAny = i == 1))
                }
                Text("of the following rules:", color = Lux.text)
            }
            p.rules.rules.forEachIndexed { i, item ->
                HorizontalDivider(color = Lux.panel)
                RuleRow(
                    item = item,
                    playlistChoices = playlistChoices,
                    playlistName = ::playlistName,
                    onChange = { setRule(i, it) },
                    onRemove = { p = p.copy(rules = p.rules.copy(rules = p.rules.rules.filterIndexed { j, _ -> j != i })) },
                )
            }
            HorizontalDivider(color = Lux.panel)
            TextButton(onClick = {
                p = p.copy(rules = p.rules.copy(rules = p.rules.rules + Smart.Rule(0x04, Smart.S_CONTAINS)))
            }) {
                Icon(Icons.Filled.Add, null, tint = Lux.accent)
                Text("Add rule", color = Lux.accent)
            }
            HorizontalDivider(color = Lux.panel)

            // Limit to 25 items selected by random
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tickbox(p.info.limit) { p = p.copy(info = p.info.copy(limit = it)) }
                Text("Limit to", color = Lux.text)
                NumberField(p.info.limitValue.toString(), Modifier.width(80.dp)) { v ->
                    v.toLongOrNull()?.let { p = p.copy(info = p.info.copy(limitValue = it)) }
                }
                Picker(
                    Smart.LIMIT_TYPES.firstOrNull { it.first == p.info.limitType }?.second ?: "items",
                    Smart.LIMIT_TYPES.map { it.second },
                ) { i -> p = p.copy(info = p.info.copy(limitType = Smart.LIMIT_TYPES[i].first)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 48.dp)) {
                Text("selected by", color = Lux.text)
                val current = Smart.LIMIT_SORTS.firstOrNull { it.first == p.info.limitSort && it.second == p.info.reverse }
                Picker(current?.third ?: "random", Smart.LIMIT_SORTS.map { it.third }) { i ->
                    val (code, reverse, _) = Smart.LIMIT_SORTS[i]
                    p = p.copy(info = p.info.copy(limitSort = code, reverse = reverse))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tickbox(p.info.checkedOnly) { p = p.copy(info = p.info.copy(checkedOnly = it)) }
                Text("Match only checked items", color = Lux.text)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tickbox(p.info.live) { p = p.copy(info = p.info.copy(live = it)) }
                Text("Live updating", color = Lux.text)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "${plural(matching, "song")} on this phone match now. LuxTunes checks the rules against " +
                    "your whole library at the next sync, so there it may hold more.",
                color = Lux.dim, fontSize = 13.sp,
            )
            if (library.formatVersion < 2) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your LuxTunes doesn't take playlists back yet (it needs version 0.6.0 or newer). " +
                        "This playlist stays on the phone until it does.",
                    color = Lux.dim, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RuleRow(
    item: Smart.Item,
    playlistChoices: List<Pair<String, String>>,
    playlistName: (String) -> String,
    onChange: (Smart.Item) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            if (item !is Smart.Rule || !item.known) {
                // A nested group, or a rule only LuxTunes can edit: kept exactly.
                Text(Smart.describe(item, playlistName), color = Lux.dim, fontSize = 14.sp)
                Text("Edit this one in LuxTunes", color = Lux.dim, fontSize = 12.sp)
            } else {
                RuleEditor(item, playlistChoices, onChange)
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.RemoveCircleOutline, "Remove rule", tint = Lux.icon) }
    }
}

private val FIELD_LIST = Smart.FIELDS.values.filter { it.column != null || it.kind == Smart.Kind.PLAYLIST }.sortedBy { it.label }

/** A fresh rule for a field: its first operator, sensible empty values. */
private fun fresh(field: Smart.Field): Smart.Rule {
    val op = Smart.OPERATORS.getValue(field.kind).first().first
    return when (field.kind) {
        Smart.Kind.BOOL, Smart.Kind.MEDIA -> Smart.Rule(field.id, op, value = 1)
        Smart.Kind.DATE -> Smart.Rule(field.id, op, value = System.currentTimeMillis() / 1000 / 86400 * 86400)
        else -> Smart.Rule(field.id, op)
    }
}

@Composable
private fun RuleEditor(rule: Smart.Rule, playlistChoices: List<Pair<String, String>>, onChange: (Smart.Rule) -> Unit) {
    val field = Smart.FIELDS.getValue(rule.field)
    val ops = Smart.OPERATORS.getValue(field.kind)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Picker(field.label, FIELD_LIST.map { it.label }) { i ->
            val f = FIELD_LIST[i]
            if (f.id != rule.field) onChange(fresh(f))
        }
        Picker(Smart.opLabel(field.kind, rule.op), ops.map { it.second }) { i ->
            val op = ops[i].first
            val range = (op and Smart.NEGATE.inv()) == Smart.RANGE
            onChange(rule.copy(op = op, value2 = if (range) rule.value2 ?: rule.value else null))
        }
    }
    val range = (rule.op and Smart.NEGATE.inv()) == Smart.RANGE
    // The key makes the text boxes start over when the rule's field or operator changes.
    val key = "${rule.field}:${rule.op}"
    when (field.kind) {
        Smart.Kind.TEXT -> OutlinedTextField(
            value = rule.text, onValueChange = { onChange(rule.copy(text = it)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Smart.Kind.BOOL -> {}
        Smart.Kind.MEDIA -> Picker(
            Smart.MEDIA_KINDS.firstOrNull { it.first.toLong() == rule.value }?.second ?: "Music",
            Smart.MEDIA_KINDS.map { it.second },
        ) { i -> onChange(rule.copy(value = Smart.MEDIA_KINDS[i].first.toLong())) }
        Smart.Kind.PLAYLIST -> Picker(
            playlistChoices.firstOrNull { it.first == rule.playlist.uppercase() }?.second ?: "Choose a playlist",
            playlistChoices.map { it.second },
        ) { i -> onChange(rule.copy(playlist = playlistChoices[i].first)) }
        Smart.Kind.RATING -> Row(verticalAlignment = Alignment.CenterVertically) {
            StarRating(rule.value.toInt(), false, onRate = { onChange(rule.copy(value = it.toLong())) }, size = 32.dp)
            if (range) {
                Text(" to ", color = Lux.text)
                StarRating((rule.value2 ?: rule.value).toInt(), false, onRate = { onChange(rule.copy(value2 = it.toLong())) }, size = 32.dp)
            }
        }
        Smart.Kind.DATE -> if (rule.op == Smart.IN_LAST || rule.op == Smart.NOT_IN_LAST) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(rule.amount.toString(), Modifier.width(80.dp), key) { v ->
                    v.toLongOrNull()?.let { onChange(rule.copy(amount = it)) }
                }
                Picker(
                    Smart.DATE_UNITS.firstOrNull { it.first == rule.units }?.second ?: "weeks",
                    Smart.DATE_UNITS.map { it.second },
                ) { i -> onChange(rule.copy(units = Smart.DATE_UNITS[i].first)) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DateField(rule.value, key + ":a") { onChange(rule.copy(value = it)) }
                if (range) {
                    Text(" to ", color = Lux.text)
                    DateField(rule.value2 ?: rule.value, key + ":b") { onChange(rule.copy(value2 = it)) }
                }
            }
        }
        else -> {
            // Numbers; time as minutes:seconds, size in MB.
            val show: (Long) -> String = when (field.kind) {
                Smart.Kind.TIME -> { v -> "${v / 60000}:%02d".format((v / 1000) % 60) }
                Smart.Kind.SIZE -> { v -> "${v / 1048576}" }
                else -> { v -> "$v" }
            }
            val parse: (String) -> Long? = when (field.kind) {
                Smart.Kind.TIME -> { t ->
                    val parts = t.split(":")
                    if (parts.size == 2) parts[0].toLongOrNull()?.let { m -> parts[1].toLongOrNull()?.let { s -> (m * 60 + s) * 1000 } }
                    else t.toLongOrNull()?.let { it * 60000 }
                }
                Smart.Kind.SIZE -> { t -> t.toLongOrNull()?.let { it * 1048576 } }
                else -> { t -> t.toLongOrNull() }
            }
            val unit = when (field.kind) { Smart.Kind.TIME -> "m:ss"; Smart.Kind.SIZE -> "MB"; else -> "" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(show(rule.value), Modifier.width(110.dp), key + ":a") { t -> parse(t)?.let { onChange(rule.copy(value = it)) } }
                if (range) {
                    Text(" to ", color = Lux.text)
                    NumberField(show(rule.value2 ?: rule.value), Modifier.width(110.dp), key + ":b") { t ->
                        parse(t)?.let { onChange(rule.copy(value2 = it)) }
                    }
                }
                if (unit.isNotEmpty()) Text(" $unit", color = Lux.dim)
            }
        }
    }
}

/** A text box that keeps what's typed and reports it; the caller keeps only valid values. */
@Composable
private fun NumberField(start: String, modifier: Modifier, key: String = "", onText: (String) -> Unit) {
    var text by remember(key) { mutableStateOf(start) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onText(it) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** A date as YYYY-MM-DD (UTC, like LuxTunes' dates). */
@Composable
private fun DateField(unixSeconds: Long, key: String, onValue: (Long) -> Unit) {
    var text by remember(key) { mutableStateOf(Smart.dateText(unixSeconds)) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            runCatching { LocalDate.parse(it.trim()).atStartOfDay().toEpochSecond(ZoneOffset.UTC) }.getOrNull()?.let(onValue)
        },
        singleLine = true,
        label = { Text("YYYY-MM-DD") },
        modifier = Modifier.width(150.dp),
    )
}

/** A button showing the current choice; tapping it lists the choices. */
@Composable
private fun Picker(current: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clickable { open = true }.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(current, color = Lux.accent, fontSize = 16.sp)
            Icon(Icons.Filled.ArrowDropDown, null, tint = Lux.accent, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, label ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(i) })
            }
        }
    }
}

@Composable
private fun Tickbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Checkbox(checked = checked, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = Lux.accent))
}
