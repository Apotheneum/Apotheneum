
/**
 * Ignition Spiral (with Saturation Control)
 * Flame-inspired swirl with radial band and full saturation control
 * @author Theresa Silver
 */

knob("spiralFreq", "Spiral Frequency", "Number of spiral arms", 0.4);
knob("spiralFlare", "Spiral Flare", "Asymmetric flare warp", 0.3);
knob("spiralSpin", "Spin Offset", "Rotational offset", 0.1);
knob("spiralBand", "Ignition Band", "Distance band flare", 0.5);
knob("spiralHue", "Hue", "Color warmth (0 = red, 1 = yellow)", 0.2);
knob("spiralSat", "Saturation", "Color saturation (0 = white, 1 = full color)", 1.0);
knob("spiralBrt", "Brightness", "Brightness", 0.6);
knob("spiralSharp", "Sharpness", "Contrast of spiral", 0.5);

function renderPoint(point, deltaMs) {
    var dx = point.xn - 0.5;
    var dy = point.yn - 0.5;

    var angle = Math.atan2(dy, dx);
    var radius = Math.sqrt(dx * dx + dy * dy);

    var flare = 1 + spiralFlare * 3.0;
    var warpedRadius = Math.pow(radius, flare);

    var folds = 2 + spiralFreq * 10;
    var spin = spiralSpin * Math.PI * 2;
    var band = Math.sin((radius - spiralBand) * 20.0);

    var wave = Math.sin(folds * (angle + spin) + warpedRadius * 8) + 0.5 * band;
    var contrast = Math.pow(Math.abs(wave), 1 + spiralSharp * 4);

    var brightness = Math.min(1.0, spiralBrt * contrast);
    var hue = 10 + spiralHue * 40;
    var saturation = spiralSat * 100;

    return hsb(hue % 360, saturation, brightness * 100);
}
