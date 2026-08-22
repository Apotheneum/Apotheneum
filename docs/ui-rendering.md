# Rendering a pattern's device UI

`scripts/render-ui` captures the real Chromatik device panel for a pattern without
installing the Apotheneum package or opening visible windows. It writes both pixels and
structure so a developer or coding agent can judge the UI it just changed.

## Run it

Pass the pattern's fully-qualified class name:

```bash
./scripts/render-ui apotheneum.doved.patterns.Fireflies
```

The default output directory is `target/ui-review/`:

- `Fireflies.png` is the device panel rendered by Chromatik.
- `Fireflies.json` describes the live UI tree: control types, labels, parameters,
  dimensions, nesting and layout.

An optional second argument changes the output directory:

```bash
./scripts/render-ui apotheneum.mcslee.CubeBlinks target/cube-blinks-ui
```

Look at the PNG after changing `buildDeviceControls`; the JSON is there for deterministic
checks and for agents that need the control names alongside the image. The command warns
when a vertical container directly contains more than the repository limit of three
parameter controls.

## Safety and isolation

This is a test-scope development tool. It runs `mvn -Ptests test-compile`, never the
`install` profile, and never copies a jar into `~/Chromatik/Packages`.

The renderer uses Chromatik's official application runtime to construct the real
`LXStudio.UI`, but it:

- creates invisible GLFW windows;
- passes `--disable-output` and explicitly verifies engine output is disabled;
- disables zeroconf and preferences;
- redirects Chromatik's home and media files into `target/ui-render-home`;
- loads the real Apotheneum fixture from that isolated copy, so geometry-dependent
  pattern constructors behave as they do in the installation.

## Requirements and current scope

- macOS and Chromatik installed in `/Applications`;
- JDK 25, as used by the Maven build;
- a pattern class with a public constructor accepting `LX`.

Set `CHROMATIK_JAR` to use an application runtime jar in another location.

This first version renders patterns. Effects need a known host pattern, and modulators
need both a host and a named target parameter; adding those comparison workflows is
separate work. Automated `--changed` discovery and Linux CI are also future work. Linux
will need a tested Xvfb/software-OpenGL path, while this version deliberately uses the
known-good macOS Metal readback path.
