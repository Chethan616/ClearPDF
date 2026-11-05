package com.chethan616.clearpdf.notes

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** What a note is: words, a checklist, or a voice memo. */
enum class NoteKind { Text, List, Voice }

@Immutable
data class CheckItem(val id: String, val text: String, val done: Boolean)

/**
 * A note on the desk. Text notes have a [title] and a [body] (light Markdown: `**bold**`, `# `),
 * lists their [items], voice notes their [audio] with a [wave] to draw. Any of them can hold
 * [images]. Media files live in the notes folder under their own names.
 */
@Immutable
data class Note(
    val id: String,
    val kind: NoteKind,
    val title: String = "",
    val body: String = "",
    val items: List<CheckItem> = emptyList(),
    val images: List<String> = emptyList(),
    val audio: String? = null,
    val durationMs: Long = 0,
    val wave: List<Float> = emptyList(),
    val created: Long = System.currentTimeMillis(),
    val updated: Long = created,
    val pinned: Boolean = false,
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank() && items.none { it.text.isNotBlank() } && images.isEmpty() && audio == null
}

/**
 * The notes, kept in memory as Compose state and written to `files/notes/notes.json` (a moment
 * after each change, atomically). Media sits beside it in `files/notes/media`.
 */
class Notes(private val context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "notes").apply { mkdirs() }
    private val media = File(dir, "media").apply { mkdirs() }
    private val store = File(dir, "notes.json")

    var all by mutableStateOf<List<Note>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    private var saveJob: Job? = null

    init {
        scope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { read() }.getOrDefault(emptyList()) }
            all = list
            loaded = true
        }
    }

    /** True when the notes were never written (a fresh install): the welcome notes go in. */
    val isFresh: Boolean get() = !store.exists()

    fun get(id: String): Note? = all.firstOrNull { it.id == id }

    fun file(name: String): File = File(media, name)

    /** A new file name in the media folder. */
    fun newMedia(ext: String): File = File(media, "${UUID.randomUUID()}.$ext")

    fun create(kind: NoteKind, title: String = "", body: String = "", items: List<CheckItem> = emptyList()): Note {
        val n = Note(UUID.randomUUID().toString(), kind, title, body, items)
        all = listOf(n) + all
        save()
        return n
    }

    fun put(note: Note) {
        all = all.map { if (it.id == note.id) note.copy(updated = System.currentTimeMillis()) else it }
        save()
    }

    fun add(note: Note) {
        all = listOf(note) + all.filter { it.id != note.id }
        save()
    }

    fun toggle(noteId: String, itemId: String) {
        val n = get(noteId) ?: return
        put(n.copy(items = n.items.map { if (it.id == itemId) it.copy(done = !it.done) else it }))
    }

    fun delete(id: String) {
        val n = get(id) ?: return
        all = all.filter { it.id != id }
        save()
        scope.launch(Dispatchers.IO) {
            n.images.forEach { file(it).delete() }
            n.audio?.let { file(it).delete() }
        }
    }

    /** Drops notes left empty (a new note closed without a word in it). */
    fun prune(id: String) {
        if (get(id)?.isEmpty == true) delete(id)
    }

    /** Copy a picked picture into the notes folder; its file name, or null when it couldn't be read. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val out = newMedia("jpg")
            context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            out.name
        }.getOrNull()
    }

    private fun save() {
        saveJob?.cancel()
        val snapshot = all
        saveJob = scope.launch(Dispatchers.IO) {
            delay(250)
            runCatching { write(snapshot) }
        }
    }

    private fun write(list: List<Note>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(dir, "notes.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(store)) {
            store.delete()
            tmp.renameTo(store)
        }
    }

    private fun read(): List<Note> {
        if (!store.exists()) return emptyList()
        val arr = JSONArray(store.readText())
        return (0 until arr.length()).mapNotNull { i -> runCatching { arr.getJSONObject(i).toNote() }.getOrNull() }
    }
}

private fun Note.toJson() = JSONObject().apply {
    put("id", id)
    put("kind", kind.name)
    put("title", title)
    put("body", body)
    put("items", JSONArray().apply { items.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) } })
    put("images", JSONArray(images))
    audio?.let { put("audio", it) }
    put("duration", durationMs)
    put("wave", JSONArray().apply { wave.forEach { put(it.toDouble()) } })
    put("created", created)
    put("updated", updated)
    put("pinned", pinned)
}

private fun JSONObject.toNote(): Note {
    val items = optJSONArray("items")?.let { a ->
        (0 until a.length()).map { i -> a.getJSONObject(i).let { CheckItem(it.optString("id", UUID.randomUUID().toString()), it.optString("text"), it.optBoolean("done")) } }
    }.orEmpty()
    val images = optJSONArray("images")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    val wave = optJSONArray("wave")?.let { a -> (0 until a.length()).map { a.getDouble(it).toFloat() } }.orEmpty()
    return Note(
        id = getString("id"),
        kind = runCatching { NoteKind.valueOf(optString("kind")) }.getOrDefault(NoteKind.Text),
        title = optString("title"),
        body = optString("body"),
        items = items,
        images = images,
        audio = optString("audio").takeIf { it.isNotEmpty() },
        durationMs = optLong("duration"),
        wave = wave,
        created = optLong("created", System.currentTimeMillis()),
        updated = optLong("updated", System.currentTimeMillis()),
        pinned = optBoolean("pinned"),
    )
}

/** A new checklist item. */
fun checkItem(text: String, done: Boolean = false) = CheckItem(UUID.randomUUID().toString(), text, done)

/** [levels] (one per 50 ms) squeezed to [bars] for a card's waveform, each 0.12..1. */
fun waveOf(levels: List<Float>, bars: Int = 48): List<Float> {
    if (levels.isEmpty()) return List(bars) { 0.2f }
    val peak = levels.max().coerceAtLeast(0.02f)
    return List(bars) { i ->
        val from = i * levels.size / bars
        val to = ((i + 1) * levels.size / bars).coerceAtLeast(from + 1).coerceAtMost(levels.size)
        val v = levels.subList(from.coerceAtMost(levels.size - 1), to).max() / peak
        (0.12f + 0.88f * v).coerceIn(0.12f, 1f)
    }
}
