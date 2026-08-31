# The colour-native pattern substance mechanism

Who this is for: an agent or contributor adopting a `ColorNativePattern` subclass onto
`ColorNativePattern.colorizeCells` — most likely one of `Dunes`, `Grass`, `Jungle`,
`LavaLamp`, or `Rockfall`. `Fireball` and `Waterfall` are already migrated, and are the
two worked examples this file points at. No prior context from any conversation is assumed; everything needed
is either in this file or in the source it points at.

## The problem this solves

Two `ColorNativePattern` subclasses, `Fireball` and `Waterfall`, used to force their
interior surface to match their exterior exactly: `Fireball` computed one colour per cell
and wrote it to both the exterior point and its interior mirror in one call; `Waterfall`
rendered exterior only and then bulk-copied the finished, already-colourized pixels onto
interior via `ApotheneumPattern.copyExterior()` (a raw `System.arraycopy`). Both were
deliberate, pre-`ApotheneumColor` designs — not bugs — but they had a real consequence:
`ApotheneumColor.Axis.INSIDE_OUTSIDE` (one of three settings for which surfaces share a
palette stop — see `ApotheneumColor`'s own class javadoc) requires the ability to give a
surface's interior a *different* colour from its exterior. On these two patterns, that
was structurally impossible: selecting Inside/Outside didn't just fail to show a
difference, it made the pattern's cube and cylinder collapse to the *same* colour too
(since both patterns only ever query their own exterior identity), which reads on screen
as `Axis.NONE`, not as an inert or muted `INSIDE_OUTSIDE`.

The owner's own framing, watching the piece: *"They should be modified to just copy the
pixels, like the white and black, and then the colorize happens after that. The colorize
should be a global way of doing things, not different per pattern."* — i.e., a pattern
should produce brightness/shape only, and colour should be resolved afterward, per real
surface, using the same mechanism every time.

## Why this isn't one shared data structure

An earlier design tried a single per-pixel "substance" scalar shared by every pattern.
That does not fit: `Waterfall` carries two conceptually distinct substances (rock and
water) with their own colour roles; `Fireball` derives both its ember and core roles from
one heat value plus a recomputed noise term. Forcing every pattern into "exactly one
scalar per pixel" would have made `Waterfall` and others fight the shape rather than fit
it. The owner's resolution: *"What if it's just multiple arrays that you can optionally
pass through and populate?"*

So **the base class prescribes no array at all.** A pattern owns however many arrays it
needs — named however fits its own domain (`heat`, `rockIntensity`, `colorSlope`), sized
once (at construction or on a model change, never per frame — see "Allocation and
lifecycle" below) — and reads them from inside a small callback it supplies. The shared
mechanism is only the *walk, mask, and write* — the part that used to be hand-written,
subtly different, and error-prone in every subclass that wanted it.

## The API

Two things, both in `src/main/java/apotheneum/doved/patterns/ColorNativePattern.java`:

```java
@FunctionalInterface
public interface PhysicsColorizer {
  int colorFor(ApotheneumColor.Surface surface, int cell);
}

protected final void colorizeCells(
  int cellCount,
  int[] exteriorPointIndex,
  ApotheneumColor.Surface exteriorSurface,
  int[] interiorPointIndexOrNull,
  ApotheneumColor.Surface interiorSurfaceOrNull,
  PhysicsColorizer colorAt
)
```

`colorizeCells` walks `cell` from `0` to `cellCount - 1`. For each cell:

- Looks up `exteriorPointIndex[cell]` — a real point's index into the global colour
  buffer. If it's negative, that cell has no real exterior point (a ragged/partial cell
  range) and is skipped entirely — no call to `colorAt`, nothing written.
- Otherwise, if `isViewPoint(exteriorPoint)` (the pattern's own model-view mask), calls
  `colorAt.colorFor(exteriorSurface, cell)` and writes the result to
  `colors[exteriorPoint]`.
- Does the same for `interiorPointIndexOrNull[cell]` against `interiorSurfaceOrNull`, **if
  `interiorPointIndexOrNull` is non-null at all.** Passing `null` for the whole array (not
  a per-cell sentinel) means "this pattern has no interior geometry here" — the mirror
  write is skipped for every cell, with no branch the pattern has to write itself. This is
  how `LavaLamp` (exterior only, no interior content ever) adopts the mechanism for free:
  it would pass `null` for `interiorPointIndexOrNull`/`interiorSurfaceOrNull` and get
  exactly its current behaviour, unconditionally.

Both writes are independently guarded and neither reads `colors[]` back to derive the
other — the same two properties `Fireball`'s own hand-written mirror already had (view-
mask correctness; never assuming what a different pattern or a bulk copy already left in
the buffer), now enforced once here instead of by convention in every subclass.

**`colorAt` is called once per real surface a cell actually has a point on** — once for
exterior-only geometry, twice (once per surface identity, `exteriorSurface` then
`interiorSurfaceOrNull`) for a cell with a real interior mirror. This is deliberate: a
pattern's own bespoke colour blend (Fireball's ember/core blackbody curve; a plain
`compositeColors`/`blendTones` call; anything else) stays entirely inside `colorAt` and is
simply invoked twice with a different `surface` argument. `colorizeCells` never resolves
colour itself and never sees a pattern's arrays — it only sees the `int` each call
returns.

