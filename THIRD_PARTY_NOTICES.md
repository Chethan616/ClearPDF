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

The app does not add GPL or LGPL components for document rendering. If a future dependency changes that, its license and redistribution obligations must be reviewed before release.
