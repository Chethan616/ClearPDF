package com.chethan616.clearpdf.utils

import java.text.DecimalFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Applies a workbook's number formats to raw cell values.
 *
 * Everything in an .xlsx sheet is stored unformatted: a date is a plain `<v>46267</v>` and the only
 * thing that makes it a date is the `numFmtId` its style points at. A reader that ignores styles —
 * as this app's did — shows the serial, so `02-09-2026` reads as `46267` and the column looks like
 * corrupt data rather than a date. This resolves the format code for a cell and renders the value
 * the way Excel would.
 *
 * Deliberately hand-written: the alternative is pulling in POI's OOXML half (several MB of jars plus
 * the XmlBeans/curvesapi transitive tail) to format one column, and the app already ships its own
 * pull-parser for these files.
 */
internal object ExcelCellFormat {

    /**
     * The built-in format ids. Excel never writes these into `styles.xml` — a cell just points at
     * id 14 and every reader is expected to already know it means a short date. Ids not listed here
     * are either locale/currency variants we render as plain numbers or genuinely unused.
     */
    private val BuiltIn: Map<Int, String> = mapOf(
        0 to "General", 1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00",
        9 to "0%", 10 to "0.00%", 11 to "0.00E+00",
        14 to "dd-mm-yyyy", 15 to "d-mmm-yy", 16 to "d-mmm", 17 to "mmm-yy",
        18 to "h:mm AM/PM", 19 to "h:mm:ss AM/PM", 20 to "h:mm", 21 to "h:mm:ss",
        22 to "dd-mm-yyyy h:mm",
        37 to "#,##0", 38 to "#,##0", 39 to "#,##0.00", 40 to "#,##0.00",
        45 to "mm:ss", 46 to "h:mm:ss", 47 to "mm:ss.0",
        48 to "##0.0E+0", 49 to "@"
    )

    /** `numFmtId` 14 is "short date" in the *user's* locale, so it gets the device's pattern. */
    private val ShortDatePattern: String by lazy {
        runCatching {
            val fmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, Locale.getDefault())
            (fmt as? java.text.SimpleDateFormat)?.toPattern()
                ?.replace('M', 'm')   // Excel codes are lower-case; the renderer below expects that
                ?.replace('E', 'd')
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "dd-mm-yyyy"
    }

    /**
     * `cellXfs` index → format code. Built once per workbook from `xl/styles.xml`; an empty list
     * (no styles part, or an unreadable one) simply means every value renders raw, which is the old
     * behaviour and never worse than it.
     */
    fun formatCodesFor(numFmtIdByStyle: List<Int>, customCodes: Map<Int, String>): List<String> =
        numFmtIdByStyle.map { id ->
            customCodes[id] ?: if (id == 14) ShortDatePattern else (BuiltIn[id] ?: "General")
        }

    /**
     * Render [raw] (the literal `<v>` text of a numeric cell) through [code].
     *
     * Returns [raw] untouched for anything this doesn't understand — a blank/General/text format, a
     * value that isn't a number, or a format code that fails to compile. A wrong-looking number is
     * a bug; a *silently invented* one would be worse, so every uncertain path falls back to what
     * the file literally says.
     */
    fun apply(raw: String, code: String?): String {
        if (code.isNullOrBlank() || code.equals("General", true) || code == "@") return raw
        val value = raw.trim().toDoubleOrNull() ?: return raw

        // "positive;negative;zero;text" — pick the section that applies, then drop the colour and
        // condition brackets (`[Red]`, `[<=100]`) that only affect styling we don't reproduce.
        val sections = splitSections(code)
        val section = when {
            value < 0 && sections.size >= 2 -> sections[1]
            value == 0.0 && sections.size >= 3 -> sections[2]
            else -> sections[0]
        }
        val body = section.replace(Regex("\\[(?!h+]|hh+])[^]]*]", RegexOption.IGNORE_CASE), "")
        if (body.isBlank()) return raw

        return runCatching {
            if (isDateTime(body)) formatDateTime(value, body) else formatNumber(value, body)
        }.getOrDefault(raw)
    }

