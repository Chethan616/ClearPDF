package com.chethan616.clearpdf.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Page bookmarks are stored separately from recent-file pins and reading progress. */
object PdfPageBookmarksManager {
    private const val PREFS_NAME = "clearpdf_page_bookmarks"
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(uri: Uri) = uri.toString()

    fun get(context: Context, uri: Uri): List<Int> = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(uri), null) ?: return emptyList()
        json.decodeFromString<List<Int>>(raw).distinct().sorted()
    }.getOrDefault(emptyList())

    fun toggle(context: Context, uri: Uri, page: Int): List<Int> {
        val updated = get(context, uri).toMutableSet().apply {
            if (!add(page)) remove(page)
        }.sorted()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(key(uri), json.encodeToString(updated))
            .apply()
        return updated
    }
}
