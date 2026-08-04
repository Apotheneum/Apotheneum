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

* Download the latest [Chromatik release](https://chromatik.co/download/)
* Register a [Chromatik account](https://chromatik.co/login)
* Coordinate with the Apotheneum team to receive a developer license

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

* [Java 21 Temurin](https://adoptium.net/)
* [Maven](https://maven.apache.org/)

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

Releases are cut by pushing a calendar tag. CI builds the JAR and creates the GitHub
Release — no local build or manual upload needed:

```bash
$ git tag 2026.07.21 && git push origin 2026.07.21
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
