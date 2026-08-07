# Lighting Control

**Status: draft.** Items marked **TBD** need to be confirmed on site.

Ableton Live, Bitwig Studio, and Vezér send OSC to Chromatik. Chromatik renders
patterns and outputs Art-Net over Ethernet to the LED controllers.

All three applications send OSC, and Chromatik is the only destination — OSC is
not used between the applications themselves or anywhere else in the system.

**Chromatik receives no audio.** There are no audio-reactive patterns — control
is entirely OSC. The audio chain and the lighting chain share source
applications and a machine, but not a signal.

This is the sibling of the [audio system](audio-system.md) — the same three
applications feed both, but the paths are entirely separate downstream.

```text
Ableton Live ─┐
Bitwig Studio ─┤── OSC (UDP) ──▶ Chromatik ── Art-Net ──▶ 32 controllers ──▶ LEDs
Vezér ────────┘                                              10.0.1.101–.132
```

## OSC into Chromatik

Chromatik's OSC receiver is configured in the project file, not in code.

| Setting | Value | Source |
|---|---|---|
| Receive host | `0.0.0.0` (all interfaces) | `Apotheneum.lxp` |
| Receive port | `3030` | `Apotheneum.lxp` |
| Receive active | **`false` in `Apotheneum.lxp`**, `true` in `Apotheneum-Test.lxp` | both project files |

Worth noting up front: **OSC receive is disabled in the main project file as
checked in.** Either it gets enabled by hand at showtime, or the running show
uses a project saved elsewhere. That discrepancy should be resolved rather than
left as folklore — if the live project is a different file, this doc should say
where it lives.

**TBD:** the OSC address space. Which addresses do the three applications send,
and what do they map to — pattern parameters, channel faders, tempo, cues? An
OSC address that nothing listens for fails silently, so this is worth writing
down explicitly.

**TBD:** are all three sending, or one at a time? Same question as the audio
side, and probably the same answer.

**TBD:** do the senders address `localhost` (everything on the one MacBook) or a
network address? `0.0.0.0` accepts either.

**TBD:** is tempo/clock shared — MIDI clock, Ableton Link, or OSC-driven?

## Chromatik → LEDs

Output is **Art-Net over Ethernet**, defined in the fixture files
(`src/main/resources/fixtures/`), which specify `"protocol": "artnet"`
throughout.

### Controllers

32 controllers on a flat `10.0.1.0/24` network. Addresses are fixture variables
with these defaults:

| Group | Count | Addresses |
|---|---|---|
| Cube (`cub01`–`cub20`) | 20 | `10.0.1.101` – `10.0.1.120` |
| Cylinder (`cyl01`–`cyl12`) | 12 | `10.0.1.121` – `10.0.1.132` |

Because these are fixture *variables*, they can be overridden per project — so a
controller's real address is whatever the running project says, and the table
above is the default, not a guarantee.

Each controller also carries per-unit flags in the fixture definition: an
enable, a `Flip`, and on the cylinder a `B2F` (back-to-front). These encode
physical mounting reality, so they matter when a section renders mirrored or
upside down.

- **TBD:** what the controllers physically are (make/model)
- **TBD:** universe and channel mapping per controller
- **TBD:** what provides addressing — static IPs on the controllers themselves,
  or DHCP reservations?
- **TBD:** switch topology, and whether lighting is on its own VLAN or physical
  network
- **TBD:** where the MacBook sits on `10.0.1.0/24` and what its address is

### Model

13,280 LED nodes across two nested chambers, cube and cylinder. Geometry
constants (grid dimensions, door cutouts, ring lengths) are documented in
[`CLAUDE.md`](../../CLAUDE.md) at the repository root rather than duplicated
here — that file is the source of truth and is kept current with the code.

## Planned: CDJ beat sync

**Not built yet.** Recorded here so the design constraints aren't rediscovered
later.

The intent is to drive Chromatik's metronome from live CDJ performance:

```text
CDJs ──(Pro DJ Link, Ethernet)──▶ Beat Link Trigger ──(OSC)──▶ Chromatik
```

[Beat Link Trigger](https://github.com/brunchboy/beat-link-trigger) joins the
Pro DJ Link network as a virtual CDJ, reads tempo and beat position, and
forwards them as OSC. Chromatik documents this integration officially, so it is
a supported path rather than something we'd be inventing.

Per the [Chromatik guide](https://chromatik.co/guide/beat-link-trigger/):

- Chromatik's clock source is set to **OSC** in the toolbar.
- Beat Link Trigger sends to **`localhost:3030`** — configured in its Setup
  Expression as `(osc/osc-client "localhost" 3030)`.
- Its Beat Expression sends two addresses:
  `/lx/tempo/beat` (beat within bar) and `/lx/tempo/setBPM` (effective tempo).
- Beat Link Trigger requires Java and must be on the **same Ethernet network as
  the CDJs**.

### Consequences worth noting now

**Same port as the DAWs — by design.** Beat Link Trigger sends to port 3030,
the same port Chromatik already listens on for Ableton/Bitwig/Vezér. This is
fine: OSC multiplexes by address, so multiple senders share a port normally.
The tempo addresses (`/lx/tempo/*`) are Chromatik's own namespace and won't
collide with anything the DAWs send.

**Network separation — handled by a second NIC.** The Chromatik guide
recommends keeping Pioneer Pro DJ Link traffic off the Art-Net network, using
dual network interfaces or a VLAN-capable switch. Our approach is the former:
the live control laptop gets a **USB Ethernet adapter as a second port**, with
one interface on the CDJ network and the other on the LED network at
`10.0.1.0/24`. Pro DJ Link does its own device discovery and is chatty, so
keeping it off the Art-Net segment is the right call.

This requires a dedicated Ethernet run from the stage area to wherever the live
laptop sits. Note that this is a *separate cable* from the GEARit analog snake,
which also uses Cat5/6 but carries no network data — see [live DJ
setup](audio-system.md#live-dj-setup).

**TBD:** addressing on the CDJ interface. Pro DJ Link devices normally
self-assign; confirm whether the laptop's second NIC needs DHCP, link-local, or
a static address in the CDJs' range.

**Clock source is exclusive.** Setting Chromatik's clock source to OSC means the
CDJs become the tempo authority. If a DAW is also expected to drive tempo, only
one can win — decide which, per show mode.

**TBD:** which of the two MacBooks runs Beat Link Trigger. Since CDJs imply a
live set, the live MacBook is the likely host — but `localhost:3030` only works
if Chromatik is running on that same machine, so this needs deciding alongside
the handoff question in the [machines section](README.md#machines).

**TBD:** CDJ models, and how many.

## Failure modes to document

None of these are written up yet; each is worth a short entry once observed.

- A controller drops off the network — what does it look like, and how do we
  tell which one?
- OSC stops arriving — does the show freeze, hold, or go dark?
- Chromatik crashes or is restarted mid-show — what is the recovery sequence?
- Wrong project file opened — how would we notice?

## References

- Fixture definitions: `src/main/resources/fixtures/`
- Project files: `src/main/resources/projects/`
- [Chromatik user guide](https://chromatik.co/guide/)
