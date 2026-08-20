# Ocean and Organic Patterns — design doc

**Status:** design only. No code has been written. This document is the handoff.

**Branch:** `claude/ocean-organic-patterns`

**Provenance:** drafted from a three-way review — an initial design pass, an
independent blind pass by a second model that was given only the rig context and
the brief, and an adversarial critique by Codex that was given the full set of
claims. Where all three converged, the doc says so. Where they were *wrong*, §4
records the correction so it does not get reintroduced. The project owner then
supplied the observation that reframed the whole document: **Hyperspace2D reads
better than Hyperspace.** §1 and §3 are built on that.

Read [`lx-coding-guidelines.md`](lx-coding-guidelines.md) and the root
`AGENTS.md` before writing any pattern here. This doc assumes both.

---

## 1. The organizing principle: render fields, not objects

Everything below follows from one distinction, and it is the thing to get right
before any specific algorithm matters.

**A field assigns a value to every LED.** Evaluate `f(p.x, p.y, p.z, t)` at each
point and you get a defined brightness everywhere, and — critically — the result
is *coherent across surfaces*. A wavefront crossing the installation lines up as
it passes from cube face to cube face to cylinder, because all of them are
sampling the same function of world position.

**An object occupies a location and lights whatever is near it.** A star, a
fish, a bubble. In 3D world space this fails on this rig: the audience is inside
a thin shell, so an object in the open volume touches no LEDs at all, and an
object near a wall produces a distorted imprint on whichever surface happens to
be closest. There is also no valid perspective centre — many people are inside at
once — so there is no projection that is correct for anyone.

This is not theory. `Hyperspace` renders objects in 3D world space via a spatial
grid. `Hyperspace2D` renders objects in 2D surface space. **The 2D one reads
better**, and that is the empirical result this document is designed around.

So:

| Content | Where it lives | Why |
|---|---|---|
| Waves, flood level, caustics, depth gradient, chemistry | **3D world-space scalar field** | Every LED gets a value; fronts stay coherent across cube and cylinder |
| Stars, bubbles, fish, foam flecks, agents | **2D surface space** (azimuth × height, or face UV) | Always on a surface, always legible, never an imprint |

Depth does not require 3D. `Hyperspace2D` gets a convincing depth cue from two
purely 2D devices: **radial expansion from a movable vanishing point**, and
**trails**. Use those instead of reaching into the volume.

### 1.1 The caveat that bit Milestone 1: world space is for *fronts*, not for going *around*

"Field in world space" is right for a wavefront **crossing** the structure. It is
wrong for anything that runs **around** the audience — a waterline, an azimuthal
undulation, a ring of anything.

The reason is that the cube's cross-section is a **square**. Any linear function
of world `(x, z)` — which is what a plane wave's phase is — becomes **piecewise
linear** as you walk the 200-column ring, with slope breaks at the four corners.
The waterline stops being a curve and becomes a polyline. Measured on the
Milestone 1 undulation, walking both perimeters through the real function:

```
                                  max|2nd diff|   mean     ratio
linear world XZ, cube ring           0.0797      0.0029     27.8   <- corners
linear world XZ, cylinder ring       0.0030      0.0014      2.1      smooth
```

A 28x curvature spike at each cube corner. That is the "it looks like a triangle"
report, and it is why the same code looks fine on the cylinder.

**The rule: parameterize anything azimuthal by normalized arc length around the
ring, with integer wavenumbers.**

```
s = columnIndex / ringLength                     // 0..1, perimeter position
undulation(s, t) = sum_i A_i * sin(TWO_PI * n_i * s + phi_i - omega_i * t)
                                                 // n_i INTEGER, so the seam closes
```

`orientation.columns()` already iterates in perimeter order, so `s` is available
for free — this replaces `nx`/`nz`, it does not add a coordinate system. Integer
wavenumbers make `s = 0` and `s = 1` agree exactly, so there is no seam.

Measured, same test:

```
integer-k in arc length,  cube ring   0.0068     0.0028      2.4   <- smooth
integer-k in azimuth,     cube ring   0.0086     0.0033      2.6      also fine
```

