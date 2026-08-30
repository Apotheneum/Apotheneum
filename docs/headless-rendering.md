# Rendering a pattern headlessly

Produces visual evidence of what a pattern does without launching Chromatik, so it
works in a worktree, in CI, and in a cloud session. The renderer is
`src/test/java/apotheneum/render/RenderSpike.java` (test scope — it never ships in
the package jar).

## Run it

Render Fireflies (the default):

```bash
mvn -Ptests test-compile exec:exec
```

Render a specific pattern by fully-qualified class name:

```bash
mvn -Ptests -Dpattern=apotheneum.mcslee.CylinderRings test-compile exec:exec
```

Set registered pattern parameters by path with a comma-separated `name=value` list:

```bash
mvn -Ptests test-compile exec:exec \
  -Dpattern=apotheneum.doved.patterns.Raybeam \
  -Dparams=shape=Cone,coneAngle=0.35,radius=0.3
```

Render the cylinder interior from the center of the floor looking straight up:

```bash
mvn -Ptests test-compile exec:exec \
  -Dpattern=apotheneum.doved.patterns.Vortex \
  -Dview=lookup
```

The default `unwrapped` view remains unchanged. The `lookup` view writes only
`cylinder-interior-lookup.gif` and its contact sheet. It uses a 512×512 equidistant
180-degree fisheye projection: image radius is proportional to the real fixture point's
polar angle away from straight up, as seen from the cylinder floor center. The top ring
therefore falls near the image center. Every output pixel samples its nearest real
cylinder-interior LED with no smoothing.

**The black margin at the rim is correct, not missing output.** Sampling is bounded half a
row beyond the first and last real rows, so the image only covers where LEDs physically
are. The fixture's bottom row sits near an 80-degree polar angle rather than at the
90-degree rim, so on the current geometry the lit field ends around 231 px of a 255.5 px
radius. Without that bound the outer annulus would clamp onto the final row and draw the
bottom LEDs several times thicker than they are. The projection logs all four radii so
this is checkable per run:

```
RenderSpike lookupProjection=equidistant-fisheye fovDegrees=180 \
  topRadiusPx=64.48 bottomRadiusPx=227.01 \
  sampledInnerPx=63.83 sampledOuterPx=231.12 imageRimPx=255.50
```

Numeric, boolean, discrete-option and enum parameters are supported. Names must match
the pattern's registered parameter paths exactly. A direct child-component parameter is
addressable as `child/parameter`; for example, Fireball's primary role's physics coupling can
be assigned with `-Dparams=primary/amount=0.8`. Option and enum values are case-insensitive. An
unknown name fails with the complete list of available names, and the renderer logs the
resolved values so the invocation remains self-describing.

### Drive a static position parameter

Patterns ship static by default under the motion guideline: performers wire LX modulation
to a plain position parameter rather than relying on a pattern-owned speed accumulator.
`RenderSpike` can exercise that same performance path with `-Dmodulate=`. Its value is
`parameter:cyclesPerSecond`; it registers a deterministic 0→1 sawtooth, attaches it through
`LXCompoundModulation`, moves the target's base to the bottom of its range so the sweep
covers the whole parameter, and logs the resolved target, rate, period, range, shape and the
value the sweep starts and ends on. The base is set by normalized position, not by numeric
value, so a bipolar target such as Gravity's `gravityDirection` (`-1..1`) sweeps its full
range rather than only the upper half.

For example, this moves Vortex's otherwise-static `spin` through 0.6 of a turn over the
renderer's five-second run:

```bash
mvn -Ptests test-compile exec:exec \
  -Dpattern=apotheneum.doved.patterns.Vortex \
  -Dmodulate=spin:0.12
```

The target must be a registered `CompoundParameter` or `CompoundDiscreteParameter`; direct
child-component targets use the same `child/parameter` spelling, such as
`-Dmodulate=primary/amount:0.2` for Fireball. An unknown target lists all available names
and a non-modulatable target fails clearly. Without `-Dmodulate=`, existing renderer invocations
are unchanged. When invoked directly, the modulation assignment is the sixth positional argument,
after the view name.

