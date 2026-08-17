@AGENTS.md

<!--
  Claude Code reads CLAUDE.md, not AGENTS.md, so this file exists purely to pull
  in the canonical instructions. The @-import is expanded into context at launch,
  so Claude gets the full text of AGENTS.md — this is not a "go read that file"
  pointer. Keep the import on the first line and unquoted; inside backticks or a
  code fence it is treated as literal text and nothing loads.

  https://code.claude.com/docs/en/memory#agents-md

  Claude-specific instructions, if we ever need any, go below the import — they
  are appended after the imported content. Anything that applies to every agent
  belongs in AGENTS.md instead.
-->
