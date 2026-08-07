# Physical Installation

Where things are and what plugs into what. The [plan view](README.md#physical-layout)
is the master record; this page is its notes. Open questions live in
[commissioning](commissioning.md).

## The stack

Two Pelican cases, stacked and kept stacked, with the resident MacBook on top.

```text
┌──────────────────────┐
│  Resident MacBook    │  sits on the lid
├──────────────────────┤
│  MOTU case (M4)      │  always in place
├──────────────────────┤
│  Ashly case (AQM408) │  rear panel + multipin connector
└──────────────────────┘
```

- Do not open or move the stack while a show is running.
- Reaching the Ashly means moving the MacBook and the MOTU case above it.

## One USB-C cable to the MacBook

A single USB-C cable runs from the MOTU case to the resident MacBook and carries
**both audio and network**.

```text
     Resident MacBook
           │  ONE USB-C cable
           ▼
    ┌───────────────────────────┐
    │  MOTU case                │
    │   powered hub ──▶ MOTU M4 │  audio
    │        └────────▶ Ethernet ──▶ local switch
    └───────────────────────────┘
```

The Ethernet link belongs to the MacBook, not the MOTU — the M4 is not on the
network at all. The panel port is where the MacBook's connection emerges.

**Failure symptom: sound and lights die together.** That reads as two unrelated
faults; it is one cable.

The hub runs from AC, so bus-power starvation is not a suspect. That the hub
exists is inferred from a USB-C-only MacBook plus a panel Ethernet port — nobody
has opened the case to confirm.

## The Ashly's connectors are not reachable

The AQM408's rear panel is enclosed. Everything reaches the outside through one
**panel-mount connector**, which also seals the opening against dust.

```text
outside            case rear panel              inside
  ├── field cable ──▶ [panel connector] ──▶ internal loom ──▶ AQM408 outputs
                     (also the dust seal)
```

- The Euroblock outputs are **not** the service point. What
  [audio-system.md](audio-system.md) says about eight balanced outputs describes
  the device, not what is reachable with the case closed.
- That one connector carries **all eight outputs** — a single point of failure
  for the whole PA, and the item most worth a spare.
- Removing the panel opens the case to dust.

*Believed, not confirmed:* that the panel connector lands internally on a single
multipin plug which breaks out to the Euroblocks.

## Four switches, chained

```text
  MacBook ─┐                              ┌─▶ Top switch ──▶ 32 LED controllers
           ├─▶ Local switch ──one cable──▶ Corner switch
  Floor ───┘   (control position)         └─▶ Stage switch ──┬─▶ floor motors + lights
  controller                                                 └─▶ Live MacBook
```

| Switch | Where | Serves | If it fails |
|---|---|---|---|
| Local | Control position | MacBook, floor controller | Resident machine and floor controller offline |
| Corner | Corner of the cube | Uplinks to top and stage | Everything downstream |
| Top | On top of the cube | All 32 LED controllers | All lights, nothing else |
| Stage | Corner of the stage | Floor, live MacBook | Floor and live MacBook |

The symptom tells you which switch to look at.

> **Never add a second cable between any two switches.** These are unmanaged, so
> a second path is a loop with nothing to break it. The broadcast storm takes
> down lights, floor, and control at once.

### Sharing the network between lights and floor

Safe as built, because **Art-Net here is unicast** — every controller has its own
host address in the fixture files, so each stream is forwarded only to its
destination. The floor never sees LED traffic.

- **All four switches must be gigabit.** LED traffic alone is roughly 40 Mbps.
- **Nothing may broadcast Art-Net onto this network**, which would undo the above.
- **Keep Pro DJ Link off it** — separate NIC on the live MacBook.

## Cable runs

| Run | From | To | Notes |
|---|---|---|---|
| Analog audio snake | GEARit box under stage | GEARit box at control position | Cat5/6 carrying **analog audio, not network** |
| Pro DJ Link | Stage | Live MacBook | Separate Cat5/6, real network traffic |
| Speaker outputs | Ashly panel connector | 4 corners | 8 circuits through one multipin |
| Local uplink | Local switch | Corner switch | Single cable |
| Corner uplinks | Corner switch | Top switch, stage switch | |

The two Cat5/6 runs from the stage look identical and do entirely different
things. **Label both ends of both.**

## Power

Everything in the case runs from AC, the powered hub included.
