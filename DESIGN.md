# Artefact design rules

This is how Artefact looks, moves and feels, and the rules a change has to follow to fit in. If a
screen you are building needs something this file does not cover, copy the closest existing screen
and ask in the pull request.

Everything named here lives in `android/app/src/main/java/com/chethan616/clearpdf/ui/`:
`theme/` for colour and type, `glass/` for the liquid glass, motion and haptics.

## The idea

- The document, note or picture is the hero. Chrome floats over it as glass and gets out of the way.
- One action per place. A screen has one obvious next step (the blue pill, the check mark).
- Everything responds to the finger: a press scales, a toggle ticks, a menu unfolds from its button.
- Nothing is hard coded twice. Colours, type, glass, springs and haptics come from the shared
  objects below, so Settings can change them everywhere at once.

## Colour

Accents come from `Candy` (`theme/ClearTheme.kt`). They are the same in light and dark themes.

| Name | Use |
| --- | --- |
| `Candy.Sky` | The primary action: the action pill, the done check, selected chips, sliders, links |
| `Candy.Tomato` | Destructive actions (Delete), errors, "not found" |
| `Candy.Lemon` | A value that differs from its default (an edited slider value, Reset) |
| `Candy.Mint`, `Lime`, `Grape`, `Bubblegum`, `Tangerine` | Tool and branch identity colours |

Surfaces do not use Candy. They use:

- `clear.ink`, `clear.inkSoft`, `clear.inkFaint` for text on the app's ground (they flip with the theme);
- `CardTone`s (`clear.tone(Tone.Paper)`, `Tone.Lavender`, `Tone.Sage` …) for desk cards: a soft
  gradient, the ink to write on it and the card's own accent for its title;
- `pdfColors` only on the tool pages that follow the PDF reference layout.

Rules:

- Never write `Color(0xFF…)` for an accent. If you need a new one, add it to `Candy` with a comment
  saying what it is for.
- Text on a filled Candy colour is white, except on `Candy.Lemon`, where it is near-black.
- Check both themes. A screen is not done until it reads well on the light sky and the dark sky.

## Type

All interface text is Plus Jakarta Sans through `ClearType`. Use the style, not a font size:

| Style | Where |
| --- | --- |
| `Hero`, `Display` | Onboarding and empty states |
| `Headline` | A tool page's large title |
| `Title` | A top bar's title, a sheet's title |
| `BodyStrong` | Button labels, a row's main text |
| `Body` | Reading text, text fields |
| `Label` | Secondary labels, chip text, values |
| `Caption` | Dates, sizes, hints under things |
| `Eyebrow` | Small uppercase section labels |

The display faces are reserved for the places that already use them: the desk titles (`DeskAll`,
`DeskNotes` …, one per shelf, all the same size so switching shelves never moves the layout),
`ToolsTitle` (Fraunces, only on the Tools screen), `ToolWord`, `SerifWord` and `DotTitle` on cards.
Do not introduce a new face; if you draw text on a canvas (the mind map does), load Plus Jakarta Sans
from `R.font.jakarta` so it matches.

Rules:

- No `fontSize = 14.sp` on a `Text`. Pick a `ClearType` style and `.copy()` it only for weight or a
  small size step.
- Words come from `res/values/strings.xml`, never from code (the `UiTextTest` unit test fails the
  build on English in Kotlin). Add every new string to all the `values-xx` translations too.
- Write labels the way a person would say them: "Remove password", not "Password removal".

## Liquid glass

The glass is Kyant's backdrop library (vendored in `android/backdrop`) wrapped in our own
components. A glass element blurs, refracts and tints what is behind it, so it needs a backdrop:

```kotlin
val backdrop = rememberLayerBackdrop()
Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { /* the content glass sits on */ }
    CompositionLocalProvider(LocalBackdrop provides backdrop) { /* the glass chrome */ }
}
```

Use the components, not raw `drawBackdrop`:

