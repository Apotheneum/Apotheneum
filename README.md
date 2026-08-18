![Logo](media/Apotheneum-banner.jpg)

_Apo·then·eum_ (place of divine elevation) is a visual, sonic and haptic instrument designed to transport visitors through participatory immersion.

Comprised of two nested chambers made of back-to-back LED nets (13,280 nodes in all), Apotheneum presents four independent canvases and an immersive sound system for collaborating visual and sound artists to play. Measuring 40&times;40&times;40 feet, Apotheneum's cubic antechamber envelops a thirty-foot cylindrical inner sanctum that opens to the sky and is anchored by a 24-foot pressure-sensing bed from which vantage point our three primary somatic senses are engaged.

This repository contains materials used to Apotheneum's animation engine in the [Chromatik](https://chromatik.co/) Digital Lighting Workstation.

Learn more on the [Apotheneum Wiki &rarr;](https://github.com/Apotheneum/Apotheneum/wiki)

---

### Copyright Notice

Unless otherwise indicated, all contents in this repository are copyright their original authors (as stated in the file or recorded by the version history). Materials here are not under open source license, artworks are the intellecual property of their creators.

---

### Getting Started

This package currently requires macOS on an Apple Silicon machine. Windows instructions will be added in the future.

#### Installing Chromatik

**Chromatik 1.2.2 or later is required.** Chromatik 1.2.2 added a new
`MediaPathParameter filePath` to the image pattern, which the `DeformableImage`
pattern now uses. That field does not exist in 1.2.1, so running this package on
1.2.1 fails with a `NoSuchFieldError` when a project is opened. See
[issue #41](https://github.com/Apotheneum/Apotheneum/issues/41).

* Download the latest [Chromatik release](https://chromatik.co/download/)
* Register a [Chromatik account](https://chromatik.co/login)
* Coordinate with the Apotheneum team to receive a developer license

Only one Apotheneum package may be installed at a time. If `~/Chromatik/Packages`
contains more than one `apotheneum-*.jar`, Chromatik logs `Ignoring duplicate
class` and may load classes from the stale jar, which reintroduces the error
above. Remove the older jar.

#### Apotheneum Assets

* Download the latest [Apotheneum package](https://github.com/Apotheneum/Apotheneum/releases/latest) — grab the `.jar` file from the release's Assets
* Open Chromatik, drag-and-drop the downloaded `.jar` file onto the application window
* From Chromatik, open the example project file `~/Chromatik/Projects/Apotheneum/Apotheneum.lxp`

Need more help?<br />
[Installation Guide &rarr;](https://github.com/Apotheneum/Apotheneum/wiki/Installation-Guide)

Learn how to create animation content.<br />
[Chromatik User Guide &rarr;](https://chromatik.co/guide/)<br />
[Chromatik Developer Guide &rarr;](https://chromatik.co/develop/)

Know the limitations of developing large-scale animation on a computer monitor.<br />
[Simulation Principles &rarr;](https://github.com/Apotheneum/Apotheneum/wiki/Simulation-Principles)

![Logo](media/Apotheneum-screenshot.jpg)

---

### Software Development

Coding experience is neither required nor necessary to build animation content in Chromatik. But for those comfortable with basic Java coding, Chromatik offers an extensible framework for custom animation development.

Learn more by reading the [Chromatik Developer Guide &rarr;](https://chromatik.co/develop/)

#### Development Setup

Install the following tools:

* [Java 25 Temurin](https://adoptium.net/) — `brew install --cask temurin@25`
* [Maven](https://maven.apache.org/)

JDK 25 is required to *build*. The `glx` and `glxstudio` artifacts of Chromatik
1.2.2 are compiled at release 25, so their class files are major version 69 and
JDK 21's `javac` cannot read them off the classpath. (`lx` is still release 21,
but that doesn't help — all three are on the compile classpath.) If you build on
21 you get a class-file-version error naming a `heronarts` class — that means
"upgrade your JDK", not "your code is wrong".

This does not change how patterns are written: the pom stays at
`maven.compiler.release 21`, so the language level and the bytecode produced are
unchanged. Running Chromatik needs no JDK at all — it bundles its own runtime.

The build targets Chromatik `1.2.2` (`<lx.version>` in `pom.xml`), which Maven
resolves from Maven Central — no manual setup needed.

Maven can be installed using [Homebrew](https://brew.sh/) via the following command:

```bash
$ brew install maven
```

#### Building and Installing

After developing new animation content, you may install it by running `update.command` or invoking Maven directly:

```bash
$ mvn -Pinstall install
```

This builds the JAR file and copies it to `~/Chromatik/Packages` for automatic loading in Chromatik.

#### Releasing

CI builds the JAR and creates the GitHub Release — no local build or manual upload
needed. Cut one by pushing a calendar tag:

```bash
$ git tag 2026.07.21 && git push origin 2026.07.21
```

Or run the release workflow from the repo's **Actions** tab, or the command line.
With no tag given it uses today's UTC date — appending `-2`, `-3`, … if that tag is
already taken — and creates the tag for you:

```bash
$ gh workflow run release.yml
```

Each release publishes two assets:

| asset | use |
|---|---|
| `apotheneum.jar` | stable name — `releases/latest/download/apotheneum.jar` serves the newest CI-cut release (live once the first tag is pushed after this lands) |
| `apotheneum-<tag>.jar` | pin a specific release |

Every pull request also gets a build check, so compile breakage surfaces before it lands.

#### Pattern Development

Apotheneum provides specialized base classes for different types of animations:

**For Apotheneum-specific geometry patterns:**
```java
public class MyPattern extends ApotheneumPattern {
    protected void render(double deltaMs) {
        // Access cube and cylinder geometry
        // Use utility methods like copyExterior()
    }
}
```

**For 2D raster-based patterns:**
```java
public class MyRasterPattern extends ApotheneumRasterPattern {
    protected void render(double deltaMs) {
        // Use Graphics2D for 2D rendering
        // Automatic mapping to cube faces
    }
}
```

**For general 3D patterns:**
```java
public class MyGeneralPattern extends LXPattern {
    public void run(double deltaMs) {
        // Standard LX pattern development
    }
}
```

**Coding guidelines:** see [LX/Chromatik coding guidelines](docs/lx-coding-guidelines.md) before opening a PR — LX idioms from past review feedback on this repo (no allocation in the render loop, enums instead of magic constants, framework helpers, lifecycle symmetry), plus a review checklist.

**For audio-reactive patterns:** do envelope-following in the DAW and pipe the result in over OSC rather than reading the internal audio meter — see [Audio-Reactive Patterns](docs/audio-modulation.md).

#### Example Patterns

* [`StripePattern.java`](src/main/java/apotheneum/examples/StripePattern.java) - General 3D geometry pattern
* [`RasterOval.java`](src/main/java/apotheneum/examples/RasterOval.java) - 2D raster pattern
* [`Raindrops.java`](src/main/java/apotheneum/mcslee/Raindrops.java) - Apotheneum-specific geometry pattern

#### Physical Structure

The installation consists of:
* **Cube**: 4 faces of 50×45 LED grids (exterior + interior)
* **Cylinder**: 120 columns of 43 LEDs each (exterior + interior)  
* **Doors**: 10×11 LED cutouts affect pixel availability
* **Total**: 13,280 LED nodes

A more general overview of the content package structure is provided in the [LXPackage Template Repository &rarr;](https://github.com/heronarts/LXPackage)