Arc length wins narrowly and is cheaper (no `atan2`); azimuth is an acceptable
alternative if cube/cylinder phase alignment is easier to reason about that way.
Either removes the corners. If you use arc length on both surfaces, confirm that
`s = 0` lands at the same physical azimuth on the cube ring and the cylinder ring,
or the two will undulate out of phase with each other.

**Second, less obvious consequence.** A plane front on a square also *degenerates*
per face. With travel along +X, the two faces perpendicular to travel have a
**constant** phase coordinate across their whole width — they do not receive a
passing wave at all; they sit still and then the entire wall lifts in unison when
the front arrives:

```
face 0 (x varies):   a bump sweeps across
face 1 (x constant): flat, then the whole wall moves at once
face 2 (x varies):   a bump sweeps across
face 3 (x constant): flat, then the whole wall moves at once
```

This is geometrically correct for a plane wave striking a box and completely wrong
for someone standing inside one. Use a world-space plane front only where that
"two walls together" behavior is wanted for impact (§B), and make it a deliberate
choice. For "a wave travels around the room," use a bump that moves in `s`.

---

## 2. Rig facts that constrain every pattern

All constants verified in `src/main/java/apotheneum/Apotheneum.java`.

```
GRID_WIDTH      = 50    // cube face columns
GRID_HEIGHT     = 45    // cube face rows
CYLINDER_HEIGHT = 43
DOOR_WIDTH      = 10
DOOR_HEIGHT     = 11
Cube.DOOR_START_COLUMN     = 20
Cylinder.DOOR_START_COLUMN = 10
RING_LENGTH = 120 (cylinder) / 200 (cube, = 4 faces x 50)
```

**Y = 0 is the TOP.** Y increases downward. Every "rising" effect in this
document — flood level, bubbles, growth — counts *down* from `GRID_HEIGHT` or
`CYLINDER_HEIGHT`, not up from zero. This is the single easiest thing to get
backwards and it silently inverts an entire scene.

**Rings already give you the wrapping lattice.** `Orientation.ring(index, wrap)`
and `Ring.next(wrap)` / `Ring.previous(wrap)` mean the four cube faces are
already addressable as one 200-wide ring that wraps, and the cylinder as a
120-wide ring. Any cellular automaton or azimuthal wave in this document runs on
those rings directly; do not hand-roll a face-stitching layer.

**Wrap azimuth only.** A surface here is S¹ × [0,1], not a torus. Wrapping
vertically would connect floor to ceiling.

**Doors are the constraint nobody remembers.** Door columns carry a full
`GRID_HEIGHT` / `CYLINDER_HEIGHT` of points, but only part of that is physically
present. Use `orientation.available(globalColumnIndex)` for the usable height.
**Never use `column.points.length` as the boundary** — it is the full height on
every column and lighting past `available()` lights pixels that do not exist.

This matters more here than for most patterns, because a large share of this
document is vertical:

- A rising flood level must clamp per column to `available()`, or the waterline
  appears to pass through solid door frames.
- Bubbles rising a column must despawn or deflect at `available()`.
- A CA lattice must treat door cells as absent, not as zero — a dead cell that
  participates in the neighbour sum will eat a spiral wave as it passes.

**Unresolved point count.** `AGENTS.md` says 13,280 LED nodes. The show notes in
`chromatik-shows` report 28,320 points for the Apotheneum fixture, which matches
`8 x 50 x 45 + 240 x 43`. These probably count different things (nodes vs.
points, or exterior only), but nothing in this document depends on the total.
Do not use either number in code; use the constants.

---

## 3. What the Hyperspace patterns actually do

Read these before building anything — they are the house style for this class of
pattern, and one of them is the thing the show is built around.

### 3.1 `Hyperspace` — 3D, the weaker one

A cloud of stars at random positions in normalized 0–1 model space. The stars are
**static**; `motionAxis` (0/1/2 = X/Y/Z) and `motionDirection` (±1) translate the
whole field along **one axis**. It is a linear scroll of a fixed cloud, not a
radial warp. Stars `spawnBehind()` the installation, fade in over the first 10%
of life and out over the last 10%, and die on lifespan or on leaving [-0.2, 1.2].
Rendering goes through an `LEDSpatialGrid` — a hashed uniform grid for
nearest-neighbour lookup from a 3D star to the LEDs around it.