Combined with `-Deffects=`, the sawtooth restarts from basis 0 at the top of each variant, so
`<surface>.gif` and `<surface>-effects.gif` cover the identical parameter sweep and the only
difference between them is the effect.

Add channel effects by fully-qualified class name with a comma-separated list. They are
applied in the order given:

```bash
mvn -Ptests test-compile exec:exec \
  -Dpattern=apotheneum.doved.patterns.Raybeam \
  -Dparams=shape=Cone \
  -Deffects=heronarts.lx.effect.HueSaturationEffect
```

An effects invocation produces both variants in one run: `<surface>.gif` and
`<surface>-contact.png` with every listed effect bypassed, then
`<surface>-effects.gif` and `<surface>-effects-contact.png` with them enabled. Unknown
classes and classes that do not extend `LXEffect` fail before rendering.

The selected class must extend `LXPattern` and have a public constructor accepting
`LX`. `RenderSpike.main` also accepts these as positional arguments when invoked
directly: the class name first, then the parameter list, the effect class list, the
palette assignment, the view name, the modulation assignment, the `ApotheneumColor`
per-surface offsets, and the `ApotheneumGradient` azimuth/elevation, in that order; an
absent or blank class name selects Fireflies.

### `GradientMultiplyEffect` needs an explicit `-DapotheneumColor=` *and* `-DapotheneumGradient=`

