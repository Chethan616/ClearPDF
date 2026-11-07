# Contributing to Artefact

Thanks for helping! Bug reports, ideas and focused pull requests are all welcome.

## Before you start

- Look through [issues](https://github.com/Chethan616/ClearPDF/issues) and [Discussions](https://github.com/Chethan616/ClearPDF/discussions) first; for anything big, open an issue or a discussion so we can agree on the approach.
- Keep a pull request to one change, with a clear description and screenshots or a short video for anything visible.

## Building

See [Build from source](README.md#build-from-source). The app is in `android/` (Kotlin, Jetpack Compose); the engine is in `engine/` (Rust). Run `cargo test -p artefact-mobile` in `engine/` after engine changes, and fix every warning (`cargo clippy`).

## Style

- Match the code around your change: naming, comment density and idiom.
- UI uses the glass components in `android/app/src/main/java/com/chethan616/clearpdf/ui/glass/` and strings in `res/values/strings.xml`.
- Every third-party asset or library is credited in `ATTRIBUTION.toml` / `ATTRIBUTION.md` and the app's licences page.

## Licence

By contributing you agree that your contribution is licensed under MIT OR Apache-2.0, like the rest of the project.
