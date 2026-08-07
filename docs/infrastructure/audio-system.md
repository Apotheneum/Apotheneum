# Audio System

**Status: draft.** Items marked **TBD** need to be confirmed on site.

The audio chain runs from a MacBook in a Pelican case, through a MOTU M4 USB
interface, into an Ashly AQM408 DSP processor that handles matrix routing and
the high/sub crossover, and out to QSC amplification — four outputs to the highs
and four to the subs. The AQM408 is configured and operated from an iPad over
the network.

The same three applications that feed this chain also send OSC to Chromatik —
see [lighting control](lighting-control.md). The two paths diverge at the source
and share nothing downstream except the MacBook they both run on.

## Signal chain

```text
Ableton Live ─┐
Bitwig Studio ─┤  audio out set to → Loopback Audio 1/2
Vezér ────────┘                       (virtual device, Rogue Amoeba Loopback)
                                              │
                                              ▼
MacBook (Pelican case)
  │  USB-C
  ▼
MOTU M4                      4-in / 4-out USB audio interface
  │  balanced TRS (1/4")  ── TBD: 2 channels or 4?
  ▼
Ashly AQM408                 4 in × 8 out DSP matrix / speaker processor
  │  balanced Euroblock, 8 outputs
  ├─ outputs 1–4 ──▶ QSC highs   (TBD: model, amp or powered)
  └─ outputs 5–8 ──▶ QSC subs    (TBD: model, amp or powered)

iPad ──(network)──▶ AQM408 web UI (AquaControl)
```

The crossover is done in the AQM408, not in the amplifiers.

## Software → Loopback Audio

Playback software does not address the MOTU directly. It outputs to a **virtual
audio device** named `Loopback Audio`, and that stream is what reaches the
interface. This gives us one stable output target regardless of which
application is playing, and lets the same stream be tapped by more than one
consumer.

### Sources

Three applications feed the same virtual device, each with its audio output set
to `Loopback Audio 1/2`:

| Application | Role | Where to set the output |
|---|---|---|
| **Ableton Live** | TBD — describe what it's used for | Preferences → Audio → Audio Output Device |
| **Bitwig Studio** | TBD | Settings → Audio → Output Device |
| **Vezér** | TBD | TBD — confirm audio output setting |

They are interchangeable at the routing level: whichever one is running, the
downstream chain from Loopback onward is identical. Nothing below the virtual
device needs to change when we switch between them.

**TBD:** are these ever run *simultaneously*? Loopback will sum multiple sources
into the same channel pair, which is either the intended behavior or an
accident waiting to happen at showtime. If only one runs at a time, say so
explicitly.

All three do double duty: alongside audio, each also sends OSC to Chromatik.
That control path is documented in [lighting control](lighting-control.md) — the
two roles are independent, and this page covers only the audio half.

