# Rendering a component UI

`scripts/render-ui` captures the real Chromatik panel for a pattern, effect, global modulator, or MIDI template without
installing the Apotheneum package or opening visible windows. It writes both pixels and
structure so a developer or coding agent can judge the UI it just changed.

## Run it

Pass the component's fully-qualified class name:

```bash
./scripts/render-ui apotheneum.doved.patterns.Fireflies
```

Effects render in a SolidPattern host. `LinkedColorize` receives a temporary named style,
`Style A`, so its selector demonstrates a real link:

```bash
./scripts/render-ui apotheneum.doved.effects.LinkedColorize
```

Global modulators render their control panel directly:

```bash
./scripts/render-ui apotheneum.doved.modulators.ColorizeStyle
```

MIDI templates render their dedicated MIDI-template panel:

```bash
./scripts/render-ui apotheneum.doved.midi.MidiFighterTwister64
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

## Use it as a feedback loop

Rendering is part of both implementation and code review whenever a change adds a pattern, effect,
global modulator, or MIDI template, changes its registered parameters, or changes its device controls.
Identify every affected component class from the diff and render each one. A reviewer with a supported environment
should render the current PR head independently even when the author already attached an
image; the attachment is evidence for the conversation, while the local render verifies the
code currently under review.

Do not stop at a successful exit code or the existence of a PNG. Inspect the image and JSON:

- labels are understandable in context, use consistent terminology, and are not truncated
  into ambiguous text;
- related controls are grouped and ordered as a performer would expect to use them;
- columns respect the three-control limit, with no clipping, collisions, or overflow;
- important actions such as Clear are visible and distinguishable from continuous controls;
- the panel is neither needlessly wide nor so dense that scanning it becomes difficult;
- defaults and enabled/disabled states communicate the intended hierarchy;
- JSON warnings are either fixed or explicitly justified.

Treat a visual problem as a normal review finding: change the layout or labels, render again,
and inspect the replacement. Publish only the final reviewed image with the PR. If the renderer
cannot run because the environment does not meet the requirements below, say so explicitly in
the review rather than implying the UI was inspected.

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
- an `LXPattern`, `LXEffect`, or `LXMidiTemplate` class with a public constructor accepting `LX`,
  or a global `LXModulator` with a public no-argument constructor.

Set `CHROMATIK_JAR` to use an application runtime jar in another location.

Effects render in a known SolidPattern host; global modulator screenshots show their real controls;
and MIDI templates use their dedicated template panel. The renderer does not make visual claims about
a modulator's downstream behavior; use the headless effect/modulator workflow and named wiring for
that. Automated `--changed` discovery and Linux CI are also future work. Linux
will need a tested Xvfb/software-OpenGL path, while this version deliberately uses the
known-good macOS Metal readback path.
