package com.chethan616.clearpdf.notes

import android.content.res.Resources
import com.chethan616.clearpdf.R
import java.time.LocalDate

/** A day's thought: a short line to lead with, and a few words under it (`**bold**` marks the heart of it). */
class Thought(val quote: String, val reflection: String)

/** Today as a day number, the key a thought note is filed under. */
fun today(): Long = LocalDate.now().toEpochDay()

/** The thought for [day]: one a day, in the app's language, round the list. */
fun thoughtFor(res: Resources, day: Long = today()): Thought {
    val quotes = res.getStringArray(R.array.thought_quotes)
    val reflections = res.getStringArray(R.array.thought_reflections)
    val i = Math.floorMod(day, quotes.size.toLong()).toInt()
    return Thought(quotes[i], reflections[i.coerceAtMost(reflections.lastIndex)])
}
