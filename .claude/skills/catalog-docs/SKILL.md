---
name: catalog-docs
description: Regenerate or extend the semantic catalog entries in src/main/resources/catalog/ (one hash-keyed markdown doc per pattern/effect, served to AI clients via lx-mcp's get_component_doc). Use after pattern/effect code changes, when adding new classes, or when an entry is reported stale.
---

# Apotheneum catalog docs

Each concrete pattern/effect class gets one markdown entry at
`src/main/resources/catalog/<fully.qualified.ClassName>.md`. Entries ship inside the
Apotheneum jar as plain Maven resources; any MCP client connected through
[lx-mcp](https://github.com/oveddan/lx-mcp) resolves them via the class's own
classloader (`get_component_doc`), with bytecode hashes for staleness detection.

The format contract is owned by lx-mcp
([`docs/catalog-format.md`](https://github.com/oveddan/lx-mcp/blob/main/docs/catalog-format.md))
— read it if available; the essentials are inlined below so this skill is
self-sufficient.

**Entries are generated, never hand-maintained.** They are a cache of source
understanding keyed by `sourceSha256`: re-running generation is cheap, and unchanged
classes are skipped. Hand edits get silently clobbered by the next run.

## Entry format (v2)

Frontmatter — flat `key: value` scalars only, `---` delimited:

```
class: apotheneum.mcslee.Raindrops
kind: pattern                     # pattern | effect | modulator
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Raindrops.java
sourceSha256: <64 hex>            # shasum -a 256 of the .java at generation time
classBytesSha256: <64 hex>        # shasum -a 256 of target/classes/.../Raindrops.class
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: <ISO-8601 instant>
generator: lx-mcp-catalog/2 (<model>)
tags: <behavior vocabulary, lowercase, comma-separated>
```

Body — EXACTLY three headings, in order: `## Summary`, `## Parameter interactions`,
`## Usage tips` (the runtime splits on them; every entry needs all three, even minimal
ones). Inside each: at most one topic sentence, then `- ` bullets, one verifiable
claim per bullet.

Content rules (the ones generators actually get wrong):

- **No parameter/method/class code identifiers** — role prose only ("the spawn rate",
  not a backticked field name). Backticks are allowed solely for one optional fenced
  pseudocode mechanism sketch (role names inside, never source identifiers).
- **Claim filter**: every bullet either helps an agent *choose* the class or prevents
  a concrete *misuse*. Implementation narration that doesn't change what an operator
  does is cut.
- **Live vs latched**: per behavior-shaping control, say whether it acts continuously
  (responds to live modulation) or is sampled at trigger/spawn — the key fact for
  modulation-routing agents.
- **Budget**: Summary ≤ ~100 words, body ≤ ~250. Minimal 3-bullet entries are valid
  (diagnostics, examples, value-holders) — but still need all three headings.
- **Verify every mechanism claim against source.** No hedging ("presumably",
  "appears to") — verify or omit. Never write an entry for a class whose source was
  not read. A class with no source gets NO entry (undocumented is the honest state).

## Pipeline

1. **Worktree.** `git worktree add <scratch>/apotheneum-catalog -b <branch>` — never
   generate in the main checkout.
2. **Class list.** Concrete (non-abstract) subclasses of `LXPattern`/`LXEffect` under
   `src/main/java/`, excluding anything annotated `@LXComponent.Hidden`. Prefer the
   live registry when Chromatik is running (port in `~/.lx-mcp/status.json`,
   `list_available_patterns` / `_effects`).
3. **Incremental gate.** `shasum -a 256` each source file; an existing entry whose
   `sourceSha256` matches is fresh — skip it. Only changed/new classes regenerate.
4. **Class bytes.** `mvn compile` in the worktree, then hash each
   `target/classes/.../<Name>.class` for `classBytesSha256`.
5. **Generate.** Sonnet subagents, ~8 classes per batch. Each agent reads the class
   source in full, plus base classes (`ApotheneumPattern`, `ApotheneumRasterPattern`,
   `ApotheneumEffect`, `mcslee/Bursts`) and any delegated helpers (e.g.
   `doved/lightning/*`), then writes the entry per the rules above. Front-load the
   full rule list into each agent prompt.
6. **Mechanical checks.** Run `scripts/validate-catalog.sh` (frontmatter keys,
   class==filename, hash shapes recomputed against source and class bytes, exact
   headings) and grep the bodies for backticks outside fenced blocks — generators
   leak backticked identifiers reliably; expect to strip some.
7. **Adversarial review — required, not optional.** Spawn an independent review agent
   that reads each entry AND the full class source and actively tries to refute every
   mechanism claim (wrong geometry target, wrong transform order, inverted
   live-vs-latched, controls that are registered but never read). Also have it sweep
   all entries for rule violations. This step has caught confidently-wrong claims in
   every generation round so far — inverted push/brake scope (Gravity), spawn-time
   vs live retuning (Crawlers), an inert reflection-mode control described as
   functional (Symmetry), controls attributed to the wrong algorithm (Lightning).
   A confidently wrong mechanism is worse than no entry.
8. **Fix via regeneration.** Feed the reviewer's findings (with the source evidence)
   to a fix agent that rewrites the affected bodies against source — never hand-edit
   content yourself. Frontmatter hashes stay untouched unless the source changed.
   Re-run step 6 after fixes; re-review if any fix was judgment-heavy.
9. **Ship.** `mvn package` must succeed and bundle every entry into the jar
   (`unzip -l target/*.jar | grep -c 'catalog/apotheneum'`). Single squashed commit;
   PR body reports counts: generated / skipped-fresh / no-source, plus any upstream
   code flags the review surfaced (inert parameters, sign-inverted comments — file
   them, they are free code review).

## Never

- Hand-edit an entry body to "fix" it — fix generation and re-run.
- Record parameter names, ranges, defaults, or bare option lists — the live MCP tools
  own structure; describing what a mode *does* is in scope.
- Write or keep an entry whose claims were not verified against the current source.