`GradientMultiplyEffect` owns neither its colours nor its direction — both are read from
the shared, engine-registered `ApotheneumColor` and `ApotheneumGradient` singletons (see
`ApotheneumGradient`'s class javadoc for why the direction moved from four per-surface 2D
angles to one shared 3D vector). Render it with `-Deffects=apotheneum.doved.effects.GradientMultiplyEffect`
on a plain host pattern (a uniform field, e.g. a `SolidPattern`, isolates the effect's own
transformation — see `GradientMultiplyEffectWiringTest` for the same choice at the test
level). With no `-DapotheneumColor=` the multiply is a no-op (both ends resolve neutral
white); with no `-DapotheneumGradient=` the direction falls back to straight up
(`ApotheneumGradient`'s own default), which is a real, useful thing to render once but
does not exercise the horizontal case. Pass both, and quote the palette per the note above:

```bash
mvn -Ptests test-compile exec:exec \
  -Dpattern=heronarts.lx.pattern.color.SolidPattern \
  -Deffects=apotheneum.doved.effects.GradientMultiplyEffect \
  '-Dpalette=30,95,100;210,92,100' \
  -DapotheneumColor=0,0,0 \
  -DapotheneumGradient=0,0
```

The `-DapotheneumColor=` spec is `pair,swap,axis` — three values, not four; see the
colour-native section below for what each one does.

The `-DapotheneumGradient=` spec is `azimuth,elevation` in degrees — `0,0` is fully horizontal at azimuth 0;
`0,90` (or `0,-90`) is the vertical gradient confirmed to look right on every surface;
something like `45,30` exercises a genuine diagonal. Render all three before trusting a
change to the projection math: a seam that only shows up off-axis is exactly the kind of
regression a single "looks fine" render at one direction would miss.

### Colour-native patterns need an explicit `-Dpalette=` *and* `-DapotheneumColor=`

`ColorNativePattern` no longer owns a palette index of its own — every instance reads the
single global `apotheneum.doved.modulators.ApotheneumColor` singleton (see that class's
javadoc). **With no `ApotheneumColor` in the render, every role resolves neutral white**
(`ColorRole#resolveBase`'s fallback), same as an explicitly-disabled `Color` toggle — a
colour-native pattern therefore renders in greyscale by default and reads as broken when it
is not. Deliberately not listed by name here: the set grows with every colour-native pattern,
and a list that goes stale is worse than none, because a reviewer checks it, does not find
the pattern in hand, and concludes the warning does not apply. `git grep -l "extends
ColorNativePattern"` is the current answer.

The default project palette also has **exactly one swatch stop**, pure red — a fresh `LX`
reports `lx.engine.palette.swatch.colors.size() == 1` at hue 0 / saturation 100 / brightness
100 — so even with an `ApotheneumColor` present, every surface clamps onto that single stop
unless a real palette is passed too. Both are required for a meaningful render, not either
alone.

Pass a palette. The spec is `hue,sat,bri` per stop, semicolon-separated, appended to the
swatch as needed:

```bash
mvn -Ptests test-compile exec:exec \
  -Dpattern=apotheneum.doved.patterns.Fireball \
  '-Dpalette=30,95,100;210,92,100'
```

Pass an `ApotheneumColor`. The spec is **three** integers, comma-separated — `pair,swap,axis`
— set directly on the registered singleton's three parameters. There is no per-surface value
to pass: `ApotheneumColor` no longer has per-surface `indexOffset`/`hueOffset`/`satTrim`
parameters, and `axis` is what makes the four surfaces resolve to different stops (see that
class's `Axis` javadoc). `pair` is 0 or 1, `swap` is 0 or 1, and `axis` is 0 (None), 1
(Shape) or 2 (In/Out). Anything other than three values is rejected outright with
`Invalid apotheneumColor spec`.

`axis=1` (Shape, cube and cylinder one stop apart) is the setting that proves the surfaces
resolve independently, so it is the useful default for a colour render:

```bash
mvn -Ptests test-compile exec:exec \
  -Dpattern=apotheneum.doved.patterns.Rockfall \
  -DapotheneumColor=0,0,1 \
  '-Dpalette=200,90,70;30,90,70;280,92,70'
```

Give the palette **at least as many stops as the axis will reach** — `axis` shifts a surface
by one stop and `ApotheneumColor` wraps around the live stop count, so a two-stop palette
under `axis=1` puts the cylinder on the same stop pair the cube already has, wrapped, rather
than on a visibly distinct one. Three or more stops, as above, makes the shift legible.

Quote it — the stop separator is a `;`, which an unquoted shell reads as a command
separator. On a cube-exterior contact sheet that example is the difference between one
occupied hue bucket and eight. The renderer logs the resolved stops
(`RenderSpike palette=…`, or `palette=(project default)`), so which one you got is
always in the log.

### Invoke `RenderSpike` directly for `-D` properties and tight iteration

The pom's exec plugin forks `java` with a **fixed** argument list — the eight positional
arguments above, plus `-Djava.awt.headless=true`. It has no `<systemProperties>` and no
pass-through, so an arbitrary `-Dfoo=bar` on the `mvn` command line stays a Maven
property and never reaches the forked JVM. Anything a pattern reads through
`System.getProperty` therefore silently does not arrive — `Rockfall`'s
`apotheneum.rockfall.seed` is the case that matters, because a "same-seed" comparison
that quietly is not one looks like a real behavioural difference. Two `mvn exec:exec`
runs with the identical seed produce different frames; two direct runs produce
bit-identical ones.

Invoke `main` yourself on the already-built classpath. This also skips the Maven round
trip, which is most of the wall time on a tight edit-render loop:

```bash
mvn -Ptests test-compile          # once, after each source change

java -Djava.awt.headless=true -Dapotheneum.rockfall.seed=4242 \
  -cp "target/classes:target/test-classes:/Applications/Chromatik.app/Contents/app/glxstudio-1.2.2-jar-with-dependencies.jar" \
  apotheneum.render.RenderSpike apotheneum.doved.patterns.Rockfall
```

The shaded `glxstudio` jar that ships inside Chromatik.app carries `lx`, `glx` and
`glxstudio` together, which is the whole provided-scope classpath `RenderSpike` needs, and
it is already on disk. Deriving the classpath with `mvn dependency:build-classpath`
instead does not work from a clean or offline checkout: `maven-dependency-plugin` is not
declared in `pom.xml`, so `dependency:` resolves the plugin over the network and fails
with `No plugin found for prefix 'dependency'` where there is none. If Chromatik is
installed somewhere else, point at that jar instead.

Arguments after the class name are positional and in the order listed above, so an empty
string holds a slot:

```bash
java -Djava.awt.headless=true \
  -cp "target/classes:target/test-classes:$CP" \
  apotheneum.render.RenderSpike apotheneum.doved.patterns.Fireball \
  "" "" "30,95,100;210,92,100"
```

Use the same JDK the build uses (see AGENTS.md); a `java` that cannot read Chromatik's
class files fails here exactly as it does in a build.

The renderer requires `ffmpeg` on `PATH` to assemble GIFs. This is an agent-only
rendering dependency, not a package build dependency. The command checks for it before
loading LX or rendering any frames and fails immediately with installation guidance if
it is unavailable.

Writes to `target/spike/`: `<surface>.gif` and `<surface>-contact.png` for every
surface that contains a lit usable pixel during the run. For example,
`cube-exterior.gif` and `cube-exterior-contact.png`. The per-frame PNGs used to
assemble each GIF live in a temporary directory and are removed afterwards.

## Renders are never committed

The GIFs and contact sheets are evidence for one review conversation. They are not
documentation, and they do not belong in the repository — a few hundred KB per pattern
PR, kept forever, for images nobody opens again after the merge.

`target/` is already gitignored, so leaving the output where the renderer writes it is
the correct and easiest thing to do. Do not copy it anywhere else in the tree.

### Recommended (optional): publish to your own storage and link

If you have your own object storage that returns a stable public URL, uploading a
render there and linking the result in the PR is better than handing over local
paths — and it's the only handoff here that works from a cloud or remote session.

This is personal tooling, not a repository requirement. There is no supported
in-repo script for it; contributors without their own storage use the local
handoff below, and that must keep working for everyone, including contributors
from a fork.

As one example, publishing a GIF to a Cloudflare R2 bucket with Wrangler looks
like:

```bash
wrangler r2 object put my-bucket/apotheneum/cube-exterior.gif \
  --file=target/spike/cube-exterior.gif --remote
```

`--remote` is required — without it Wrangler writes to a local simulated bucket
instead of R2. See the
[full R2 Wrangler command reference](https://developers.cloudflare.com/r2/reference/wrangler-commands/)
for making the bucket public and other providers' equivalents.

Cloudflare MCP servers cannot publish these files. The Workers Bindings server exposes
only bucket-level R2 tools: `r2_buckets_list`, `r2_bucket_create`, `r2_bucket_get` and
`r2_bucket_delete`. It has no object-put or public-URL tool, and authenticating it does
not add one. Publishing uses the Wrangler CLI, not MCP.

### Default for local sessions: hand off the printed paths

This path needs no setup and works for anyone, including contributors from a fork, but
only when the agent and human share a filesystem.

At the end of a successful run, after the statistics, the renderer prints the absolute
path of every artifact it wrote so the files can be dragged straight into the PR
description or a comment:

```
Renders ready to attach:
  /abs/path/to/target/spike/cube-exterior.gif
  /abs/path/to/target/spike/cube-exterior-contact.png
```

Skipped surfaces are omitted. When effects are requested, the block groups the files
under `Effects bypassed` and `Effects applied` headings so the comparison is
unambiguous.

Dragging a file into a GitHub comment box uploads it to GitHub's own attachment host and
inlines it, with no commit involved. That upload is browser-only — there is no `gh`
command and no public API for it — which is exactly why the renderer hands off the paths
rather than an agent trying to get the images into the PR itself. Committing them into
`docs/` to work around the missing API is the thing this section exists to prevent.

In a cloud or remote session, these paths name a filesystem the human cannot reach. The
agent must publish through the optional path above or say plainly in the PR that renders
could not be attached and why. Never commit them as a workaround: a stated gap is
recoverable; a committed binary is permanent.

## Effects and modulators need a host

A pattern renders itself. An effect and a modulator do not — an effect transforms
something, a modulator is a number until something consumes it. Rendering either one
alone shows nothing, so each needs a host pattern and a stated comparison.

**Effects — use `-Deffects=` to render the pair, off and on.** The renderer adds the
listed effects to the channel, disables them for the unsuffixed artifacts, then enables
the same instances for the `-effects` artifacts. It samples the channel's post-effect
mix in both cases. The *difference* is the review artifact; a single frame of an
effected pattern tells a reviewer nothing about what the effect did.

These are two distinct intents. To review a pattern's own logic, omit `-Deffects=` so
the renderer shows only that pattern through the normal channel mix. To review an effect
or see the result of colorization, pass `-Deffects=` and inspect the generated pair.

**Modulators — wire it, then name the target.** Register the modulator, wire it to a
specific parameter of a host pattern via `LXCompoundModulation`, run, and render. Say in
the PR which parameter it drove — "SampleHold → GradientPattern.hue" — because the same
modulator on a different parameter tells a completely different story, and a reviewer
who has to guess the wiring is reviewing their own guess.

A modulator's *correctness* is numeric and belongs in a unit test (see the existing
`SampleHoldTest`, `SelectorTest`, `TempoTapTest`). The render shows something different
and equally necessary: whether the movement is musically useful. Neither replaces the
other.

**Choosing a host.** Prefer a stock LX pattern so the render isn't confounded by a
second thing changing:

- `SolidPattern` — a uniform field. The default choice, because any transformation is
  unambiguous against flat colour.
- `GradientPattern` — when the effect or modulation is position- or hue-dependent and a
  flat field would hide it.

Reach for an Apotheneum pattern as host only when the behaviour under review needs real
geometry, and say why in the PR.

## Which surfaces to render

Apotheneum is **two nested chambers — a cube and a cylinder.** There is no sphere.
Each chamber has an exterior and, when `Apotheneum.hasInterior` is true, an interior.
So there are four renderable surfaces, and which ones matter depends entirely on the
pattern:

| Surface | Unwrapped size | Accessor |
|---|---|---|
| Cube exterior | 200 × 45 (4 faces × 50 columns) | `Apotheneum.cube.exterior` |
| Cube interior | 200 × 45 | `Apotheneum.cube.interior` |
| Cylinder exterior | 120 × 43 (`RING_LENGTH` × `CYLINDER_HEIGHT`) | `Apotheneum.cylinder.exterior` |
| Cylinder interior | 120 × 43 | `Apotheneum.cylinder.interior` |

**One GIF per surface**, named for it — `cube-exterior.gif`, `cylinder-exterior.gif`,
and so on. Not a composite. The two chambers unwrap to different widths (200 vs 120),
so any single image needs gutters, labels, and a decision about alignment, all to
produce something the reader then has to visually separate again. Separate files skip
all of it, and let the reviewer look at one surface at a time.

Render every surface the pattern drives; **the agent then chooses which to attach to
the PR description.** That choice is the point — a pattern whose whole idea is the
interior wants the interior GIF, and attaching all four would bury it.

Skip a surface the pattern never lights. A reviewer seeing an all-black panel
reasonably concludes the pattern is broken, so an empty render is worse than a missing
one. A skipped-surface log includes its peak non-black fraction and, for a spherical
active region exposing normalized `originX/Y/Z`, `radius` and `width`, the nearest
model-point distance and active-region gap. Skip interior surfaces entirely when
`Apotheneum.hasInterior` is false.

Points come from `orientation.point(x, y)` or `column.points[y]`, and **Y=0 is the
top.** Use `surface.available(globalColumn)` for a column's usable height at a door —
never `column.points.length`, which is full height on every column, door or not.

## Frame rate — get this right or the motion lies

**The engine runs at 60fps. The GIF plays at 30.** So write every *other* frame, or
playback is half speed. This is not cosmetic — a pattern reviewed at half speed reads
as calmer and smoother than it will be on the installation, which is exactly the
judgement the render exists to support.

- 5 seconds of real time = 300 engine frames = **150 written frames** at 30fps.
- The first version of this doc got it wrong and produced a 10-second GIF of 5
  seconds of animation. If a render looks unexpectedly languid, check this first.

### Those five seconds are simulated — and they are all you get

`RenderSpike` calls `lx.engine.setFixedDeltaMs(1000. / 60.)` before the first
`lx.engine.run()`. `LXEngine` derives `deltaMs` from the wall clock and then overrides
it with the fixed value when one is set, so 300 frames is exactly 5000 ms of *pattern*
time however fast the machine ran them — a render takes well under a second of real
time.

The fixed step makes simulated *time* deterministic. It does not by itself make two
invocations byte-identical, and an A/B comparison is only meaningful where the pattern's
randomness is controlled too. `Fireflies` calls `Math.random()` throughout, and
`Rockfall.createRandom()` falls back to an unseeded `new Random()` when its seed property
is absent — two runs of either differ from each other on unchanged code, so a diff proves
nothing. Patterns that seed from a constant (`Fireball`) or from a property that is
actually set are reproducible, and those are the ones worth diffing. The logged
`meanFrameMs` is wall-clock from `System.nanoTime()` regardless, so it varies run to run
under any pattern; never read a frame-time difference as a behavioural one. Before
attributing an A/B difference to a change, render the *unchanged* code twice and confirm
those two agree.

Five seconds is the entire window, and two things follow.

- **Judge slow motion by rendering it faster.** A pattern whose time constants are
  longer than the window looks frozen or dead in the render when nothing is wrong with
  it. Render again with the relevant parameter raised — `-Dparams=` for a rate,
  `-Dmodulate=` for a position — and say in the PR that you did, and at what value.
- **A pattern starting cold shows only its startup transient.** Five seconds of an empty
  field filling up is not five seconds of the pattern. Seed a warm initial state at
  construction. That is worth doing regardless of rendering: it is also what a show
  operator sees every time the pattern goes active.

The renderer assembles each GIF with ffmpeg — no ImageMagick on this machine. Its
per-surface command is equivalent to:

```bash
ffmpeg -y -framerate 30 -start_number 1 \
  -i /tmp/render-frames/cube-exterior/frame-%03d.png \
  -vf "scale=iw:ih:flags=neighbor,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=none" \
  target/spike/cube-exterior.gif
```

`flags=neighbor` and `dither=none` matter: this is pixel art at LED scale, and smooth
scaling or dithering invents detail that isn't in the render.

## Resolution has a hard ceiling

The cube exterior is 200×45; the cylinder is 120×43. That is all the detail that
exists. The default 4× render is an upscale for legibility — going higher gives bigger
blocks and a bigger file, never more information. Don't reach for a larger scale
expecting a sharper image.

## The six things that break this

Established facts, verified 2026-08-20/21. Don't re-derive them.

1. **The model has 28,320 points, not 13,280.** `AGENTS.md` quotes 13,280 — that is
   *physical LEDs*: exterior only, after door masking. The model carries interior
   surfaces and full logical columns. Any coverage or non-black fraction divides by
   28,320, so expect roughly half the number you'd guess.

2. **The model is hollow.** It contains the cube and cylinder shells, with no points
   filling the volume between them. The nearest point to the normalized centre is about
   .36 away, so a centred sphere with radius .3 and width .05 correctly renders black:
   its active region ends at .35 before reaching an LED.

3. **Output is ON after construction.** `OutputMode.INACTIVE` alone leaves
   `lx.engine.output.enabled` true, and `Apotheneum.lxf` carries real Art-Net
   addresses for the installation. **Disable output explicitly before loading the
   fixture, and assert it stayed false.** This is the one mistake with consequences
   outside your machine.

4. **`Fixtures` is capitalized; this repo's directory is not.** `JsonFixture` resolves
   via `lx.getMediaFile(LX.Media.FIXTURES, "Apotheneum.lxf")` → `<mediaPath>/Fixtures/`.
   The repo stores it at `src/main/resources/fixtures/` (lowercase). macOS is
   case-insensitive so a naive path works locally and fails on Linux CI. Copy the
   `.lxf` into a correctly-cased `Fixtures/` dir inside a temp dir and point
   `LX.Flags.mediaPath` there. Never point mediaPath at `src/main/resources`.

5. **`Apotheneum` holds static state — one `LX` per process.** `initialized`, `lx`,
   `cube`, `cylinder`, and `exists` are all static, and `initialize()` early-returns
   if already initialized, so a second `LX` in the same JVM silently keeps the first
   one's model listener. To render several patterns, build **one** `LX` and swap
   patterns on the channel. This is also why surefire runs `reuseForks=false`.

6. **`addFixture()` only stages regeneration.** Call `structure.beforeEngineRun()`
   before `Apotheneum.initialize(lx)`, or the model isn't there yet and
   `Apotheneum.exists` comes back false.

## Never

- **Never run `mvn -Pinstall install`.** It copies a jar into `~/Chromatik/Packages`,
  which is shared by every worktree and by the live rig. Plain `mvn -Ptests …`, or the
  direct `java` invocation above, only.
- **Never commit render output.** It goes to `target/spike/`, which is gitignored. The
  renderer prints the paths for a human to attach; see "Renders are never committed"
  above.
- **Never substitute a simplified or synthetic model** to make a render succeed. A
  fake model that renders defeats the entire purpose — the point is to see the pattern
  on the real geometry. If the fixture won't load, stop and report where.

## Reading the output

Check the cheap numbers before spending tokens on an image — non-black fraction, mean
brightness, ms/frame. Most failures (all black, no coverage, NaN, blown frame budget)
show up there for free.

Spend an image when the numbers pass and the question is aesthetic. The five-second
run samples every tenth engine frame, so a contact sheet contains 30 frames. At the
default 4× scale and three columns, cube sheets are 2416×1872 and cylinder sheets are
1456×1792.

Review images get a 2× gamma/brightness lift so dim patterns are judgeable, and blue
markers separate front/right/back/left on the unwrap. **The lift applies to the image
only, never to the statistics** — keep it that way, or the numbers stop meaning
anything.

### A fine-scale claim needs a crop, not the contact sheet

A door cutout is 10 columns by 11 rows of a 200×45 unwrap. A whole-surface contact sheet
or GIF will not settle a claim at that scale, for two reasons that upscaling does not
fix:

- **Cells with no model point are drawn `LXColor.BLACK`** — the same black as an LED the
  pattern left dark. Against a fully-lit field the cutouts do read as clear notches, but
  against anything sparse you cannot tell "no LED here" from "the pattern wrote nothing
  here", which is usually the question being asked.
- **The sheet samples every tenth engine frame.** A transient at a door edge can happen
  entirely between two samples.

So when the claim is about a handful of pixels, build the evidence ad hoc: crop the
region of interest, magnify it with nearest-neighbour
(`scale=iw*8:ih*8:flags=neighbor`), sample every N frames rather than every tenth, and
tint the model-missing cells so a hole is legible as a hole. It is a throwaway script,
and it is the only thing that shows the behaviour. Its output is render output like any
other — it belongs in `target/spike/` and is never committed.

Rough performance, for spotting a regression rather than for benchmarking: **~300–400 ms**
JVM start plus fixture parse, **~0.22–0.28 ms/frame**. Startup dominates, so a 5-second
render is well under a second of engine time.

## Known gaps

- **A remote session with no publishing set up cannot attach renders.** The printed paths
  name a filesystem the reviewer cannot reach. Say so in the PR and fall back to the
  numeric statistics; never commit the files instead.
- **No before/after diff.** Comparing two renders of the same pattern across a change
  is done by eye today.