The virtual device is created by [Rogue Amoeba
Loopback](https://rogueamoeba.com/loopback/). `Loopback Audio` is its default
device name, and the `1/2` in "Loopback Audio 1/2" is the first stereo pair on
that device, not a separate device.

**Partially confirmed.** On a development Mac the device is present and reports:

| | |
|---|---|
| Name | `Loopback Audio` |
| Manufacturer | Rogue Amoeba Software, Inc. |
| Channels | 6 in / 6 out |
| Sample rate | 48000 Hz |
| Transport | Virtual |

Six channels means three stereo pairs, so `1/2` is one of several available —
worth knowing before assuming a given app is on the pair we think it is.

**TBD — confirm on the show MacBook, not the dev machine.** The above was read
off a development machine; the Pelican-case Mac needs its own check. Run:

```bash
system_profiler SPAudioDataType | grep -A 8 "Loopback Audio"
```

**TBD — how the stream actually reaches the M4.** This is the one place in the
whole chain where the MOTU is named, so it is worth getting exactly right.
Loopback offers two routes and they behave differently:

1. **Monitors.** The virtual device is configured with the MOTU M4 added as a
   monitor, so anything passing through the device is also sent to the M4's
   hardware outputs. Channels map one-to-one by default. This is the usual
   setup and needs no second application.
2. **Pass-through app.** Something takes `Loopback Audio` as its *input* and
   outputs to the M4. More moving parts, and a second thing to launch at
   showtime.

**Chromatik does not take audio.** There are no audio-reactive patterns in this
show. Chromatik's only input is OSC, from the same three applications — see
[lighting
control](lighting-control.md). Audio and lighting share source applications and
a machine, but no signal. Nothing downstream of this virtual device affects the
LEDs.

### Why Loopback and not the MOTU directly

**Because it makes projects portable between home and the live system.**

A DAW stores its output device by name. Set a project's output to `MOTU M4` and
it only resolves on a machine with the M4 attached — at home it falls back or
goes silent. Set it to your home interface and it breaks the other direction.
This is the recurring failure: someone builds a set at home, brings it to the
live system, and the output device they saved doesn't exist there.

`Loopback Audio` exists identically on every machine that has Loopback
installed. Anyone can set their DAW output to `Loopback Audio 1/2` at home,
where it works, and the same project works unchanged on the show system.

The MOTU is named exactly once, on the show machine, at the point where Loopback
hands off to hardware. Everything upstream is abstract.

```text
At home:      DAW → Loopback Audio 1/2 → (no hardware bound)
On the rig:   DAW → Loopback Audio 1/2 → MOTU M4 → PA
              └─ same project setting ─┘   └─ bound once, here ─┘
```

**Do not "simplify" this by pointing the DAWs at the M4 directly.** The
indirection is the feature.

Consequences worth stating:

- **Everyone contributing sets needs Loopback installed**, and the device must
  be named `Loopback Audio` — the default. A renamed device breaks portability
  for that person's projects.
- **`1/2` is a shared convention**, not a technical requirement. The device has
  three stereo pairs; the whole scheme only works because everyone agrees to use
  the first.
- Loopback is paid software (Rogue Amoeba), and most contributors already have
  it. **For those who don't, we set the output on their software during
  setup** — a manual fallback rather than a blocker. Those projects lose the
  portability benefit, so expect to re-check their output device every time they
  come back to the rig.

**TBD — startup order and failure mode.** Virtual devices can be missed by an
application that launched before the device existed, and macOS can silently fall
back to built-in speakers if a device disappears. What is the correct launch
sequence, and how do we notice when audio has gone to the wrong place?

## Live DJ setup

For prerecorded sets, audio originates in software on the resident MacBook and
the chain above is the whole story. **For a live DJ, audio originates at the
decks instead** and has to get from the stage back to the MOTU.

We do that with a pair of **GEARit 4-channel XLR-over-Cat5/Cat6 snake extender
boxes** — one under the stage, one by the MOTU — joined by a single Ethernet
cable.

```text
Decks ──2 × XLR──▶ GEARit box (under stage) ──Cat5/6──▶ GEARit box (at MOTU) ──2 × XLR──▶ MOTU M4 in 1–2
```

Each box has four XLR connectors and one Ethercon-compatible RJ45. One box of
the pair carries female XLRs (source side), the other male (destination side).
The units are passive and support 48V phantom power and 3-pin DMX as well as
analog audio.

**We use 2 of the 4 channels** — a stereo pair from the decks into MOTU inputs
1 and 2. Those two are the M4's combo XLR/TRS jacks, so the XLRs land natively
with no adapters. Channels 3 and 4 on the snake stay spare.

### The thing to get right

**That Ethernet cable is not carrying Ethernet.** It carries balanced analog
audio, one channel per twisted pair. Nothing about it is a network link — you
cannot put a switch on either end, and you cannot share it with network
traffic. Two of the four pairs are unused in our config, but the boxes only
break out to XLR, so there is no way to reach them for data anyway.

This matters because the same live setup **also** needs a real Ethernet run from
the stage area to the live control laptop, for the CDJ Pro DJ Link network. That
is a second, separate cable that happens to look identical. Label both ends of
both runs.

```text
Stage ══════ Cat5/6 #1 ══════▶ analog audio (GEARit snake)  → MOTU
Stage ══════ Cat5/6 #2 ══════▶ Pro DJ Link network (CDJs)   → live control laptop
```

### Live control laptop networking

The live laptop needs a **second Ethernet port** via adapter: one interface for
the CDJ Pro DJ Link network, the other for the LED/Art-Net network. This
satisfies the dual-NIC separation that Chromatik's Beat Link Trigger guide
recommends — see [lighting control](lighting-control.md#planned-cdj-beat-sync).

### Open items for the live path

- **TBD:** which output on the DJ side feeds the snake — presumably the mixer's
  stereo master out, but worth recording the exact jack.
- **TBD:** gain staging for a live deck feed versus software playback. Deck
  output is hotter than a DAW's, so the AQM408 input trim probably needs a
  different setting per mode. Worth a saved preset for each.
- **TBD:** cable lengths for both runs, and whether they're permanently
  installed or deployed per show.
- **Watch for hum.** Passive snakes give no galvanic isolation, so a long run
  between separately-powered stage and FOH positions can pick up ground noise.
  If it appears, note here what fixed it.

## MacBook → MOTU M4

USB-C. The M4 is bus-powered and class-compliant on macOS, so no driver is
required for audio; MOTU's installer is only needed for the control panel.

### Front panel controls

The M4's routing is hardware, set with knobs and buttons — there is no mixer
application to configure.

| Control | What it does |
|---|---|
| **Gain** (per input) | Input trim. Set with the deck running, watching the meters. |
| **48V** | Phantom power. **Not needed for line-level decks** — leave off. |
| **MON** buttons | Engage direct hardware monitoring. Inputs 1–2 have their own; inputs 3–4 share one. Hold to link a pair as stereo. |
| **INPUT / PLAYBACK mix** | Blends direct-monitored input against computer playback. This is the important one for DJ mode. |
| **MONITOR** | Main output level. |
| **PHONES** | Headphone level, independent of MONITOR. |

### Two ways to get DJ audio to the PA

Yes — the inputs can reach the outputs, and for a live DJ there is a clear right
answer.

**Use direct hardware monitoring (MON engaged).** The input signal is routed to
the outputs inside the interface, bypassing the computer entirely. Zero latency,
and the audio path survives a software crash, a beachball, or a Chromatik
restart. **The Mac is not in the critical path for sound at all.**

The alternative — capturing inputs in software and sending them back out — buys
processing and routing flexibility, but costs latency and makes a live DJ's
sound dependent on the Mac staying healthy mid-set. Since nothing downstream
needs the computer to see the audio (Chromatik takes no audio input), there is
no reason to accept that risk. Route it in hardware.

The knob to watch is **INPUT / PLAYBACK**. Fully toward PLAYBACK, direct
monitoring is inaudible no matter what the MON buttons are doing; fully toward
INPUT, software playback disappears. In DJ mode it wants to be toward INPUT —
and its position needs to be recorded here once set, because nothing on screen
reflects it and the next person will not guess it.

**Unverified:** whether direct monitoring reaches line outs 3–4 or only the main
outs 1–2. Since we feed the Ashly from the main outs this is likely moot, but it
should be confirmed rather than assumed if outputs 3–4 are ever used.

- **TBD:** MacBook model and macOS version
- **TBD:** sample rate and buffer size in use
- **TBD:** record the physical knob positions once set — gain, INPUT/PLAYBACK,
  MONITOR — for both prerecorded and DJ modes. A photo of the front panel in
  each state is the cheapest form of this documentation.

## MOTU M4 → Ashly AQM408

This is the connection that needed research. The two devices use different
connectors, so it is a wired adapter cable, not an off-the-shelf patch lead.

| | MOTU M4 output | AQM408 input |
|---|---|---|
| Connector | 1/4" TRS, balanced, tip hot | 3-pin Euroblock (Phoenix), `+ – G` |
| Count | 4 (Monitor L/R + Line Out 3–4) | 4 (Balanced Line Inputs 1–4) |
| Max level | +16 dBu | +21 dBu |

**Wiring:** TRS tip → `+`, ring → `–`, sleeve → `G`. If an unbalanced source is
ever used instead, wire hot to `+`, ground to `–`, and leave that channel's
ground pin unconnected — this is the manual's specified unbalanced scheme, not
a general convention.

### The cable

**A cable for this already exists and is in service** — this connection is made,
not pending. What follows is for identifying it, spare-ing it, or replacing it,
not for building it from scratch.

**TBD — record what the existing cable actually is.** Photograph both ends and
note the type and length. Until that's written down, a failure at showtime means
identifying an unfamiliar cable under pressure, and there is no way to order a
spare in advance.

There is no off-the-shelf "TRS to Euroblock" patch lead in the way there is for
TRS-to-XLR, so a replacement is most likely a **1/4" TRS male to bare-wire
(pigtail) cable** landed into the screw terminals:

```text
[1/4" TRS male] ──────── cable ──────── [stripped ends] → screw into Euroblock plug
       │                                        │
   into MOTU M4                          tip → +   ring → –   sleeve → G
   Monitor Out L / R                     into AQM408 Balanced Line Input
```

**The Euroblock plugs themselves ship with the AQM408** — the manual refers to
"the provided Euroblock connectors" — so nothing exotic needs sourcing for a
rebuild. Hosa and similar make TRS-to-bare-wire as a stock item; a normal
balanced TRS cable cut in half and stripped works equally well.

**TBD:** Euroblock pitch (3.5 mm is typical for Ashly) — only matters if a spare
plug is ever needed, since the unit came with its own.

**Levels:** the M4 tops out 5 dB below the AQM408's input ceiling, so there is
headroom in hand and no pad is needed. Gain staging still needs to be set
deliberately — see open questions.

**TBD:** are we sending a stereo pair into inputs 1–2, or four channels into
1–4? This determines both the cable count and the AQM408 matrix configuration.

## Ashly AQM408

1RU, 100–240 VAC, universal supply. Four balanced Euroblock inputs, eight
balanced Euroblock outputs, all post-DSP.

DSP blocks available per channel include crossover, high-pass and low-pass
filters, parametric and graphic EQ, FIR filters, compressor/limiter, brick wall
limiter, gate, delay, autoleveler, ducking, and signal generation. Routing is a
full 8-mixer matrix — any input can feed any output.

### Routing

Two channels come in and are distributed across the eight outputs — four to the
highs, four to the subs. Since the source is stereo and the destination is four
positions, there is a choice about how left and right are laid out:

- **Left/right** — conventional stereo. Left-side boxes get L, right-side get R.
- **Criss-cross** — L and R alternate around the space rather than staying on
  one side, giving a more enveloping, less directional field.

Both are configured in the AQM408 matrix, so switching between them is a routing
change, not a re-patch.

**TBD:** the exact output-to-position mapping for each layout — which physical
output drives which box, in both L/R and criss-cross. Worth capturing as two
saved presets so the choice is one recall rather than eight matrix edits.

**TBD:** which layout is the default, and what drives the choice — content,
audience position, or the geometry of the chamber the audio is playing into.

- **TBD:** current crossover point and slope between highs and subs
- **TBD:** limiter settings per output, and what they were derived from
- **TBD:** delay/alignment values, if any
- **TBD:** output-to-speaker mapping (which physical output drives which box)

### Control from the iPad

The AQM408 runs a **built-in web server**. AquaControl is served from the device
itself — there is no app to install on the iPad, just a browser. Supported
browsers are Chrome, Edge, and Safari; minimum resolution 1024×768, with a 10"
screen recommended, so an iPad is a well-supported target rather than a
workaround.

Connect the AQM408's RJ-45 to the same network as the iPad, or directly to a
computer. It negotiates 10/100/1000.

- **Address:** by default the device is set to automatic IP (DHCP, falling back
  to link-local). It also answers on a hostname of the form
  `http://AQM408_<MAC>.local/` — for example `http://AQM408_0014AAF00036.local/`.
- **Login:** factory default is user `admin`, password `secret`. **These must be
  changed** — see below.
- **Roles:** AquaControl has admin, guest admin, operator, and view-only roles.
  An operator-level login for show use, separate from the admin credentials, is
  worth setting up.

**TBD:** the network itself. Is there a router or switch in the Pelican case? Is
the iPad on Wi-Fi from that router, or from house network? Does the AQM408 have
a DHCP reservation or a static IP? The manual is explicit that an automatic
address can be reassigned after a lease expiry or reboot, which would silently
break the iPad's bookmark — so this should be pinned down rather than left on
DHCP.

**TBD:** record the device's MAC address (printed on a sticker on the unit) —
it is needed for both the hostname and any DHCP reservation.

### Physical controls to know

- **No power switch.** The unit powers off only from Settings → Panels in the
  software, or by pulling AC.
- **Reset switch** on the rear does two things depending on how long it is held
  during a cold boot: an *admin reset* (restores the admin password to `secret`,
  preserves presets and settings, and reverts IP config to automatic) or a
  *factory default reset* (erases everything). The distinction matters if we
  ever lose the admin password — admin reset recovers access without losing the
  tuning, but it will drop a static IP.
- Three VCA remote level-control inputs and two trigger inputs are present but
  **TBD** whether we use them.

## Open questions

1. Confirm `Loopback Audio 1/2` on the show MacBook, and confirm whether the
   stream reaches the M4 via Loopback monitors or a pass-through app.
2. Two channels or four from the M4 into the AQM408?
3. QSC model numbers for both highs and subs, and whether they're powered boxes
   or separate amplifiers.
4. Network topology — what provides DHCP, and is the iPad wired or wireless?
5. Static IP vs. DHCP reservation for the AQM408, and the chosen address.
6. Gain structure: nominal operating level at the M4 output and at the AQM408
   input, and where the system limit is set.
7. Is there a saved AQM408 preset that represents the known-good show state, and
   where is that preset backed up? AquaControl supports preset import/export.
8. Physical: is the AQM408 rack-mounted, and where does it live relative to the
   Pelican case?

## To do

- [ ] Screenshot the Loopback configuration on the show Mac
- [ ] Photograph the AQM408 rear panel as wired, and the inside of the Pelican
      case
- [ ] Record MAC address and final IP
- [ ] Change default credentials and record where they're stored
- [ ] Export the AQM408 preset and store it in the shared drive
- [ ] Draw the rack/cable diagram once the TBDs are resolved

## References

- [AQM408 product page](https://ashly.com/processor-card/aqm408/)
- [AQM408 Operating Manual (PDF)](https://ashly.com/wp-content/uploads/2024/06/AQM408_manual_r4.pdf)
