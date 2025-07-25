# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Apotheneum is a visual, sonic and haptic instrument for immersive LED art installations. It consists of two nested chambers (cube and cylinder) with 13,280 LED nodes total, built on the Chromatik Digital Lighting Workstation framework.

## Build Commands

- **Build**: `mvn compile` - Compiles the Java source code
- **Install**: `mvn -Pinstall install` - Builds and installs the package to ~/Chromatik/Packages
- **Quick Install**: `./update.command` - Convenience script that runs `mvn install`

**IMPORTANT**: Always use `mvn -Pinstall install` instead of `mvn compile` when working on patterns, as this updates the Chromatik code and makes changes available in the lighting system.

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