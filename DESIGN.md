# ClearPDF design system

**Read this before you build or change any UI.** It applies to every contributor and every AI agent.
The target is Apple's iOS 26/27 *Liquid Glass*, built on Android with kyant's `backdrop` library
(the vendored `backdrop/` module; only hand-port upstream fixes into it). It should look and feel
better than Adobe Acrobat: minimal, compact, playful, smooth, and lag-free.

---

## 1. Principles (in priority order)

1. **No lag.** 60/120 fps while scrolling, switching tabs and animating. A beautiful effect that drops
   frames is a bug. Measure on a real device before you call something done.
2. **One material.** Every surface is the same liquid glass, the material the title chips use
   (`GlassTitlePill`, e.g. "ClearPDF" on Home and "Settings" on Settings). Never introduce a flat card,
   a Material surface, a ripple or a grey slab.
3. **Minimal and compact.** Show less, but make it obvious. Don't nest panels inside panels. Prefer one
   glass panel holding rows over many small cards.
4. **Physical motion.** Everything moves on springs (easing plus damping). It overshoots a little,
   settles quickly and never wobbles more than once.
5. **One tap gives one haptic.** Every tappable thing ticks once. Nothing buzzes twice.
6. **Legible everywhere.** Ink adapts to what is behind the glass (light or dark page, theme), and
   dark reader mode is covered.

---

## 2. Glass: always use the primitives

| Need | Use | Notes |
|---|---|---|
| Button / pill | `LiquidButton` | Capsule, press deformation, long-press bloom, haptic. `tint` gives the vivid "Get it" button; `surfaceColor` gives a neutral pill. |
| Round icon button | `LiquidIconButton` | Size it with `Modifier.size(...)`. Its internal 40 dp is coerced to that size. |
| Title chip | `GlassTitlePill` | The reference material. |
| Section / panel | `Modifier.liquidGlassPanel(backdrop, uiSensor)` | 28 dp corners. **No shadow** (default). |
| Viewer chrome surface | `Modifier.viewerGlass(backdrop, color)` | Not composable. Reads `GlassSettings.style` at draw time. No shadow by default. |
| List row inside a panel | `Modifier.chipGlassSurface(...)` + `liquidRowClick {}` | See Recents rows. |
| Floating menu | `Modifier.glassMenu(backdrop, dark)` | Heavier frost so text stays legible. |
| Dropdown | `LiquidGlassDropdown` | The Aug-22 share-sheet dropdown, 1:1. Menu renders in `GlassOverlayHost`. |
| Dialog | `GlassDialog` + `GlassDialogAction` | Springy entrance, vivid primary, red destructive. |
| Segmented choice in a dialog | `GlassDialogSegmented` | Jelly thumb, slide to select. |
| Colour swatch (≤ ~10) | `LiquidIconButton(tint = c)` | Real glass bead. |
| Colour swatch (many) | `GlassBead` | Drawn glass bead with no backdrop pass, so it is cheap at 80+ swatches. |
| Tappable row | `Modifier.liquidRowClick { }` | Press spring, light glow from the touch point and a haptic. Never use a ripple. |
| Press light on a custom surface | `Modifier.liquidPressGlow(interaction, onLight)` | Driven by press interactions, so it never flashes when a scroll starts. |

**Never hand-roll `drawBackdrop` effect stacks.** Use `glassEffects(style, blurPx, refractionHeightPx,
refractionAmountPx)`, `glassTint(style)` and `glassHighlight(style)` from `GlassStyle.kt`. This keeps
every surface in line with **Settings → Liquid Glass** (blur, refraction height and amount, depth,
chromatic aberration, vibrancy, brightness, tint, highlight, corners, plus presets).

### Backdrop rules (these are why things look grey or flat)
- **Glass cannot refract the layer it is drawn in.** Floating UI must be a *sibling* of the
  captured content layer, not a child of it.
  - Use `GlassOverlay { }` (app-root portal) for menus and popovers.
  - On screens, things outside the scaffold sample `screenBackdrop.glass` (the live screen).
  - In the viewer, chrome samples `contentBackdrop`.
- Content inside a screen samples the wallpaper `backdrop`. With the default flat wallpaper this goes
  through the **flat fast path** (`FlatBackdrop.kt`): it produces the same pixels with no blur or lens
  pass. Keep the wallpaper-off layer a single solid fill.
