#!/usr/bin/env bash
#
# Keeps the agent instruction files consistent.
#
# AGENTS.md is canonical (Codex, Cursor, and most other agents read it). CLAUDE.md
# is a byte-identical mirror for clients that only load CLAUDE.md. A mirror rather
# than a pointer file, because a pointer costs every session an extra read and is
# silently skippable.
#
#   check-agent-instructions.sh          verify (used by CI)
#   check-agent-instructions.sh --sync   copy AGENTS.md over CLAUDE.md, then verify
#
# On Windows, run this from Git Bash (bundled with Git for Windows) — it needs a
# POSIX shell plus cmp/wc/grep/sed. If that is inconvenient, the sync is just a
# file copy, so `copy AGENTS.md CLAUDE.md` in cmd or `Copy-Item AGENTS.md
# CLAUDE.md` in PowerShell is equivalent; CI runs the real check either way.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
AGENTS_FILE="$REPO_ROOT/AGENTS.md"
CLAUDE_FILE="$REPO_ROOT/CLAUDE.md"

# Instruction files are prepended to every session's context. Past this size the
# cost outweighs the guidance; move detail into docs/ and link to it instead.
MAX_BYTES=32768

if [[ "${1:-}" == "--sync" ]]; then
  cp "$AGENTS_FILE" "$CLAUDE_FILE"
elif [[ $# -ne 0 ]]; then
  echo "usage: $0 [--sync]" >&2
  exit 2
fi

if [[ ! -s "$AGENTS_FILE" ]]; then
  echo "FAIL: AGENTS.md is missing or empty" >&2
  exit 1
fi

if [[ ! -s "$CLAUDE_FILE" ]]; then
  echo "FAIL: CLAUDE.md mirror is missing or empty — run $0 --sync" >&2
  exit 1
fi

if ! cmp -s "$AGENTS_FILE" "$CLAUDE_FILE"; then
  echo "FAIL: CLAUDE.md differs from canonical AGENTS.md — run $0 --sync" >&2
  exit 1
fi

agents_bytes=$(wc -c < "$AGENTS_FILE" | tr -d ' ')
if (( agents_bytes > MAX_BYTES )); then
  echo "FAIL: AGENTS.md is ${agents_bytes} bytes; limit is ${MAX_BYTES}" >&2
  exit 1
fi

# A broken link in an instruction file sends every agent that follows it on a
# detour, so treat it as a build failure rather than a doc nit.
broken=0
while IFS= read -r target; do
  target=${target%%#*}
  [[ -z "$target" ]] && continue
  case "$target" in
    *://* | mailto:* | /*) continue ;;
  esac
  # Markdown links may percent-encode spaces; decode before hitting the filesystem.
  decoded=$(printf '%b' "${target//%/\\x}")
  if [[ ! -e "$REPO_ROOT/$decoded" ]]; then
    echo "FAIL: AGENTS.md has a broken relative link: $target" >&2
    broken=1
  fi
  # Both link forms Markdown allows: inline `](target)` and reference definitions
  # `[label]: target`. Checking only the inline form would let a reference-style
  # link point at a missing file and still pass.
done < <(
  {
    grep -oE '\]\([^)]+\)' "$AGENTS_FILE" | sed -e 's/^](//' -e 's/)$//'
    grep -oE '^\[[^]]+\]:[[:space:]]*[^[:space:]]+' "$AGENTS_FILE" | sed -e 's/^\[[^]]*\]:[[:space:]]*//'
  } || true
)

if (( broken )); then
  exit 1
fi

echo "agent instructions OK — ${agents_bytes} bytes, mirror matches, links resolve"
