# Physical Installation

**Draft.** Where things are and what plugs into what. **TBD** = unverified.

For how a subsystem works, see [audio](audio-system.md) and
[lighting](lighting-control.md). This page is only *where it is*.

## Layout

**TBD — the spatial description.** Cube with the stage in the middle; cable runs
from stage to the Pelican case, and from the case out to the speakers and LED
controllers. Worth a plan-view diagram once the routes are confirmed.

## Pelican case

Houses the resident MacBook, the MOTU M4, and the Ashly AQM408.

### The rear panel hides the Ashly's own connectors

**The AQM408's rear panel is not accessible from outside the case.** A panel on
the back of the Pelican covers it. Connection to the outside world is through a
**panel-mount connector** that plugs in and also seals the opening against dust.

```text
outside            case rear panel              inside
  │                      │                        │
  ├── field cable ──▶ [panel connector] ──▶ internal loom ──▶ AQM408 Euroblock outputs
                     (also the dust seal)
```

**Believed, not confirmed:** that the panel connector lands internally on a
single multipin plug which then breaks out to the Ashly's Euroblock outputs.

This matters more than it looks:

- The **Euroblock outputs are not the service point.** Anything about
  "8 balanced Euroblock outputs" in the [audio doc](audio-system.md) describes
  the device, not what you can reach with the case closed.
- The panel connector and its mating field cable are **single points of failure
  for all eight outputs at once**. A spare matters more here than for any
  individual speaker cable.
- Removing the panel to work on the Ashly **opens the case to dust**, so it isn't
  a casual operation on site.

**TBD:** what the connector actually is — make, series, pin count. Candidates in
this role are typically multipin types (Socapex, Harting, EDAC, or a DB25-style
breakout), but this needs to be read off the part rather than guessed.
**TBD:** the internal pinout — which pin carries which AQM408 output.
**TBD:** is there a spare mating cable? Where is it kept?
**TBD:** does the MOTU → Ashly connection also run internally, or does it come
out through this panel too?
**TBD:** photograph the case rear panel, the connector, and the internal loom.

### Also in the case

**TBD:** confirm what else lives in the Pelican — power distribution, a network
switch, the MacBook's position, ventilation and whether the case runs closed.

## Cable runs

| Run | From | To | Notes |
|---|---|---|---|
| Analog audio snake | GEARit box under stage | GEARit box at MOTU | Cat5/6 carrying **analog audio, not network**. **TBD** length and route |
| Pro DJ Link | Stage | Live MacBook | Separate Cat5/6, real network. **TBD** route |
| Speaker outputs | Pelican panel connector | 4 corners | 8 circuits through one multipin. **TBD** breakout location |
| LED network | **TBD** | 32 controllers | **TBD** switch location and topology |

**Labelling:** the two Cat5/6 runs from stage look identical and carry entirely
different things. Both ends of both should be labelled.

## Power

**TBD** — not documented. Needs: what's on which circuit, total draw, what the
Pelican case is fed from, and whether the speakers are locally powered.

## Photos needed

- Pelican case rear panel, and the panel connector
- Internal loom from the connector to the AQM408
- AQM408 rear as wired
- Inside the case, lid open
- Stage-end GEARit box with both Cat5 runs
- MOTU front panel in prerecorded and live-DJ states
- Both ends of the MOTU → Ashly cable
