# Handoff

Read this first after a `/clear`. The roadmap is `PLAN.md`.

## Where things are

- Repo: `D:\clearPDF_V2`, branch `clearpdf-v2`; remote `upstream` = artcraft-android/pdfcraft-android.
- Android project: `android/`; Rust workspace: `engine/` (bridge crate `engine/mobile`, package
  `artefact-mobile`, library `libartefact.so`; engine crates keep their `pdfcraft-*` package names).
- Build (phone is arm64): `cd android; ./gradlew installDebug -Partefact.abis=arm64-v8a`
  (emulator: `x86_64`). Debug id `com.chethan616.clearpdf.debug`. Release:
  `assembleRelease -Partefact.abis=arm64-v8a` (signed with the local Play upload key, `key.properties`).
- Phone: `adb -s 5bdd59db` (OnePlus CPH2487, Android 16, 1240×2772 @560dpi). In Git Bash set
  `MSYS_NO_PATHCONV=1` before `adb shell …/sdcard…`. `pm clear` is blocked; reset with
  `adb shell "run-as com.chethan616.clearpdf.debug rm -rf shared_prefs files/notes"`.
- A sample PDF is on the phone: `/sdcard/Download/Quarterly-Report.pdf` (MediaStore id 1000144849).
  Quick Preview test: open Settings, then
  `adb shell am start -a android.intent.action.VIEW -t application/pdf -d content://media/external/file/1000144849 --grant-read-uri-permission -n com.chethan616.clearpdf.debug/com.chethan616.clearpdf.preview.QuickPreviewActivity`

