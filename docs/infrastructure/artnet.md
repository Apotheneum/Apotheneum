# Art-Net Destinations

**Generated from the fixture files** — `src/main/resources/fixtures/`. If these
disagree with the running system, the fixtures are the source of truth for
defaults, but a project can override any host.

Protocol is Art-Net throughout. Every address below is a *default*: the hosts
are fixture variables (`$cub01` … `$cyl12`, `$host`), so a project may point
them elsewhere.

## LEDs

32 controllers on `10.0.1.0/24`. Each gets a block of **6 universes** — three
for the exterior surface, three for the interior. Three because a 450-point RGB
run is 1,350 channels, which spans three 512-channel universes.

The `universe` field in the fixture is the *starting* universe; LX continues
into the following ones automatically.

| Controller | IP | Exterior universes | Interior universes |
|---|---|---|---|
| `cub01` | `10.0.1.101` | 0–2 | 3–5 |
| `cub02` | `10.0.1.102` | 6–8 | 9–11 |
| `cub03` | `10.0.1.103` | 12–14 | 15–17 |
| `cub04` | `10.0.1.104` | 18–20 | 21–23 |
| `cub05` | `10.0.1.105` | 24–26 | 27–29 |
| `cub06` | `10.0.1.106` | 30–32 | 33–35 |
| `cub07` | `10.0.1.107` | 36–38 | 39–41 |
| `cub08` | `10.0.1.108` | 42–44 | 45–47 |
| `cub09` | `10.0.1.109` | 48–50 | 51–53 |
| `cub10` | `10.0.1.110` | 54–56 | 57–59 |
| `cub11` | `10.0.1.111` | 60–62 | 63–65 |
| `cub12` | `10.0.1.112` | 66–68 | 69–71 |
| `cub13` | `10.0.1.113` | 72–74 | 75–77 |
| `cub14` | `10.0.1.114` | 78–80 | 81–83 |
| `cub15` | `10.0.1.115` | 84–86 | 87–89 |
| `cub16` | `10.0.1.116` | 90–92 | 93–95 |
| `cub17` | `10.0.1.117` | 96–98 | 99–101 |
| `cub18` | `10.0.1.118` | 102–104 | 105–107 |
| `cub19` | `10.0.1.119` | 108–110 | 111–113 |
| `cub20` | `10.0.1.120` | 114–116 | 117–119 |
| `cyl01` | `10.0.1.121` | 120–122 | 123–125 |
| `cyl02` | `10.0.1.122` | 126–128 | 129–131 |
| `cyl03` | `10.0.1.123` | 132–134 | 135–137 |
| `cyl04` | `10.0.1.124` | 138–140 | 141–143 |
| `cyl05` | `10.0.1.125` | 144–146 | 147–149 |
| `cyl06` | `10.0.1.126` | 150–152 | 153–155 |
| `cyl07` | `10.0.1.127` | 156–158 | 159–161 |
| `cyl08` | `10.0.1.128` | 162–164 | 165–167 |
| `cyl09` | `10.0.1.129` | 168–170 | 171–173 |
| `cyl10` | `10.0.1.130` | 174–176 | 177–179 |
| `cyl11` | `10.0.1.131` | 180–182 | 183–185 |
| `cyl12` | `10.0.1.132` | 186–188 | 189–191 |

Universe allocation is contiguous and regular: controller *n* starts at
`6 × (n − 1)`, cube first (`cub01` at 0), then cylinder (`cyl01` at 120).
Total span is universes **0–191**.

Point counts per output vary — door cutouts shorten some columns, so cube
controllers carry either ~450 or ~340 points and cylinder ones ~430 or ~320.

Each controller also has `Flip` (and on the cylinder, `B2F` — back to front)
boolean parameters that select between alternate output blocks. These encode
physical mounting, so a section rendering mirrored or inverted is usually one of
these being wrong rather than a wiring fault.

## Haptic floor

Currently **not driven by Chromatik** — a standalone box drives the floor on an
interval. The path below exists in the fixtures with output disabled by default.
See [haptics](haptics.md).

| | |
|---|---|
| Host | `10.0.1.201` |
| Universe | 0 |
| Byte order | `w` — one channel per motor |
| Structure | 6 × `Apotheneum-Haptic-Triangle`, 16 motors each, rolled −60° per instance |

| Triangle | Bank A channels | Bank B channels |
|---|---|---|
| 0 | 0–15 | 96–111 |
| 1 | 16–31 | 112–127 |
| 2 | 32–47 | 128–143 |
| 3 | 48–63 | 144–159 |
| 4 | 64–79 | 160–175 |
| 5 | 80–95 | 176–191 |

96 motors across two banks, 192 channels total — all within universe 0.

**TBD:** what the two banks are physically. The fixture emits both from the same
16 points per triangle at a 96-channel offset, which suggests two motors per
position or a second physical layer.

## Regenerating

These tables are derived, not hand-maintained. If the fixtures change, re-extract
rather than editing by hand — the `.lxf` files are JSON with comments, trailing
commas, and leading-dot numbers, so they need a tolerant parser.
