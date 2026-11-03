<p align="center">
  <img src="assets/app-icon/pdfcraft-1024.png" alt="PdfCraft's app icon" width="128">
</p>

<h1 align="center">PdfCraft for Android</h1>

<p align="center">
  <b>An Android native open-source PDF reader and editor written in Rust, built on the same engine as the desktop app, <a href="https://github.com/storytold/pdfcraft">PdfCraft</a>, with a Kotlin/Jetpack Compose UI.</b><br>
  Many credits to the original maintainers, <a href="https://github.com/storytold">storytold</a>.
</p>

<p align="center">
  <a href="https://f-droid.org/en/packages/io.github.artcraftandroid.pdfcraft/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="60"></a>
  <a href="https://github.com/artcraft-android/pdfcraft-android/releases"><img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="60"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/artcraft-android/pdfcraft-android"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60"></a>
  <br><sub>Each release has two APKs: the plain one for most phones, the <code>-armeabi-v7a</code> one for 32-bit ARM phones.</sub>
</p>

<p align="center">
  <img alt="Rust engine" src="https://img.shields.io/badge/engine-Rust-lightblue?style=flat&logo=rust&logoColor=white">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/UI-Kotlin%20%C2%B7%20Jetpack%20Compose-blue?style=flat">
  <img alt="Minimum: Android 10 (API 29)" src="https://img.shields.io/badge/min-Android%2010%20%28API%2029%29-blue?style=flat">
  <img alt="Target: Android 16 (API 36)" src="https://img.shields.io/badge/target-Android%2016%20%28API%2036%29-blue?style=flat">
  <img alt="License: MIT OR Apache-2.0" src="https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue?style=flat">
  <img alt="Status: beta" src="https://img.shields.io/badge/status-beta-lightblue?style=flat">
</p>

<p align="center">
  <a href="#reading">Reading</a> ·
  <a href="#navigate-long-documents">Navigate</a> ·
  <a href="#comments">Comments</a> ·
  <a href="#forms--fill--sign">Forms &amp; Fill &amp; Sign</a> ·
  <a href="#edit-pdf">Edit PDF</a> ·
  <a href="#read-aloud">Read aloud</a> ·
  <a href="#tools">Tools</a> ·
  <a href="#home">Home</a> ·
  <a href="#light-theme">Light theme</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="#planned-features">Planned features</a> ·
  <a href="#building-instructions">Building</a>
</p>

<br>

<p align="center">
  <img src="docs/images/viewer.png" alt="The PdfCraft Showcase open on its cover: a deep blue and orange gradient with the title Showcase and 'A tour of the page, glyph by glyph.', the page pill at 1, the top bar with back, the Read aloud switch, Find, Share and More, and the quick toolbar: Edit PDF, Comment, Highlight, Draw, Fill & Sign, Export PDF and More tools" width="250">
  <br>
  <sub>The PdfCraft Showcase, a 13-page specimen PDF, open on its cover.</sub>
</p>

<br>

## Reading

<p align="center">
  <img src="docs/images/scripts.png" alt="The Scripts of the World page with the bars hidden: Arabic, Hebrew, Devanagari, Thai, Greek, Cyrillic, Chinese, Korean, IPA, Armenian, Georgian and Tamil samples, vertical Japanese in the margin, mixed-direction text and colour emoji, with the Mathematics page below" width="250">
  <br>
  <sub>Twelve writing systems, vertical Japanese and colour emoji, with the bars hidden by a tap.</sub>
</p>

<br>

<table align="center">
<tr>
<td align="center"><img src="docs/images/reading-mode.png" alt="Reading Mode on the foreword: the heading Why a specimen? and its paragraphs reflowed in large type to the width of the screen, under the viewer's top bar" width="250"></td>
<td align="center"><img src="docs/images/night-mode.png" alt="Night Mode: the Scripts of the World and Mathematics pages shown as light text on black" width="250"></td>
</tr>
<tr>
<td align="center"><sub>Reading Mode: text reflowed to the screen</sub></td>
<td align="center"><sub>Night Mode, from View settings</sub></td>
</tr>
</table>

<br>

## Navigate long documents

<table align="center">
<tr>
<td align="center"><img src="docs/images/pages.png" alt="The Pages grid: two columns of page thumbnails numbered 1 to 8, the current page outlined in blue, with Edit PDF and Crop pages in the bar" width="250"></td>
<td align="center"><img src="docs/images/bookmarks.png" alt="Bookmarks and Table of Contents over the cover: the Contents tab open on the document's outline, from Contents and Why a specimen? to Review and Markup and Interactive Form" width="250"></td>
</tr>
<tr>
<td align="center"><sub>Pages: every page at a glance</sub></td>
<td align="center"><sub>The outline, and bookmarks of your own</sub></td>
</tr>
</table>

<br>

## Comments

