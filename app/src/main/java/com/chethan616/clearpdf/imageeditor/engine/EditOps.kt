package com.chethan616.clearpdf.imageeditor.engine

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.CropOutline

/**
 * One non-destructive step of an edit. The editor keeps an ordered list of these and replays
 * it on a downscaled preview (interactive) or on the full-resolution source (export).
 *
 * Every coordinate is **normalised** (0..1 of the bitmap the op receives) and every stroke /
 * text size is a fraction of that bitmap's short edge, so an op renders identically at any
 * resolution.
 */
sealed interface EditOp

/** Quarter turns + fine straighten + mirror. Straightening auto-zooms so no empty corners show. */
data class TransformOp(
    val quarterTurns: Int = 0,
    val straighten: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false
) : EditOp {
    val isIdentity: Boolean
        get() = Math.floorMod(quarterTurns, 4) == 0 && straighten == 0f && !flipH && !flipV
}

/** Rectangular crop (normalised) with an optional shape mask (circle, rounded, polygon…). */
data class CropOp(
    val rect: Rect,
    val outline: CropOutline? = null
) : EditOp

/** Four corners (TL, TR, BR, BL; normalised) warped into an upright rectangle. */
data class PerspectiveOp(val corners: List<Offset>) : EditOp

/** A set of tonal adjustments; values are keyed by [Adjust] in that adjustment's own range. */
data class AdjustOp(val values: Map<Adjust, Float>) : EditOp {
    val isIdentity: Boolean get() = values.all { (k, v) -> v == k.default }
}

/** A look preset blended with the input by [intensity] (0..1). */
data class FilterOp(val preset: FilterPreset, val intensity: Float = 1f) : EditOp

/** Brush / shape strokes drawn on their own layer (so the eraser only removes drawing). */
data class DrawOp(val strokes: List<DrawStroke>) : EditOp

/** A single text overlay. */
data class TextOp(val layer: TextLayer) : EditOp

/**
 * Background removal: an optional automatic subject mask (any resolution; it is scaled to the
 * target) refined by manual erase / restore strokes.
 */
data class BackgroundOp(
    val autoMask: Bitmap?,
    val strokes: List<MaskStroke> = emptyList()
) : EditOp {
    // Bitmaps compare by identity, which is exactly what the render cache needs.
}

data class WatermarkOp(val params: WatermarkParams) : EditOp

/** Target size in **full-resolution** pixels; the preview applies the same ratio. */
data class ResizeOp(val width: Int, val height: Int) : EditOp

// ── Draw model ─────────────────────────────────────────────────────────────────────────────

enum class BrushKind { Pen, Highlighter, Neon, Eraser, Blur, Pixelate }

enum class ShapeKind(val filled: Boolean = false, val sharp: Boolean = false) {
    Free,
    Line,
    Arrow,
    DoubleArrow,
    LineArrow,
    DoubleLineArrow,
    Lasso(filled = true, sharp = true),
    OutlinedRect(sharp = true),
    Rect(filled = true, sharp = true),
    OutlinedOval(sharp = true),
    Oval(filled = true, sharp = true),
    OutlinedTriangle,
    Triangle(filled = true),
    OutlinedPolygon,
    Polygon(filled = true),
    OutlinedStar,
    Star(filled = true),
    FloodFill(filled = true, sharp = true),
    Spray(filled = true, sharp = true);

    val isFreehand: Boolean get() = this == Free || this == Lasso || this == Spray ||
        this == Arrow || this == DoubleArrow
}

data class DrawStroke(
    val brush: BrushKind,
    val shape: ShapeKind,
    /** ARGB. */
    val color: Int,
    /** Stroke width as a fraction of the short edge. */
    val width: Float,
    /** 0..1 edge softness (blur mask / neon glow). */
    val softness: Float = 0f,
    val points: List<Offset>,
    /** Blur radius / pixel size, as a fraction of the short edge (Blur / Pixelate brushes). */
    val effectStrength: Float = 0.03f,
    val floodTolerance: Float = 0.25f,
    val polygonVertices: Int = 5,
    val seed: Int = 0
)

data class MaskStroke(
    val points: List<Offset>,
    val width: Float,
    val restore: Boolean
)

// ── Text model ─────────────────────────────────────────────────────────────────────────────

enum class TextFont { Sans, Serif, Mono, Condensed, Cursive }

data class TextLayer(
    val text: String,
    /** Centre, normalised. */
    val center: Offset = Offset(0.5f, 0.5f),
    /** Text size as a fraction of the short edge. */
    val size: Float = 0.08f,
    val rotation: Float = 0f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val font: TextFont = TextFont.Sans,
    val bold: Boolean = true,
    val italic: Boolean = false,
    /** ARGB or 0 for none. */
    val outlineColor: Int = 0,
    /** Outline width as a fraction of text size. */
    val outlineWidth: Float = 0.08f,
    /** ARGB or 0 for none. */
    val backgroundColor: Int = 0,
    val alpha: Float = 1f
)

// ── Watermark model ────────────────────────────────────────────────────────────────────────

enum class WatermarkPosition { TopLeft, TopCenter, TopRight, CenterLeft, Center, CenterRight, BottomLeft, BottomCenter, BottomRight, Tiled }

data class WatermarkParams(
    val text: String = "",
    val image: Bitmap? = null,
    val color: Int = 0xFFFFFFFF.toInt(),
    val alpha: Float = 0.5f,
    /** Text size or image width as a fraction of the short edge. */
    val size: Float = 0.06f,
    val rotation: Float = 0f,
    val position: WatermarkPosition = WatermarkPosition.BottomRight,
    /** Margin / tile spacing as a fraction of the short edge. */
    val spacing: Float = 0.04f
)