| Component | Use |
| --- | --- |
| `GlassCircle` | A round icon button (Back, More, Undo). Pass `tint = Candy.Sky` for the main action |
| `GlassPill` | A capsule button with words. `tint` makes it the vivid candy button |
| `GlassChip` | A small capsule (filters, choices) |
| `Modifier.glassBar()` | A floating bar or panel (toolbars, the photo editor's controls) |
| `GlassCard`, `washPanel()` | A larger frosted panel |
| `GlassMenu`, `MenuCard` | The popover menu that unfolds beside its button |
| `GlassDialog`, `GlassSheet` | Dialogs and bottom sheets |
| `GlassTextField` | A text field on glass |
| `GlassToast` | A short message |
| `GlassBackButton` | The Back of every screen |

Ink on glass: wrap content with `rememberGlassInk(...)` and use `ink.color` (or `LocalGlassInk`).
The ink turns dark or light from what is behind the glass, so icons stay readable over any page.

Customisation lives in Settings ▸ Liquid glass & motion and is read through `GlassSettings`
(`glass/Style.kt`): the glass look (frost, lens depth and strength, tint, colour, brightness, rim
light, depth, prism edge), adaptive luminance, and the wallpaper. Presets are `GlassStyle.Default`,
`Frosted`, `Crystal`, `Lite` and the menu's `MenuFrost` / `MenuFrostDark`.

Rules:

- Never fake glass with a translucent colour. If it floats, it is a glass component.
- Do not read `GlassStyle` values directly in a screen. Components read `GlassSettings`, so a user's
  settings and adaptive luminance apply everywhere.
- Glass over a dark editor (the photo editor) uses `rememberGlassInk(Color.White)`.

## Motion

Springs and curves come from `glass/Motion.kt`, scaled by the user's Settings ▸ Motion:

| Spec | Use |
| --- | --- |
| `GlassMotion.unfold()` / `fold()` | A menu or popover opening and closing |
| `GlassMotion.morph()` | A dialog popping in |
| `GlassMotion.pop()` | A small bouncy reaction |
| `GlassMotion.shape()` / `shapeBack()` | A shape changing (a pressed button, a thumb) |
| `Fluid.quick()`, `Fluid.fade()`, `Fluid.dismiss()` | Fades and quick tweens |
| `Fluid.Ease` | Screen pushes and sheets |

Rules:

- No `tween(300)` or `spring(0.8f, 400f)` in a screen. Use a named spec so Settings applies.
- Leaving is faster than arriving, and never bounces.
- Menus unfold beside the button that opened them. There is no morph from the button.
- Animate state the user caused. A static page does not breathe or shimmer.

## Haptics

`rememberFeel()` gives a function that plays a `Feel` (`glass/Haptics.kt`). It honours the phone's
touch-vibration setting.

| Feel | When |
| --- | --- |
| `Tap` | A button press (glass buttons already play it) |
| `Tick` | A detent: a slider notch, picking an item, snapping to a guide |
| `ToggleOn` / `ToggleOff` | A switch, folding and unfolding |
| `Rise` / `Fall` | Something added or removed |
| `Press` | A long press or the start of a drag |
| `Confirm` / `Reject` | A drop that worked, an action refused |
| `Spin` | A quarter turn |

Rules:

- Every slider ticks as it moves and taps firmly through zero (see `FeltSlider` in the photo editor).
- Do not play a feel on every frame of a drag; play it when something changes state.

## Layout and touch

- Touch targets are at least 44dp. Bars are 44 to 72dp tall.
- Screen edges: 12 to 16dp gutters for chrome, 24dp for tool page content.
- Top bars sit under the status bar (`statusBarsPadding()`); bottom bars over the navigation bar
  (`navigationBarsPadding()`), and over the keyboard with `imePadding()` when they hold a text field.
- Corners: capsules (`ChromeCapsule`) for controls, `RoundedRectangle(20 to 30dp)` for panels.
- Use `Modifier.pressable(onClick, pressedScale = 0.9f)` for anything tappable that is not already a
  glass component, so it scales under the finger.

## Screen patterns

Reuse these instead of designing a new layout:

- **Tool page** (`ToolPage` in `ui/ToolScreens.kt`): Back, a `Headline` title, the file card
  (`OutlinedFileCard`), the options (radio rows, or underlined tabs as in Export and Encryption),
  and the blue `ActionPill` at the bottom right. Compress, Export and Encryption are the models.
- **Editor over content** (photo editor, mind map): the content fills the screen; a glass top bar
  (cancel or back, the title or mode switch, done); a glass control bar at the bottom.
- **Desk card** (`ui/home/Cards.kt`): `cardPaint(tone)`, a `CardTitle`, a preview, the date in the
  corner.
- **Settings row**: `SettingRow` with a coloured icon tile.

Do not add a new tile to the Tools grid for something an existing tool already does (PDF to text
is an option of Export; text to PDF is a source in Create PDF; finding form fields is part of Fill
& Sign). Put it inside that tool.

## Before you open a pull request

- [ ] Light and dark theme both checked on a phone.
- [ ] Glass, colour, type, motion and haptics come from the shared objects above.
- [ ] Strings are in `strings.xml` and every `values-xx` file.
- [ ] Screenshots or a short screen recording are in the pull request.
