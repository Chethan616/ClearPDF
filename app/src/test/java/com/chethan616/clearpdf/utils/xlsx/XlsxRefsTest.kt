package com.chethan616.clearpdf.utils.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XlsxRefsTest {

    @Test fun parseAndFormat() {
        assertEquals(6 to 1, XlsxRefs.parseCell("B7"))
        assertEquals(6 to 1, XlsxRefs.parseCell("\$B\$7"))
        assertEquals("AA10", XlsxRefs.cellRef(9, 26))
        assertEquals(CellRange(0, 0, 2, 2), XlsxRefs.parseRange("A1:C3"))
        assertEquals(CellRange(0, 2, XlsxRefs.MaxRow, 3), XlsxRefs.parseRange("C:D"))
    }

    @Test fun insertRowsShiftsRangesBelowAndGrowsSpanning() {
        assertEquals(CellRange(5, 0, 6, 1), XlsxRefs.shiftRange(CellRange(3, 0, 4, 1), Axis.ROW, 2, 2))
        assertEquals(CellRange(1, 0, 6, 1), XlsxRefs.shiftRange(CellRange(1, 0, 4, 1), Axis.ROW, 2, 2))
        assertEquals(CellRange(0, 0, 1, 1), XlsxRefs.shiftRange(CellRange(0, 0, 1, 1), Axis.ROW, 2, 2))
    }

    @Test fun deleteRowsShrinksOrRemoves() {
        assertNull(XlsxRefs.shiftRange(CellRange(2, 0, 3, 0), Axis.ROW, 2, -2))
        assertEquals(CellRange(1, 0, 2, 0), XlsxRefs.shiftRange(CellRange(1, 0, 5, 0), Axis.ROW, 2, -3))
        assertEquals(CellRange(1, 0, 4, 0), XlsxRefs.shiftRange(CellRange(3, 0, 6, 0), Axis.ROW, 1, -2))
    }

    @Test fun sqrefShift() {
        assertEquals("A4 C6:D8", XlsxRefs.shiftSqref("A3 C5:D7", Axis.ROW, 1, 1))
        assertNull(XlsxRefs.shiftSqref("B2", Axis.COL, 1, -1))
        assertEquals("B2:B3", XlsxRefs.shiftSqref("C2:C3", Axis.COL, 0, -1))
    }

    @Test fun formulaShiftSameSheet() {
        assertEquals("SUM(A1:A6)+B7", XlsxRefs.shiftFormula("SUM(A1:A5)+B6", "Sheet1", true, Axis.ROW, 2, 1))
        assertEquals("\$A\$1+\$B\$8", XlsxRefs.shiftFormula("\$A\$1+\$B\$7", "Sheet1", true, Axis.ROW, 3, 1))
        assertEquals("A1+#REF!", XlsxRefs.shiftFormula("A1+A3", "Sheet1", true, Axis.ROW, 2, -1))
        assertEquals("C1*2", XlsxRefs.shiftFormula("B1*2", "S", true, Axis.COL, 0, 1))
    }

    @Test fun formulaShiftLeavesFunctionsStringsAndOtherSheets() {
        assertEquals("LOG10(A3)", XlsxRefs.shiftFormula("LOG10(A2)", "S", true, Axis.ROW, 0, 1))
        assertEquals("\"A1\"&A3", XlsxRefs.shiftFormula("\"A1\"&A2", "S", true, Axis.ROW, 0, 1))
        assertEquals("Other!A2+A3", XlsxRefs.shiftFormula("Other!A2+A2", "S", true, Axis.ROW, 0, 1))
        assertEquals("'My Sheet'!A3+A2", XlsxRefs.shiftFormula("'My Sheet'!A2+A2", "My Sheet", false, Axis.ROW, 0, 1))
    }

    @Test fun shiftIndex() {
        assertEquals(1, XlsxEdits.shiftIndex(1, 2, 3))
        assertEquals(5, XlsxEdits.shiftIndex(2, 2, 3))
        assertEquals(-1, XlsxEdits.shiftIndex(3, 2, -2))
        assertEquals(2, XlsxEdits.shiftIndex(4, 2, -2))
    }
}