    /** Split on `;` while honouring quoted literals and backslash escapes. */
    private fun splitSections(code: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        var quoted = false
        while (i < code.length) {
            val c = code[i]
            when {
                c == '\\' && i + 1 < code.length -> { sb.append(c).append(code[i + 1]); i++ }
                c == '"' -> { quoted = !quoted; sb.append(c) }
                c == ';' && !quoted -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    /** A format is a date/time one if any y/m/d/h/s appears outside quotes and escapes. */
    private fun isDateTime(code: String): Boolean {
        var i = 0
        var quoted = false
        while (i < code.length) {
            val c = code[i]
            when {
                c == '\\' -> i++
                c == '"' -> quoted = !quoted
                !quoted && c.lowercaseChar() in "ymdhs" -> return true
            }
            i++
        }
        return false
    }

    // ── Date / time ─────────────────────────────────────────────────────────────

    private val MonthsShort = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MonthsLong = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val DaysShort = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val DaysLong = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    private fun formatDateTime(serial: Double, code: String): String {
        val cal = calendarFor(serial)
        val tokens = tokenize(code)
        val hasAmPm = tokens.any { it.kind == 'a' }
        val sb = StringBuilder()

        for ((index, t) in tokens.withIndex()) {
            when (t.kind) {
                'l' -> sb.append(t.literal)
                'y' -> {
                    val y = cal.get(Calendar.YEAR)
                    sb.append(if (t.run >= 3) y.toString() else (y % 100).toString().padStart(2, '0'))
                }
                'm' -> {
                    // The one genuinely ambiguous code in the whole spec: `m` is a month unless it
                    // sits next to an hour or a second, where it means minutes.
                    if (isMinute(tokens, index)) {
                        sb.append(pad(cal.get(Calendar.MINUTE), t.run))
                    } else when {
                        t.run >= 4 -> sb.append(MonthsLong[cal.get(Calendar.MONTH)])
                        t.run == 3 -> sb.append(MonthsShort[cal.get(Calendar.MONTH)])
                        else -> sb.append(pad(cal.get(Calendar.MONTH) + 1, t.run))
                    }
                }
                'd' -> when {
                    t.run >= 4 -> sb.append(DaysLong[cal.get(Calendar.DAY_OF_WEEK) - 1])
                    t.run == 3 -> sb.append(DaysShort[cal.get(Calendar.DAY_OF_WEEK) - 1])
                    else -> sb.append(pad(cal.get(Calendar.DAY_OF_MONTH), t.run))
                }
                'h' -> {
                    val h24 = cal.get(Calendar.HOUR_OF_DAY)
                    val h = if (hasAmPm) (h24 % 12).let { if (it == 0) 12 else it } else h24
                    sb.append(pad(h, t.run))
                }
                's' -> sb.append(pad(cal.get(Calendar.SECOND), t.run))
                'a' -> sb.append(if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM")
            }
        }
        return sb.toString().trim()
    }

    /** Excel's epoch, including the deliberate 1900 leap-year bug it inherited from Lotus 1-2-3. */
    private fun calendarFor(serial: Double): Calendar {
        val days = Math.floor(serial).toInt()
        // Serial 1 is 1900-01-01 and serial 60 is 1900-02-29 — a day that did not exist, which Excel
        // still counts so that Lotus-era files keep working. Everything from serial 61 on is one day
        // ahead of a real calendar, so 1899-12-30 is the epoch there; below the phantom day the
        // epoch is effectively 1899-12-31.
        //
        // Serial 60 itself has no representable answer: no calendar has a 29 Feb 1900. It lands on
        // 28 Feb 1900 here, which is what POI and most other readers also do, and it only ever comes
        // up in a file that stores that impossible date.
        val offset = if (days < 60) days + 1 else days
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US)
        cal.clear()
        cal.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
        cal.add(Calendar.DATE, offset)
        val secondsInDay = Math.round((serial - days) * 86400.0).toInt()
        cal.add(Calendar.SECOND, secondsInDay)
        return cal
    }

    private fun pad(value: Int, run: Int): String =
        if (run >= 2) value.toString().padStart(2, '0') else value.toString()

    private data class Token(val kind: Char, val run: Int = 1, val literal: String = "")

    private fun tokenize(code: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '\\' && i + 1 < code.length -> { out.add(Token('l', literal = code[i + 1].toString())); i += 2 }
                c == '"' -> {
                    val end = code.indexOf('"', i + 1)
                    val stop = if (end == -1) code.length else end
                    out.add(Token('l', literal = code.substring(i + 1, stop)))
                    i = stop + 1
                }
                code.startsWith("AM/PM", i, ignoreCase = true) -> { out.add(Token('a')); i += 5 }
                code.startsWith("A/P", i, ignoreCase = true) -> { out.add(Token('a')); i += 3 }
                c == '[' -> { // `[h]` / `[mm]` elapsed-time brackets: treat as the bare code
                    val end = code.indexOf(']', i)
                    val stop = if (end == -1) code.length else end
                    val inner = code.substring(i + 1, stop)
                    if (inner.isNotEmpty() && inner[0].lowercaseChar() in "hms") {
                        out.add(Token(inner[0].lowercaseChar(), inner.length))
                    }
                    i = stop + 1
                }
                c.lowercaseChar() in "ymdhs" -> {
                    val kind = c.lowercaseChar()
                    var run = 0
                    while (i < code.length && code[i].lowercaseChar() == kind) { run++; i++ }
                    out.add(Token(kind, run))
                }
                else -> { out.add(Token('l', literal = c.toString())); i++ }
            }
        }
        return out
    }

    /** `m` is minutes when the nearest time-ish neighbour is an hour before it or a second after. */
    private fun isMinute(tokens: List<Token>, index: Int): Boolean {
        for (i in index - 1 downTo 0) {
            val k = tokens[i].kind
            if (k == 'l') continue
            if (k == 'h') return true
            break
        }
        for (i in index + 1 until tokens.size) {
            val k = tokens[i].kind
            if (k == 'l') continue
            if (k == 's') return true
            break
        }
        return false
    }

    // ── Numeric ─────────────────────────────────────────────────────────────────

    /**
     * Excel and [DecimalFormat] share enough pattern syntax (`#`, `0`, `,`, `.`, `%`, `E`) that the
     * cheapest correct thing is to hand the code straight over, once the Excel-only decorations are
     * translated: `\x` and `"x"` literals become the `'x'` DecimalFormat spells them with.
     */
    private fun formatNumber(value: Double, code: String): String {
        val pattern = StringBuilder()
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '\\' && i + 1 < code.length -> { pattern.append('\'').append(code[i + 1]).append('\''); i += 2 }
                c == '"' -> {
                    val end = code.indexOf('"', i + 1)
                    val stop = if (end == -1) code.length else end
                    pattern.append('\'').append(code, i + 1, stop).append('\'')
                    i = stop + 1
                }
                c == '_' -> i += 2               // "width of the next character" padding — no analogue
                c == '*' -> i += 2               // fill-repeat — likewise
                c == '?' -> { pattern.append('#'); i++ }
                else -> { pattern.append(c); i++ }
            }
        }
        val text = pattern.toString()
        if (text.none { it == '#' || it == '0' }) return trimTrailingZeros(value)
        return DecimalFormat(text).format(if (value < 0) -value else value).let { if (value < 0) "-$it" else it }
    }

    /** `3.0` came out of a spreadsheet as an integer; show it as one. */
    private fun trimTrailingZeros(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString()
        else value.toString()
}
