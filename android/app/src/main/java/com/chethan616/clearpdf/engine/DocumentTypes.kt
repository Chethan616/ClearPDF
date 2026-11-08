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
        // PowerPoint 97-2003: its slides' text (Artefact's own reader)
        "application/vnd.ms-powerpoint",
    )

    /** Workbooks and tables. */
    val SHEETS = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
        "application/vnd.ms-excel.sheet.macroEnabled.12",
        "application/vnd.ms-excel.sheet.binary.macroEnabled.12",
        "application/vnd.ms-excel",
        "application/vnd.oasis.opendocument.spreadsheet",
        "text/csv",
        "text/comma-separated-values",
        "text/tab-separated-values",
    )

    /** Plain text and Markdown, read by the Word engine. */
    val TEXT = arrayOf("text/plain", "text/markdown", "text/x-markdown")

    /** Everything the viewer opens. */
    val OPENABLE = arrayOf(PDF) + WORD + SLIDES + SHEETS + TEXT

    private val WORD_EXTENSIONS = setOf("docx", "docm", "dotx", "dotm", "doc", "dot", "odt", "ott", "rtf")
    private val SLIDE_EXTENSIONS = setOf("pptx", "pptm", "ppsx", "ppsm", "potx", "potm", "ppt", "pps", "pot")
    private val SHEET_EXTENSIONS = setOf("xlsx", "xlsm", "xltx", "xltm", "xlsb", "xls", "xlt", "ods", "csv", "tsv")
    private val TEXT_EXTENSIONS = setOf("txt", "md", "markdown")

    /** The kinds a file list can be narrowed to (Files ▸ ⋯). */
    enum class Kind(val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
        All(com.chethan616.clearpdf.R.string.files_kind_all, com.chethan616.clearpdf.ui.LucideIcons.Files),
        Pdf(com.chethan616.clearpdf.R.string.files_kind_pdf, com.chethan616.clearpdf.ui.LucideIcons.FileText),
        Word(com.chethan616.clearpdf.R.string.files_kind_word, com.chethan616.clearpdf.ui.LucideIcons.FilePenLine),
        Slides(com.chethan616.clearpdf.R.string.files_kind_slides, com.chethan616.clearpdf.ui.LucideIcons.Presentation),
        Sheets(com.chethan616.clearpdf.R.string.files_kind_sheets, com.chethan616.clearpdf.ui.LucideIcons.Sheet),
        Text(com.chethan616.clearpdf.R.string.files_kind_text, com.chethan616.clearpdf.ui.LucideIcons.Type),
    }

    /** The kind a file named [name] is. */
    fun kindOf(name: String): Kind = when (extension(name)) {
        in WORD_EXTENSIONS -> Kind.Word
        in SLIDE_EXTENSIONS -> Kind.Slides
        in SHEET_EXTENSIONS -> Kind.Sheets
        in TEXT_EXTENSIONS -> Kind.Text
        else -> Kind.Pdf
    }

    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase()

    fun isWordName(name: String) = extension(name) in WORD_EXTENSIONS

    /** An office document's name (Word, slides or a workbook): it keeps its own extension. */
    fun isOfficeName(name: String) = extension(name).let { it in WORD_EXTENSIONS || it in SLIDE_EXTENSIONS || it in SHEET_EXTENSIONS || it in TEXT_EXTENSIONS }

    /** Whether a file of type [mime] named [name] is one the viewer opens. */
    fun isOpenable(mime: String?, name: String) =
        mime == PDF || mime in WORD || mime in SLIDES || mime in SHEETS || mime in TEXT || extension(name) == "pdf" || isOfficeName(name)

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
        "ppt", "pps", "pot" -> "application/vnd.ms-powerpoint"
        "xls", "xlt" -> "application/vnd.ms-excel"
        "xlsx", "xltx" -> SHEETS[0]
        "xlsm", "xltm" -> SHEETS[2]
        "xlsb" -> SHEETS[3]
        "ods" -> SHEETS[4]
        "csv" -> "text/csv"
        "tsv" -> "text/tab-separated-values"
        "txt" -> "text/plain"
        "md", "markdown" -> "text/markdown"
        else -> PDF
    }
}
