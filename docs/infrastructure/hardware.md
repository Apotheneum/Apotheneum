# Hardware Inventory

**Draft.** Master list of physical gear. **TBD** = model/count unconfirmed.

Status: **In service** = deployed and working · **Needed** = required but not
yet acquired · **Planned** = future work.

## Computers

| Item | Qty | Status | Notes |
|---|---|---|---|
| Resident MacBook | 1 | In service | Sits on top of the MOTU Pelican case. Runs prerecorded sets. **TBD** model, macOS version |
| Live MacBook | 1 | In service | Brought out for live sets. USB MIDI connects here. **TBD** model, macOS version |
| Ethernet adapter, live MacBook | 1 | In service | Its only link to the system — Ethernet to the switch, nothing else |
| Second Ethernet adapter, live MacBook | 1 | Needed | For the CDJ Pro DJ Link network, keeping it off the Art-Net segment |
| iPad | 1 | In service | AQM408 web UI. **TBD** model, iPadOS version |

## Audio

| Item | Qty | Status | Notes |
|---|---|---|---|
| MOTU M4 | 1 | In service | 4-in/4-out USB-C interface, bus-powered. In its own Pelican case, MacBook on top |
| USB hub/dock, MOTU case | 1 | In service | Powered from AC. Fans the single USB-C cable out to the M4 and the case's Ethernet port. **TBD** make/model |
| Ashly AQM408 | 1 | In service | 1RU, 4x8 DSP matrix + crossover. In its own Pelican case. **TBD** MAC, IP, rack position |
| QSC highs | 8 | In service | Powered, with loop-thru. 4 driven from the Ashly, 4 daisy-chained. **TBD** model |
| QSC lows | 8 | In service | Powered, with loop-thru. 4 driven from the Ashly, 4 daisy-chained. **TBD** model |
| GEARit XLR-over-Cat5 box | 2 (1 pair) | In service | 4-channel passive snake. One under stage, one at MOTU. We use 2 of 4 channels |

## Lighting

| Item | Qty | Status | Notes |
|---|---|---|---|
| LED controllers | 32 | In service | 20 cube + 12 cylinder. `10.0.1.101`–`.132`. **TBD** make/model |
| LED nodes | 13,280 | In service | Cube + cylinder |

## Haptics

| Item | Qty | Status | Notes |
|---|---|---|---|
| Haptic floor driver box | 1 | In service | Drives the floor on an interval. **TBD** make/model |
| Haptic floor switch | 1 | In service | Motors and lights hang off it; reached from the main switch. **TBD** make/model/port count |
| Haptic triangles | 6 | In service | 16 positions each, every position a motor + a light. 96 motors, 96 lights. Art-Net path to `10.0.1.201` exists but is disabled. See [haptics](haptics.md) |

## Power

| Item | Qty | Status | Notes |
|---|---|---|---|
| AC adapter / distribution, case | 1 | In service | Everything in the case plugs into it, hub included. **TBD** make/model, capacity |
| UPS | 0 | — | **TBD** whether one is wanted; a blip currently drops the whole show |

## Network

| Item | Qty | Status | Notes |
|---|---|---|---|
| Switch | 1+ | In service | Ties the whole system together — LED controllers, both MacBooks, AQM408 control. **TBD** make/model/port count, location, and what serves DHCP |
| Router | **TBD** | **TBD** | **TBD** whether one exists, and what the iPad joins |

## Cables

| Item | Qty | Status | Notes |
|---|---|---|---|
| MOTU → Ashly (TRS to Euroblock) | 2 | In service | Existing fixed cable. **TBD** photograph, record type and length |
| XLR, decks → GEARit | 2 | In service | **TBD** length |
| XLR, GEARit → MOTU | 2 | In service | **TBD** length |
| Cat5/6, stage → MOTU (**analog audio**) | 1 | In service | Carries audio, not network. **TBD** length |
| Cat5/6, stage → live laptop (Pro DJ Link) | 1 | Needed | Separate run from the above |
| Pelican panel connector + mating field cable | 1 | In service | Carries all 8 Ashly outputs. Single point of failure; also the dust seal. **TBD** make/series/pins, and whether a spare exists |
| Ashly → QSC | 8 | In service | One per corner per driver type, downstream of the panel connector. **TBD** connector type and lengths |
| QSC daisy-chain (loop-thru) | 8 | In service | Second box in each chain. **TBD** connector type and lengths |
| USB-C, MacBook → MOTU case | 1 | In service | **Carries audio and network both.** Single point of failure for the resident machine. **TBD** spec, and whether a spare exists |

## Planned

| Item | Qty | Notes |
|---|---|---|
| CDJs | **TBD** | **TBD** models. Feed Beat Link Trigger for tempo sync |
| MIDI controllers | **TBD** | **TBD** which ones. USB to the live MacBook |

## Spares to consider

None held today. Candidates, cheapest-first by consequence:

- MOTU → Ashly cable (custom-ish, hardest to source at short notice)
- Cat5/6 runs of the right lengths
- USB-C cable
- Euroblock plugs (spares beyond those shipped with the AQM408)

**TBD:** what already exists as spares, and where it's kept.

## Software licenses

| Item | Notes |
|---|---|
| Rogue Amoeba Loopback | Most contributors have their own. Those who don't get output set manually at setup |
| Ableton Live / Bitwig Studio / Vezér | Per-contributor |
| Beat Link Trigger | Free, open source. Requires Java |
