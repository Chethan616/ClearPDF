package com.chethan616.clearpdf.engine

/**
 * The kinds of file Artefact opens: PDFs, word-processor documents read by the Word engine
 * (.docx, .doc, .odt, .rtf), presentations read by the slides engine (.pptx) and workbooks read
 * by the sheets engine (.xlsx, .xlsb, .ods, .csv). The engine tells them apart by their bytes; these lists are for the
 * system's pickers, file lists and names, which go by type and extension.
 */
object DocumentTypes {
    const val PDF = "application/pdf"

    /** Word documents, as pickers and other apps type them. */
    val WORD = arrayOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
        "application/vnd.ms-word.document.macroEnabled.12",
        "application/msword",
        "application/vnd.oasis.opendocument.text",
        "application/rtf",
        "text/rtf",
    )

    /** Presentations. */
    val SLIDES = arrayOf(
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
        "application/vnd.openxmlformats-officedocument.presentationml.template",
        "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
    )

    /** Workbooks and tables. */
    val SHEETS = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
        "application/vnd.ms-excel.sheet.macroEnabled.12",
        "application/vnd.ms-excel.sheet.binary.macroEnabled.12",
        "application/vnd.oasis.opendocument.spreadsheet",
        "text/csv",
        "text/comma-separated-values",
        "text/tab-separated-values",
    )

    /** Everything the viewer opens. */
    val OPENABLE = arrayOf(PDF) + WORD + SLIDES + SHEETS

    private val WORD_EXTENSIONS = setOf("docx", "docm", "dotx", "dotm", "doc", "dot", "odt", "ott", "rtf")
    private val SLIDE_EXTENSIONS = setOf("pptx", "pptm", "ppsx", "ppsm", "potx", "potm")
    private val SHEET_EXTENSIONS = setOf("xlsx", "xlsm", "xltx", "xltm", "xlsb", "ods", "csv", "tsv")

    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase()

    fun isWordName(name: String) = extension(name) in WORD_EXTENSIONS

    /** An office document's name (Word, slides or a workbook): it keeps its own extension. */
    fun isOfficeName(name: String) = extension(name).let { it in WORD_EXTENSIONS || it in SLIDE_EXTENSIONS || it in SHEET_EXTENSIONS }

    /** Whether a file of type [mime] named [name] is one the viewer opens. */
    fun isOpenable(mime: String?, name: String) =
        mime == PDF || mime in WORD || mime in SLIDES || mime in SHEETS || extension(name) == "pdf" || isOfficeName(name)

    /** A file name without its extension, as documents are titled (`Report.docx` → `Report`). */
    fun title(name: String): String =
        if (isOfficeName(name)) name.substringBeforeLast('.') else name.removeSuffix(".pdf").removeSuffix(".PDF")

    /** The type to hand another app a file named [name] as. */
    fun mimeOf(name: String): String = when (extension(name)) {
        "docx" -> WORD[0]
        "dotx" -> WORD[1]
        "docm" -> WORD[2]
        "doc", "dot" -> "application/msword"
        "odt", "ott" -> "application/vnd.oasis.opendocument.text"
        "rtf" -> "application/rtf"
        "pptx", "pptm" -> SLIDES[0]
        "ppsx", "ppsm" -> SLIDES[1]
        "potx", "potm" -> SLIDES[2]
        "xlsx", "xltx" -> SHEETS[0]
        "xlsm", "xltm" -> SHEETS[2]
        "xlsb" -> SHEETS[3]
        "ods" -> SHEETS[4]
        "csv" -> "text/csv"
        "tsv" -> "text/tab-separated-values"
        else -> PDF
    }
}