- **Don't translate or scale a glass surface every frame** (entrances, drags). Its sample region moves,
  so blur and lens run again each frame. Fade glass; move the flat content inside it.

### Shadows
- **No drop shadows on panels or sections** (`withShadow = false` is the default). Shadows
  flickered on fade-in and cost frames.
- Only `LiquidButton` / `LiquidIconButton` keep their small `Shadow.Default`.
- When glass fades, use `graphicsLayer { alpha = a; compositingStrategy = ModulateAlpha }`. Never use
  `Modifier.alpha`, because an offscreen layer clips the glass rim and highlight and they snap back in
  when the fade ends.

### Ink and tint
- Theme surfaces use `LiquidGlassColors.text/secondary(isDark)`.
- Over documents use `chipGlass(onLight)` and `chipInk(onLight)`, picking `onLight` from the content
  behind the control (the viewer's `bandLuminance`). **In dark reader mode, invert the luminance**
  (`1 - l`).
- Vivid actions use the per-screen accent (`ToolAccents`, `LiquidGlassColors`) via `tint`.

---

## 3. Motion

Use the shared springs in `GlassMotion` (or springs close to them):

| Spec | Use for |
|---|---|
| `GlassMotion.morph()` (0.58, 420) | Things that spring open: capsules, chevrons, thumbs |
| `GlassMotion.pop()` (0.45, 500) | Small things landing (icons, beads, toasts) |
| `GlassMotion.press()` (0.42, Medium) | Press down and back |
| `GlassMotion.settle()` / `fade()` | Alpha and layout. Critically damped, never bouncy |

- **Alpha never bounces.** Clamp it (`coerceIn(0f, 1f)`) or drive it with `settle`.
- Stagger lists about 20–30 ms per item, and cap the stagger so long lists don't trail.
- Use **one animation per element**. Derive scale, offset and alpha from one progress value rather
  than running three animations.
- Page pushes (Settings sub-pages): slide in from the side plus a fade on a 0.74 / 380 spring.
- Opening a document grows out of the tapped row (`DocumentOpenOrigin`) and settles back into it on
  Back.
- Long-press on a glass button blooms (grows ≤ ~14 dp, glows brighter, one LongPress haptic). A
  long-press **action** runs on release, never mid-press.

---

## 4. Haptics
- `LiquidButton` and `LiquidIconButton` tick on tap (`ContextClick`). Rows tick through
  `liquidRowClick`.
- Picks and segment changes use `SegmentTick`. Sliding across options uses `SegmentFrequentTick`.
  Destructive actions use `LongPress`.
- `ThrottledHaptics` (installed at the app root) drops anything within 90 ms of the last tick and
  respects **Settings → Personalization → Haptic feedback**. Always use `LocalHapticFeedback`, never
  `View.performHapticFeedback`, so both apply.

---

## 5. Performance checklist (before you merge)
- [ ] No new `drawBackdrop` inside a list item that has many siblings. Use `GlassBead` or
      `chipGlassSurface` with the flat path, or one panel around the list.
- [ ] No snapshot-state writes from `onGloballyPositioned` that recompose large trees. Keep geometry
      in plain holders and read it at layout or draw time.
- [ ] Read animated values inside `graphicsLayer {}` or `drawBehind {}`, not in composition.
- [ ] No infinite animations while idle.
- [ ] No Material ripples (`indication = null` + `liquidRowClick` / `liquidPressGlow`).
- [ ] Glass entrances fade with `ModulateAlpha`; they never translate.
- [ ] Tested in light, dark and dark-reader modes, with the wallpaper on and off.

---

## 6. Layout and UX
- Settings is an iOS-style index of rows. Each topic opens its own page with a back button. Keep the
  root light: three small panels.
- Use 16 dp screen gutters and 12–16 dp between panels. Rows are 44–48 dp tall. Text is 13–16 sp:
  titles SemiBold, values in the secondary colour.
- Menus are compact: rows about 40 dp, menu width about 228 dp.
- Every popover, menu and sheet dismisses on Back and on an outside tap.
- Strings live in `values/strings.xml`. Never hard-code user-facing text.
