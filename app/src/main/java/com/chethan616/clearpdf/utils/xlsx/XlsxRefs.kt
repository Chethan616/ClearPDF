package com.chethan616.clearpdf.utils.xlsx

/** 0-based, inclusive cell range. */
data class CellRange(val r1: Int, val c1: Int, val r2: Int, val c2: Int) {
    fun contains(r: Int, c: Int) = r in r1..r2 && c in c1..c2
    val isSingle get() = r1 == r2 && c1 == c2
    fun toRef(): String =
        if (isSingle) XlsxRefs.cellRef(r1, c1) else XlsxRefs.cellRef(r1, c1) + ":" + XlsxRefs.cellRef(r2, c2)

    companion object {
        fun of(r1: Int, c1: Int, r2: Int, c2: Int) = CellRange(minOf(r1, r2), minOf(c1, c2), maxOf(r1, r2), maxOf(c1, c2))
    }
}

/** Which axis a structural edit moves along. */
enum class Axis { ROW, COL }

/**
 * A1-reference helpers, plus the shifting that inserting/deleting rows or columns needs across
 * every place a sheet names its own cells (`sqref`, `ref`, merges, formulas).
 */
object XlsxRefs {
    const val MaxRow = 1_048_575
    const val MaxCol = 16_383

    fun colLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) { sb.insert(0, 'A' + (i % 26)); i = i / 26 - 1 }
        return sb.toString()
    }

    fun colIndex(letters: String): Int {
        var idx = 0
        for (ch in letters) {
            val up = ch.uppercaseChar()
            if (up !in 'A'..'Z') return -1
            idx = idx * 26 + (up - 'A' + 1)
        }
        return idx - 1
    }

    fun cellRef(r: Int, c: Int) = colLetter(c) + (r + 1)

    /** "B7" / "$B$7" → (6, 1); null when malformed. */
    fun parseCell(ref: String): Pair<Int, Int>? {
        val s = ref.replace("$", "")
        var i = 0
        while (i < s.length && s[i].isLetter()) i++
        if (i == 0 || i == s.length) return null
        val c = colIndex(s.substring(0, i))
        val r = s.substring(i).toIntOrNull() ?: return null
        if (c < 0 || r < 1) return null
        return (r - 1) to c
    }

    /** "A1:C3" or "B2" → range; also whole columns "A:C" and rows "2:5". */
    fun parseRange(ref: String): CellRange? {
        val s = ref.substringAfterLast('!').replace("$", "").trim()
        val parts = s.split(':')
        if (parts.size == 1) return parseCell(parts[0])?.let { (r, c) -> CellRange(r, c, r, c) }
        if (parts.size != 2) return null
        val a = parts[0]; val b = parts[1]
        if (a.all { it.isLetter() } && b.all { it.isLetter() }) {
            val c1 = colIndex(a); val c2 = colIndex(b)
            if (c1 < 0 || c2 < 0) return null
            return CellRange.of(0, c1, MaxRow, c2)
        }
        if (a.all { it.isDigit() } && b.all { it.isDigit() }) {
            val r1 = a.toInt() - 1; val r2 = b.toInt() - 1
            return CellRange.of(r1, 0, r2, MaxCol)
        }
        val p1 = parseCell(a) ?: return null
        val p2 = parseCell(b) ?: return null
        return CellRange.of(p1.first, p1.second, p2.first, p2.second)
    }

    fun parseSqref(sqref: String): List<CellRange> =
        sqref.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.mapNotNull { parseRange(it) }

    /**
     * Shift a range for [count] rows/cols inserted (count > 0) or deleted (count < 0) at [at].
     * Returns null when a deletion swallows the whole range.
     */
    fun shiftRange(range: CellRange, axis: Axis, at: Int, count: Int): CellRange? {
        val (lo, hi) = if (axis == Axis.ROW) range.r1 to range.r2 else range.c1 to range.c2
        val max = if (axis == Axis.ROW) MaxRow else MaxCol
        val nl: Int
        val nh: Int
        if (count > 0) {
            // Whole-row / whole-column spans stay whole.
            if (lo == 0 && hi == max) return range
            nl = if (lo >= at) lo + count else lo
            nh = if (hi >= at) (hi + count).coerceAtMost(max) else hi
        } else {
            val n = -count
            val delEnd = at + n - 1
            if (lo >= at && hi <= delEnd) return null
            if (lo == 0 && hi == max) return range
            nl = when {
                lo < at -> lo
                lo <= delEnd -> at
                else -> lo - n
            }
            nh = when {
                hi < at -> hi
                hi <= delEnd -> at - 1
                else -> hi - n
            }
            if (nh < nl) return null
        }
        return if (axis == Axis.ROW) range.copy(r1 = nl, r2 = nh) else range.copy(c1 = nl, c2 = nh)
    }

    /** Shift every range in a space-separated `sqref`; null when none survive. */
    fun shiftSqref(sqref: String, axis: Axis, at: Int, count: Int): String? {
        val out = parseSqref(sqref).mapNotNull { shiftRange(it, axis, at, count) }
        return if (out.isEmpty()) null else out.joinToString(" ") { it.toRef() }
    }

    /**
     * Shift the A1 references inside a formula. References qualified with another sheet are left
     * alone unless [sheetName] matches; unqualified ones move only when [formulaOnSameSheet].
     * A reference a deletion removes becomes `#REF!`, as Excel does.
     */
    fun shiftFormula(
        formula: String,
        sheetName: String,
        formulaOnSameSheet: Boolean,
        axis: Axis,
        at: Int,
        count: Int
    ): String {
        val out = StringBuilder()
        var i = 0
        val n = formula.length
        while (i < n) {
            val ch = formula[i]
            if (ch == '"') {                                   // string literal — copy verbatim
                val start = i
                i++
                while (i < n) {
                    if (formula[i] == '"') {
                        if (i + 1 < n && formula[i + 1] == '"') { i += 2; continue }
                        i++; break
                    }
                    i++
                }
                out.append(formula, start, i)
                continue
            }
            val m = if (i == 0 || !(formula[i - 1].isLetterOrDigit() || formula[i - 1] in "_.$'")) RefToken.matchAt(formula, i) else null
            if (m == null) { out.append(ch); i++; continue }
            val prev = if (m.range.first > 0) formula[m.range.first - 1] else ' '
            val next = if (m.range.last + 1 < n) formula[m.range.last + 1] else ' '
            val token = m.value
            val boundaryOk = !(prev.isLetterOrDigit() || prev == '_' || prev == '.' || prev == '$') &&
                !(next.isLetterOrDigit() || next == '_' || next == '(' || next == '!')
            if (!boundaryOk) {
                out.append(ch)
                i++
                continue
            }
            val bang = token.lastIndexOf('!')
            val qualifier = if (bang >= 0) token.substring(0, bang) else null
            val refPart = if (bang >= 0) token.substring(bang + 1) else token
            val applies = if (qualifier == null) formulaOnSameSheet else {
                qualifier.removeSurrounding("'").replace("''", "'").equals(sheetName, ignoreCase = true)
            }
            if (!applies) {
                out.append(token)
            } else {
                val shifted = shiftRefText(refPart, axis, at, count)
                if (qualifier != null) out.append(qualifier).append('!')
                out.append(shifted)
            }
            i = m.range.last + 1
        }
        return out.toString()
    }

    private val RefToken = Regex(
        "(?:(?:'(?:[^']|'')+'|[A-Za-z_][A-Za-z0-9_.]*)!)?" +
            "(?:\\$?[A-Za-z]{1,3}\\$?[0-9]{1,7}(?::\\$?[A-Za-z]{1,3}\\$?[0-9]{1,7})?" +
            "|\\$?[A-Za-z]{1,3}:\\$?[A-Za-z]{1,3}" +
            "|\\$?[0-9]{1,7}:\\$?[0-9]{1,7})"
    )

    /** One reference ("$A$1", "A1:B2", "A:C", "3:5") shifted, keeping `$` markers. */
    private fun shiftRefText(ref: String, axis: Axis, at: Int, count: Int): String {
        val parts = ref.split(':')
        data class P(val colAbs: Boolean, val col: Int, val rowAbs: Boolean, val row: Int) // -1 = absent
        fun parse(p: String): P? {
            var i = 0
            var colAbs = false
            var rowAbs = false
            if (i < p.length && p[i] == '$') { colAbs = true; i++ }
            val cs = i
            while (i < p.length && p[i].isLetter()) i++
            val letters = p.substring(cs, i)
            if (i < p.length && p[i] == '$') { rowAbs = true; i++ }
            val digits = p.substring(i)
            if (letters.isEmpty() && colAbs && digits.isNotEmpty()) { rowAbs = true; colAbs = false }
            if (digits.isNotEmpty() && !digits.all { it.isDigit() }) return null
            val col = if (letters.isEmpty()) -1 else colIndex(letters)
            val row = if (digits.isEmpty()) -1 else digits.toInt() - 1
            if (col < -1 || col > MaxCol) return null
            return P(colAbs, col, rowAbs, row)
        }
        fun fmt(p: P): String = buildString {
            if (p.col >= 0) { if (p.colAbs) append('$'); append(colLetter(p.col)) }
            if (p.row >= 0) { if (p.rowAbs) append('$'); append(p.row + 1) }
        }
        val ps = parts.map { parse(it) ?: return ref }
        val v = ps.map { if (axis == Axis.ROW) it.row else it.col }
        if (v.any { it < 0 }) return ref       // whole rows when shifting columns, etc.
        val lo = v.first()
        val hi = v.last()
        val range = if (axis == Axis.ROW) CellRange(lo, 0, hi, 0) else CellRange(0, lo, 0, hi)
        val shifted = shiftRange(range, axis, at, count) ?: return "#REF!"
        val nlo = if (axis == Axis.ROW) shifted.r1 else shifted.c1
        val nhi = if (axis == Axis.ROW) shifted.r2 else shifted.c2
        return if (ps.size == 1) {
            fmt(if (axis == Axis.ROW) ps[0].copy(row = nlo) else ps[0].copy(col = nlo))
        } else {
            val a = if (axis == Axis.ROW) ps[0].copy(row = nlo) else ps[0].copy(col = nlo)
            val b = if (axis == Axis.ROW) ps[1].copy(row = nhi) else ps[1].copy(col = nhi)
            fmt(a) + ":" + fmt(b)
        }
    }

    fun escapeXml(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> if (ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r') Unit else sb.append(ch)
            }
        }
        return sb.toString()
    }
}
