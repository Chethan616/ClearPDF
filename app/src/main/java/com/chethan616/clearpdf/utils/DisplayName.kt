package com.chethan616.clearpdf.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** The file's real name from its provider (a picked uri's last path segment is an id like "msf:49918"). */
fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i != -1 && c.moveToFirst()) c.getString(i)?.takeIf { it.isNotBlank() } else null
    }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { !it.contains(':') }
