# Artefact code guide

How the code is laid out and the rules it is written to. For how screens should look and move, see
[DESIGN.md](DESIGN.md).

## The two halves

```
android/        the app: Kotlin, Jetpack Compose
  app/src/main/java/com/chethan616/clearpdf/
    engine/     the bridge to the Rust engine, files, thumbnails, document types
    notes/      the notes store (notes.json), voice notes, audio export
    preview/    Quick Preview: the card other apps open files into
    ui/         every screen
      glass/    liquid glass components, motion, haptics, adaptive luminance
      theme/    colours (Candy, CardTone) and type (ClearType)
      home/     the desk and its cards
      desk/     Tools, Files, Settings
      viewer/   the PDF and document viewer and its tools
      sheets/   the spreadsheet viewer
      notes/    the note editor, photo editor, mind map
  backdrop/     Kyant's backdrop library (vendored, Apache-2.0)
engine/         the engine: Rust
  crates/       pdfcraft (PDF: model, render, edit, sign, OCR …)
  mobile/       artefact-mobile: the one library the app loads (UniFFI)
  vendor/       patched copies of upstream crates (see engine/vendor/README.md)
```

The app never parses a document itself. It asks the engine through `MobileSession` (generated
Kotlin bindings, built by Gradle from `engine/mobile`). Office files go through the storytold
engines: WordCraft (Word, Markdown, text), DeckCraft (PowerPoint), GridCraft (spreadsheets) and
LightCraft (photos). Legacy formats GridCraft or DeckCraft skip are read in `engine/mobile`
(`ppt.rs` for .ppt drawings, `xls_art.rs` for .xls pictures and charts).

## Hard rules

These are checked by CI or by review, and a pull request that breaks one is not merged.

1. **Offline.** The app has no internet permission and never will. CI fails if the built APK asks
   for `INTERNET`. No analytics, no crash reporting, no fonts or images from the network.
2. **No English in Kotlin.** Every word a person sees is a string resource. `UiTextTest` fails on
   text in code. New strings go into `values/strings.xml` and every `values-xx` folder.
3. **Engine code is clean.** `cargo fmt`, `cargo clippy` with no warnings, and
   `cargo test -p artefact-mobile` pass. The engine crates deny `unwrap`, `expect` and `panic` in
   library code: return a `MobileError` instead.
4. **App code builds and tests.** `./gradlew :app:testDebugUnitTest` passes.
5. **Credit.** Every library, font, icon set or sample file is listed in `ATTRIBUTION.toml`,
   `ATTRIBUTION.md` and the app's licences page.

## Writing Kotlin

- Compose, state in `mutableStateOf`, one `AppViewModel` for app-wide state. Screens take the view
  model and read what they need.
- Long work runs on `Dispatchers.Default` or `IO` (the engine call wrappers `eng.command { }` do
  this). Nothing heavy on the main thread; a frame is 8ms on a 120Hz phone.
- Name things for what they are to a person: `finishTyping`, `pickShape`, `Placement`, not
  `handleInput2` or `Manager`.
- Comments say what a thing is or why it is done that way, in plain sentences, above the code.
  A KDoc on every public or non-obvious function. No commented-out code.
- Match the file you are in. If it uses fully qualified names for a one-off import, so can you.
- Shared UI goes in `ui/glass/` only if two screens use it. Otherwise keep it private to its file.
- Pointer input that reads state: read through `rememberUpdatedState` or a state delegate, never
  a value captured at the first composition (that bug made the sheet tap the wrong cell).

## Writing Rust

- Follow the crate you are in. `engine/mobile` is the only crate with UniFFI types
  (`#[derive(uniffi::Record)]`, `#[uniffi::export]`).
- Read untrusted files defensively: bounds-checked reads (`u16_at`, `get(..)`), a budget on
  records, depth and bytes, and no allocation sized by a number the file gave you.
- Tests go in `engine/mobile/src/tests.rs` (or the crate's own `tests` module). A bug fix comes with
  a test that failed before it. Small fixture files live in `engine/mobile/tests/data/`.
- A patch to a vendored crate is marked `Artefact patch` and noted in `engine/vendor/README.md`.

## Adding things

- **A tool:** first check it is not already part of a tool (see DESIGN.md). Then a `ToolDef` in
  `ui/desk/Common.kt`, its screen in `ui/ToolScreens.kt` following the tool page pattern, and the
  work in `AppViewModel`.
- **A file type:** `engine/DocumentTypes.kt` (types, extensions, kind), the intent filters in
  `AndroidManifest.xml`, the engine's sniffing in `engine/mobile/src/office.rs`, a thumbnail in
  `engine/LocalFiles.kt`, and Quick Preview.
- **A setting:** `GlassSettings` / `MotionSettings` or the app preferences, a `SettingRow`, and a
  string in every language.
- **A note kind:** `NoteKind` in `notes/Notes.kt`, its card in `ui/home/Cards.kt`, its editor routed
  from `NoteEditor`, and its entry in the create menu (`ui/home/CreateDock.kt`).

## Building and releasing

- Debug build for a phone: `./gradlew :app:assembleFast -Partefact.abis=arm64-v8a`. It installs as
  `com.chethan616.clearpdf.debug`, beside the release.
- Versions live in `android/gradle.properties` (`artefact.version`, `artefact.versionCode`). The
  version code of an APK is the base times ten plus an ABI digit (armeabi-v7a 1, arm64-v8a 2,
  x86_64 3, x86 4, universal 0).
- A release is a tag `vX.Y.Z[-alpha.N]`; the release workflow builds and signs one APK per ABI.
  Each needs a changelog in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
  (500 characters at most).
- F-Droid builds the same tag and checks its APKs are identical to ours (reproducible builds), so
  the build must not depend on the machine: pinned Gradle, Rust toolchain, NDK and cargo-ndk.

## Commits and pull requests

- One change per pull request, described in plain words, with screenshots for anything visible.
- Commit messages say what changed for a person using the app ("Spreadsheet charts are drawn"),
  not which files moved.
- Pull requests are reviewed by the code owner (`.github/CODEOWNERS`) and CI must be green.
