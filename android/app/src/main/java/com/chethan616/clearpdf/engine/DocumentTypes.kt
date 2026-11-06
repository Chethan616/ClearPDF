package com.chethan616.clearpdf.engine

/**
 * The kinds of file Artefact opens: PDFs, and word-processor documents read by the Word engine
 * (.docx, .doc, .odt, .rtf). The engine tells them apart by their bytes; these lists are for the
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

    /** Everything the viewer opens. */
    val OPENABLE = arrayOf(PDF) + WORD

    private val WORD_EXTENSIONS = setOf("docx", "docm", "dotx", "dotm", "doc", "dot", "odt", "ott", "rtf")

    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase()

    fun isWordName(name: String) = extension(name) in WORD_EXTENSIONS

    /** Whether a file of type [mime] named [name] is one the viewer opens. */
    fun isOpenable(mime: String?, name: String) =
        mime == PDF || mime in WORD || extension(name) == "pdf" || isWordName(name)

    /** A file name without its extension, as documents are titled (`Report.docx` → `Report`). */
    fun title(name: String): String =
        if (isWordName(name)) name.substringBeforeLast('.') else name.removeSuffix(".pdf").removeSuffix(".PDF")

    /** The type to hand another app a file named [name] as. */
    fun mimeOf(name: String): String = when (extension(name)) {
        "docx" -> WORD[0]
        "dotx" -> WORD[1]
        "docm" -> WORD[2]
        "doc", "dot" -> "application/msword"
        "odt", "ott" -> "application/vnd.oasis.opendocument.text"
        "rtf" -> "application/rtf"
        else -> PDF
    }
}
