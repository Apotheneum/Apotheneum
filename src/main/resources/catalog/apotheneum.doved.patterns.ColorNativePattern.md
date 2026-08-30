---
class: apotheneum.doved.patterns.ColorNativePattern
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/doved/patterns/ColorNativePattern.java
sourceSha256: 8e7ef92453aa37605cad1f2b7c41f4c825ea0aace3370f2d59ef1d72c3eea4fa
classBytesSha256: 0891670425303acf42b9772ffe539cb2279fecf7c1e93a9126aeb719d217359a
classBytesOrigin: target/classes
lxVersion: 1.2.2
generatedAt: 2026-08-29T00:00:00Z
generator: manual (claude-code; no chromatik-mcp-catalog run available in this offline session)
tags: color, palette, base-class, modulation, ui
---

## Summary

ColorNativePattern is an abstract base class for "colour-native" patterns — patterns that own
their own colour logic per pixel rather than rendering luminance and relying on a downstream
Colorize-style effect to apply colour. It is not directly instantiable as a device (no LX
pattern registry entry of its own); it exists to be extended. Subclasses as of this writing:
`Rockfall`, `Waterfall`, `Fireball`, `Jungle`, `LavaLamp`, `Grass`, `Dunes`.

**2026-08-29: this class no longer owns colour, only physics coupling.** Each role used to
carry its own `paletteIndex`/`hueOffset`/`satTrim` — nine parameters per pattern instance,
duplicated across every instance and individually relay-wired on the live rig. That is gone.
See `apotheneum.doved.modulators.ApotheneumColor` for where colour now lives; this file
describes only what remains here.

- Provides exactly two colour roles, `primary` and `secondary`, each a `ColorRole` component
  attached with `addChild(...)` at the fixed, generic keys `"primary"`/`"secondary"` — every
  colour-native pattern exposes the same addresses, so `.../primary/amount` means the same
  thing on every subclass. A subclass supplies only each role's default physics-coupling
  amount at construction (`ColorNativePattern(LX lx, double primaryAmount, double
  secondaryAmount)`) — there is no per-role palette index to choose any more.
- `ColorRole` now holds exactly one parameter, `amount` (0-1, `CompoundParameter`) — couples
  the role's colour to a physics-driven perturbation the subclass supplies per pixel via
  `ColorRole.color(ApotheneumColor.Surface surface, double physics)`. The base colour that
  `amount` perturbs comes from `ApotheneumColor.instance.primaryColor(surface)` /
  `.secondaryColor(surface)` — a static singleton reference, not a modulation-wired copy — with
  a neutral-white fallback when no `ApotheneumColor` exists in the project (mirrors
  `Apotheneum.exists`-style gating).
- **`surface` is the caller's responsibility, and it is cheap because the caller already has
  it.** Every subclass already renders one physical `Apotheneum.Orientation` at a time (a
  per-surface loop, e.g. `Rockfall`'s `surfaceWaters`); resolve it once per loop via
  `ApotheneumColor.Surface.of(orientation)` and reuse it for every pixel in that loop, rather
  than re-resolving per pixel.
- **The device-panel colour controls are built by this class, not by subclasses.**
  `ColorNativePattern` itself implements `UIDeviceControls<ColorNativePattern>` and provides a
  working `buildDeviceControls` — a bare subclass that never overrides it gets the full colour
  UI for free. A subclass that also wants its own columns (like `Rockfall`) overrides
  `buildDeviceControls(UI, UIDevice, ColorNativePattern)` (note: the parameter type is
  `ColorNativePattern`, not the subclass — Java does not allow implementing the same generic
  interface at two different type arguments), adds its own columns, and calls the protected
  `buildColorDeviceControls(ui, uiDevice)` last. That method is now a single three-control
  column — the `color` enable button plus each role's one remaining `amount` knob — down from
  the four columns (two roles times a palette-preview-plus-four-knobs column apiece) the old
  vocabulary needed; `PaletteColorPreview` and the old per-role knob columns are gone along
  with the parameters they displayed.
- Colour resolution order is unchanged in spirit: `ApotheneumColor` resolves the palette stop
  and applies that surface's hue/saturation offsets (see that class), then `ColorRole.color`
  applies the physics perturbation on top, then — outside this class, wherever the subclass
  composites and writes its output — brightness scaling by whatever intensity mask the
  subclass computes.

## Parameter interactions

- `amount` is unchanged from before this redesign: at `amount = 0` the physics perturbation is
  a no-op and the role's colour is exactly `ApotheneumColor`'s resolved-for-this-surface
  colour, unperturbed.
- `primary`/`secondary` are `addChild(...)` components, exactly as before. Their `amount`
  parameter is therefore invisible to `LXSnapshot` and clip recording
  (`LXSnapshot.addDeviceView` in LX 1.2.2 walks only `getParameters()`, `getLayers()`, and the
  never-populated `automationChildren`). Accepted trade-off, not an oversight.
- **Breaking change for existing projects:** any live relay wiring that targeted
  `.../primary/paletteIndex`, `.../primary/hueOffset`, `.../primary/satTrim` (or the
  `secondary` equivalents) on any instance of a `ColorNativePattern` subclass is now dangling
  — those parameters no longer exist on `ColorRole`. Re-point such wirings at the equivalent
  `ApotheneumColor` parameters instead (its `pair`/`swap`, or a surface's `indexOffset`/
  `hueOffset`/`satTrim`).

## Usage tips

- Extend this class when a pattern wants palette-aware colour with two roles, each getting its
  own physics-driven perturbation — colour itself is no longer a per-pattern decision; every
  instance reads the same global `ApotheneumColor`.
- A subclass gets the colour UI without writing any UI code for it: implement
  `buildDeviceControls(UI, UIDevice, ColorNativePattern)`, add the subclass's own columns, then
  call `buildColorDeviceControls(ui, uiDevice)` as the last line. A subclass with no controls
  of its own does not need to override `buildDeviceControls` at all.
- See `Rockfall` for a worked example: `rockColor`/`waterColor` are Java-level aliases for
  `primary`/`secondary`, kept for readability in the subclass's own code — the underlying
  component path is still `.../primary/...` and `.../secondary/...`.
- See `apotheneum.doved.modulators.ApotheneumColor` for the shared colour state itself: the
  `pair`/`swap` two-knob scheme (design/color-system.md section 4 in the chromatik-shows repo,
  reproduced here in integer form) and the four surfaces' standing `indexOffset`/`hueOffset`/
  `satTrim`.
