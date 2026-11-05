# Artefact: plan and roadmap

Artefact (formerly ClearPDF) is a free, open-source document app for Android: PDFs today, then Word,
PowerPoint, Excel, notes, pictures and voice, all on one engine. No ads, no accounts, nothing leaves
the phone.

This file is the roadmap. `HANDOFF.md` is the short "where we are right now" for the next session.

## Ground rules

- **Package names stay**: app id `com.chethan616.clearpdf` (so it updates ClearPDF installs), Kotlin
  package `com.chethan616.clearpdf`. Only the product name changed.
- **Signing keys per channel** (never unify): GitHub/F-Droid CI key `92:FA:50…` (repo secrets), Play
  upload key `2C:18:8D…` (`key.properties` + `clearpdf-upload-key.jks`, local only, git-ignored).
- **Design**: the mocks in `D:\ClearPDF-latest\design\` are the spec. Liquid glass (Kyant backdrop)
  + Material 3 Expressive motion and loaders. Apple HIG colours, more playful. See "Design system".
- **Commits** as `chethan616 <chethankrishna2022@gmail.com>`, no AI trailers.
- **Credit** the engine's authors in NOTICE / Licences (MIT OR Apache-2.0 requires it), but public
  posts talk about "open-source tools", not a specific project.

## Design system (`ui/theme/ClearTheme.kt`, `ui/glass/*`)

| Token | Light | Dark |
|---|---|---|
| Wallpaper | sky gradient `#9AB4DC → #B3C5E8 → #A8BEE3` + blooms (aqua, lilac, pink, periwinkle) | `#0E1A20 → #13262C` + teal / slate / plum blooms |
| Hero titles | white + soft glow | white |
| Ink | `#172033` | `#F1F5F9` |
| Chip (chosen) | lemon `#EEF27E` | same |
| Accents (`Candy`) | Apple system hues: red `#FF5B4F`, orange, yellow, green, mint, blue `#0A84FF`, indigo, pink | same |
| Card tones (`Tone`) | Sage (lists), Paper (notes), Lavender (voice), Butter (Files), Orchid (Pdf), Blush, Sky, Peach | same hues, 22 % deeper |

Type: Plus Jakarta Sans (all UI), Doto (list titles, dot-matrix), DM Serif Display (collection
words). All OFL, in `res/font`, licences in `assets/licences`.

Rules: glass always refracts what is really behind it (`LocalBackdrop` provided by the layer
below, never passed by hand). Bounce only on draw-time properties. One loader everywhere:
`GlassLoader` (M3 Expressive `LoadingIndicator` in a glass circle). Mascots (`ui/mascot`, MIT
bot-avatars) for empty, waiting and error states only.

## Done

- [x] V2 base: Rust engine + UniFFI bridge, glass UI kit, viewer, tools, signing, forms, OCR.
- [x] Upstream PRs: hide bars on scroll (#9), double-tap-drag zoom (#14), live signature trust
      refresh (Android half of #22; its engine half is older than ours).
- [x] Rename to Artefact (strings in every language, icons, splash, Pictures/Artefact folder).
- [x] New launcher icon (adaptive + themed monochrome) and Play/F-Droid icon from `artefact.png`.
- [x] Theme rebuild to the mocks (wallpaper, tones, type, chips).
- [x] Desk (home): header mark + menu, title per chip, chips, bento of cards, + fan, mic recorder.
- [x] Notes: text (light Markdown), to-do lists, voice memos, photos; JSON store in `files/notes`.
- [x] Welcome (onboarding), 4 pages, shown once, "Show the welcome again" in Settings.
- [x] Quick Preview (floating card over the calling app) with Expand / Share / More / Close.
- [x] M3 Expressive loader in a glass circle replaces every spinner.

## Next (UI first)

- [ ] Viewer chrome to the new look (floating glass top bar, dock), check every tool sheet.
- [ ] Files / Tools / Settings polish against the mocks; Collection deck (mock 2) for Pdf & notes.
- [ ] Signature validation badges drawn by the viewer (Acrobat-style tick), never in the file.
- [x] Translations: ar, de, es, fr, it, pt-BR complete + **pt** and **ru**.
- [x] Folder restructure, README/NOTICE/ATTRIBUTION/fastlane to Artefact (9 store languages).
- [ ] Release build, `master` branch on GitHub, "Building the version 2" issue + discussion.

## Folder layout

```
android/          Gradle project (app, backdrop)
engine/           Rust workspace: crates/ (PDF engine), mobile/ (libartefact.so bridge),
                  assets/ (fonts, icons, OCR models), vendor/ (patched crates)
docs/             design notes and README pictures (docs/media)
fastlane/         store listings (9 languages)
tools/            i18n helper
```

## Later: one engine for every document

A single Rust engine (`artefact-engine`) behind the same UniFFI bridge:

1. **PDF**: the current engine.
2. **Word (.docx)**: model + layout + editing, ideas from storytold/wordcraft (Markdown too, which
   also powers notes and lists).
3. **PowerPoint (.pptx)**: slides render + present, ideas from storytold/deckcraft.
4. **Excel (.xlsx)**: grid, formulas, charts, ideas from storytold/gridcraft.
5. **Pictures** (storytold/lightcraft) and **voice** (storytold/soundcraft) for notes.

Quick Preview's intent filter grows with each format (DOCX, XLSX, PPTX, images, text).
