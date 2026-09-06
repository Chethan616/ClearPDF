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

The app does not add GPL or LGPL components for document rendering. If a future dependency changes that, its license and redistribution obligations must be reviewed before release.

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