<table align="center">
<tr>
<td align="center"><img src="docs/images/comment-panel.png" alt="The Review and Markup page with a sticky note selected and ringed in blue, and the comment panel below it: 3 of 17 with arrows, a thread of two comments by PdfCraft Reviewer and Layout Editor, and a Reply box" width="250"></td>
<td align="center"><img src="docs/images/comment-list.png" alt="The full-screen comment list titled 17 comment threads, grouped by page: notes, a highlighted phrase with its comment, a threaded note with a reply and a text box, each with Reply" width="250"></td>
</tr>
<tr>
<td align="center"><sub>A comment's thread, ‹ 3 of 17 › and Reply</sub></td>
<td align="center"><sub>The Comment List, by page</sub></td>
</tr>
</table>

<br>

## Forms & Fill & Sign

<table align="center">
<tr>
<td align="center"><img src="docs/images/forms.png" alt="The Interactive Form page: text fields, a date, an amount, a multiline comment, checkboxes, radio buttons, a combo box, a multi-select list, a push button and a signature field, every fillable field tinted blue" width="250"></td>
<td align="center"><img src="docs/images/fill-sign.png" alt="Fill and Sign on the Interactive Form page: a drawn signature placed in the signature field, Done at the top left with undo and redo, and the Sign tool in the bar below" width="250"></td>
</tr>
<tr>
<td align="center"><sub>Every fillable field tinted blue</sub></td>
<td align="center"><sub>Fill & Sign: a signature placed with a tap</sub></td>
</tr>
</table>

<br>

## Edit PDF

<p align="center">
  <img src="docs/images/edit-pdf.png" alt="Edit PDF on the Review and Markup page: a paragraph selected in a blue box with round handles, a menu with Edit text, Select all and Delete above it, and the formatting bar below: bold, italic, underline, size, font, colour and alignment" width="250">
  <br>
  <sub>Edit PDF: tap a paragraph to edit its text, change its style, move it or rewrap it.</sub>
</p>

<br>

## Read aloud

<table align="center">
<tr>
<td align="center"><img src="docs/images/read-aloud.png" alt="Read aloud playing on the Code and Images page: the running header washed yellow with the current word stronger, the Read aloud switch turned on, and the player at the bottom with a progress bar, back 15 seconds, pause, forward 15 seconds and 1.0x" width="250"></td>
<td align="center"><img src="docs/images/read-aloud-player.png" alt="The full Read aloud player: a picture of the page being read, the file name pdfcraft-showcase, a progress bar with 20:20 and -5:03, back 15 seconds, pause and forward 15 seconds, then Voice and 1.0x" width="250"></td>
</tr>
<tr>
<td align="center"><sub>Read aloud, the words marked as they're read</sub></td>
<td align="center"><sub>The full player: time left, Voice and speed</sub></td>
</tr>
</table>

<br>

## Tools

<table align="center">
<tr>
<td align="center"><img src="docs/images/more-tools.png" alt="The More tools sheet over the Scripts of the World page: Combine files, Compress PDF, Set password, Organize pages, Crop pages, Recognize text, Create PDF, Text, Scan and Read aloud, then Customize toolbar" width="250"></td>
<td align="center"><img src="docs/images/compress.png" alt="Compress PDF: the file card for pdfcraft-showcase, PDF, 2.8 MB, then High, Medium and Low compression with Medium chosen, and the Compress button" width="250"></td>
</tr>
<tr>
<td align="center"><sub>More tools, and Customize toolbar</sub></td>
<td align="center"><sub>Compress PDF, one of the tools that make a new file</sub></td>
</tr>
</table>

<br>

## Home

<table align="center">
<tr>
<td align="center"><img src="docs/images/home.png" alt="Home: Welcome back, Start with a tool with Open file, Edit PDF, Read aloud, Fill & Sign, Create PDF and Organize pages, then Recent, Starred and On device with application and pdfcraft-showcase listed, the add button, and the Home, Files, Tools and Search tabs" width="250"></td>
<td align="center"><img src="docs/images/tools.png" alt="The Tools tab: All, Read, Create, Edit and Convert, and cards for Fill & Sign, Add comment, Read aloud, Create PDF, New scan, Combine files, Organize pages, Crop pages, Edit PDF, Compress PDF, Export PDF and Set password" width="250"></td>
</tr>
<tr>
<td align="center"><sub>Home: the tools, Recent, Starred and On device</sub></td>
<td align="center"><sub>The Tools tab: every tool, by what it does</sub></td>
</tr>
</table>

<br>

## Light theme

<table align="center">
<tr>
<td align="center"><img src="docs/images/light-viewer.png" alt="The viewer in the light theme on the Scripts of the World page, with the white top bar and quick toolbar" width="250"></td>
<td align="center"><img src="docs/images/light-home.png" alt="Home in the light theme: the tools on pale tiles, Recent with application and pdfcraft-showcase, and the tabs" width="250"></td>
</tr>
<tr>
<td align="center"><sub>The viewer in the light theme</sub></td>
<td align="center"><sub>Home in the light theme</sub></td>
</tr>
</table>

<br>

## Architecture

