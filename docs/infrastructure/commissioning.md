# Commissioning

Everything not yet verified. Replace entries with observed facts rather than
deleting them; if something turns out not to matter, say so and strike it.

The other pages state what is known. This page holds what is not, so the
documentation itself stays readable.

## Blockers — settle before the next show

| # | Question | Evidence needed |
|---|---|---|
| B1 | Why is OSC receive `false` in `Apotheneum.lxp` but `true` in the test project? Is it enabled by hand each show, or does the live show run a project not in this repo? | The project file actually used, and where it lives |
| B2 | What happens if the floor controller and Chromatik address the floor at once? | Observation, ideally with the floor isolated |
| B3 | How does the Loopback stream reach the M4 — a monitor on the virtual device, or a pass-through app? | Screenshot of the Loopback configuration on the show machine |
| B4 | Exactly where in Chromatik is Art-Net output toggled, and how do we verify only one instance is driving? | A written procedure, tested |
| B5 | Are all four switches gigabit? | Model numbers |

## Safety and power

| # | Question |
|---|---|
| S1 | What feeds the rig, on which circuits, and what is the total draw? |
| S2 | Are the QSC boxes locally powered at each corner? |
| S3 | Is anything on UPS? A power blip currently drops the whole show. |
| S4 | Is there a spare for the Ashly panel connector — the single point of failure for all eight outputs? |

## Configuration to record

| # | Item |
|---|---|
| C1 | AQM408: static IP or DHCP reservation, and the address. An automatic address can change after a reboot and break the iPad's bookmark |
| C2 | AQM408: crossover point and slope, limiter settings, delay/alignment |
| C3 | AQM408: which output drives which corner, for highs and lows |
| C4 | AQM408: criss-cross and left/right saved as two presets, and which is the default |
| C5 | Gain structure end to end, and where the system limit is set |
| C6 | Gain staging for live decks versus software playback — deck output is hotter, which probably wants its own preset |
| C7 | MOTU knob positions in each mode — a photo of the front panel is the cheapest form of this |
| C8 | The OSC addresses each application sends, captured from the running system rather than recalled |
| C9 | Whether the applications are ever run simultaneously; Loopback sums them |
| C10 | Startup order, and how we notice if audio has gone to the wrong device |
| C11 | How project files stay in sync between the two MacBooks |

## Identification

| # | Item |
|---|---|
| I1 | QSC models for highs and lows |
| I2 | LED controller make and model |
| I3 | Floor controller make and model, and what sets its interval |
| I4 | The Ashly panel connector — make, series, pin count, and internal pinout |
| I5 | The USB hub in the MOTU case — make, model; and whether the MacBook charges over the same cable |
| I6 | All four switches — make, model, port count, location |
| I7 | MacBook models and macOS versions; AQM408 MAC address; live MacBook's IP |
| I8 | The MOTU → Ashly cable: type and length, so a spare can be ordered |
| I9 | Cable lengths for both stage runs, and whether they are permanent |

## Photos to take

- MOTU front panel in prerecorded and live-DJ states
- Both ends of the MOTU → Ashly cable
- Ashly case rear panel and its connector; the internal loom
- Inside both Pelican cases, lids open
- MOTU case rear panel showing the Ethernet port
- Stage-end GEARit box with both Cat5 runs
- Each of the four switches in place

## Backups

| # | Item |
|---|---|
| K1 | Export the known-good AQM408 preset and store it in the shared drive |
| K2 | Record where the real AQM408 credentials are kept |

## Failure modes to write up

Each needs one observation, then a short entry on the relevant page.

- A controller drops off the network — what does it look like, and which one is it?
- OSC stops arriving — does the show freeze, hold, or go dark?
- Chromatik restarted mid-show — what is the recovery sequence?
- Wrong project opened — how would we notice?
