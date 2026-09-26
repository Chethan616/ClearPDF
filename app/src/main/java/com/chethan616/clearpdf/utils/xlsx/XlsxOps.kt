package com.chethan616.clearpdf.utils.xlsx

/** One cell before/after an edit. `null` = no cell element. */
data class CellChange(val r: Int, val c: Int, val before: CellData?, val after: CellData?) {
    /** Only the `s` attribute differs — the writer then touches nothing else in the element. */
    val styleOnly: Boolean
        get() = before != null && after != null && before.copy(style = after.style) == after
}

/**
 * The edit log. The viewer applies these to the in-memory model (and reverts them for undo); the
 * writer replays the applied ones, in order, onto the original package's XML. Keeping both driven by
 * the same list is what guarantees the saved file is exactly what was on screen.
 */
sealed class EditOp {
    abstract val sheet: Int

    data class SetCells(override val sheet: Int, val changes: List<CellChange>) : EditOp()

    /** [count] > 0 inserts before [at]; < 0 deletes `-count` lines starting at [at]. */
    data class Lines(override val sheet: Int, val axis: Axis, val at: Int, val count: Int) : EditOp() {
        /** Snapshot for undo, filled when first applied. */
        @Transient var snapshot: SheetSnapshot? = null
    }

    /** Column width in characters (Excel units); null restores the default. */
    data class ColWidth(override val sheet: Int, val col: Int, val before: Float?, val after: Float?) : EditOp()
}

/** Structural state of a sheet captured before an insert/delete, for an exact undo. */
class SheetSnapshot(
    val rows: java.util.TreeMap<Int, RowData>,
    val cols: java.util.TreeMap<Int, ColumnInfo>,
    val merges: List<CellRange>,
    val validations: List<Pair<DataValidation, List<CellRange>>>
)

object XlsxEdits {

    fun apply(wb: XlsxWorkbook, op: EditOp) {
        val sheet = wb.sheets.getOrNull(op.sheet) ?: return
        when (op) {
            is EditOp.SetCells -> for (ch in op.changes) put(sheet, ch.r, ch.c, ch.after)
            is EditOp.Lines -> {
                op.snapshot = snapshot(sheet)
                shiftLines(sheet, op.axis, op.at, op.count)
            }
            is EditOp.ColWidth -> setWidth(sheet, op.col, op.after)
        }
    }

    fun revert(wb: XlsxWorkbook, op: EditOp) {
        val sheet = wb.sheets.getOrNull(op.sheet) ?: return
        when (op) {
            is EditOp.SetCells -> for (ch in op.changes.asReversed()) put(sheet, ch.r, ch.c, ch.before)
            is EditOp.Lines -> op.snapshot?.let { restore(sheet, it) }
            is EditOp.ColWidth -> setWidth(sheet, op.col, op.before)
        }
    }

    private fun setWidth(sheet: XlsxSheet, col: Int, w: Float?) {
        val old = sheet.cols[col]
        sheet.cols[col] = ColumnInfo(w, old?.hidden ?: false, old?.style ?: -1)
    }

    private fun put(sheet: XlsxSheet, r: Int, c: Int, d: CellData?) {
        if (d == null) {
            sheet.rows[r]?.cells?.remove(c)
        } else {
            sheet.rows.getOrPut(r) { RowData() }.cells[c] = d
        }
    }

    private fun snapshot(sheet: XlsxSheet): SheetSnapshot {
        val rows = java.util.TreeMap<Int, RowData>()
        for ((k, v) in sheet.rows) rows[k] = copyRow(v)
        return SheetSnapshot(
            rows, java.util.TreeMap(sheet.cols), sheet.merges.toList(),
            sheet.validations.map { it to it.ranges }
        )
    }

    private fun copyRow(v: RowData) = RowData(v.heightPt, v.hidden, v.style).also { it.cells.putAll(v.cells) }

    private fun restore(sheet: XlsxSheet, s: SheetSnapshot) {
        sheet.rows.clear()
        for ((k, v) in s.rows) sheet.rows[k] = copyRow(v)
        sheet.cols.clear(); sheet.cols.putAll(s.cols)
        sheet.merges.clear(); sheet.merges.addAll(s.merges)
        sheet.validations.clear()
        for ((v, ranges) in s.validations) { v.ranges = ranges; sheet.validations.add(v) }
    }

    /** Move a line index for an insert/delete; -1 when the line is deleted. */
    fun shiftIndex(i: Int, at: Int, count: Int): Int = when {
        i < at -> i
        count > 0 -> i + count
        i < at - count -> -1
        else -> i + count
    }

    private fun shiftLines(sheet: XlsxSheet, axis: Axis, at: Int, count: Int) {
        if (axis == Axis.ROW) {
            val old = java.util.TreeMap(sheet.rows)
            sheet.rows.clear()
            for ((r, row) in old) {
                val nr = shiftIndex(r, at, count)
                if (nr >= 0) sheet.rows[nr] = row
            }
        } else {
            for ((r, row) in sheet.rows.entries.toList()) {
                if (row.cells.isEmpty()) continue
                val old = java.util.TreeMap(row.cells)
                val nr = RowData(row.heightPt, row.hidden, row.style)
                for ((c, d) in old) {
                    val nc = shiftIndex(c, at, count)
                    if (nc >= 0) nr.cells[nc] = d
                }
                sheet.rows[r] = nr
            }
            val oldCols = java.util.TreeMap(sheet.cols)
            sheet.cols.clear()
            for ((c, info) in oldCols) {
                val nc = shiftIndex(c, at, count)
                if (nc >= 0) sheet.cols[nc] = info
            }
        }
        val merges = sheet.merges.mapNotNull { XlsxRefs.shiftRange(it, axis, at, count) }.filter { !it.isSingle }
        sheet.merges.clear(); sheet.merges.addAll(merges)
        val it = sheet.validations.iterator()
        while (it.hasNext()) {
            val v = it.next()
            val nr = v.ranges.mapNotNull { r -> XlsxRefs.shiftRange(r, axis, at, count) }
            if (nr.isEmpty()) it.remove() else v.ranges = nr
        }
    }
}
