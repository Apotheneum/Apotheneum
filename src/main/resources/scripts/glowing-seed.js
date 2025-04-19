
/**
 * Glowing Seed (Relative Normalization + Orientation Control)
 * Works across cube faces with manual rotation knob
 * @author Theresa Silver
 */

knob("swirlFreq", "Swirl Frequency", "Swirl density", 0.5);
knob("swirlSize", "Swirl Size", "Swirl radius scaling", 0.5);
knob("swirlTwist", "Swirl Twist", "Phase offset", 0.5);
knob("swirlOrient", "Orientation", "Manual swirl rotation", 0.0);
knob("swirlHue", "Hue", "Hue (0 = red, 1 = violet)", 0.1);
knob("swirlBrt", "Brightness", "Brightness", 0.5);
knob("swirlSharp", "Sharpness", "Contrast", 0.5);

function renderPoint(point, deltaMs) {
    // Use normalized view coordinates (Requires Relative Normalization)
    var dx = point.xn - 0.5;
    var dy = point.yn - 0.5;

    // Orientation offset
    var rot = swirlOrient * Math.PI * 2;
    var cosR = Math.cos(rot);
    var sinR = Math.sin(rot);

    // Rotate coords manually
    var xRot = dx * cosR - dy * sinR;
    var yRot = dx * sinR + dy * cosR;

    // Polar conversion
    var angle = Math.atan2(yRot, xRot);
    var radius = Math.sqrt(xRot * xRot + yRot * yRot);

    var folds = 1 + swirlFreq * 12;
    var twist = swirlTwist * Math.PI * 2;
    var scale = 1 + swirlSize * 6;

    var wave = Math.sin(folds * angle + radius * scale + twist);
    var contrast = Math.pow(Math.abs(wave), 1 + swirlSharp * 4);

    var brightness = Math.min(1.0, swirlBrt * contrast);
    var hue = (swirlHue * 360) % 360;

    return hsb(hue, 90, brightness * 100);
}
