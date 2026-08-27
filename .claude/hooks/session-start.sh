#!/bin/bash
# Makes a Claude Code on the web container able to build this repo.
#
# Prefer provisioning via the environment's *setup script* (see
# docs/cloud-environment.md) — that runs once and is snapshotted, so later sessions
# start with the toolchain already on disk. This hook runs after Claude Code launches,
# on every session including resumes, and its writes are NOT in that snapshot. So it
# is written to be a fast no-op when the setup script has already done the work, and
# to still self-heal an environment that has no setup script configured.
#
# What the base image is missing:
#   * JDK 25. The pom targets Java 21 bytecode, but Chromatik 1.2.2 (lx/glx/glxstudio)
#     ships class-file major version 69, which javac 21 refuses to read off the
#     classpath — every build fails on "class file has wrong version 69.0, should be
#     65.0". CI pins JDK 25 for the same reason. The image ships only JDK 21, and
#     Maven picks that up by default, so JAVA_HOME must point at 25 explicitly.
#   * ffmpeg. AGENTS.md requires a headless render for any pattern/effect/modulator
#     change, and RenderSpike preflights ffmpeg before it will render.
#
# Both come from Ubuntu noble's own archive, which matters: this environment's network
# policy blocks api.adoptium.net, corretto.aws, cdn.azul.com, download.java.net and
# GitHub release assets, so the usual "download a Temurin tarball" provisioning 403s.
# archive.ubuntu.com and repo1.maven.org are reachable.
set -euo pipefail

# Local checkouts are developer machines (mostly macOS) with a working toolchain
# already; apt would be wrong there. Only provision the remote container.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

JAVA_HOME_25="/usr/lib/jvm/java-25-openjdk-amd64"

missing=()
[ -x "${JAVA_HOME_25}/bin/javac" ] || missing+=(openjdk-25-jdk)
command -v ffmpeg >/dev/null 2>&1 || missing+=(ffmpeg)

if [ ${#missing[@]} -gt 0 ]; then
  echo "Installing ${missing[*]} (not in the environment snapshot — consider moving this"
  echo "into the environment's setup script; see docs/cloud-environment.md)"
  export DEBIAN_FRONTEND=noninteractive
  # Third-party PPAs in this image (deadsnakes, ondrej/php) are blocked by the network
  # policy and make apt-get update exit non-zero. Their failure is irrelevant — the
  # packages we need are in the Ubuntu archive — so don't let it kill the hook.
  apt-get update -qq || echo "apt-get update reported errors (blocked PPAs); continuing"
  apt-get install -y -qq "${missing[@]}"
fi

# Maven honours JAVA_HOME; without this it resolves to the image's JDK 21.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export JAVA_HOME=\"${JAVA_HOME_25}\""
    echo "export PATH=\"${JAVA_HOME_25}/bin:\$PATH\""
  } >> "$CLAUDE_ENV_FILE"
fi

export JAVA_HOME="${JAVA_HOME_25}"
export PATH="${JAVA_HOME_25}/bin:$PATH"

# Warm ~/.m2 only when Chromatik is genuinely absent. This hook fires on resume and
# compact too, and a full test-compile on every one of those would cost minutes for
# nothing. When the setup script has run, the snapshot already holds these artifacts.
if [ ! -d "${HOME}/.m2/repository/com/heronarts" ]; then
  echo "Warming ~/.m2 (first run in this container)"
  cd "${CLAUDE_PROJECT_DIR:-.}"
  mvn -q -B -Ptests test-compile
fi

# JAVA_TOOL_OPTIONS makes the JVM print a proxy/truststore banner to stderr; drop it
# so the hook's one line of session context stays readable.
echo "Ready: $(javac -version 2>/dev/null || true), $(ffmpeg -version 2>/dev/null | head -1 | cut -d' ' -f1-3)"
