#!/usr/bin/env python3
"""Bake the Robot Heart LED fixtures into a wireframe OBJ for RobotHeart.java.

The sculpture is defined as 36 Chromatik fixtures under a `heart/` directory,
each one a chain of straight LED strips. This walks those strips at full LED
resolution, chains consecutive strips into continuous polylines, and normalizes
the result to a height of 1.0 centred on the origin.

Every vertex is a real LED position, so the pattern can either stamp the vertices
as dots (the sculpture's own pixels) or stroke the polylines as continuous bars.

Bars are tagged `bar_*` and rings `ring_*` so the pattern can draw them
separately: at 50x45 the full cage reads as a filled blob, and the bars alone
read as the sculpture.

Usage: scripts/bake-robot-heart.py [FIXTURE_DIR] [OUTPUT_OBJ]
"""

import json
import glob
import math
import os
import re
import sys

DEFAULT_FIXTURES = os.path.expanduser("~/Chromatik/Fixtures/heart")
DEFAULT_OUTPUT = "src/main/resources/models/robot-heart.obj"

# Consecutive strips within a bar sit about one LED apart; anything wider is a
# genuine break in the bar and must not be bridged with a line.
JOIN_TOLERANCE = 15.0

# A polyline spanning more than this fraction of the sculpture's height is a
# vertical bar; the flatter ones are the latitude rings.
BAR_HEIGHT_FRACTION = 0.5


def sort_key(name):
    match = re.match(r"([A-Z]+)(\d+)", name)
    return (match.group(1), int(match.group(2)))


def read_bars(fixture_dir):
    bars = {}
    for path in sorted(glob.glob(os.path.join(fixture_dir, "*.lxf"))):
        fixture = json.load(open(path))
        strips = []
        for component in fixture["components"]:
            count = component["numPoints"]
            spacing = component["spacing"]
            origin = (component["x"], component["y"], component["z"])
            direction = component["direction"]
            dx, dy, dz = direction["x"], direction["y"], direction["z"]
            length = math.sqrt(dx * dx + dy * dy + dz * dz)
            dx, dy, dz = dx / length, dy / length, dz / length
            strips.append([
                (origin[0] + dx * spacing * i, origin[1] + dy * spacing * i, origin[2] + dz * spacing * i)
                for i in range(count)
            ])

        polylines = []
        current = list(strips[0])
        for strip in strips[1:]:
            if math.dist(current[-1], strip[0]) <= JOIN_TOLERANCE:
                current.extend(strip)
            else:
                polylines.append(current)
                current = list(strip)
        polylines.append(current)
        bars[fixture["label"]] = polylines
    return bars


def main():
    fixture_dir = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_FIXTURES
    output = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_OUTPUT

    bars = read_bars(fixture_dir)
    every_point = [p for polylines in bars.values() for line in polylines for p in line]
    xs = [p[0] for p in every_point]
    ys = [p[1] for p in every_point]
    zs = [p[2] for p in every_point]
    cx, cy, cz = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2
    height = max(ys) - min(ys)

    lines = [
        "# Robot Heart wireframe, baked from the sculpture's LED fixture definitions.",
        "# Normalized: centred on the origin, 1.0 tall, silhouette facing +Z.",
        "# Every vertex is a real LED position; each polyline is one bar's run of pixels.",
        "# Objects are tagged bar_* (vertical) and ring_* (horizontal).",
        "# Regenerate with scripts/bake-robot-heart.py.",
    ]
    vertex_ids = {}
    vertex_lines = []
    element_lines = []

    for name in sorted(bars, key=sort_key):
        for index, polyline in enumerate(bars[name]):
            span = max(p[1] for p in polyline) - min(p[1] for p in polyline)
            kind = "bar" if span > BAR_HEIGHT_FRACTION * height else "ring"
            suffix = "" if len(bars[name]) == 1 else "_%d" % (index + 1)
            element_lines.append("o %s_%s%s" % (kind, name, suffix))
            indices = []
            for point in polyline:
                key = (
                    round((point[0] - cx) / height, 4),
                    round((point[1] - cy) / height, 4),
                    round((point[2] - cz) / height, 4),
                )
                if key not in vertex_ids:
                    vertex_ids[key] = len(vertex_ids) + 1
                    vertex_lines.append("v %.4f %.4f %.4f" % key)
                indices.append(vertex_ids[key])
            element_lines.append("l " + " ".join(str(i) for i in indices))

    os.makedirs(os.path.dirname(output), exist_ok=True)
    with open(output, "w") as handle:
        handle.write("\n".join(lines + vertex_lines + element_lines) + "\n")

    polylines = sum(1 for line in element_lines if line.startswith("l "))
    segments = sum(len(line.split()) - 2 for line in element_lines if line.startswith("l "))
    bars_count = sum(1 for line in element_lines if line.startswith("o bar"))
    print("%s: %d vertices, %d polylines (%d bars, %d rings), %d segments"
          % (output, len(vertex_ids), polylines, bars_count, polylines - bars_count, segments))


if __name__ == "__main__":
    main()