Its params: `speed`, `density`, `starSize`, `duration`, `brightness`, `pulse`,
`motionAxis`, `motionDirection`, `renderToCube`, `renderToCylinder`,
`clearStars` (momentary).

Good practice to copy: a preallocated `starPool` with an `activeStarCount`, so
the render loop allocates nothing.

### 3.2 `Hyperspace2D` — the one that works

Stars spawn at a **source point in 2D surface space** (`sourceX`, `sourceY` —
both live parameters) within a `spreadRadius`, pick a random angle once, and then
travel in a dead-straight line outward forever; velocity never changes after
construction. A star dies after visiting two faces.

Trails are done by **framebuffer feedback**: instead of clearing, the render
scales every non-zero pixel's brightness by 0.5 each frame, so the star's own
history decays in place.

```java
if (trailAmount > 0.01f) {
  for (int i = 0; i < colors.length; i++) {
    if (colors[i] != 0) colors[i] = LXColor.scaleBrightness(colors[i], fadeAmount);
  }
} else {
  setApotheneumColor(0);
}
```

Params: `sourceX`, `sourceY`, `speed`, `density`, `duration`, `brightness`,
`trailLength`, `trailBrightness`, `spreadRadius`, `twinkleIntensity`,
`twinkleSpeed`, `debugSource`.

**Why it works** — these four properties are the spec for everything in §5:

1. **Radial expansion from a vanishing point.** A perspective cue with no
   perspective maths, and the vanishing point is a live parameter, so the
   operator can throw it around the room.
2. **Sparse bright elements on black.** The ideal low-resolution encoding.
   Gradients band across 43–45 rows; points do not.
3. **Trails.** Feedback decay turns a 1-pixel dot into a streak that carries
   speed information a single frame cannot. This is doing a large share of the
   work.
4. **Performable global state.** A movable source, a clear, randomizable motion.

**Do not copy two things from it.** It calls `new Star(...)` and
`ArrayList.remove(i)` inside the render loop, which violates the no-allocation
rule in `AGENTS.md`; use `Hyperspace`'s pool instead. And `getCurrentFace()` is
self-described as simplified — do not build on it, use `Ring` and
`available()`.

There is also a `HyperspaceOptimized`; check what it already fixes before
re-solving either problem.

### 3.3 The transferable recipe

Sparse discrete elements in surface space + a coherent global motion field +
trails + two or three scalars on the existing Envelope→Scaler lanes + one
momentary trigger.

---

## 4. Corrections — claims that were reviewed and found wrong

These were in the original design pass. They are recorded because they are all
plausible enough to be reinvented.

