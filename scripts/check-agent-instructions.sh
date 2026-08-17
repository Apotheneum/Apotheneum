#!/usr/bin/env bash
#
# Checks the agent instruction files.
#
# AGENTS.md is canonical — Codex, Cursor and most other agents read it. Claude
# Code reads CLAUDE.md instead, so CLAUDE.md is a one-line `@AGENTS.md` import;
# Claude Code expands it into context at launch, so there is one copy of the
# text and nothing to sync. https://code.claude.com/docs/en/memory#agents-md
#
# A symlink would also work, but needs Administrator or Developer Mode on
# Windows, so the import is the portable choice.
#
# Run from Git Bash on Windows — needs a POSIX shell plus grep/sed/wc.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
AGENTS_FILE="$REPO_ROOT/AGENTS.md"
CLAUDE_FILE="$REPO_ROOT/CLAUDE.md"

# Instruction files are prepended to every session's context. Past this size the
# cost outweighs the guidance; move detail into docs/ and link to it instead.
# The docs recommend staying under 200 lines for adherence, not just size.
MAX_BYTES=32768
MAX_LINES=200

if [[ $# -ne 0 ]]; then
  echo "usage: $0" >&2
  exit 2
fi

if [[ ! -s "$AGENTS_FILE" ]]; then
  echo "FAIL: AGENTS.md is missing or empty" >&2
  exit 1
fi

if [[ ! -s "$CLAUDE_FILE" ]]; then
  echo "FAIL: CLAUDE.md is missing or empty — Claude Code reads it, not AGENTS.md." >&2
  echo "      It should contain a single line: @AGENTS.md" >&2
  exit 1
fi

# The import only works unquoted: inside backticks or a fenced block, Claude Code
# treats `@AGENTS.md` as literal text and silently loads nothing. Strip code spans
# and fenced blocks before looking, so a documentation mention can't satisfy this.
claude_md_live=$(
  awk '
    /^[[:space:]]*```/ { fenced = !fenced; next }
    !fenced { gsub(/`[^`]*`/, ""); print }
  ' "$CLAUDE_FILE"
)

if ! grep -qE '(^|[[:space:]])@AGENTS\.md([[:space:]]|$)' <<<"$claude_md_live"; then
  echo "FAIL: CLAUDE.md does not import AGENTS.md outside of a code block." >&2
  echo "      Add an unquoted '@AGENTS.md' line — inside backticks it loads nothing." >&2
  exit 1
fi

agents_bytes=$(wc -c < "$AGENTS_FILE" | tr -d ' ')
if (( agents_bytes > MAX_BYTES )); then
  echo "FAIL: AGENTS.md is ${agents_bytes} bytes; limit is ${MAX_BYTES}" >&2
  exit 1
fi

agents_lines=$(wc -l < "$AGENTS_FILE" | tr -d ' ')
if (( agents_lines > MAX_LINES )); then
  echo "WARN: AGENTS.md is ${agents_lines} lines; adherence drops past ${MAX_LINES}." >&2
  echo "      Consider moving detail into docs/ or a path-scoped .claude/rules/ file." >&2
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
  #
  # A destination may carry an optional title — [text](file.md "Title") — and may
  # be wrapped in angle brackets to allow spaces. Both have to come off before the
  # filesystem check, or a perfectly valid link fails CI for a file that exists.
  #
  # A title is only stripped when its quote is properly closed. An unterminated
  # one — [text](file.md "oops) — is not a link at all in Markdown; it renders as
  # literal text. Stripping it anyway would check the bare path, find it, and pass
  # a link that doesn't exist. Left intact, the path check fails and reports it.
  # Parenthesized titles are deliberately unhandled: they're rare, and the capture
  # below ends at the first ")" so the text can't be told apart from a real path.
done < <(
  {
    grep -oE '\]\([^)]+\)' "$AGENTS_FILE" | sed -e 's/^](//' -e 's/)$//'
    grep -oE '^\[[^]]+\]:[[:space:]]*[^[:space:]]+' "$AGENTS_FILE" | sed -e 's/^\[[^]]*\]:[[:space:]]*//'
  } | sed -E \
        -e 's/^[[:space:]]+//' \
        -e 's/[[:space:]]+$//' \
        -e 's/^([^[:space:]]+)[[:space:]]+"[^"]*"$/\1/' \
        -e "s/^([^[:space:]]+)[[:space:]]+'[^']*'\$/\1/" \
        -e 's/^<(.*)>$/\1/' \
    || true
)

if (( broken )); then
  exit 1
fi

echo "agent instructions OK — AGENTS.md ${agents_bytes} bytes / ${agents_lines} lines, CLAUDE.md imports it, links resolve"
