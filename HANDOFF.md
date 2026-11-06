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

## Next up

See PLAN.md "Next". Immediate: viewer chrome polish, release APK, GitHub master branch + issue + discussion.
