#!/bin/bash
# Provisions a Claude Code on the web container for this repo.
#
# Two things the base image does not carry:
#   * JDK 25. The pom targets Java 21 bytecode, but Chromatik 1.2.2 (lx/glx/glxstudio)
#     ships class-file major version 69, which javac 21 refuses to read off the
#     classpath — every build fails on "class file has wrong version 69.0, should be
#     65.0". CI pins JDK 25 for the same reason. The image ships only JDK 21, and
#     Maven picks that up by default, so JAVA_HOME has to be pointed at 25 explicitly.
#   * ffmpeg. AGENTS.md requires a headless render for any pattern/effect/modulator
#     change, and RenderSpike preflights ffmpeg before it will render the GIFs.
#
# Both come from Ubuntu noble's own archive. That matters: this environment's network
# policy blocks api.adoptium.net, corretto.aws, cdn.azul.com, download.java.net and
# GitHub release assets, so the usual "download a Temurin tarball" provisioning fails
# with a 403. archive.ubuntu.com and repo1.maven.org are reachable.
set -euo pipefail

# Local checkouts are developer machines (mostly macOS) that already have a working
# toolchain — apt would be wrong there. Only provision the remote container.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

JAVA_HOME_25="/usr/lib/jvm/java-25-openjdk-amd64"

missing=()
[ -x "${JAVA_HOME_25}/bin/javac" ] || missing+=(openjdk-25-jdk)
command -v ffmpeg >/dev/null 2>&1 || missing+=(ffmpeg)

if [ ${#missing[@]} -gt 0 ]; then
  echo "Installing: ${missing[*]}"
  export DEBIAN_FRONTEND=noninteractive
  # Third-party PPAs in this image (deadsnakes, ondrej/php) are blocked by the network
  # policy and make apt-get update exit non-zero. Their failure is irrelevant to us —
  # the packages we need are in the Ubuntu archive — so don't let it kill the hook.
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

# Warm ~/.m2 and compile main + tests. The container image is snapshotted after this
# hook completes, so the download cost is paid once here rather than in every session.
cd "${CLAUDE_PROJECT_DIR:-.}"
mvn -q -B -Ptests test-compile

# JAVA_TOOL_OPTIONS makes the JVM print a proxy/truststore banner to stderr; drop it
# so the hook's one line of session context stays readable.
echo "Ready: $(javac -version 2>/dev/null || javac -version 2>&1 | grep -o 'javac .*'), $(ffmpeg -version 2>/dev/null | head -1 | cut -d' ' -f1-3)"
