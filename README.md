<p align="center">
  <img src="docs/media/artefact-icon.png" alt="Artefact" width="120">
</p>

<h1 align="center">Artefact</h1>

<p align="center">
  <b>Your PDFs, notes, to-do lists, photos and voice notes on one beautiful desk.</b><br>
  Free and open source. No ads. No accounts. Nothing leaves your phone.
</p>

<p align="center">
  <a href="https://github.com/Chethan616/ClearPDF/releases"><img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="60"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/Chethan616/ClearPDF"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60"></a>
</p>

<p align="center">
  <img alt="Engine: Rust" src="https://img.shields.io/badge/engine-Rust-lightblue?style=flat&logo=rust&logoColor=white">
  <img alt="UI: Kotlin and Jetpack Compose" src="https://img.shields.io/badge/UI-Kotlin%20%C2%B7%20Compose-blue?style=flat">
  <img alt="Minimum: Android 10" src="https://img.shields.io/badge/min-Android%2010-blue?style=flat">
  <img alt="License: MIT OR Apache-2.0" src="https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue?style=flat">
</p>

> Artefact is the next version of **ClearPDF**. It installs over ClearPDF as an update, keeps your
> files where they are, and is still made by the same person for everyone, for free.

<p align="center">
  <img src="docs/media/welcome.png" alt="Welcome" width="200">
  <img src="docs/media/desk.png" alt="The desk" width="200">
  <img src="docs/media/create.png" alt="Making something new" width="200">
  <img src="docs/media/preview.png" alt="Quick Preview over another app" width="200">
</p>

## What it does

**One desk for everything you make.** PDFs, notes, to-do lists, photos and voice notes sit side by
side as cards. Tick a list item right on its card, play a voice note in place, filter by kind.

**Real PDF tools.** Read (continuous, single page, Reading Mode, Night Mode), find, select and copy
text, highlight, underline, strike through, draw, comment (text and voice), fill forms, sign (drawn,
typed or with a digital ID, with live validation), edit text and pictures, organise, crop, compress,
combine, password-protect, export to Word, Excel, PowerPoint or images, scan paper with the camera
and recognise its text, and read aloud.

**Quick Preview.** "Open with Artefact" from WhatsApp, Gmail or a file manager shows the PDF in a
floating card over that app: scroll, zoom, share, or pull it up into the full viewer. Close it and
you are exactly where you were.

**Notes, lists and voice.** Light Markdown notes with photos, to-do lists, and voice notes recorded
right from the desk.

**Liquid glass, expressive motion.** A design that mixes Apple's liquid glass with Material 3
Expressive: real refraction, springs everywhere, mascots that keep you company when things are empty.

**Private by design.** No network permission for your documents, no analytics, no account. Your
files stay on your phone.

## Languages

English, Arabic, German, Spanish, French, Italian, Portuguese (Portugal and Brazil) and Russian.
Help translate: copy `android/app/src/main/res/values/strings.xml` into `values-<language>/` and
open a pull request, or add strings with `tools/i18n/apply.py`.

## Build

You need JDK 21, the Android SDK with NDK `29.0.14206865`, Rust (the toolchain in
`engine/rust-toolchain.toml` installs itself) and `cargo install cargo-ndk --version 4.1.2`.

```sh
cd android
./gradlew installDebug -Partefact.abis=arm64-v8a      # a phone
./gradlew installDebug -Partefact.abis=x86_64         # the emulator
./gradlew assembleRelease                             # both, signed with key.properties if present
```

Gradle builds the Rust engine with cargo-ndk and generates its Kotlin bindings (UniFFI) on the way.

## Project layout

| Path | What |
|---|---|
| `android/` | The app: Kotlin, Jetpack Compose, the glass design system (`app/`), the backdrop library (`backdrop/`) |
| `engine/` | The Rust workspace: the document engine (`crates/`), the bridge the app loads (`mobile/`, `libartefact.so`), fonts, icons and OCR models (`assets/`), patched third-party crates (`vendor/`) |
| `docs/` | Design notes and pictures |
| `fastlane/` | Store listings |
| `tools/` | Helpers (translations) |
| `PLAN.md` | The roadmap |

## Roadmap

PDF first, then one Rust engine for every document: Word, PowerPoint and Excel, with pictures and
voice for notes. See [PLAN.md](PLAN.md).

## Contributing

Issues and pull requests are welcome, from bug reports to translations to new tools. Please keep
the app free of ads, trackers and accounts.

## Credits

Artefact stands on open-source work, with thanks:

- Its PDF engine grew from **PdfCraft** by storytold and **PdfCraft for Android** by the ArtCraft
  Team (MIT OR Apache-2.0).
- Liquid glass: **AndroidLiquidGlass** by Kyant0 (Apache-2.0).
- Mascots: **bot-avatars** by Jakub Antalik (MIT).
- Type: Plus Jakarta Sans, Doto and DM Serif Display (SIL OFL 1.1). Icons: Lucide (ISC).

The full list is in [NOTICE](NOTICE) and [ATTRIBUTION.md](ATTRIBUTION.md).

## License

MIT OR Apache-2.0, at your option ([LICENSE-MIT](LICENSE-MIT), [LICENSE-APACHE](LICENSE-APACHE)).
