package io.github.metricambient.luxtunes.data

import org.json.JSONObject

/**
 * Song details changed on the phone (Get Info → Edit), waiting to go to LuxTunes.
 * `fields` holds library.db columns: text, or a number (Long) for the number ones.
 * The state goes as for playlist edits: new, in changes.json, taken by LuxTunes.
 */
data class TagEdit(
    val id: String,
    val fields: Map<String, Any?>,
    val state: Int = PlaylistEdit.NEW,
)

object TagFields {
    /** The details Edit can change. */
    val TEXT = listOf("name", "artist", "album_artist", "album", "genre", "composer", "grouping", "comments")
    val NUMBERS = listOf("year", "track_number", "track_count", "disc_number", "disc_count", "compilation")
    val ALL = TEXT + NUMBERS

    /** Only known fields, as text or Long. */
    fun clean(fields: Map<String, Any?>): Map<String, Any?> = fields.filterKeys { it in ALL }.mapValues { (k, v) ->
        if (k in NUMBERS) (v as? Number)?.toLong() ?: v?.toString()?.trim()?.toLongOrNull() ?: 0L
        else v?.toString() ?: ""
    }

    fun toJson(fields: Map<String, Any?>): String {
        val o = JSONObject()
        for ((k, v) in clean(fields).toSortedMap()) o.put(k, v)
        return o.toString()
    }

    fun fromJson(text: String?): Map<String, Any?> = try {
        val o = JSONObject(text ?: "{}")
        clean(o.keys().asSequence().associateWith { o.get(it).takeUnless { v -> v == JSONObject.NULL } })
    } catch (e: Exception) {
        emptyMap()
    }

    /** One song's changes for changes.json `edited_tracks`: its ID and the new values. */
    fun entry(e: TagEdit): String {
        val o = JSONObject()
        o.put("id", e.id.filter { it.isLetterOrDigit() })
        for ((k, v) in clean(e.fields).toSortedMap()) o.put(k, v)
        return o.toString()
    }
}
