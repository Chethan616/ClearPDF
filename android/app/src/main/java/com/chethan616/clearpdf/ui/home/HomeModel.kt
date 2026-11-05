package com.chethan616.clearpdf.ui.home

import com.chethan616.clearpdf.R
import com.chethan616.clearpdf.text
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** The places the dock goes. */
enum class HomeTab { Home, Files, Tools }

/** A file as the lists show it, wherever it comes from (recent, starred, on this device). */
data class FileRow(
    val key: String,
    val name: String,
    /** A local path to draw its thumbnail from, if there is one. */
    val thumbPath: String?,
    val location: String,
    val date: Long,
    val size: Long,
    val starred: Boolean,
    val open: () -> Unit,
)

/** A tool that starts by choosing a PDF. */
enum class FileTool { Edit, ReadAloud, FillSign, Organize, Compress, Export, Protect, Comment, Crop, Ocr }

/** "Just now", the time if today, "Yesterday", else a medium date. */
fun whenLabel(t: Long): String {
    val now = System.currentTimeMillis()
    if (now - t < 60_000) return text(R.string.home_just_now)
    val cal = Calendar.getInstance().apply { timeInMillis = t }
    val today = Calendar.getInstance()
    val sameDay = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t))
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) return text(R.string.common_yesterday)
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
}

/** Which day bucket [t] falls in: 0 today, 1 yesterday, 2 this week, 3 earlier. */
fun dayBucket(t: Long): Int {
    val day = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return when {
        t >= day -> 0
        t >= day - 86_400_000L -> 1
        t >= day - 6 * 86_400_000L -> 2
        else -> 3
    }
}

/**
 * A file's name as the screens show it: no ".pdf", underscores as spaces, and a break allowed after
 * each hyphen, so "report_2026-09-22.pdf" wraps between words instead of inside a number.
 */
fun displayName(name: String): String =
    name.removeSuffix(".pdf").removeSuffix(".PDF").replace('_', ' ').replace("-", "-\u200B")