<p align="center">
  <img src="docs/images/architecture.png" alt="Architecture diagram: the PDF user chooses a document in the Android experience (Home library, Open document, App state, PDF viewer, Reading tools, Page navigation, Forms and signing, PDF editing, Comment tools), which calls the Rust engine through the Android engine bridge; the document runtime (PDF engine, page rendering, PDF object parser) and its PDF operations (create and extract, document editing, form handling, annotations) and extended capabilities (accessibility checks, automation tools, office export, PDF signatures)" width="700">
  <br>
  <sub>How the app and the engine fit together. Generated with <a href="https://gitdiagram.com/artcraft-android/pdfcraft-android">GitDiagram</a>.</sub>
</p>

<br>

## Planned features

PdfCraft for Android is in beta.

| Area | Status |
|---|---|
| Open PDFs from files, other apps and Recent; Starred, On device, Files and Search | ✅ |
| Reading: continuous and single page, Reading Mode, Night Mode, find, text selection, links, bookmarks and contents, attachments | ✅ |
| Read aloud with the phone's voice, the words marked as they're read, and a full player | ✅ |
| Comments: notes, voice comments, highlight, underline, strikethrough, drawing (snap to shape), text; the Comment List and threads | ✅ |
| Forms, and Fill & Sign: text, marks and a drawn or imported signature; form JavaScript (calculations, field checks, buttons) | ✅ |
| Digital signatures: sign with a digital ID (a .p12 / .pfx file, or one made in the app), check a document's signatures, trust signers | ✅ |
| Edit PDF: edit text, add text and images, move, resize and turn pictures | ✅ · cropping a picture ⬜ |
| Organize and crop pages | ✅ |
| Compress, combine, set password, create PDF (images, text, office documents), scan | ✅ |
| Export to Word, RTF, Excel, PowerPoint, JPEG and PNG | ✅ · Word 97 ⬜ |
| Recognize text | ✅ (the Latin alphabet without accents) |
| A customizable quick toolbar, upright and sideways layouts, light and dark themes | ✅ |
| A mouse and a keyboard: the wheel scrolls and zooms, a drag selects text, shortcuts; zoom buttons on DeX and Chromebooks | ✅ · a tablet layout ⬜ |
| Extra Features in Preferences, off until you turn them on: the status bar in documents, the number of search results | ✅ |
| Translations: all of the app's text is ready to translate ([how](#translating)) | ✅ · Spanish, German, French, Italian, Brazilian Portuguese and Arabic ✅ · other languages ⬜ |
| AI assistant, cloud storage, sharing for review | 🚫 PdfCraft is a local app |

<sub>✅ works today · 🚧 in progress · ⬜ not started · 🚫 not planned</sub>

<br>

## Building Instructions
*One Gradle command builds the whole app: it compiles the Rust engine with [cargo-ndk](https://github.com/bbqsrc/cargo-ndk) and generates the Kotlin bindings.*

* Clone this repository
* Make sure you have at least 3 GB of free disk space
* Recognize text's models (12 MB, CC-BY-SA-4.0) are in `assets/models/`; `apps/pdfcraft-android/fetch-models.sh` downloads them again if you need to
* enter `apps/pdfcraft-android/android` and run `./gradlew assembleRelease`, or open that folder in Android Studio and press Run.

Build time dependencies:

<details>
  <summary>required tools</summary>

  ```
      rustup (it installs the Rust release and Android targets rust-toolchain.toml names)
      cargo install cargo-ndk --version 4.1.2 --locked
      Android SDK (API 37) and NDK 29.0.14206865
      JDK 17 or newer
  ```
</details>

*Builds are only tested on Windows 11.*

<br>

## Translating

All of PdfCraft's text is in one file:
[`apps/pdfcraft-android/android/app/src/main/res/values/strings.xml`](apps/pdfcraft-android/android/app/src/main/res/values/strings.xml).
To add a language:

* copy it to a `values-<language>` folder beside it: `values-es/strings.xml` for Spanish, `values-pt-rBR/strings.xml` for Brazilian Portuguese
* translate the text between the tags; keep each `name`, the placeholders (`%1$s`, `%2$d`, which you may move) and escapes such as `\'`
* leave out anything you haven't translated yet: it shows in English
* open a pull request

The store listing is in `fastlane/metadata/android/en-US/`; a copy in a folder for your language (`es-ES`, `pt-BR`)
translates it too. On Android 13 and newer, PdfCraft's language can be set on its own under Settings ▸ Apps ▸
PdfCraft ▸ Language. A few messages from the PDF engine, such as some errors and the names in Undo, are English
for now.

<br>

## License

[MIT](LICENSE-MIT) OR [Apache-2.0](LICENSE-APACHE), at your option. PdfCraft for Android is not affiliated with Adobe;
Acrobat is a trademark of Adobe Inc.

<br>

## Misc info for myself

Try to use [semantic commit](https://kapeli.com/cheat_sheets/Semantic_Commits.docset/Contents/Resources/Documents/index)
messages. Use `feat:` / `fix:` / `chore:` / `docs:` / etc.