- **Judge smoothness on the `fast` build, never debug**: `./gradlew installFast -Partefact.abis=arm64-v8a`
  (same `.debug` id and data, debug key, R8, not debuggable, Compose's baseline profiles). Then
  `adb shell cmd package compile -f -m speed-profile com.chethan616.clearpdf.debug` to compile the
  profile at once. A debuggable build runs Compose several times slower.

## Office documents (Word and PowerPoint in; Excel next)

Plan and status: PLAN.md "Office documents". How the Word bridge works:

- **Engine**: storytold/wordcraft pinned at `729cd0a` (git deps). Two crates vendored with patches
  (`engine/vendor/README.md`, section WordCraft): `wordcraft-engine` (font store behind `font-store`,
  which Artefact leaves off: no ureq/rustls/ring; check with
  `cargo tree -p artefact-mobile --target aarch64-linux-android -e normal`) and `wordcraft-fonts`
  (`/system/fonts` on Android; its bundled faces `include_bytes!` from `engine/assets/fonts`, so
  Source Sans 3 / Source Serif 4 live there). `[patch.crates-io] krilla` = craft-libs `c5a1cbf`
  (Word's PDF export needs the suite's krilla, and Cargo ignores a dependency's own patches).
  Upstream draft (not filed, don't file without the owner): `docs/upstream/wordcraft-android.md`.
- **Bridge**: `engine/mobile/src/office.rs` is the shared part: the `OfficeDoc` trait (info, render,
  text layer, links, thumbnail, release_memory), `sniff` (zip part names / CFB streams / `{tf`),
  `text_glyph` (a glyph box through an `Affine`), ids from `OFFICE_IDS` = 2^40. `word.rs` and
  `slides.rs` implement it; `MobileSession` keeps `office: HashMap<u64, Arc<dyn OfficeDoc>>`.
  Slides: `deckcraft_pptx::import`, `deckcraft_render::render_slide` (tiles via `RenderOpts.offset`
  and `size`, 2 threads), text from `place_slide(..).texts` (runs' glyphs + `cells` chars, through
  `PlacedText.transform`), links from `place_slide(..).links` (URL actions).
- **Word** (`engine/mobile/src/word.rs`): `MobileSession.open(path)` sniffs the bytes
  (`word_extension`: zip with `word/document.xml`, ODF text, CFB `WordDocument`, `{
tf`; an encrypted
  CFB only when the name says .docx) and opens a `WordDocument` (wordcraft `Session`) under an id
  from `WORD_IDS` = 2^40. Every per-document call checks `self.word(doc)` first: `document`,
  `render_page` (whole page or tile via `render_region` with a translate+scale `Affine`), `links`
  (web/mail runs only), `close`, `documents`; comments/widgets/attachments/outline are empty for
  Word. Text: `page_text` builds glyph boxes from `page_display` (`Draw::Glyphs`, recursing into
  `Figure`/`Clip`; turned/rotated boxes left out) and lays them out with
  `pdfcraft_render::text::layout`, so `word_at`, `text_rects`, `find_in_page`, `reading_blocks`
  are the PDF code. Headers are drawn undimmed (`dim_header: false`); proofing is off
  (`session.view.proofing = false`: no spelling squiggles in a reader). `DocumentInfo.kind` tells the
  app; `read_only_reason` is set (no editing yet). Errors reuse `MobileError` (NeedsPassword /
  WrongPassword for protected .docx).
- **Android**: `engine/DocumentTypes.kt` (PDF/Word/slides types and extensions, `title`, `mimeOf`);
  `OpenDocument.isOffice` / `isSlides`; `QuickTool.WORD` (Read Aloud only) on the bar, empty More tools, More sheet
  without Edit/Print/comments/signatures/PDF tools; `safeName` keeps Word extensions (the engine
  needs .docx for encrypted files); manifest SEND and Quick Preview VIEW filters list the Word types.
- Tests: `cargo test -p artefact-mobile` (Word tests write a .docx with wordcraft, open, render,
  search, select, read aloud, thumbnail). To look at any file's pages on the PC:
  `ARTEFACT_SAMPLE=<path> cargo test -p artefact-mobile --lib render_sample -- --ignored --nocapture`
  writes `<path>.<page>.<w>x<h>.rgba` (convert with Pillow).
- Sample on the phone: `/sdcard/Download/Artefact-Word-Sample.docx` (MediaStore id 1000145153;
  headings, table, link, header/footer, 2 pages, Cyrillic/Greek/Japanese/Hindi line). Made by a
  small Python script (zipfile, hand-written OOXML). Quick Preview test:
  `adb shell am start -a android.intent.action.VIEW -t application/vnd.openxmlformats-officedocument.wordprocessingml.document -d content://media/external/file/1000145153 --grant-read-uri-permission -n com.chethan616.clearpdf.debug/com.chethan616.clearpdf.preview.QuickPreviewActivity`
- Size: with WordCraft the arm64 fast APK went from 49 MB to 76 MB (libartefact.so is now 58 MB). Look at what is big (craft-proof's word lists? the .doc/ODT/RTF readers?) before
  PowerPoint adds its codecs.
- Build note: the first release build of wordcraft is long (~20 min on this PC); `assembleFast`
  compiles the Rust release profile (thin LTO, one codegen unit).

## Motion rules (2026-11-06)

- **Glass things spring, screens glide** (sheets on their own fixed spring, see Gotchas). Glass controls AND glass popovers (menus, dialogs, toasts, hint toasts, the recorder pill) use
  `GlassMotion` springs (`ui/glass/Motion.kt`:
  `morph`, `pop`, `unfold`/`unfoldLag`, `fold`, `rise`), tuned by Settings ▸ Button motion
  (`MotionSettings.damping/stiffness`). Screen-to-screen transitions use `Fluid`: tweens on UIKit's
  curve `(0.32, 0.72, 0, 1)`, never past the target, ignoring the motion setting. Screens push
  420 ms / pop 360 ms (`RouteHost` in `ClearApp.kt`); do not change these, the user likes them.
- **Overshoot rule**: a spring may overshoot only draw-time values (scale, translation, rotation) or
  layout values clamped positive (`coerceAtLeast`); alpha is always clamped to 0..1. A spring past 1
  once crashed Quick Preview with negative padding.
- **Every glass part follows Settings ▸ Liquid glass** (`GlassSettings.style` through `glassEffects`)
  and Button motion. New glass uses `glass`/`glassControl`/`GlassChip`, never a hand-rolled `drawBackdrop`.
- A glass thing can't refract its own layer: put the glass in a sibling layer (`GlassCard`, `GlassHost`
  in `ui/glass/Surfaces.kt`) so chips on a card refract the wallpaper behind the card.
- Flat `pressable` sinks to 0.97 on critically damped springs (no jelly).
- Overlays animate out: `GlassOverlay(animatesOut = true)` keeps an entry in the portal after its
  caller leaves; read `rememberOverlayAppear()` (or `LocalOverlayPresence`) and call `done()`.
- Read moving values in draw/layout, never composition (pager offsets, player position, morphs). That
  includes fades: an `animateFloatAsState` read in composition recomposed the whole dock every frame,
  rebuilding every fan chip's `drawBackdrop` (that was the + lag on a busy desk). Use an `Animatable`
  read in `graphicsLayer {}`, plus `derivedStateOf` for "is it shown" flags.
- **Performance rules for busy screens** (welcome, fan): give anything that animates forever (mascots,
  dots) its own `graphicsLayer {}` so its redraws stop at that layer; pause what is off-screen; use
  `CompositingStrategy.ModulateAlpha` on fading content (the default goes offscreen below alpha 1, a
  full-screen texture for a scrim); cache costly still content (22 px glow text shadows, glass pills)
  with `Offscreen` only while it is still or just moving, never when it rotates (soft sampling);
  compose items of a group one by one when each carries live glass (`FanChip`).
- The dock capsule (+ and mic ⇄ recorder) uses `GlassMotion.shape()` / `shapeBack()` (stiffness 105 / 125,
  slow on purpose so the swell and rebound are seen); the other glass springs stay brisk.

## Code map (new UI)

| What | Where |
|---|---|
| Tokens, type, tones | `ui/theme/ClearTheme.kt` |
| Wallpaper, glass stack, overlay portal | `ui/glass/Backdrops.kt` |
| Glass controls, sheets, menus, loader | `ui/glass/*` (`Loader.kt` = M3 Expressive in glass) |
| Glass chips (filter chips, card chips, check pills, lemon chip) | `GlassChip` in `ui/glass/Buttons.kt` (wraps `glassControl`, same glass as Back/More) |
| Dark theme ("Midnight Aurora", jewel card tones) | `DarkClear` / `DarkTones` in `ui/theme/ClearTheme.kt`; viewer surfaces in `ui/Theme.kt` |
| Routing (welcome / desk / files / tools / settings / note / viewer) | `ui/ClearApp.kt` |
| Desk | `ui/home/HomeScreen.kt`, cards `ui/home/Cards.kt`, + and mic `ui/home/CreateDock.kt` |
| Notes store | `notes/Notes.kt` (JSON in `files/notes`, media in `files/notes/media`) |
| Note editor | `ui/notes/NoteEditor.kt` |
| Thought of the day | `notes/Thoughts.kt` (`thoughtFor`), `DeskItem.Thought` + `ThoughtCard`, `AppViewModel.openThought/closeNote` |
| Welcome | `ui/onboarding/Onboarding.kt` |
| Mascots | `ui/mascot/*` (copied from rewordium; never edit rewordium itself) |
| Quick Preview | `preview/QuickPreviewActivity.kt` + `preview/QuickPreview.kt` |
| Viewer | `ui/viewer/*`; its glass refracts the pages (`pagesBackdrop` in `ViewerScreen.kt`) |
| Viewer sheets (iOS style) | `PdfSheet`, `SheetHeader`, `SheetSection`, `SheetRow`, `SheetCircleButton` in `ui/Components.kt` |
| Header glass buttons | `HeaderGlassButton`; buttons inside a glass bar are `BarGlyphButton` (no glass on glass) |

## Gotchas

- Apostrophes in `strings.xml` must be `\'`. Shell heredocs eat the backslash: use the Edit tool.
- `material3` is pinned to `1.5.0-beta02` in `gradle/libs.versions.toml` for `LoadingIndicator`.
- Glass can't sample other apps: Quick Preview's card is solid; the caller behind is dimmed and
  blurred with Android 12+ cross-window blur (`FLAG_BLUR_BEHIND`), when the phone allows it.
- Quick Preview copies the file into the viewer's working-copy folder first, so Expand still opens
  after the caller's temporary grant ends.

- Welcome pager: pages are NOT translated at the page level (that put neighbours' cards in view at
  rest); only each card sways, shaped to zero at rest. The Skip button (a `GlassChip`) fades before the
  last page. Continue is a `GlassPill(tintAt = ...)`: the candy colour follows the pager, read in draw.
  `Mascot(paused = true)` freezes a mascot that is off-screen (its infinite animations stop redrawing).
- Recorder pill (`CreateDock.kt`): expand overshoots in layout (clamped), shrink squeezes/stretches in
  draw. Judge it on the `fast` build; the capsule blur re-runs per overshoot frame.
- Performance rules for glass (pass 4): shaders are shared process-wide (`RuntimeShaderCache.kt`; the
  Crystal lens used to compile per node), warmed one per idle moment from `MainActivity`
  (`GlassShaderWarmup`); the press glow in `Press.kt` is shared too. The dispersion shader takes 1 / 3 / 7
  taps by how much the rim bends. `HighlightNode` re-records only when its own inputs change.
  `Modifier.throttleBackdrops { moving }` (`backdrop/BackdropThrottle.kt`) makes glass UNDER it refresh its
  sampled position at most every 100 ms while `moving()` (trailing write on settle): used by the welcome
  cards (`flyIn`, with `cache = true` on page-2 cards) and by `GlassSheet`'s content. Never put it on
  something the eye tracks closely (a fan chip would look like a sticker).
- Jelly: `GlassMotion.shape()` has a damping floor (0.46); the dock's layout overshoot is soft-capped
  (tanh) to the room left on the screen and its squash is bounded: before, a swing wider than the screen
  was clamped by the constraints and the pill froze at the extremes.
- **Bounce tweens were tried and removed** (2026-11-06): the user found them slow. Everything glass is on
  springs again. Sheets use the old ClearPDF sheet's fixed spring (0.82 / 380, never profile-scaled: at
  Jelly a scaled one shivered) with drag-down-to-dismiss; menus/dialogs/toasts/fan/+/mic use `GlassMotion`.
- **Why overlays were slow, and the fix** (measured, Crystal + Jelly, 60 Hz): with the sheet's glass removed
  entirely the long-press sheet still cost ~28 ms GPU a frame. The cost was the *scene under it*: every frame
  of anything moving redrew every glass control on the desk. Two fixes, both in the backdrop/glass layer:
  1. `GlassStage` keeps wall+screens (and chrome) as one offscreen texture while an overlay is up or chrome
     holds `GlassOverlayHost.stillHolds` (the + fan, the recorder). Sheet frames fell to ~3.5 ms GPU.
  2. `Modifier.floatingLayer` (backdrop module) records content once into an offscreen layer and animates
     only its transform/alpha/clip (an `ObserverModifierNode`; no re-record, no re-blur). Used by sheets,
     dialogs, menus, toasts, fan chips, the viewer bars and the pop menu. Don't fade content *inside* a
     floating layer with its own graphicsLayer: that dirties the texture and re-blurs every frame.
- **Desk menu is a glass pop** (`GlassMorphMenu`, `ui/glass/MorphMenu.kt`): the four-dot button becomes the
  menu, iOS 26 style (clip opens from the button circle, spring overshoot becomes a swell about the button).
  ~5 ms GPU a frame. The real button hides from tap until `onGone`.
- Settings ▸ Liquid glass & motion (`desk/GlassMotionSettings.kt`): live glass stage, glass presets + every
  `GlassStyle` value, motion presets + spring graph (step response of the `morph` spring at the current
  setting) + the six `MotionStyle` values, a cubic-bezier editor for `MotionSettings.curve` (drives
  `Fluid.Ease`, persisted as `motion_curve`) with presets, `MotionSettings.duration` scaling every `Fluid`
  tween. New strings are English-only so far (other locales fall back). Settings root: small-caps section
  labels, one row opens this page, credits as a footnote. Morph menu is layout-driven like the old app's
  ShareMorphButton (user rejected the clip-reveal pop).
- 2026-11-06 late pass (built, NOT seen on the phone: adb went unauthorized): Back beat 300 ms everywhere
  via `rememberBeat` (ScreenHeader, viewer Back, tool screens); morph menu fades in place when an item is
  chosen (no glyph over the next screen); viewer search field grows from the title pill (`GlassMotion.morph`);
  viewer dock = icon-only ink glyphs on theme glass (`glassBar` default = `clear.glass`, follows the glass
  style, shows the page), More = plain glass circle; dock⇄note toolbar morphs both ways, handing over at 96 %
  (max 600 ms); tool screens (`ToolScaffold`/`ToolPage`) stand on the Aurora in a `GlassHost` with glass
  Back, candy `GlassPill` action, `washPanel()` file cards, glass chips for the scan actions.
- Desk → note: the dock's capsule grows into the note toolbar's size (`CreateDock(intoNote)`, measured via
  `vm.noteBarSize`) on `GlassMotion.morph()`, then hands over (`vm.noteBarHidden`). ClearApp keeps the dock
  composed for it. Sheets enter on 0.74 / 380 (~3 % rise past rest).
- Viewer: no bands. Pages start at y 0 (`setPadding(top = 0f)`), status band and nav band are transparent,
  bars are `Modifier.glassBar()` (frosted, press-reactive by Settings ▸ Motion) and `GlassBars` springs them
  in/out as textures. Things that must sit below the top row use `statusPx + topPx`, not `padTop`.
- Glass circles/pills no longer bloom on a long hold unless they have a long-press job (as the chips).
  ScreenHeader's Back waits 150 ms so its press plays before the screen goes. The logo no longer spins.
- `app/src/main/baseline-prof.txt` AOT-compiles all app + backdrop code: a sideloaded release had been running
  JIT-only while the fast build was force-compiled with `cmd package compile`, so "fast beat release".
- Desk shelf titles use six faces (`ClearType.DeskAll/Notes/Todo/Pdfs/Images/Audio`): "Your Artefacts" is a
  clean bold Bricolage Grotesque (the wonky Fraunces looked bad there); Notes is Fraunces upright (`wonk` 0).
  Both variable fonts are OFL, credited in Settings and `assets/licences`.
- 2026-11-06 passes 1 to 4 (Quick Preview, thought of the day, glass chips, springs, dark theme,
  welcome/fan performance, slower dock, Crystal/Jelly/sheet fixes) build but were not seen on the phone (not
  attached): check them first when it is. Fast APK: `android/app/build/outputs/apk/fast/app-fast.apk`.
  If gradle complains a backdrop class is "outside the root directory", run `./gradlew :backdrop:clean`.

## Quick Preview corners (2026-11-07)

The card is a `Squircle` (new in `ui/glass/Shapes.kt`: kyant's continuous corners, allowed only for
content clipped inside its own moving layer) with a radius concentric with the screen's corners
(`RoundedCorner` bottom-left radius minus the 10 dp inset, 24..48 dp; 34 dp without it).

## Desk flicker (2026-11-06, fix built, not yet seen on the phone)

The owner saw the desk's cards "appear again for a second" after tapping a card. Cause found in
the code: opening a document calls `refreshRecents()` while the viewer is still rising out of the
card (`Reveal`, not yet covering), so the desk grid re-sorted behind it: the tapped card jumped to
the front and the cards' tones and tilts (picked by index) changed. Fix: `AppViewModel.deskStill`
(kept by ClearApp from the reveal's progress: 0 or 1) and `HomeScreen` keeps the last item list
while it is false, taking the new order once the viewer covers the desk or has gone. If the owner
still sees a flash on the Files/Pdf collection cards (a route change, not a document), look at
`RouteHost` next.

## Next up

See PLAN.md "Office documents" and "Next". Immediate: when Artefact is in front on the phone,
check Word and slides viewing (Files, Recent, Quick Preview with the samples), the desk flicker fix
and the squircle Quick Preview card; then slides present mode, then Excel viewing (gridcraft).