| Claim | Verdict | Do this instead |
|---|---|---|
| Beat should modulate the **simulation timestep** | Dangerous | Keep `dt` fixed. Modulate sim-clock *accumulation* with a capped substep count. Changing `dt` changes solver stability for Gray-Scott and any explicit wave solver |
| A global phase kick makes Kuramoto oscillators re-cohere | **Wrong** | Adding the same Δφ to every oscillator is a global rotation; the order parameter `r = abs(mean(exp(i·phi)))` is exactly invariant. Use pulse-coupled (Mirollo–Strogatz 1990), where the pulse effect is phase-dependent |
| Kuramoto has a sharp synchronization transition | Overstated | That is a mean-field, infinite-N result. 120–240 locally coupled oscillators give a soft transition and can lock into twisted states |
| Foam where `abs(dL/dtheta) > threshold` | Detects the wrong thing | Steep flanks are not breaking crests. Key foam off the **displacement Jacobian folding** (Tessendorf's whitecap criterion), which Gerstner gives analytically. Two reviewers converged on this independently |
| `foam *= 0.93` per frame | Frame-rate dependent | `foam *= exp(-lambda * dt)` |
| Gerstner steepness as an "how angry" knob | Unbounded | Past a steepness bound the horizontal map self-intersects — fold-over, not surf. Bound by aggregate steepness / Jacobian positivity |
| `omega = sqrt(g*k)` for shore break | Wrong regime | That is infinite depth. Shoaling needs `omega^2 = g*k*tanh(k*d)` with decreasing `d`. A slow level envelope is a tide or surge, not a shore break |
| Non-harmonic frequencies ⇒ no visible loop | Overstated | A finite sinusoid sum is quasi-periodic; near-recurrences can still be obvious |
| Ring wave equation makes pulses "crash" | Wrong | It is nondispersive and linear — counter-propagating pulses superpose and pass through each other |
| Bubbles accelerate as they rise | **Wrong** | They reach near-terminal velocity quickly. Expansion does not imply monotonic acceleration; drag and wake instability change too. Model constant terminal rise + size-dependent path instability. (Both model passes got this wrong; Codex caught it) |
| Caustics must be hard-thresholded | Aliases | Hard thresholds shimmer on a discrete grid. Use an antialiased distance-to-ridge field ~1.5–3 px wide |
| Worley `F1` for caustics | Wrong feature | `F2 - F1` gives the reticulated web |
| Fish as a 3D distance field crossing surfaces | **Wrong** — see §1 | Surface-native motion, or deliberate shadow projection |
| A 3–5 px fish with a travelling body wave | Illegible | No articulation at that size. Use head/tail asymmetry and velocity change; identity comes from collective motion |
| Beer–Lambert attenuation | Metaphor, not physics | These are emissive LEDs, not objects seen through water. Keep it as colour grading, with "depth" meaning distance below an imagined lit surface |
| The cylinder is a torus | Wrong topology | S¹ × [0,1]. Wrap azimuth only |
| Azimuthal undulation as a linear function of world `(x, z)` | **Wrong on the cube** — found in Milestone 1 | The square cross-section makes it piecewise linear around the ring: 28x curvature spikes at the four corners, i.e. a polyline waterline. Use integer wavenumbers in arc length `s`. See §1.1 |
| "Skip the FFT because there are only ~28k LEDs" | Right conclusion, wrong reason | FFT cost is set by spectral grid size, not output point count. Skip it because direct summation of 8–16 components is already trivial here |
| Subpixel antialiasing is the single biggest win | Partly | True for long edges and moving fronts. An isolated 60/40 bubble may just read as two dim LEDs — verify against real LED diffusion and gamma before relying on it |

Also unverified at time of writing: physical pixel **aspect ratio**. Isotropic
diffusion in index space becomes anisotropic in world space if the pitch is not
square. Measure before trusting any index-space reaction-diffusion.

---

## 5. Scenes

Each is a separate `LXPattern`. All of them: extend `ApotheneumPattern` unless
noted, expose 2–4 modulatable scalars for the K1/K2/C1/C2 lanes plus one
momentary trigger, respect `available()`, and remember Y=0 is the top.

### A. Flood — "the room fills"

The strongest single inside-the-structure ocean gesture, and the cheapest.

A horizontal plane rises through the installation. Below it: the underwater
palette, dimmer with depth. At it: an agitated bright meniscus band with sparkle.
Above it: near black.

- Level is one scalar in world Y, driven by an envelope or a macro. Per column,
  clamp the rendered band to `available()`.
- The meniscus should be 1–2 rows with an antialiased edge — this is the
  fractional-height test in §6 doing double duty.
- Add small azimuthal undulation to the level (a few low-order sinusoids) so it
  is not a perfect ring. **Parameterize it by arc length `s`, not by world
  `(x, z)` — see §1.1.** This is the one that was got wrong the first time: linear
  world coordinates put hard corners in the waterline at the cube edges.
- The traveling surge is the same trap. A surge that should read as "a wave goes
  around the room" must move in `s`. A surge in world `(x, z)` leaves two of the
  four faces without a passing wave at all.

Params: `level`, `meniscusWidth`, `agitation`, `depthFalloff`, `sparkle`.

The level itself stays a world-Y scalar — that part is genuinely a plane, and it
is correct as written. Only the *azimuthal* terms move to `s`.

### B. Bore — the crashing wave

A traveling wavefront, not a rising level. This is what actually reads as impact,
and keeping it distinct from A is deliberate: a horizon band's natural reading is
"the room is filling," so the crash needs its own vocabulary.

**Read §1.1 first.** A world-space plane front on a square cross-section hits two
opposite walls all at once. For a *crash* that may be exactly right — an impact
should slam a whole wall — but decide it deliberately, and if you want the crash
to travel *around* the audience instead, move the front in arc length `s`.

Also note the crest shape, which is the other half of "it doesn't look like a
wave": a raised cosine is **symmetric**, and no symmetric profile ever reads as
surf. A breaking wave is steep-fronted with a long gentle back. That asymmetry
plus the Gerstner horizontal displacement below is what produces curl; without
both, the result is a smooth hump regardless of how the phase is computed.

- A signed-distance wavefront in world space, tilted off vertical, sweeping
  through the structure. Field, per §1.
- Foam as a separate scalar field, seeded where the Jacobian folds, then
  **advected** with the front and decayed by `exp(-lambda*dt)`.
- Choreograph the event rather than simulating breaking: **approach** (phase
  speed ramps, crest sharpens) → **impact** (foam burst saturating and decaying)
  → **wash** (dim low-contrast front receding the other way). This maps onto the
  existing Envelope→Scaler idiom instead of fighting it.
- Optional and worth trying early: drive the **interior** face from the same
  field as its **exterior** twin with a ~200 ms lag. The wall acquires thickness
  and the wave appears to penetrate the room. Nothing else in the project's
  vocabulary does this.

For impulse-driven waves specifically, prefer Tessendorf's **iWave**
(convolution-based, built for impulses and obstacles) over a passive spectrum.

Params: `speed`, `direction`, `steepness` (bounded), `foamGain`, `interiorLag`.

### C. Underwater kit

Composable layers, each independently useful.

- **Depth grade.** Colour-only. Teal near the top, deep blue at the floor.
- **Caustics.** Two or three counter-scrolling Worley layers, `F2 - F1`, as an
  antialiased ridge field ~1.5–3 px wide. Brightest near the top. If the Gerstner
  field from B exists, deriving caustics from its surface normal makes them
  breathe with the wave above.
- **Godrays.** A handful of the 240 columns lit with slow intensity sway and
  slight azimuthal drift. Nearly free.
- **Bubbles.** Surface-space objects rising columns at near-constant terminal
  velocity, with lateral wobble. Emit in **plumes** from a source, not uniformly —
  uniform bubbles read as inverted rain. Despawn at `available()`.
- **Drift.** Advect motes with curl noise (Bridson et al. 2007) — divergence-free,
  no solver, nothing ever clumps.
- **Fish.** Schooling only — boids (Reynolds 1987) in surface space, 30–80 dots
  with an occasional coordinated startle. Collective motion is what reads; an
  individual fish does not. For a large fish, render a **shadow** gliding across
  the caustic layer: occlusion is cheaper and more legible than illumination.

### C.1 Fish, in detail — the flocking sim already exists, colour is the only open question

The §C fish bullet says "schooling only — boids." That sim is not a proposal
anymore: `Boids.java` (`src/main/java/apotheneum/doved/patterns/Boids.java`)
already exists and is a complete surface-space flock — separation, alignment,
cohesion, a `HashMap` spatial hash rebuilt per frame, per-boid organic speed
variation, door avoidance, motion-blur trails via frame decay, and
bilinear-antialiased additive rendering. This is the "collective motion is what
reads" recommendation from §C, already built.

Verified against the live rig (read-only survey, not code changes):

- `Boids` renders grayscale only (`LXColor.gray(...)`) and has no hue parameter.
  Its params are Max Flock (default 100, range 5–300), Density, Radius,
  Separation, Alignment, Cohesion, Blur, Shape.
- Each `Boids` instance renders to exactly **one** `Shape` — Cube or Cylinder,
  not both. A school crossing the whole structure needs a pair of instances,
  which doubles the instance count for full coverage.

**Colour belongs at the school level, not the individual.** At 1–4 px, a
saturated hue on one LED is just a coloured dot, and a scatter of many different
hues reads as noise or a glitch, not "colourful reef." What works: one hue per
school of 20–60 boids, with two or three schools in different hues crossing each
other. Slow hue drift across tens of seconds reads as exotic or bioluminescent.

**The fix is composition, not a code change.** Instantiate `Boids` several times
in a blend-mode channel or `PatternRack` and put a `ColorizeEffect` on each
instance. No Java, no build, no jar reload — this is live show-control work, and
it is the project's own idiom already: Treetop instantiates the same pattern two
or three times with different per-instance treatment, and RobotHeart puts 17 of
its 26 effect instances directly on patterns.

This composition is not a guess about how `ColorizeEffect` behaves — it was
checked:

- `ColorizeEffect` picks a per-pixel scalar and looks it up along a gradient,
  rescaling pixels above threshold so threshold-to-max spans the full gradient.
  It therefore **preserves** the antialiased brightness ramp rather than
  flattening it — grayscale boids keep their soft edges and gain a hue.
- **No trail interference between instances.** The concern was that several
  instances each decaying a shared buffer would shorten every school's trail.
  They do not: `colors` is backed by an `LXBuffer` owned **per component
  instance** in `LXLayeredComponent` (LX source), reset each `loop()`. Each
  instance decays its own buffer; the channel blend merges already-decayed
  buffers. Adding schools costs nothing in trail length.
- Rough estimate, not measured: 3–4 instances is ~300–400 boids across separate
  spatial hashes, unlikely to bottleneck at 60fps over ~28k points.

**Cheapest experiment that proves or kills the whole idea:** build the
multi-instance Boids + Colorize rack live and look at it. Minutes, no code.

**Still worth building in Java, separately:** a coordinated startle trigger — a
brief spike in turbulence plus a biased target heading across a school. §C
already names this as the thing that sells school-of-fish over particle-system,
and composition cannot provide it; it needs a new parameter path through the
sim.

**The rest of the fish thread, for completeness:**

- **Shark / large creature.** An occlusion mask subtracted from `Flood`'s
  already-lit field (§A), moving in arc length `s` per §1.1. Cheaper than any
  lit version and more legible — occlusion reads as solidity where a lit 3–5 px
  blob does not. Needs a tapered footprint (~5–10 columns wide, 5–10 rows, wide
  at the head narrowing to the tail) and curved, hesitating, burst-then-coast
  motion. A constant-velocity rectangle reads as a projector glitch, not an
  animal.
- **Coral reef.** Dead end. Depicted coral at 1–4 px reads as broken LEDs —
  actively worse than an empty floor. Cut it. What survives is a warm, mottled,
  low-frequency colour band in the bottom rows of the existing depth gradient
  (§C): "reef mood" without depicting a single object.
- **"Just move an image around."** `DeformableImage` exists — GIF animation,
  translate/scale/rotate, four wrap modes — but it projects via the whole
  model's bounding-box UV, i.e. exactly the linear world-XZ mapping §1.1 shows
  is broken on a square cross-section. A sprite shark would kink at every cube
  corner. Wins on silhouette fidelity, loses on geometry; not the first thing to
  reach for.

### D. Excitable medium — Greenberg–Hastings

The best organic CA for this rig. Discrete states (resting → excited →
refractory → resting), which makes it robust where FitzHugh–Nagumo is fiddly.
Gives spiral waves, expanding target rings, and annihilation on collision. Hard
edged fronts, so it survives low resolution.

Runs directly on the cube 200-ring and the cylinder 120-ring. A kick nucleates a
new target; the refractory period is a natural tempo lock. Door cells must be
excluded from the neighbour sum, not merely held at zero.

### E. Gray–Scott, with advection

Turing patterns: spots, worms, labyrinths, mitosis. `F` and `k` on two encoders.

Two cautions carried from review: it is a **texture, not motion** — carry the
chemistry on a slow curl-noise current (reaction-advection-diffusion) or it looks
like a laboratory plate. And do not let audio hammer `F`/`k`; the sim collapses.
Instead **inject chemical splats on the kick**, so each hit blooms a ring outward.
Ship curated presets rather than assuming a live `F`/`k` sweep is safe or
continuous.

Choose feature wavelengths that fit 43–45 vertical samples.

### F. Physarum

Agents deposit and follow a decaying pheromone trail; the trail map is the render.
Structurally the closest organic sibling to `Hyperspace2D` — many discrete
elements in surface space, with persistence doing the visual work. Self-organizes
into a living network that continuously rewires. Sensor angle, sensor distance,
deposit rate, and decay are the performance knobs.

### G. Pulse-coupled synchronization

Fireflies. Use **Mirollo–Strogatz pulse coupling**, not plain Kuramoto — see §4.
Coupling strength on a knob; the ensemble converges from incoherent flicker into
unison. Best as an overlay or as a modulation source rather than a headline
visual, since synchronization generates timing, not spatial form.

---

## 6. Build order

Ordered so that each step de-risks the next, and so the first thing built is also
the calibration harness.

**1. Flood (§A).** It is a finished, performable gesture *and* it answers every
open calibration question at once: does a fractional-height line render smoothly,
what does the gamma curve do to a soft edge, does the cube read continuously with
the cylinder, and do the door clamps behave. Build it first for that reason.

Acceptance: the level sweeps top to bottom with no visible stair-stepping; the
meniscus does not cross a door frame; the waterline is continuous where a cube
face meets the next; it looks right from at least three positions inside.

**2. Bore (§B)** — the actual ocean objective. Adds the world-space field, foam
advection, and the interior/exterior lag test.

Acceptance: a front crosses cube and cylinder in step; foam appears at the crest
and lingers behind it; a kick produces a visible discrete impact.

**3. Underwater kit (§C)** — layers on top of 1 and 2, and reuses the wave field
for caustics.

**4. Greenberg–Hastings (§D)** — the first organic pattern, and the most robust.

**5. Gray–Scott (§E)** and **Physarum (§F)**, in either order.

**6. Pulse-coupled sync (§G)** as an overlay.

Before step 1, spend twenty minutes on a throwaway calibration pass if anything
in §2 is in doubt: sweep a fractional-height line, orbit a single dot, and draw a
circle in index space next to one in world space. That measures the pixel aspect
ratio and the subpixel question directly instead of assuming either.

---

## 7. Open questions

- Physical pixel **aspect ratio** — unmeasured. Blocks index-space reaction-
  diffusion (§E) being trustworthy.
- Whether subpixel antialiasing helps or hurts **isolated single-pixel** elements
  on this hardware, given LED diffusion and gamma. Settled by step 1.
- What `HyperspaceOptimized` already fixes.
- The `AGENTS.md` 13,280 vs. 28,320 point-count discrepancy — cosmetic, but
  someone should reconcile the doc.
- Whether the ~200 ms interior/exterior lag (§B) actually reads as thickness or
  just as blur. Cheap to test, no prior art in the project.

---

## 8. References

- Gerstner / trochoidal waves; Fournier & Reeves, *A Simple Model of Ocean Waves*, SIGGRAPH 1986
- Finch, *Effective Water Simulation from Physical Models*, GPU Gems, 2004
- Tessendorf, *Simulating Ocean Water*, SIGGRAPH course notes, ~1999–2001 — spectrum, dispersion, whitecap criterion
- Tessendorf, *Interactive Water Surfaces* (iWave) — convolution solver for impulses
- Worley, *A Cellular Texture Basis Function*, SIGGRAPH 1996
- Bridson, Hourihan & Nordenstam, *Curl-Noise for Procedural Fluid Flow*, SIGGRAPH 2007
- Stam, *Stable Fluids*, SIGGRAPH 1999 — feasible at this grid size if genuine surge is ever wanted
- Reynolds, *Flocks, Herds, and Schools*, SIGGRAPH 1987
- Turing, *The Chemical Basis of Morphogenesis*, 1952
- Pearson, *Complex Patterns in a Simple System*, Science 1993 — the Gray-Scott F/k map
- Greenberg & Hastings, SIAM J. Appl. Math, 1978 — excitable media
- Jones, 2010 — Physarum transport networks
- Mirollo & Strogatz, *Synchronization of Pulse-Coupled Biological Oscillators*, 1990
- Witten & Sander, 1981 — diffusion-limited aggregation
- Runions, Lane & Prusinkiewicz — space colonization / venation (date uncertain, ~2005–2007)