### What a pattern implements versus inherits

**Inherits, unchanged:** `ColorRole`/`primary`/`secondary` (still available if a pattern
wants `role.color(surface, physics)` inside its own colour logic — `colorizeCells` is
orthogonal to `ColorRole`, not a replacement for it), the `color` enable toggle, the
device-panel UI, `dispose()`, everything else `ColorNativePattern` already provided.
`colorizeCells` itself is new and inherited as-is — no override point, no subclassing.

**Implements, per pattern:**

1. Whatever physics array(s) the pattern's own simulation already produces or is
   refactored to retain (see "Allocation and lifecycle").
2. A geometry mapping from the pattern's own cell index to real point indices — usually
   already present in some form (see Fireball's pre-existing `pointIndex`/`mirrorIndex`
   below).
3. One `PhysicsColorizer` lambda (or method reference) per call to `colorizeCells`, which
   reads the pattern's own array(s) for `cell` and returns a finished colour for `surface`.

### Allocation and lifecycle

Arrays (physics data, and the `exteriorPointIndex`/`interiorPointIndexOrNull` geometry
maps) are allocated **once**, at construction or when the pattern re-binds to a changed
model — never per frame. This repo has a no-per-frame-allocation guideline
(`docs/lx-coding-guidelines.md` §1); `colorizeCells` itself allocates nothing, and neither
should the arrays it's given. `Fireball`'s existing `attach()` pattern (rebuild geometry
arrays only when the bound orientation's identity actually changes, a cheap reference
check) is the model to follow — every pattern already allocates its per-cell buffers this
way for its physics simulation; this is not a new discipline, only a new set of arrays to
apply it to.

### Recomputable inputs need no array

Not everything a `PhysicsColorizer` reads needs to be stored. `Fireball`'s
`colorPhysics(x, y)` is a pure function of `(x, y, noiseTime)` — `noiseTime` is fixed for
the whole frame — so it costs the same to recompute inside the colorizer callback as it
would to store and re-read. **Do not add a buffer for something cheaper to recompute than
to store and invalidate correctly.** Only genuinely stateful, expensive-to-derive
per-pixel values (heat after diffusion/advection; an accumulated water-speed average) earn
their own persistent array.

## Worked example: `Fireball`

Before (single shared colour, mirrored):

```java
final ApotheneumColor.Surface surface = ApotheneumColor.Surface.of(this.orientation);
final float[] heat = this.heat;
for (int x = 0; x < this.width; ++x) {
  final int column = x * this.height;
  for (int y = 0; y < this.height; ++y) {
    final int i = column + y;
    if (!this.usable[i]) continue;
    final float value = heat[i];
    if (value <= HEAT_EPSILON) continue;
    paint(colors, i, Fireball.this.colorHeat(surface, value, colorPhysics(x, y)));
  }
}
renderSparks(colors, surface);

// paint() wrote the SAME `color` argument to both pointIndex[cell] and mirrorIndex[cell].
```

After (colour resolved independently per real surface):

