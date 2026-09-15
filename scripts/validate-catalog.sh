#!/bin/bash
# Validate src/main/resources/catalog/ entries against the lx-mcp catalog format:
# frontmatter keys, class==filename, hash shapes, exact section headings, and
# recorded hashes recomputed against source (and target/classes when compiled).
# Run from anywhere; operates on the repo containing this script.
set -u
REPO="$(cd "$(dirname "$0")/.." && pwd)"
CAT="$REPO/src/main/resources/catalog"
[ -d "$CAT" ] || { echo "no catalog dir at $CAT"; exit 1; }

fail=0
count=0
for f in "$CAT"/*.md; do
  [ -e "$f" ] || continue
  count=$((count+1))
  base="$(basename "$f" .md)"
  err() { echo "FAIL $base: $1"; fail=1; }

  head -1 "$f" | grep -qx -- '---' || err "no opening ---"
  cls="$(grep -m1 '^class: ' "$f" | sed 's/^class: //')"
  [ "$cls" = "$base" ] || err "class '$cls' != filename"
  grep -Eqx 'kind: (pattern|effect|modulator)' "$f" || err "kind"
  grep -qx 'sourceRepo: Apotheneum' "$f" || err "sourceRepo"
  grep -q '^sourcePath: src/main/java/' "$f" || err "sourcePath"
  grep -Eq '^sourceSha256: [0-9a-f]{64}$' "$f" || err "sourceSha256 shape"
  grep -Eq '^classBytesSha256: [0-9a-f]{64}$' "$f" || err "classBytesSha256 shape"
  grep -Eq '^generatedAt: [0-9]{4}-[0-9]{2}-[0-9]{2}T' "$f" || err "generatedAt"
  grep -q '^generator: lx-mcp-catalog/' "$f" || err "generator"
  grep -q '^tags: [a-z]' "$f" || err "tags"
  grep -qx '## Summary' "$f" || err "missing ## Summary"
  grep -qx '## Parameter interactions' "$f" || err "missing ## Parameter interactions"
  grep -qx '## Usage tips' "$f" || err "missing ## Usage tips"

  rel="$(grep -m1 '^sourcePath: ' "$f" | sed 's/^sourcePath: //')"
  if [ -f "$REPO/$rel" ]; then
    actual="$(shasum -a 256 "$REPO/$rel" | awk '{print $1}')"
    rec="$(grep -m1 '^sourceSha256: ' "$f" | sed 's/^sourceSha256: //')"
    [ "$actual" = "$rec" ] || err "sourceSha256 STALE (source changed since generation)"
  else
    err "source file missing: $rel"
  fi
  clsfile="$REPO/target/classes/$(echo "$cls" | tr '.' '/').class"
  if [ -f "$clsfile" ]; then
    actual="$(shasum -a 256 "$clsfile" | awk '{print $1}')"
    rec="$(grep -m1 '^classBytesSha256: ' "$f" | sed 's/^classBytesSha256: //')"
    [ "$actual" = "$rec" ] || err "classBytesSha256 mismatch vs target/classes"
  fi
done

echo "checked $count entries"
if [ "$fail" = 0 ]; then echo "ALL PASS"; else echo "FAILURES PRESENT"; fi
exit "$fail"
