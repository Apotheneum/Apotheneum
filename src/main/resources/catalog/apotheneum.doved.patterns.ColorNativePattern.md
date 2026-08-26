---
class: apotheneum.doved.patterns.ColorNativePattern
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/doved/patterns/ColorNativePattern.java
sourceSha256: 83e3f9bad4dd3a2f83caf9b39138836d0981151a1cabde484f8a5082925c5567
classBytesSha256: 4a2a969dbe902e341f64bf8ca2e875bd423a25354691daa99b10062628d15984
classBytesOrigin: target/classes
lxVersion: 1.2.2
generatedAt: 2026-08-26T23:08:24Z
generator: manual (claude-code; no chromatik-mcp-catalog run available in this offline session)
tags: color, palette, base-class, modulation, ui
---

## Summary

ColorNativePattern is an abstract base class for "colour-native" patterns — patterns that own
their own colour logic per pixel rather than rendering luminance and relying on a downstream
Colorize-style effect to apply colour. It is not directly instantiable as a device (no LX
pattern registry entry of its own); it exists to be extended. `Rockfall` is the first and, as of
this writing, only subclass.

- Provides exactly two colour roles, `primary` and `secondary`, each a `ColorRole` component
  attached with `addChild(...)` at the fixed, generic keys `"primary"`/`"secondary"` — every
  colour-native pattern exposes the same addresses, so `.../primary/hueOffset` means the same
  thing on every subclass. Both roles' component labels and device-panel column headers are the
  same fixed generic strings, "Primary"/"Secondary" — there is no per-role label a subclass can
  set. A subclass supplies only each role's default palette index and physics-coupling amount:
  genuinely per-pattern numeric choices, not text.
- **The device-panel colour controls are built by this class, not by subclasses.**
  `ColorNativePattern` itself implements `UIDeviceControls<ColorNativePattern>` and provides a
  working `buildDeviceControls` — a bare subclass that never overrides it gets the full colour UI
  for free. A subclass that also wants its own columns (like `Rockfall`) overrides
  `buildDeviceControls(UI, UIDevice, ColorNativePattern)` (note: the parameter type is
  `ColorNativePattern`, not the subclass — Java does not allow implementing the same generic
  interface at two different type arguments, so the subclass cannot separately declare
  `UIDeviceControls<Rockfall>`), adds its own columns, and calls the protected
  `buildColorDeviceControls(ui, uiDevice)` last, so the colour columns land at the end of the
  panel and stay contiguous.
- Each role exposes the same four-parameter vocabulary: `paletteIndex` (which project palette
  swatch stop the role reads, 1-based), `hueOffset` (±60°), `satTrim` (0 to -40%, one-sided), and
  `amount` (0-1, couples the role's colour to a physics-driven perturbation the subclass supplies
  per pixel via `ColorRole.color(double physics)`).
- `paletteIndex` is a `CompoundDiscreteParameter`, not a plain `DiscreteParameter` — deliberately,
  so it can terminate modulation (`LXCompoundModulation.Target`). It is 1-based to match
  `heronarts.lx.color.LXPalette.IndexSelector`, the convention every other palette selector in the
  app uses (including `ColorizeMultiplyEffect`'s own `paletteIndex`), but it cannot literally be an
  `IndexSelector`: that concrete type extends plain `DiscreteParameter` and is not a modulation
  target — the same reason this vocabulary needs `CompoundDiscreteParameter` in the first place.
  `ColorRole` reproduces `IndexSelector`'s live option-label behaviour independently instead (see
  Parameter interactions).
- Colour resolution order is fixed: resolve the palette stop, apply `hueOffset`/`satTrim`, apply
  the physics perturbation, then — outside this class, wherever the subclass composites and
  writes its output — brightness scaling by whatever intensity mask the subclass computes.

## Parameter interactions

- `hueOffset` and `satTrim` are resolved once per frame (in `ColorRole.update()`), not per pixel;
  they adjust the *chosen* colour before the per-pixel physics perturbation is layered on top, so
  the two do not fight each other. `satTrim` can only ever lower saturation — its parameter range
  is bounded to [-40, 0], not clamped defensively at read time.
- `amount` is unchanged from the pattern this class generalizes: at `amount = 0` the physics
  perturbation is a no-op and the role's colour is exactly the offset-adjusted palette colour.
- Both `hueOffset = 0` and `satTrim = 0` (the defaults) short-circuit before any colour-space
  round trip, so default output is bit-identical to a pattern that only ever resolved the palette
  stop and applied the physics perturbation — i.e. identical to this class's pre-generalization
  behaviour.
