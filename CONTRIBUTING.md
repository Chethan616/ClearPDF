# Contributing to ClearPDF

Thanks for helping make ClearPDF a fast, private, offline-first PDF app.

## Project principles

- PDF data stays on the device. Do not add cloud processing, telemetry, ads, or account requirements.
- Keep core PDF editing usable without an internet connection.
- Prefer small, focused pull requests with clear user value.
- Preserve accessibility, light/dark themes, and low-end device performance.
- Respect the licenses and notices of every dependency and asset.

## Before you start

1. Search existing issues and pull requests.
2. For larger changes, open an issue describing the problem and proposed direction.
3. Do not include private documents, signing keys, `key.properties`, APKs, build outputs, or generated IDE files.

## Local setup

Requirements:

- Android Studio with JDK 17 or newer.
- Android SDK 36.
- An Android device or emulator for UI changes.

```bash
git clone https://github.com/Chethan616/ClearPDF.git
cd ClearPDF
./gradlew assembleDebug
```

For a release-like build without signing credentials:

```bash
./gradlew assembleRelease
```

Release signing is intentionally local-only. Copy `key.properties.example` to `key.properties` only when you have your own signing credentials; never commit it.

## Pull requests

Please include:

- What changed and why.
- Screenshots or a short recording for UI changes.
- Device/API level used for testing.
- Manual test steps and any known limitations.
- Updated documentation, notices, or tests when relevant.

Keep commits focused and use an imperative subject, for example `Improve recent-file actions`.

## Reporting bugs

Use the bug report form and include the app version, Android version, device, reproduction steps, expected behavior, actual behavior, and logs with personal data removed.

## Code style

Use official Kotlin formatting, descriptive names, and existing project patterns. Avoid unrelated formatting churn. New dependencies need a clear justification, license compatibility, and an offline behavior review.