```java
final ApotheneumColor.Surface exteriorSurface = ApotheneumColor.Surface.of(this.orientation);
final ApotheneumColor.Surface interiorSurface = ApotheneumColor.Surface.of(this.mirrorOrientation);
final float[] heat = this.heat;
final boolean[] usable = this.usable;
final int height = this.height;
Fireball.this.colorizeCells(
  this.width * height,
  this.pointIndex,
  exteriorSurface,
  this.mirrorIndex,
  interiorSurface,
  (surface, cell) -> {
    if (!usable[cell]) return LXColor.BLACK;
    final float value = heat[cell];
    if (value <= HEAT_EPSILON) return LXColor.BLACK;
    return Fireball.this.colorHeat(surface, value, colorPhysics(cell / height, cell % height));
  }
);
renderSparks(colors, exteriorSurface, interiorSurface);
```

What changed, and what didn't:

- **`heat[]` is untouched** — still one array, still populated by the same physics step,
  still indexed the same way. It was already exactly the "one array" shape this mechanism
  wants; nothing needed to move.
- **`colorPhysics(x, y)` is recomputed inside the colorizer**, from `cell / height` and
  `cell % height` — not stored. See "Recomputable inputs need no array" above.
- **A new field, `mirrorOrientation`**, was added to `Fire` (alongside the pre-existing
  `pointIndex`/`mirrorIndex`/`orientation`) so `render()` can resolve
  `ApotheneumColor.Surface.of(this.mirrorOrientation)` — the interior surface identity —
  which nothing needed before because the interior never got its own identity at all.
  Set once in `attach()`, exactly where `pointIndex`/`mirrorIndex` are already built.
- **`colorHeat()` itself did not change.** Fireball's ember/core blackbody blend is still
  entirely Fireball's own code; it's simply called twice now (once per surface) instead of
  once. This is the direct cost of the fix, and it was costed *before* this change was
  approved: `colorHeat()` already made two `.color()` calls per invocation (ember + core
  blend); calling it twice per cell doubles that to four, plus one LUT lookup and one HSB
  reconstruction, per lit cell. `Dunes`, `Grass`, and `Jungle` already call `.color()`
  independently per surface over their entire pixel grid every frame without a reported
  cost problem, and Fireball's lit-cell count (heat above `HEAT_EPSILON`) is a fraction of
  the full model, so no measurable frame-time impact is expected — this has not been
  profiled numerically; the headless renderer's own `meanFrameMs` output
  (`docs/headless-rendering.md`) is the way to check if that matters later.
- **`paint()` is gone**, replaced by `colorizeCells`. **`paintBrighter()` (used by
  `renderSparks()` for embers) still exists**, but now takes two colours —
  `exteriorColor`/`interiorColor` — instead of one, and compares each surface's own
  resolved brightness against its own destination's current value, independently:

  ```java
  private void paintBrighter(int[] colors, int cell, int exteriorColor, int interiorColor) {
    final int exterior = this.pointIndex[cell];
    if (isViewPoint(exterior) && (LXColor.b(exteriorColor) > LXColor.b(colors[exterior]))) {
      colors[exterior] = exteriorColor;
    }
    final int interior = this.mirrorIndex[cell];
    if (isViewPoint(interior) && (LXColor.b(interiorColor) > LXColor.b(colors[interior]))) {
      colors[interior] = interiorColor;
    }
  }
  ```

  Before this change, both surfaces shared one resolved colour and therefore one
  brightness threshold, so comparing each destination against that one shared value was
  merely *equivalent* to comparing against itself. Now that exterior and interior can
  genuinely differ (under `Axis.INSIDE_OUTSIDE`), comparing each surface's own resolved
  colour against its own destination is the *more correct* behaviour, not just an
  equivalent rewrite — worth knowing so a future reviewer doesn't mistake it for scope
  creep.

## Per-pattern notes for the remaining six

Not adopted as of this writing; `Fireball` is the only proof against a real consumer.
These notes exist so the next agent doesn't have to re-derive them.

- **`Dunes`, `Grass`, `Jungle`, `Rockfall`** already resolve
  `ApotheneumColor.Surface.of(orientation)` **independently** for interior and exterior —
  each calls its own `output()`/`writeColors()`/per-orientation render method once per real
  orientation, so `Axis.INSIDE_OUTSIDE` already works correctly on all four *without*
  adopting this mechanism. Adopting it on these four is a uniformity/nomenclature win (the
  owner's stated complaint was as much about inconsistent structure across patterns as
  about the two broken ones), not a correctness fix — mechanical in the sense that the
  hard part (independent per-surface resolution) is already done; adopting means
  restructuring each one's existing loop to go through `colorizeCells` instead of writing
  `colors[]` directly, and would need each one's already-existing per-orientation
  intensity/physics values threaded through a `PhysicsColorizer` lambda instead of being
  consumed inline.
