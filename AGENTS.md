# AGENTS.md

Project context for AI coding assistants working in this repository — Claude Code, Codex, Cursor, and anything else that reads a repo instruction file.

**Edit this file, not `CLAUDE.md`.** Claude Code reads `CLAUDE.md` rather than `AGENTS.md`, so `CLAUDE.md` is a one-line `@AGENTS.md` import that expands this file into context at launch. There is nothing to keep in sync. `scripts/check-agent-instructions.sh` verifies the import is intact.

**Any change to a pattern, effect or modulator ends with a render — read [docs/headless-rendering.md](docs/headless-rendering.md) before you start.** It renders against the real installation geometry with no Chromatik running and no jar installed, producing stills and an animated GIF per surface. **An effect or modulator has no output of its own** — host it on a known pattern and render the comparison (effect off vs on; modulator wired to a named parameter). The doc's "Effects and modulators need a host" section covers this; rendering one standalone shows nothing. Attach the render to the PR: hand over the printed local paths for a human to drag in, or — if you have your own object storage with a stable public URL — publish it there and paste the link instead. Either way, **never commit it**; it is written to gitignored `target/spike/`. It is the only way a reviewer sees what the change actually looks like, and looking at the output costs fewer tokens than reading the code that produced it. The doc also covers which surfaces to render for which pattern, and the six things that reliably break the render — including that output is enabled by default and the fixture carries real Art-Net addresses for the installation.

**Before writing or reviewing LX code, read [docs/lx-coding-guidelines.md](docs/lx-coding-guidelines.md)** — LX idioms distilled from `@mcslee`'s reviews on this repo plus conventions read out of the LX source (no render-loop allocation, enums over magic constants, framework helpers, logging, plugin/component lifecycle). It ends with a review checklist to use alongside `/code-review`. This file covers the Apotheneum-specific geometry, base classes and UI rules.

## Debugging & Logs

- **Log files location**: `~/Chromatik/Logs` - Check here for console output and debug messages from patterns
- **Logging in patterns**: Use `LX.log("message")` instead of `System.out.println()` to ensure messages appear in Chromatik log files
- **System.out.println**: Does NOT appear in Chromatik log files - always use `LX.log()` for debugging output

## Project Overview

Apotheneum is a visual, sonic and haptic instrument for immersive LED art installations. It consists of two nested chambers (cube and cylinder) with 13,280 LED nodes total, built on the Chromatik Digital Lighting Workstation framework.

## Build Commands

- **Build**: `mvn compile` - Compiles the Java source code
- **Install**: `mvn -Pinstall install` - Builds and installs the package to ~/Chromatik/Packages
- **Quick Install**: `./update.command` - Convenience script that runs `mvn install`

**IMPORTANT**: Always use `mvn -Pinstall install` instead of `mvn compile` when working on patterns, as this updates the Chromatik code and makes changes available in the lighting system.

### Testing

Tests are **opt-in**, so a normal build never waits on them:

```bash
mvn -Ptests test
```

`maven.test.skip` defaults to true, which skips test *compilation* as well, so
`mvn compile` and `mvn -Pinstall install` neither build nor run the suite. If you
add tests and they appear not to run, you almost certainly omitted `-Ptests`.

**A pattern change is not done until it has been rendered.** `mvn -Ptests test-compile exec:exec`
writes stills, a contact sheet and a GIF per surface to `target/spike/`. Check the cheap numbers
first — non-black fraction, mean brightness, ms/frame — and look at an image only once those pass.
See [docs/headless-rendering.md](docs/headless-rendering.md).

Tests live under `src/test/java`. Extend
[`HeadlessLxTest`](src/test/java/apotheneum/HeadlessLxTest.java) for anything
needing an `LX` instance: it constructs one per test over a small `GridModel` and
disposes it afterwards. Disposal is not optional — each `new LX(...)` starts a
non-daemon MIDI device-update thread that contends on a static CoreMIDI lock on
macOS, and undisposed instances accumulate until construction deadlocks. For the
same reason surefire runs with `reuseForks=false`, giving every test class a fresh
JVM, which makes the suite slower than its test count suggests.

Note a component that implements a `UI*Controls` interface pulls glxstudio in at
class-load time, so tests touching it need glxstudio on the classpath — it is a
`provided` dependency, so this works out of the box.

### Releasing

CI (`.github/workflows/release.yml`) builds the JAR and creates the GitHub Release. Never build and upload a release JAR by hand. Two ways to cut one:

Push a calendar tag:

