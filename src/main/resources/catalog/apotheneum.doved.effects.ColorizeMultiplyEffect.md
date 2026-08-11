---
class: apotheneum.doved.effects.ColorizeMultiplyEffect
kind: effect
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/doved/effects/ColorizeMultiplyEffect.java
sourceSha256: 1f9cb6ffb039abb0faea5cd1f8ae1954d5204afe6b6bfe8d955584b6cdf02769
classBytesSha256: 4301ad780c2d2f51ce058f394ef3a07ba6ad11683dd3ad7dbd54a404930139ee
classBytesOrigin: target/classes
lxVersion: 1.2.2
generatedAt: 2026-08-11T00:00:00Z
generator: chromatik-mcp-catalog/2 (codex)
tags: color, gradient, palette, brightness
---

## Summary

ColorizeMultiplyEffect maps incoming brightness or luminosity to a gradient while retaining the source image's form.
- Source value selects a gradient hue and scales that hue's brightness, so exact black remains black even when the first gradient stop is saturated.
- Source alpha is retained by colorization; transparent threshold gating is the intentional exception.
- Fixed, relative, linked, and multi-stop palette gradients use LX's standard RGB and HSV interpolation choices.

## Parameter interactions

- Multiply depth continuously crossfades from ordinary colorization to full source-brightness scaling; exact zero remains black at all depths, the deliberate exception to ordinary colorization at zero.
- Threshold gating does not rescale values above the cutoff, so changing the cutoff does not shift their selected hues.
- The final amount continuously crossfades RGB against the incoming frame after gradient lookup and brightness scaling.

## Usage tips

- Use this after grayscale or monochrome patterns when dark structure must survive a multi-color palette mapping.
- Raise the threshold to suppress faint LED haze; use clear gating when lower channels should show through instead of being covered by black.
- RGB interpolation is the safe default for wide-arc palettes; HSV modes can keep analogous palettes more saturated.
