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
    HI["8x QSC highs"]
    SUB["8x QSC lows"]
    CTRL["32 LED controllers<br>10.0.1.101-.132"]
    LEDS["13,280 LEDs<br>cube + cylinder"]
    IPAD["iPad"]

    subgraph LIVE["Live DJ only"]
        DECKS["Decks / mixer"]
        GI1["GEARit box<br>under stage"]
        GI2["GEARit box<br>at MOTU"]
        CDJ["CDJs"]
        LIVECHR["Live MacBook<br>Chromatik (LX)<br>+ USB MIDI<br>no Loopback"]
    end

    APPS -->|audio| LB
    APPS -->|"OSC :3030"| CHR
    LB -->|USB-C| MOTU
    MOTU -->|"2x TRS to Euroblock"| AQM
    AQM -->|"4 out"| HI
    AQM -->|"4 out"| SUB
    CHR -->|"Art-Net (off during live)"| CTRL
    CTRL --> LEDS
    IPAD -.->|web UI| AQM

    DECKS -->|"2x XLR"| GI1
    GI1 ==>|"Cat5/6 = ANALOG AUDIO"| GI2
    GI2 -->|"2x XLR"| MOTU
    CDJ -.->|"Cat5/6 Pro DJ Link"| LIVECHR
    LIVECHR -->|"Art-Net (live only)"| CTRL
```

Dashed = control or planned. The heavy line is Cat5/6 carrying **analog audio,
not network**.

> **Only one Chromatik may output at a time.** Both MacBooks run Chromatik and
> both can reach the LED controllers over Art-Net. When bringing the live
> MacBook up, **turn off output on the resident MacBook first** — otherwise two
> instances drive the same controllers and the result is undefined.

## Docs

- [Physical installation](physical.md) — where things are, cable runs, the Pelican case
- [Hardware inventory](hardware.md) — master gear list
- [Audio system](audio-system.md) — Loopback → MOTU → Ashly → QSC
- [Lighting control](lighting-control.md) — OSC → Chromatik → Art-Net → LEDs
- [Art-Net destinations](artnet.md) — every controller IP and universe
- [Haptic floor](haptics.md) — standalone, not driven by Chromatik today

## Machines

| | Resident MacBook | Live MacBook |
|---|---|---|
| Presence | Always, in Pelican case | Live sets only |
| Chromatik | Yes | Yes |
| Loopback | Yes | **No** |
| Art-Net to LEDs | Yes — **off during live sets** | Yes — live sets only |
| USB MIDI controllers | — | Yes |
| Audio to the PA | Yes, via Loopback → MOTU | No |

**Handoff:** disable Art-Net output on the resident machine before enabling it on
the live machine. Never both.

## Show modes

| | Prerecorded | Live DJ |
|---|---|---|
| Audio source | Software | Decks |
| Path to MOTU | Loopback Audio 1/2 | GEARit XLR-over-Cat5 snake |
| Machines | Resident only | Both |
| Tempo | Software | CDJs via Beat Link Trigger (planned) |
| Extra cabling | — | 2x Cat5/6 from stage: one analog audio, one Pro DJ Link |

## TBD

- Exact handoff steps — where in Chromatik output is disabled, and how we verify
  only one instance is driving the LEDs
- How project files stay in sync between the two machines
- The live MacBook's address on `10.0.1.0/24`
- Network topology, power distribution, cold-start runbook

Binaries (PDFs, CAD, photos, BOMs) live in the shared drive, linked from here.
