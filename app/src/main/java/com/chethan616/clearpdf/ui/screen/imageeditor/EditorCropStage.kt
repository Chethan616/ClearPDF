package com.chethan616.clearpdf.ui.screen.imageeditor

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.ImageCropper
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.AspectRatio
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.CropOutline
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.CustomPathOutline
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.CutCornerCropShape
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.OutlineType
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.OvalCropShape
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.PolygonCropShape
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.RectCropShape
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.model.RoundedCornerCropShape
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.rememberImageCropperState
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.settings.CropDefaults
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.settings.CropOutlineProperty
import com.chethan616.clearpdf.imageeditor.thirdparty.cropper.settings.Paths
import com.chethan616.clearpdf.imageeditor.thirdparty.freecorners.compose.FreeCornersCropper
import com.chethan616.clearpdf.imageeditor.thirdparty.freecorners.compose.rememberFreeCornersCropperState
import com.chethan616.clearpdf.ui.theme.LiquidGlassColors
import com.chethan616.clearpdf.ui.viewmodel.ImageEditorViewModel

/** Aspect presets; ratio null = free, -1 = the image's own ratio. */
data class CropAspect(val label: String, val ratio: Float?)

val CropAspects = listOf(
    CropAspect("Free", null),
    CropAspect("Original", -1f),
    CropAspect("1:1", 1f),
    CropAspect("4:5", 4f / 5f),
    CropAspect("4:3", 4f / 3f),
    CropAspect("3:4", 3f / 4f),
    CropAspect("3:2", 3f / 2f),
    CropAspect("16:9", 16f / 9f),
    CropAspect("9:16", 9f / 16f),
)

data class CropMask(val label: String, val property: CropOutlineProperty)

val CropMasks: List<CropMask> = listOf(
    CropMask("Rectangle", CropOutlineProperty(OutlineType.Rect, RectCropShape(0, "Rect"))),
    CropMask("Rounded", CropOutlineProperty(OutlineType.RoundedRect, RoundedCornerCropShape(1, "Rounded"))),
    CropMask("Circle", CropOutlineProperty(OutlineType.Oval, OvalCropShape(2, "Oval"))),
    CropMask("Cut corner", CropOutlineProperty(OutlineType.CutCorner, CutCornerCropShape(3, "CutCorner"))),
    CropMask("Hexagon", CropOutlineProperty(OutlineType.Polygon, PolygonCropShape(4, "Polygon"))),
    CropMask("Heart", CropOutlineProperty(OutlineType.Custom, CustomPathOutline(5, "Heart", Paths.Favorite))),
    CropMask("Star", CropOutlineProperty(OutlineType.Custom, CustomPathOutline(6, "Star", Paths.Star))),
)

/**
 * Crop surface built on the ported ImageToolbox croppers: [ImageCropper] for rect / shape crops
 * and [FreeCornersCropper] for perspective. Both report normalised geometry back to the VM.
 */
@Composable
fun EditorCropStage(
    session: ImageEditorViewModel.CropSession,
    contentPadding: PaddingValues,
    onCropped: (androidx.compose.ui.geometry.Rect, CropOutline?) -> Unit,
    onPerspective: (List<androidx.compose.ui.geometry.Offset>) -> Unit
) {
    val handle = LiquidGlassColors.Blue
    val currentOnCropped by rememberUpdatedState(onCropped)
    val currentOnPerspective by rememberUpdatedState(onPerspective)
    Box(Modifier.fillMaxSize().padding(contentPadding).padding(8.dp)) {
        Crossfade(targetState = session.perspective, label = "cropMode") { perspective ->
            if (perspective) {
                FreeCornersCropper(
                    bitmap = session.working,
                    croppingTrigger = session.trigger,
                    onCropped = { currentOnPerspective(it) },
                    state = rememberFreeCornersCropperState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    gridColor = Color.White.copy(0.6f),
                    handlesColor = handle
                )
            } else {
                val image = remember(session.working) { session.working.asImageBitmap() }
                val aspect = CropAspects.getOrElse(session.aspectIndex) { CropAspects[0] }
                val mask = CropMasks.getOrElse(session.outlineIndex) { CropMasks[0] }
                val ratio = when (aspect.ratio) {
                    null -> AspectRatio.Original
                    -1f -> AspectRatio(session.working.width / session.working.height.toFloat())
                    else -> AspectRatio(aspect.ratio)
                }
                val props = CropDefaults.properties(
                    cropOutlineProperty = mask.property,
                    aspectRatio = ratio,
                    fixedAspectRatio = aspect.ratio != null,
                    overlayRatio = 1f,
                    maxZoom = 8f
                )
                ImageCropper(
                    modifier = Modifier.fillMaxSize(),
                    imageBitmap = image,
                    cropStyle = CropDefaults.style(
                        overlayColor = Color.White.copy(0.55f),
                        handleColor = handle,
                        backgroundColor = Color.Black.copy(0.45f)
                    ),
                    cropProperties = props,
                    state = rememberImageCropperState(),
                    crop = session.trigger,
                    onCropStart = {},
                    onZoomChange = {},
                    onCropSuccess = { rect ->
                        val outline = mask.property.cropOutline.takeUnless { it is RectCropShape }
                        currentOnCropped(rect, outline)
                    }
                )
            }
        }
    }
}
