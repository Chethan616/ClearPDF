# Third-party notices

ClearPDF uses open-source components and keeps their notices with the project. Release packaging must preserve the applicable copyright and license text.

## Apache PDFBox Android

- Artifact: `com.tom-roush:pdfbox-android:2.0.27.0`
- License: Apache License 2.0
- Use: PDF parsing, editing, merging, splitting, and password removal.
- Notice practice: keep the Apache 2.0 license and upstream attribution available with redistributed builds. Do not imply that the Apache Software Foundation endorses ClearPDF.

## Apache POI

- Artifacts: `org.apache.poi:poi:3.17`, `org.apache.poi:poi-scratchpad:3.17`
- License: Apache License 2.0
- Use: text extraction from legacy `.doc`, `.xls`, and `.ppt` files. Modern Office Open XML files use the platform ZIP/XML parser for a smaller footprint.
- Notice practice: retain the Apache 2.0 license and any bundled dependency notices when distributing an APK or source package.

## AndroidLiquidGlass / Backdrop

- Component: `AndroidLiquidGlass` / the local `backdrop` module
- License: Apache License 2.0 (as attributed in the Settings screen)
- Use: the app's translucent glass surfaces, backdrop effects, and shared UI components.

## AndroidLiquidGlassView

- Component: `AndroidLiquidGlassView` by Donny Yale (https://github.com/QmDeve/AndroidLiquidGlassView), Copyright (c) 2025-2026 Donny Yale
- License: MIT License
- Use: the velocity squash-and-stretch technique from its `LiquidTracker` was re-implemented in Compose as `ui/components/LiquidStretch.kt` (`Modifier.liquidStretch`), used by the slider, toggle and segmented-control thumbs. No library code is bundled.
- Notice practice: keep this copyright line and the MIT permission notice with redistributed source/builds.

## Google ML Kit Text Recognition

- Artifact: `com.google.mlkit:text-recognition:16.0.1`
- License: Google APIs Terms of Service (proprietary; the client library, not the on-device model, carries Apache-2.0-style redistribution terms — see Google's ML Kit terms)
- Use: primary on-device OCR engine for scanned/image-only PDF pages (`ocr-core` module). This is the **bundled** artifact — the recognition model ships inside the APK, not the Play-Services-downloaded variant — so it runs fully offline with no network call and no Play Services requirement.
- Notice practice: comply with Google's ML Kit Terms of Service for redistribution; do not imply Google endorses ClearPDF.

## Tesseract4Android / Tesseract OCR / Leptonica

- Artifact: `cz.adaptech.tesseract4android:tesseract4android:4.9.0` (Copyright 2019 Adaptech s.r.o., Robert Pösel)
- License: Apache License 2.0 (wraps the Tesseract OCR engine, also Apache-2.0, and the Leptonica imaging library, BSD-2-Clause)
- Use: fully open-source, offline OCR fallback (`ocr-core` module) used when the bundled ML Kit engine fails to initialize or recognize on a given device. Bundles `eng.traineddata` (English) as a module asset so the fallback needs no download.
- Notice practice: keep the Apache 2.0 license text and the BSD-2-Clause Leptonica notice available with redistributed builds.

## Feature-set inspiration — Pdf_Tools

- Project: `Karna14314/Pdf_Tools` (https://github.com/Karna14314/Pdf_Tools)
- License: Apache License 2.0
- Use: informed ClearPDF's on-device tool set (e.g. PDF-to-Images export). ClearPDF's implementations are original code written against the app's own architecture and `backdrop` UI; no source was copied. This acknowledgement is provided in good faith for the shared feature direction.

The app does not bundle GPL or LGPL components for document rendering. If a future dependency changes that, its license and redistribution obligations must be reviewed before release. (The optional LibreOffice engine below is not bundled in the APK; it is installed only on request.)

## docx-preview (docxjs)

- Artifact: `docx-preview.min.js` 0.4.0, vendored at `app/src/main/assets/docx/`
- License: Apache License 2.0
- Source: https://github.com/VolodymyrBaydalka/docxjs
- Use: lays out .docx documents inside an offscreen WebView, which is then printed to PDF. Chosen
  over a native Office engine purely on size — `app.opendocument:odr-core-android` is a 100 MB AAR
  and Apache POI's OOXML half is ~17 MB of jars that only parse, not render.
- Notice practice: the upstream Apache-2.0 banner is preserved verbatim at the top of the vendored
  file, and the library is credited in Settings → Licenses.

## JSZip

- Artifact: `jszip.min.js` 3.10.1, vendored at `app/src/main/assets/docx/`
- License: MIT (upstream is dual MIT / GPL-3.0; this app uses it under the MIT option)
- Source: https://github.com/Stuk/jszip
- Use: required by docx-preview to read the .docx zip container in the browser context.
- Notice practice: the upstream banner (including its pako attribution) is preserved verbatim at the
  top of the vendored file, and the library is credited in Settings → Licenses.

## ImageToolbox (adapted source)

- Source: https://github.com/T8RIN/ImageToolbox — Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
- License: Apache License 2.0
- Use: the image editor's cropper (`imageeditor/thirdparty/cropper`, from `lib/cropper` minus
  its widgets), gesture helpers (`thirdparty/gesture`), image-scope helpers (`thirdparty/image`),
  the perspective cropper (`thirdparty/freecorners`, from `lib/opencv-tools/free_corners_crop`
  with OpenCV's `warpPerspective` replaced by `Matrix.setPolyToPoly`), and draw-engine code
  (`imageeditor/engine/draw`: shape/arrow geometry from `PathHelper.kt`, paint set-up from
  `DrawUtils.kt`, `FloodFill.kt`). Adjustment/preset choices and the watermark placement model
  follow `feature/filters` and `feature/watermarking`.
- Notice practice: every adapted file keeps the upstream Apache-2.0 header with an added
  "Modified by ClearPDF" line; attribution is also in `NOTICE` and Settings → Licenses.

## GPUImage for Android

- Artifact: `jp.co.cyberagent.android:gpuimage:2.1.0` (Maven Central; ~0.2 MB incl. a tiny native helper)
- License: Apache License 2.0 (CyberAgent, Inc.)
- Use: GL-accelerated adjustments (white balance, vibrance, gamma, sharpen, vignette) and
  artistic presets in the image editor.

## Google ML Kit Subject Segmentation

- Artifact: `com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1`
- License: Google APIs Terms of Service
- Use: optional automatic background removal in the image editor. The model is delivered by
  Google Play services; the call is gated at runtime (Android 7+, Play services present) behind
  the `BackgroundRemover` interface so FOSS builds can swap or drop it.

## Stack Blur

- Algorithm by Mario Klingemann (public domain), re-implemented in `imageeditor/engine/BlurUtils.kt`
  as the pre-Android-12 fallback for the blur brush.

## LibreOffice (optional Office engine)

- What: "Office engine (powered by LibreOffice)" — a prebuilt LibreOfficeKit bundle for Android
  (`liblo-native-code.so`, `libc++_shared.so` and the LibreOffice runtime tree). It is **not in the
  APK**. It is installed only when the user asks for it in Settings: the `play` flavor gets it through
  the on-demand Play Feature Delivery module `:office_engine`, and the `foss` flavor downloads it from
  a pinned release, accepting it only if its size and SHA-256 match `OfficeEngineManifest.kt`.
- Binary source: https://github.com/vasuki-re/LibreOffice-Lite (release v2.0), a build of
  LibreOffice core (https://git.libreoffice.org/core, mirror https://github.com/LibreOffice/core).
- License: Mozilla Public License 2.0 for LibreOffice itself; some bundled parts (fonts such as
  Liberation, Carlito, Caladea and Gentium Basic, and other third-party libraries compiled into
  LibreOffice) carry their own licences, listed at https://www.libreoffice.org/about-us/licenses/.
- Source availability (MPL-2.0 §3.2): the corresponding LibreOffice source is available from the
  URLs above. Anyone redistributing a build that bundles or rehosts the engine must keep that source
  available and should prefer a self-built bundle whose build recipe is published.
- ClearPDF's Kotlin JNI binding (`app/src/main/java/org/libreoffice/kit/`) is original code; only
  its package, class and method names follow LibreOffice's Android binding, because the native
  library resolves them by name.
- Trademark: "LibreOffice" is a trademark of The Document Foundation; ClearPDF uses it only to say
  the optional engine is powered by LibreOffice and is not affiliated with or endorsed by TDF.
- Credited in Settings → Licenses and Settings → Office engine.