- **`LavaLamp`** paints exterior only, never touches interior at all. Adopting means
  passing `null` for `interiorPointIndexOrNull`/`interiorSurfaceOrNull` — the mirror step
  becomes a no-op that falls out of the API rather than needing a branch. Mechanical.
- **`Waterfall` was the hard one**, and the reason this mechanism exists rather than a
  smaller fix. **It is now migrated** — read it as the second worked example, alongside
  `Fireball`. Before the migration it had **no persistent substance buffer at all**:
  `rockIntensity`/water `level` were computed and consumed on the same line as the colour
  write inside `renderShape()`, then discarded. It also carries **two distinct
  substances** (rock and water) with their own colour roles, computed at two different
  granularities — `notch`/`speed` (the values fed to `.color()`) once per *column*; rock
  intensity and water level once per *pixel*. Adopting the mechanism took all three of:
  (1) new persistent per-shape intensity buffers (`cubeRockIntensity`/`cubeWaterLevel` and
  their cylinder twins, the same architectural shape as the existing
  `cubeSpray`/`cylinderSpray`/`cubeSpill`/`cylinderSpill` grids), (2) splitting the old
  `renderShape()` into a `computeShape()` that writes substance into those buffers and a
  `colorizeShape()` that resolves colour from them, and (3) replacing the single bulk
  `copyExterior()` call with a `colorizeCells` pass per shape. `Axis.INSIDE_OUTSIDE` now
  gives Waterfall's interior its own colour rather than collapsing to look like
  `Axis.NONE`.

  Two details worth copying when you migrate the next one. **Hoist the `PhysicsColorizer`
  into a field**, one per shape (`cubeColorizer`/`cylinderColorizer`) — a lambda that
  captures anything allocates a fresh object every time the expression is evaluated, so
  building it inside the per-frame call is exactly the render-loop allocation
  `docs/lx-coding-guidelines.md` §1 forbids. Two fields rather than one reassigned field
  keeps each shape naming its own arrays, with no per-call setup. And **prove it with
  renders, not just the suite**: the refactor was accepted only after byte-for-byte
  identical renders under `Axis.NONE` and `Axis.SHAPE` against the previous build, which
  is what established that only the `INSIDE_OUTSIDE` behaviour changed.

## Verification standard

**Wiring-level tests, not unit tests.** A test that calls a pattern's own colour-resolution
method directly, in isolation, will keep passing even if the pattern's real render path
silently regresses — that is exactly how the original `Fireball`/`Waterfall` mirror-copy
bug went undetected. The standard: a real fixture (`src/main/resources/fixtures`, loaded
via `JsonFixture`, exactly as `FireballBearingAlignmentTest`/
`FireballInteriorColorWiringTest` do), a real component (`ApotheneumColor` registered on
the engine with a chosen `axis`), the pattern run for enough frames to produce real
output, and an assertion against actual rendered pixels — not a mocked or hand-picked
surface. See `FireballInteriorColorWiringTest` for the pattern to follow when adopting the
next subclass: it asserts that a lit cube-exterior point and its exact interior mirror
(same `(x, y)` on `Apotheneum.cube.exterior`/`interior`) resolve to **different** colours
under `Axis.INSIDE_OUTSIDE`, and to the **same** colour under `Axis.SHAPE` (a sanity check
that adopting this mechanism didn't accidentally break the agreement Fireball already had
correctly).

**Render it and look.** In this session, a fully green unit-test suite certified visibly
broken behaviour four separate times before renders caught the actual problem — a UI panel
whose controls fell off a 208px pane, a seam in a 2D gradient that a green suite never
exercised, and others. Before trusting an adoption of this mechanism as done, render the
pattern via `docs/headless-rendering.md`'s `RenderSpike` (`-DapotheneumColor=pair,swap,axis`
now takes an integer `axis` 0/1/2 for None/Shape/In-Out) at all three axis settings and
look at the actual images — cube exterior and cube interior side by side under
`Axis.INSIDE_OUTSIDE` should visibly differ in colour while keeping the same shape/motion
(same physics, different palette resolution); under `Axis.SHAPE` they should match again.
