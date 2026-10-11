package com.kyant.pdfcore.text

import java.text.Normalizer

/**
 * Right-to-left text (Arabic, Hebrew…) comes out of a PDF in *visual* order: glyphs left to right,
 * as painted. Selecting and copying it that way pastes every word backwards (#72). This turns a
 * line's visual order into the logical (reading) order apps expect, the way PDF readers do:
 *
 * - the line's direction is the majority of its strong letters;
 * - it is split into runs of right-to-left and left-to-right text, and the spaces and punctuation
 *   between them join the neighbouring direction (or the line's, between two different ones);
 * - for a right-to-left line the runs are read right to left; inside a right-to-left run the
 *   characters are reversed, and mirrored brackets swap; left-to-right runs (numbers, Latin words)
 *   keep their order;
 * - Arabic presentation forms (the shaped glyph code points some PDFs use) are normalized back to
 *   the plain letters people type.
 *
 * Text without right-to-left letters is returned unchanged.
 */
object PdfBidi {

    fun isRtl(c: Char): Boolean = c in '֐'..'ࣿ' || c in 'יִ'..'﷿' || c in 'ﹰ'..'﻿'

    fun hasRtl(s: CharSequence): Boolean = s.any { isRtl(it) }

    /**
     * The strings to look for in a line's (visual-order) text when searching for [query]: the
     * query itself, plus its visual form when it has right-to-left letters (the reordering is its
     * own inverse for a line).
     */
    fun searchForms(query: String): List<String> {
        if (!hasRtl(query)) return listOf(query)
        val visual = query.split('\n').joinToString("\n") { lineToLogical(it) }
        return if (visual == query) listOf(query) else listOf(query, visual)
    }

    /** Visual → logical order, line by line. */
    fun visualToLogical(s: String): String {
        if (!hasRtl(s)) return s
        return s.split('\n').joinToString("\n") { lineToLogical(it) }.let(::normalizeArabic)
    }

    private enum class Dir { RTL, LTR, NEUTRAL }

    private fun dirOf(c: Char): Dir = when {
        isRtl(c) && (Character.isLetter(c) || Character.getType(c) == Character.NON_SPACING_MARK.toInt()) -> Dir.RTL
        Character.isLetterOrDigit(c) -> Dir.LTR
        else -> Dir.NEUTRAL
    }

    private fun lineToLogical(line: String): String {
        if (!hasRtl(line)) return line
        val dirs = line.map(::dirOf)
        val rtlCount = dirs.count { it == Dir.RTL }
        val ltrCount = line.indices.count { dirs[it] == Dir.LTR && line[it].isLetter() }
        val base = if (rtlCount >= ltrCount) Dir.RTL else Dir.LTR

        // Resolve neutrals: between two runs of the same direction they take it, otherwise the base.
        val resolved = dirs.toMutableList()
        var i = 0
        while (i < resolved.size) {
            if (resolved[i] != Dir.NEUTRAL) { i++; continue }
            var j = i
            while (j < resolved.size && resolved[j] == Dir.NEUTRAL) j++
            val before = if (i > 0) resolved[i - 1] else base
            val after = if (j < resolved.size) resolved[j] else base
            val d = if (before == after) before else base
            for (k in i until j) resolved[k] = d
            i = j
        }

        // Runs of one direction, in visual order.
        val runs = ArrayList<Pair<Dir, String>>()
        var start = 0
        for (k in 1..line.length) {
            if (k == line.length || resolved[k] != resolved[start]) {
                runs += resolved[start] to line.substring(start, k)
                start = k
            }
        }
        val ordered = if (base == Dir.RTL) runs.asReversed() else runs
        return ordered.joinToString("") { (d, text) -> if (d == Dir.RTL) mirror(text.reversed()) else text }
    }

    private fun mirror(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                '(' -> ')'; ')' -> '('
                '[' -> ']'; ']' -> '['
                '{' -> '}'; '}' -> '{'
                '<' -> '>'; '>' -> '<'
                '«' -> '»'; '»' -> '«'
                else -> c
            }
        )
    }

    /** Arabic presentation forms (U+FB50–FDFF, U+FE70–FEFF) back to plain letters. */
    private fun normalizeArabic(s: String): String {
        if (s.none { it in 'ﭐ'..'﷿' || it in 'ﹰ'..'﻿' }) return s
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿') sb.append(Normalizer.normalize(c.toString(), Normalizer.Form.NFKC))
            else sb.append(c)
        }
        return sb.toString()
    }
}
