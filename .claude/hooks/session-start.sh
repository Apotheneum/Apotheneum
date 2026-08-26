#!/usr/bin/env bash
#
# SessionStart hook — installs the two things a Claude Code on the web container
# lacks and a working laptop already has.
#
# A cloud session starts from a bare Ubuntu image with JDK 21 and no ffmpeg.
# Neither missing piece is a Maven dependency, so nothing in the build can pull
# them in, and both are hard requirements here:
#
#   * JDK 25 — Chromatik 1.2.2 (lx/glx/glxstudio) ships class-file major version
#     69, which javac 21 refuses to read off the classpath, so every build fails
#     on 21. .github/workflows/build.yml pins 25 for the same reason. The pom
#     still sets maven.compiler.release 21, so this changes neither the language
#     level nor the bytecode we emit.
#   * ffmpeg — docs/headless-rendering.md assembles one GIF per surface with it.
#     RenderSpike checks for it before loading LX and fails immediately when it
#     is absent, so without it a pattern change cannot be rendered — and a
#     pattern change is not done until it has been rendered.
#
# Local sessions exit at the guard below. This is apt, most contributors are on
# macOS, and a laptop that can build this repo already has a usable JDK.
#
# Not installed here: the package jar. `mvn -Pinstall install` writes one shared
# path under ~/Chromatik/Packages that every worktree and the live rig share, and
# headless work never needs it. See AGENTS.md.
#
# Output is deliberately three lines at most. A synchronous SessionStart hook's
# stdout is prepended to the session as context, so apt's progress meters and
# dpkg's alternatives chatter would be paid for in tokens on every session. They
# go to a log that is printed only when something fails.

set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

JDK_PACKAGE="openjdk-25-jdk-headless"
JDK_HOME="/usr/lib/jvm/java-25-openjdk-amd64"

SUDO=""
if [ "$(id -u)" -ne 0 ]; then
  SUDO="sudo"
fi

LOG="$(mktemp -t session-start-XXXXXX.log)"
trap 'rm -f "$LOG"' EXIT

# Idempotent: the hook also fires on resume, clear and compact, and reinstalling
# a JDK on every one of those would make each of them wait on apt for nothing.
missing=()
command -v ffmpeg >/dev/null 2>&1 || missing+=("ffmpeg")
[ -x "$JDK_HOME/bin/javac" ] || missing+=("$JDK_PACKAGE")

if [ "${#missing[@]}" -gt 0 ]; then
  echo "session-start: installing ${missing[*]}"
  # `apt-get update` is not optional. The image ships an apt index old enough
  # that packages in ffmpeg's dependency chain have been superseded, and the
  # install 404s on them without a refresh first.
  if ! {
    $SUDO apt-get update -qq &&
      $SUDO env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "${missing[@]}"
  } >"$LOG" 2>&1; then
    echo "session-start: apt failed to install ${missing[*]}" >&2
    cat "$LOG" >&2
    exit 1
  fi
fi

if [ ! -x "$JDK_HOME/bin/javac" ]; then
  echo "session-start: expected a JDK at $JDK_HOME after installing $JDK_PACKAGE" >&2
  exit 1
fi

# Maven picks its JDK from JAVA_HOME, and so does the JVM that exec:exec forks to
# render. Without this the session still has 21 on PATH and every build fails on
# the class-file version above.
if [ -n "${CLAUDE_ENV_FILE:-}" ] && ! grep -q "JAVA_HOME=$JDK_HOME" "$CLAUDE_ENV_FILE" 2>/dev/null; then
  {
    echo "export JAVA_HOME=$JDK_HOME"
    echo "export PATH=$JDK_HOME/bin:\$PATH"
  } >> "$CLAUDE_ENV_FILE"
fi

# Warm the Maven cache so the first build of the session doesn't spend a minute
# resolving lx/glx/glxstudio and JUnit from Central. `-Ptests` because test
# compilation is skipped by default and RenderSpike is test scope — a warm cache
# that omits it leaves the renderer to compile on first use. Advisory: a cold
# cache costs time, not correctness, and a transient Central failure should not
# take the session down with it.
if JAVA_HOME="$JDK_HOME" mvn -B -q -Ptests test-compile >"$LOG" 2>&1; then
  echo "session-start: Maven cache warm, test sources compiled"
else
  echo "session-start: Maven warm-up failed; the build will resolve dependencies on first use" >&2
  tail -20 "$LOG" >&2
fi

# `java -version` writes to stderr and this environment prepends a JAVA_TOOL_OPTIONS
# notice to it, so match the version line rather than taking the first one.
jdk_version="$("$JDK_HOME/bin/java" -version 2>&1 | grep -m1 'version "' | cut -d'"' -f2)"
echo "session-start: ffmpeg $(ffmpeg -version | head -1 | cut -d' ' -f3), JDK $jdk_version at JAVA_HOME=$JDK_HOME"
