# Running this repo in a Claude Code cloud session

The cloud container cannot build Apotheneum out of the box. Its base image ships
**only JDK 21**, and Chromatik 1.2.2 (`lx`/`glx`/`glxstudio`) ships class-file major
version 69, which javac 21 refuses to read off the classpath. Every build fails with:

```
class file has wrong version 69.0, should be 65.0
```

**ffmpeg** is missing too, and `RenderSpike` preflights it before rendering — so the
render that [AGENTS.md](../AGENTS.md) requires for any pattern change cannot run either.

## What you have to configure

| | Where it lives | Picked up automatically? |
| --- | --- | --- |
| `.claude/hooks/session-start.sh` | This repo | **Yes**, once it is on the branch the session clones |
| Setup script | The environment dialog at [claude.ai/code](https://claude.ai/code) | **No** — you paste it in once |

The hook alone is enough to make sessions work. The setup script is what makes them
start *fast*, and it is worth the one-time paste.

### The hook: automatic, but not cached

A cloud session clones the repo and runs the SessionStart hooks it finds there. Nothing
to configure. The catch is *when* it runs: after Claude Code launches, on every session
including resumes — and **after** the filesystem snapshot is taken. So anything it
installs is reinstalled next session.

It only takes effect on branches that contain it. A session started against `main`
before this is merged will not have it.

### The setup script: cached, but manual

A setup script runs before Claude Code launches, and the container is snapshotted once
it finishes. Later sessions start from that snapshot with the toolchain already on disk
and skip the script entirely. Anthropic's guidance draws the line exactly here:

> Use a setup script to provision the VM itself: toolchains and CLI tools that aren't
> pre-installed. Use a SessionStart hook for project setup that should run everywhere,
> cloud and local.

Installing a JDK and ffmpeg is provisioning the VM. Open the environment settings at
[claude.ai/code](https://claude.ai/code) and paste this into **Setup script**:

```bash
#!/bin/bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
# Blocked third-party PPAs in the base image (deadsnakes, ondrej/php) make update
# exit non-zero; the packages below come from the Ubuntu archive, so continue anyway.
apt-get update -qq || true
apt-get install -y -qq openjdk-25-jdk ffmpeg
```

Then add this under **Environment variables**, so Maven stops resolving to JDK 21:

```
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
```

With both in place the hook detects the toolchain is present and becomes a fast no-op.

## Why apt, and not a Temurin download

The usual provisioning move — download a Temurin tarball — does not work here. The
environment's network policy blocks the JDK vendors outright:

| Host | Result |
| --- | --- |
| `api.adoptium.net`, `corretto.aws`, `cdn.azul.com`, `download.java.net` | blocked |
| GitHub release assets (`objects.githubusercontent.com`) | 403 |
| `archive.ubuntu.com` | reachable |
| `repo1.maven.org` | reachable |

Ubuntu noble packages `openjdk-25-jdk`, so apt is both the working route and the simple
one. Raising the environment's network access level would also unblock Temurin, but
there is no reason to widen the policy for something the distro already ships.

## Verifying a container is set up

```bash
javac -version              # javac 25.x — not 21
ffmpeg -version             # any 6.x
mvn -B -Ptests package      # 166 tests, BUILD SUCCESS
```

`mvn -v` reporting Java 21 means `JAVA_HOME` is not set; everything will fail to compile.