- `paletteIndex` is 1-based: `ColorRole.resolvedPaletteColor(index)` looks up
  `swatch.colors.get(index - 1)` (clamped). A real `LXPalette.IndexSelector` gets its option
  labels ("1".."5", or a performer's custom `label1..label5` name) refreshed automatically by
  joining `LXPalette`'s private static selector registry — closed to any type that isn't literally
  `IndexSelector`. `ColorRole` instead listens on `lx.engine.palette.labels` directly (a public
  field) and mirrors the same fallback onto `paletteIndex.setOptions(...)` by hand, so the option
  labels track the project's palette names despite `paletteIndex` not being an `IndexSelector`.
- `primary`/`secondary` are `addChild(...)` components, exactly as in the pattern this class
  generalizes. Their parameters are therefore invisible to `LXSnapshot` and clip recording
  (`LXSnapshot.addDeviceView` in LX 1.2.2 walks only `getParameters()`, `getLayers()`, and the
  never-populated `automationChildren`). This is an accepted trade-off — these controls are meant
  to be driven by modulation, which resolves a parameter by path regardless of where it is
  registered — not an oversight to work around.
- Each role needs 5 device-panel controls (a palette preview plus 4 parameters), one more than
  the repository's 3-controls-per-column limit, so `buildColorDeviceControls` still spans each
  role across two columns: one headed "Primary"/"Secondary" (preview, index, amount), one left
  without its own header (hue offset, sat trim) rather than inventing a second per-role label.
  Attempting to signal that pairing by omitting the `addVerticalBreak` between them was tried and
  rejected — a rendered check showed the panel drawing an identical divider there regardless, so
  it did not read as a tighter pairing. The two columns are simply placed adjacent, at the very
  end of the panel, with nothing else between the two roles either.
- The "Primary"/"Secondary" header column is explicitly widened to 68px (`ROLE_COLUMN_WIDTH`,
  above the framework's 52px default) because a render showed "Secondary" clipping to
  "Secondar" at the default width. `hueOffset`'s label is "H-Off", not "H-Offset", because a
  40px-wide knob (the framework default, and widening individual knobs was tried and produced a
  broken/overlapping layout) clips "H-Offset" to "H-Offse".
- `PaletteColorPreview` (the small swatch on each role's column) shows the *effective resting*
  colour — `applyOffsets(resolvedPaletteColor(paletteIndex), hueOffset, satTrim)` — not the raw
  palette stop, and not the physics-perturbed `ColorRole.color(physics)` either (physics varies
  per pixel/frame, so there is no single right value to show for it). It repaints on
  `paletteIndex`, `hueOffset`, `satTrim`, and on every parameter of every `LXDynamicColor`
  currently in the live project palette swatch (so editing the colour sitting at a fixed index
  from the Palette panel updates the chip too), via
  `UIObject.addListener(LXListenableParameter, LXParameterListener)` — which `UIObject.dispose()`
  already tears down automatically — plus an explicit `LXSwatch.Listener` (constructor-added,
  `dispose()`-removed by hand) that attaches the same tracking to colours added to the swatch
  after construction. `paletteIndex` originally did not repaint the chip on change at all — every
  redraw() call in the glx framework (`UIKnob`/`UICompoundParameterControl`/`UIParameterControl`)
  scopes to the originating control's own component, never a sibling, so nothing was ever wired
  to make a value change anywhere redraw a *different* component; the chip needed its own
  listeners regardless of which parameter was involved.

## Usage tips

- Extend this class when a pattern wants palette-aware colour with a knob-level hue/saturation
  offset per role, and wants that offset to be independently modulatable per role — rather than
  compositing luminance and applying colour with a separate `ColorizeMultiplyEffect` downstream.
- A subclass gets the colour UI without writing any UI code for it: implement
  `buildDeviceControls(UI, UIDevice, ColorNativePattern)`, add the subclass's own columns, then
  call `buildColorDeviceControls(ui, uiDevice)` as the last line so the colour columns land last.
  A subclass that has no controls of its own does not need to override `buildDeviceControls` at
  all.
- `hueOffset`'s ±60° range and `satTrim`'s 0-to-40%-down range are tuned to this installation's
  palette system: swatch pairs are chosen to read as a continuous cool ramp, and swatches sit at
  roughly 92-95% saturation with almost no headroom upward. Do not widen either range to make the
  knobs feel more "complete" without first checking whether the wider range still reads well
  against real swatches.
- See `Rockfall` for a worked example: `rockColor`/`waterColor` are Java-level aliases for
  `primary`/`secondary`, kept for readability in the subclass's own code — the underlying
  component path is still `.../primary/...` and `.../secondary/...`, and nothing user-visible in
  the colour controls says "Rock" or "Water".
