# Contributing to ClearPDF

Thanks for helping improve ClearPDF. Bug reports, usability feedback, documentation fixes, and focused code changes are welcome.

## Project principles

- Keep selected documents and extracted content on-device; do not add a ClearPDF upload service.
- Make network features explicit, user-initiated, and documented. Keep core PDF workflows usable offline where practical.
- Prefer small changes with clear user value.
- Preserve accessibility, light and dark themes, and performance across a range of Android devices.
- Respect the licenses and notices for dependencies and assets.

## Before you start

1. Search existing issues and pull requests so work is not duplicated.
2. For a larger feature, open a feature request first to agree on scope.
3. Do not include private documents, signing keys, <code>key.properties</code>, APKs, build outputs, or generated IDE files.

## Local setup

Requirements:

- Android Studio with JDK 17 or newer.
- Android SDK Platform 36 and Android Build Tools 36.1.0.
- An Android device or emulator for UI changes.

Clone the repository, then build the FOSS debug app:

~~~bash
git clone https://github.com/Chethan616/ClearPDF.git
cd ClearPDF
./gradlew :app:assembleFossDebug
~~~

On Windows, run <code>gradlew.bat :app:assembleFossDebug</code>.

For the sideload release variant, run <code>./gradlew :app:assembleFossRelease</code>. Release signing is local-only. Copy <code>key.properties.example</code> to <code>key.properties</code> only if you have your own signing credentials; never commit the copied file or a keystore. Without credentials, Gradle uses the debug signing key, which is for local checks only.

## Project layout

- <code>app/</code> — Android UI, navigation, viewers, PDF tools, image editor, and spreadsheet editor.
- <code>pdf-core/</code> — PDF operations and rendering support.
- <code>ocr-core/</code> — on-device OCR.
- <code>backdrop/</code> — shared liquid-glass backdrop effects.
- <code>office_engine/</code> — optional Play on-demand feature, included for bundle builds.

## Pull requests

Please include:

- What changed and why.
- The related issue, if one exists.
- How you checked the change, including device and Android version for UI work.
- Screenshots or a short recording for UI changes.
- Any known limitations or follow-up work.

Keep the pull request focused. Update docs, notices, or tests when they are affected. New dependencies need a clear justification, compatible licensing, and an offline/network behavior review.

## Reporting bugs

Use the [bug report form](https://github.com/Chethan616/ClearPDF/issues/new?template=bug_report.yml). Include the app version, distribution source, device, Android version, reproduction steps, and expected and actual behavior. Remove personal data and private document content from any logs or screenshots.

## Code style and conduct

Follow the existing Kotlin patterns and use descriptive names. Avoid unrelated formatting churn. Please follow the [Code of Conduct](CODE_OF_CONDUCT.md). For security issues, use the private reporting process in [SECURITY.md](SECURITY.md).
