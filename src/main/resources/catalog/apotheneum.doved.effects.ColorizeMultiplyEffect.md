---
class: apotheneum.doved.effects.ColorizeMultiplyEffect
kind: effect
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/doved/effects/ColorizeMultiplyEffect.java
sourceSha256: 73da55629ef3074cfbbcb7f67f4aee94df211baa6eb550c25943e897d3244566
classBytesSha256: b138e30d6ea1979e7b36f450fb3924e470d9590a74ad9a1439905c44e9cd7c76
classBytesOrigin: target/classes
lxVersion: 1.2.2
generatedAt: 2026-08-11T18:59:05Z
generator: chromatik-mcp-catalog/2 (codex)
tags: color, gradient, palette, brightness
---

## Summary

ColorizeMultiplyEffect maps incoming brightness or luminosity to a gradient while retaining the source image's form.
- Source value selects a gradient hue and scales that hue's brightness, so exact black remains black even when the first gradient stop is saturated.
- Source alpha is retained by colorization and black threshold gating; clear threshold gating is the intentional transparent exception.
- Fixed, relative, linked, and multi-stop palette gradients use LX's standard RGB and HSV interpolation choices.

## Parameter interactions

- Multiply depth continuously crossfades from ordinary colorization RGB to full source-brightness scaling; exact zero remains black at all depths, while nonzero depth-zero output matches ordinary colorization only in RGB because source alpha is retained.
- Threshold gating does not rescale values above the cutoff, so changing the cutoff does not shift their selected hues.
- The final amount continuously crossfades RGB against the incoming frame after gradient lookup and brightness scaling.

## Usage tips

- Use this after grayscale or monochrome patterns when dark structure must survive a multi-color palette mapping.
- Raise the threshold to suppress faint LED haze; use clear gating when lower channels should show through instead of being covered by black.
- RGB interpolation is the safe default for wide-arc palettes; HSV modes can keep analogous palettes more saturated.
- The gradient preview shows the undimmed lookup color; rendered output additionally applies multiply depth.
