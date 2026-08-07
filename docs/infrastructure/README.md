# Apotheneum Infrastructure

**Draft.** **TBD** = unverified. Replace with observed facts; don't guess.

```mermaid
flowchart TB
    subgraph SRC["Source apps"]
        APPS["Ableton Live<br>Bitwig Studio<br>Vezér"]
    end

    subgraph RESIDENT["Resident MacBook (Pelican case)"]
        LB["Loopback Audio 1/2<br>virtual device"]
        CHR["Chromatik (LX)"]
    end

    MOTU["MOTU M4"]
    AQM["Ashly AQM408<br>matrix + crossover"]
    HI["4x QSC highs"]
    SUB["4x QSC subs"]
    CTRL["32 LED controllers<br>10.0.1.101-.132"]
    LEDS["13,280 LEDs<br>cube + cylinder"]
    IPAD["iPad"]

    subgraph LIVE["Live DJ only"]
        DECKS["Decks / mixer"]
        GI1["GEARit box<br>under stage"]
        GI2["GEARit box<br>at MOTU"]
        CDJ["CDJs"]
        LIVEMAC["Live MacBook<br>+ USB MIDI"]
    end

    APPS -->|audio| LB
    APPS -->|"OSC :3030"| CHR
    LB -->|USB-C| MOTU
    MOTU -->|"2x TRS to Euroblock"| AQM
    AQM -->|"out 1-4"| HI
    AQM -->|"out 5-8"| SUB
    CHR -->|"Art-Net"| CTRL
    CTRL --> LEDS
    IPAD -.->|web UI| AQM

    DECKS -->|"2x XLR"| GI1
    GI1 ==>|"Cat5/6 = ANALOG AUDIO"| GI2
    GI2 -->|"2x XLR"| MOTU
    CDJ -.->|"Cat5/6 Pro DJ Link"| LIVEMAC
    LIVEMAC -.->|"Beat Link Trigger, planned"| CHR
```

Dashed = control or planned. The heavy line is Cat5/6 carrying **analog audio,
not network**.

## Docs

- [Audio system](audio-system.md) — Loopback → MOTU → Ashly → QSC
- [Lighting control](lighting-control.md) — OSC → Chromatik → Art-Net → LEDs

## Machines

| Machine | Presence | Role |
|---|---|---|
| Resident MacBook | Always, in Pelican case | Prerecorded sets |
| Live MacBook | Live sets only | USB MIDI controllers connect **here**, not to the resident machine |

## Show modes

| | Prerecorded | Live DJ |
|---|---|---|
| Audio source | Software | Decks |
| Path to MOTU | Loopback Audio 1/2 | GEARit XLR-over-Cat5 snake |
| Machines | Resident only | Both |
| Tempo | Software | CDJs via Beat Link Trigger (planned) |
| Extra cabling | — | 2x Cat5/6 from stage: one analog audio, one Pro DJ Link |

## TBD

- Which machine runs Chromatik during live sets; handoff procedure
- Is the MOTU re-patched between machines?
- How project files stay in sync between the two
- Network topology, power distribution, cold-start runbook
- Haptics (`Apotheneum-Haptics.lxf` exists — what drives it?)

Binaries (PDFs, CAD, photos, BOMs) live in the shared drive, linked from here.
