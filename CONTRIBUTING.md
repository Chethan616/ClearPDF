# Contributing to Artefact

Thanks for helping! Bug reports, ideas and focused pull requests are all welcome.

## Before you start

- Look through [issues](https://github.com/Chethan616/ClearPDF/issues) and [Discussions](https://github.com/Chethan616/ClearPDF/discussions) first; for anything big, open an issue or a discussion so we can agree on the approach.
- Keep a pull request to one change, with a clear description and screenshots or a short video for anything visible.

## Building

See [Build from source](README.md#build-from-source). The app is in `android/` (Kotlin, Jetpack Compose); the engine is in `engine/` (Rust). Run `cargo test -p artefact-mobile` in `engine/` after engine changes, and fix every warning (`cargo clippy`).

## Design and code rules

Read these two before you change anything people see or anything in the engine:

- **[DESIGN.md](DESIGN.md)**: colour, typography, liquid glass, motion, haptics, layout and the screen patterns to reuse.
- **[CODE.md](CODE.md)**: how the app and the engine are laid out, the hard rules, and how to add a tool, a file type, a setting or a note kind.

The short version:

- **Typography.** All interface text is Plus Jakarta Sans through `ClearType` (`Headline`, `Title`, `Body`, `Label`, `Caption` …). No raw `fontSize` on a `Text`, and no new typefaces.
- **Colour.** Accents come from `Candy` (`Candy.Sky` is the action colour, `Candy.Tomato` is for deleting); surfaces use `clear.ink*` and the card tones. No hex colours in screens. Check light and dark themes.
- **Liquid glass.** Floating chrome is built from the glass components in `ui/glass/` (`GlassCircle`, `GlassPill`, `glassBar()`, `GlassMenu`, `GlassDialog` …) over a `layerBackdrop`. They read the user's Settings ▸ Liquid glass & motion (frost, lens, tint, adaptive luminance), so never fake glass with a translucent colour or hard-code a `GlassStyle`.
- **Motion and haptics.** Use the named springs (`GlassMotion`, `Fluid`) and `Feel`s, so the user's motion settings apply. Sliders tick; menus unfold beside their button.
- **Words.** Every string is in `res/values/strings.xml` and in every `values-xx` translation. The `UiTextTest` unit test fails on English written in Kotlin.
- **Offline.** No internet permission, ever. CI checks the built APK.
- **No duplicate tools.** If an existing tool already does it (PDF to text is in Export, text to PDF in Create PDF), add it there instead of a new tile.
- Match the code around your change: naming, comment density and idiom.
- Every third-party asset or library is credited in `ATTRIBUTION.toml` / `ATTRIBUTION.md` and the app's licences page.

## Licence

By contributing you agree that your contribution is licensed under MIT OR Apache-2.0, like the rest of the project.