```bash
git tag 2026.07.21 && git push origin 2026.07.21
```

Or run the workflow — from the Actions tab, or the CLI. With no tag it uses today's UTC date, appending `-2`, `-3`, … if that tag is taken, and creates the tag itself:

```bash
gh workflow run release.yml
```

The pom version is not part of release identity — it stays a SNAPSHOT and is not bumped per release. Each release publishes `apotheneum.jar` (stable name, served by `releases/latest/download/apotheneum.jar`) and `apotheneum-<tag>.jar` (pinnable).

### Key Constants

- `GRID_WIDTH = 50`, `GRID_HEIGHT = 45` - Cube face dimensions
- `CYLINDER_HEIGHT = 43` - Cylinder height
- `DOOR_WIDTH = 10`, `DOOR_HEIGHT = 11` - Door cutout dimensions
- `RING_LENGTH = 120` (cylinder) or `200` (cube) - Ring circumference

### Physical Layout

The installation has doors that affect pixel availability:
- Cube doors start at column 20 on each face
- Cylinder doors start at column 10
- Use `orientation.available(columnIndex)` to get available pixels per column

## Development Patterns

### Thread Safety and Concurrency

- **NEVER use synchronized blocks** - The LX framework handles rendering in a single thread context
- Pattern rendering methods are called sequentially, not concurrently
- Synchronization adds unnecessary overhead and can cause performance issues
- Use standard Java collections and data structures without thread safety concerns

### Pattern Lifecycle and Activation

- **The LX framework automatically handles pattern activation** - Only the currently active pattern on a channel has its `run()` method called
- **No need to manually check if pattern is enabled** - When a pattern becomes inactive, its render methods stop being called entirely
- **For optional animation control**, use parameter-based early returns like ImagePattern's `gifAnimating` parameter:
  ```java
  public void animateGif(double deltaMs) {
    if (!this.animationEnabled.isOn()) {
      return; // Skip animation updates when parameter is off
    }
    // ... animation logic continues only if enabled
  }
  ```
- **Pattern activation is managed at the framework level** - No need for manual `enabled` checks in render methods

### Performance Best Practices

- **Avoid creating new ArrayLists in render loops** - Reuse collections or use pre-allocated arrays
- Collections created in render methods are called at high frequency (60+ FPS)
- Use `clear()` on existing collections instead of creating new ones
- Consider using primitive arrays or pre-sized collections for performance-critical code

### Choosing the Right Base Class

**Extend ApotheneumPattern when:**
- Pattern needs Apotheneum-specific geometry utilities
- Pattern works with cube faces or cylinder orientations
- Pattern needs to copy between exterior/interior surfaces
- Examples: Raindrops, Quilt, CubeBlinks

**Extend ApotheneumRasterPattern when:**
- Pattern needs 2D graphics rendering (Graphics2D, BufferedImage)
- Pattern benefits from pixel-based approach with face controls
- Example: RasterOval

**Extend LXPattern directly when:**
- Pattern doesn't need Apotheneum-specific utilities
- Pattern uses general 3D geometry or specialized components
- Examples: StripePattern (3D geometry), DeformableImagePattern (image rendering)

### ApotheneumPattern Features

- **Model Safety**: Only renders when `Apotheneum.exists` is true
- **Automatic Initialization**: Calls `Apotheneum.initialize(lx)` in constructor
- **Geometry Utilities**: 
  - `copyCubeFace(face)` - Copy one face to all cube faces
  - `copyExterior()` - Mirror exterior surfaces to interior
  - `copyCylinderExterior()` - Copy cylinder exterior to interior
  - `copyMirror(from, to)` - Mirror copy with column reversal
  - `setApotheneumColor(color)` - Set entire installation to one color

### Working with Geometry

- Access points via `orientation.point(x, y)` or `orientation.column(x).points[y]`
- Use `Ring` objects for circular operations around cube/cylinder
- Faces are ordered: front, right, back, left (clockwise when viewed from above)

### Door Handling

**Door columns are logically shorter, not physically shorter.** Every column carries a full `GRID_HEIGHT` / `CYLINDER_HEIGHT` worth of points — the `Cube.Face` and `Cylinder.Orientation` constructors throw `IllegalStateException` if one doesn't. What changes at a door is the *usable* height:
- Use `cube.exterior.available(globalColumnIndex)` for the usable height of a column — it returns `GRID_HEIGHT - DOOR_HEIGHT` for door columns, `GRID_HEIGHT` otherwise
- **Never use `column.points.length` as the door boundary.** It is the full height on every column, door or not, so treating it as the limit lights pixels that aren't there
- **Global column indexing**: For cube faces, use `face * GRID_WIDTH + localColumn` for global indexing
- **Vertical traversal**: Use adjacent full columns for going up/down around doors
- **Path building**: When tracing edges around doors:
  - Use last full column before door for ascending
  - Traverse across tops of door columns (shortened columns)
  - Use first full column after door for descending

