package com.chethan616.clearpdf.docx

import androidx.compose.runtime.Immutable

/**
 * One character's formatting: its run's raw `<w:rPr>` (kept verbatim, edited element by element),
 * the `<w:hyperlink …>` start tag wrapping it, or — for [obj] — a whole run kept verbatim (a
 * picture, a page break, a footnote mark…) that stands as a single U+FFFC in the paragraph text.
 */
@Immutable
data class CharFmt(val rPr: String, val link: String? = null, val obj: String? = null) {
    companion object {
        val Plain = CharFmt("")
    }
}

/** U+FFFC stands for an object run inside [DocPara.text]. */
const val OBJ_CHAR = '\uFFFC'

sealed interface DocBlock {
    val id: Long
}

/**
 * A paragraph. [origin] is its original XML, written back untouched while it is non-null; any
 * edit produces a copy without it, and only those are serialized again.
 */
@Immutable
class DocPara(
    override val id: Long,
    val origin: String?,
    val openTag: String,
    val pPr: String,
    /** Bookmarks and similar markers; re-emitted at the start of the paragraph. */
    val extras: String,
    val text: String,
    /** One entry per char of [text]. */
    val fmts: List<CharFmt>,
    val editable: Boolean,
    /**
     * Where Word started a new page inside this paragraph when it last laid the file out
     * (`w:lastRenderedPageBreak`), as offsets into [text]. The viewer shows Word's pages by them.
     */
    val marks: List<Int> = emptyList()
) : DocBlock {
    fun edited(
        text: String = this.text,
        fmts: List<CharFmt> = this.fmts,
        pPr: String = this.pPr,
        id: Long = this.id,
        openTag: String = this.openTag,
        extras: String = this.extras,
        marks: List<Int> = this.marks
    ): DocPara = DocPara(id, null, openTag, pPr, extras, text, fmts, editable, marks.filter { it in 0..text.length })

    val isEmpty: Boolean get() = text.isEmpty()
}

sealed interface TableItem
sealed interface RowItem

/** Verbatim XML: section properties, content controls, bookmarks between blocks… */
@Immutable
class DocRaw(
    override val id: Long,
    val xml: String,
    /** Read-only content to show for it (a table of contents' paragraphs, a cover page…). */
    val preview: List<DocBlock> = emptyList()
) : DocBlock, TableItem, RowItem

@Immutable
class DocTable(
    override val id: Long,
    val origin: String?,
    /** Start tag through `tblGrid`, verbatim. */
    val head: String,
    val items: List<TableItem>,
    val tail: String,
    /** Grid column widths, twips. */
    val grid: List<Int>,
    val tblPr: String
) : DocBlock {
    val rows: List<DocRow> get() = items.filterIsInstance<DocRow>()
    fun withItems(items: List<TableItem>) = DocTable(id, null, head, items, tail, grid, tblPr)
}

@Immutable
class DocRow(val id: Long, val head: String, val items: List<RowItem>, val tail: String, val trPr: String) : TableItem {
    val cells: List<DocCell> get() = items.filterIsInstance<DocCell>()
    fun withItems(items: List<RowItem>) = DocRow(id, head, items, tail, trPr)
}

@Immutable
class DocCell(val id: Long, val head: String, val blocks: List<DocBlock>, val tail: String, val tcPr: String) : RowItem {
    fun withBlocks(blocks: List<DocBlock>) = DocCell(id, head, blocks, tail, tcPr)
}

/** Page size and margins of the (last) section, twips. */
@Immutable
data class PageSetup(
    val width: Int = 11906,
    val height: Int = 16838,
    val left: Int = 1440,
    val right: Int = 1440,
    val top: Int = 1440,
    val bottom: Int = 1440
)

@Immutable
class DocState(val blocks: List<DocBlock>)

/** Walks every paragraph, tables and previews included, in reading order. */
fun forEachPara(blocks: List<DocBlock>, includePreview: Boolean = true, f: (DocPara) -> Unit) {
    for (b in blocks) when (b) {
        is DocPara -> f(b)
        is DocTable -> for (r in b.rows) for (c in r.cells) forEachPara(c.blocks, includePreview, f)
        is DocRaw -> if (includePreview) forEachPara(b.preview, true, f)
    }
}

fun findPara(blocks: List<DocBlock>, id: Long): DocPara? {
    for (b in blocks) when (b) {
        is DocPara -> if (b.id == id) return b
        is DocTable -> for (r in b.rows) for (c in r.cells) findPara(c.blocks, id)?.let { return it }
        is DocRaw -> {}
    }
    return null
}

/**
 * Rebuilds only the path down to the block list that holds paragraph [id], replacing that list
 * with [f]'s result. Everything else keeps its identity, so unchanged paragraphs are neither
 * re-serialized nor recomposed. Null when [id] isn't in an editable container.
 */
fun mapContainerOf(blocks: List<DocBlock>, id: Long, f: (List<DocBlock>) -> List<DocBlock>): List<DocBlock>? {
    if (blocks.any { it is DocPara && it.id == id }) return f(blocks)
    for ((i, b) in blocks.withIndex()) {
        if (b !is DocTable) continue
        for ((ri, item) in b.items.withIndex()) {
            if (item !is DocRow) continue
            for ((ci, cellItem) in item.items.withIndex()) {
                if (cellItem !is DocCell) continue
                val inner = mapContainerOf(cellItem.blocks, id, f) ?: continue
                val row = item.withItems(item.items.toMutableList().also { it[ci] = cellItem.withBlocks(inner) })
                val table = b.withItems(b.items.toMutableList().also { it[ri] = row })
                return blocks.toMutableList().also { it[i] = table }
            }
        }
    }
    return null
}

fun replacePara(blocks: List<DocBlock>, para: DocPara): List<DocBlock>? =
    mapContainerOf(blocks, para.id) { list -> list.map { if (it is DocPara && it.id == para.id) para else it } }
