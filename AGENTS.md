# AGENTS.md

Project context for AI coding assistants working in this repository — Claude Code, Codex, Cursor, and anything else that reads a repo instruction file.

**Edit this file, not `CLAUDE.md`.** Claude Code reads `CLAUDE.md` rather than `AGENTS.md`, so `CLAUDE.md` is a one-line `@AGENTS.md` import that expands this file into context at launch. There is nothing to keep in sync. `scripts/check-agent-instructions.sh` verifies the import is intact.

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

### Releasing

Releases are cut by pushing a calendar tag — CI (`.github/workflows/release.yml`) builds the JAR and creates the GitHub Release. Never build and upload a release JAR by hand.

```bash
git tag 2026.07.21 && git push origin 2026.07.21
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

**Door columns have reduced points** - They are physically cut short at the door height:
- Use `cube.exterior.available(globalColumnIndex)` to check available height per column
- Door columns return fewer than `GRID_HEIGHT` or `CYLINDER_HEIGHT` points
- **Global column indexing**: For cube faces, use `face * GRID_WIDTH + localColumn` for global indexing
- **Vertical traversal**: Use adjacent full columns for going up/down around doors
- **Path building**: When tracing edges around doors:
  - Use last full column before door for ascending
  - Traverse across tops of door columns (shortened columns)
  - Use first full column after door for descending

### Model Structure

- **Cube.Face.columns[]**: Array of `LXModel` objects (not `Face.Column` type)
- **Cylinder.exterior.columns[]**: Array of `LXModel` objects  
- **Points access**: Use `column.points[y]` directly on `LXModel`
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

## Dependencies

- Java 21+ required
- Maven for build management
- LX Framework (Chromatik) - provided dependency
- No external testing framework configured

## File Structure

- `src/main/java/apotheneum/` - Main source code
- `src/main/resources/` - Assets (fixtures, images, project files)
- `scripts/` - PHP utility scripts for fixture generation
- `target/` - Build output (ignored by git)