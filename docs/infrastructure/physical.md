# Physical Installation

**Draft.** Where things are and what plugs into what. **TBD** = unverified.

For how a subsystem works, see [audio](audio-system.md) and
[lighting](lighting-control.md). This page is only *where it is*.

## Layout

**TBD — the spatial description.** Cube with the stage in the middle; cable runs
from stage to the two Pelican cases, and from there out to the speakers and LED
controllers. Worth a plan-view diagram once the routes are confirmed.

## Pelican cases

**There are two**, and it's worth being precise about which is which.

| Case | Contains | Notes |
|---|---|---|
| **Ashly case** | Ashly AQM408 | Rear panel covers the AQM408's own connectors — see below |
| **MOTU case** | MOTU M4 | The resident MacBook **sits on top of this case**, not inside it |

The MacBook being on the lid rather than in a case has consequences worth
noting: it is the exposed element of the rig, it needs the case closed to sit
on, and anything requiring the MOTU case to be opened means moving the machine
that is running the show.

**TBD:** how the MacBook is secured, if at all.
**TBD:** where the two cases sit relative to each other and to the stage.
**TBD:** is the MOTU case's rear panelled like the Ashly's, or are the M4's
jacks reachable directly?

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
**TBD:** how the MOTU → Ashly cable travels between the two cases — through the
panel connector, or its own route?
**TBD:** photograph the case rear panel, the connector, and the internal loom.

### Also in the cases

**TBD:** what else lives in either case — power distribution, a network switch,
ventilation, and whether they run closed.

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
two cases are fed from, and whether the speakers are locally powered.

## Photos needed

- Ashly case rear panel, and the panel connector
- MOTU case with the MacBook in position
- Internal loom from the connector to the AQM408
- AQM408 rear as wired
- Inside both cases, lids open
- Stage-end GEARit box with both Cat5 runs
- MOTU front panel in prerecorded and live-DJ states
- Both ends of the MOTU → Ashly cable