### Model Structure

- **`Cube.Face.columns[]` and `Cylinder.Orientation.columns[]`**: both are `Apotheneum.Column[]` — a wrapper extending `Sequence`, not raw `LXModel`
- **Points access**: `column.points[y]` (the `LXPoint[]` on `Sequence`). `Column` also exposes `size` and navigation (`next()`/`previous()`)
- **Raw model access**: `column.model` when you specifically need the underlying `LXModel`
- **Point indexing**: Y=0 is top, Y=max is bottom (inverted from typical coordinate system)

### 3D Distance Calculations

For thickness effects that span multiple surfaces:
```java
double dist3D = Math.sqrt(
    Math.pow(point.x - pathPoint.x, 2) +
    Math.pow(point.y - pathPoint.y, 2) +
    Math.pow(point.z - pathPoint.z, 2)
);
```
- Iterate through `model.points` to find all LEDs within radius
- Use hard vs soft edges by controlling falloff calculations

### Common Utilities

- `copyCubeFace(face)` - Copy one face to all cube faces
- `copyExterior()` - Mirror exterior surfaces to interior
- `setApotheneumColor(color)` - Set entire installation to one color

### UI Design Guidelines

- **Maximum 3 controls per column** - UI columns should never exceed 3 elements to prevent overflow and maintain visibility
- **Logical grouping** - Group related parameters together (e.g., movement controls, visual controls, etc.)
- **Clear button placement** - Important buttons like "Clear" should be easily accessible and not hidden by overcrowding

### Wiring a Component to its UI

**Patterns and effects: UI is optional.** Without `UIDeviceControls`, LX Studio falls back to
`UIDeviceControls$Default` and auto-generates knobs from the parameters. Implement
`UIDeviceControls<T>` on the class and override `buildDeviceControls` only to improve on that
layout — which 23 classes here do, e.g. `mcslee/CubeBlinks.java`.

**Modulators: UI is mandatory.** There is no `Default` for modulators. A modulator with no UI
resolves to `UIModulatorControls$Missing`, which renders a placeholder and logs:

```
No UI implementation found for type: <YourModulator>
```

`LXStudio.UI.instantiateModulatorControls` resolves in this order: `LXModulator.Placeholder` →
the modulator itself `instanceof UIModulatorControls` → the `Registry.modulatorControls` map →
`Missing`. So implement the interface on the modulator itself:

```java
public class TempoTap extends LXModulator
  implements LXOscComponent, UIModulatorControls<TempoTap> {

  @Override
  public void buildModulatorControls(UI ui, UIModulator uiModulator, TempoTap tempoTap) {
    uiModulator.setLayout(UI2dContainer.Layout.HORIZONTAL, 4);
    uiModulator.addChildren(newButton(tempoTap.tap, 60).setTriggerable(true));
  }
}
```

Imports: `heronarts.glx.ui.UI2dContainer`, `heronarts.lx.studio.LXStudio.UI`,
`heronarts.lx.studio.ui.modulation.UIModulator`, `...UIModulatorControls`. All are
`provided` scope, so tests still compile and run.

The alternative — a separate UI class registered via `Registry.addUIModulatorControls(Class)`
from a plugin's `initializeUI` — keeps glxstudio imports out of the modulator, which matters only
for headless deployments without glxstudio on the classpath. It requires the plugin to be enabled,
so prefer implementing the interface directly.

## Dependencies

- **Build with JDK 25**, even though the project targets Java 21 bytecode (`maven.compiler.release=21`). Chromatik 1.2.2 (`lx`/`glx`/`glxstudio`) ships class-file major version 69, which javac 21 refuses to read off the classpath — installing exactly Java 21 makes every build fail. CI pins JDK 25 for this reason.
- Maven for build management
- LX Framework (Chromatik) - provided dependency
- JUnit 5 for tests, opt-in — see Testing below

## File Structure

- `src/main/java/apotheneum/` - Main source code
- `src/main/resources/` - Assets (fixtures, images, project files)
- `scripts/` - PHP utility scripts for fixture generation
- `target/` - Build output (ignored by git)