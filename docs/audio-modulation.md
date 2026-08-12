# Audio-Reactive Patterns: Use an External Envelope Follower

Guidance for contributing audio-reactive patterns to Apotheneum. Short version: **don't
consume `lx.engine.audio.meter` for continuous modulation** — do the envelope-follower
step in the DAW and pipe its output into Chromatik over OSC.

## Why the internal meter is fragile

Chromatik's internal audio meter (`lx.engine.audio.meter`, a 16-band `GraphicMeter`) has
no automatic gain control. It is a fixed dB gain/range mapping of whatever signal happens
to hit LX's audio input. Nothing renormalizes it per-show.

That means any pattern that consumes the raw (or smoothed) meter level as a **continuous
quantity** — brightness, spin speed, rise rate, particle count — or compares it against an
**absolute threshold** (e.g. `levelEnv < 0.04`) will visibly change behavior whenever the
system or mixer volume changes. A pattern tuned at rehearsal volume looks different at
show volume, and different again when someone rides the master fader.

## The recommended approach: DAW-side envelope following → OSC

The convention in this project (and sibling shows) is to move the envelope-follower step
upstream into the audio program, where it is tuned to the actual mix:

1. **In Ableton**, run an envelope follower (or beat/onset device) on the relevant bus.
2. **Bind it to the show-control Max for Live device** — [`live/Apotheneum Show
   Control.amxd`](../live/Apotheneum%20Show%20Control.amxd) in this repo — which sends the
   follower's output to Apotheneum **over OSC**.
3. **In Chromatik**, map that OSC source to your pattern's input parameters via modulation
   mapping.

Because the follower sits before any system/playback volume stage, patterns receive a
stable 0–1 signal regardless of output volume.

### Pattern-side: expose plain input parameters

Author patterns to take audio as ordinary parameters rather than reading the engine meter.
An in-repo example is `apotheneum/piemonte/ParameterPattern`, which exposes:

- **`Level`** — a `CompoundParameter` (0–1) intended for an external envelope follower
  (OSC/MIDI modulation).
- **`Pulse`** — a momentary `BooleanParameter` intended for an external beat trigger.

Everything the patterns derive — attack/release envelopes, rolling averages, beat/onset
state — is computed from those two inputs, never from the meter. This also makes patterns
testable without audio at all: wiggle `Level` by hand or map it to any LFO and the
audio-reactive behavior is fully exercised.

## Lower-risk: relative / self-normalizing detection

Not every meter use is equally fragile. Beat or onset detection that compares a band
against **its own rolling average** (e.g. `bass > bassAvg * threshold`) is reasonably
robust to volume changes, since both sides of the comparison scale together. Uses like
that don't strictly need the external-follower treatment.

The cases to avoid are:

- raw or smoothed meter level driving a continuous visual quantity, and
- comparisons against absolute constants.

If a pattern needs tempo rather than level, `lx.engine.tempo` (BPM, beat basis, beat
events) is tempo-locked and fine to use directly.
