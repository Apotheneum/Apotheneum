/**
 * Moiré Pattern Generator (Chromatik Relative Normalization Edition)
 * This script generates dynamic moiré patterns inspired by Carsten Nicolai's
 * 'The Moiré Index'. Moiré patterns emerge from overlapping grids, producing
 * interference effects.
 * @author Theresa Silver
 */

knob("frequency", "Frequency", "Base frequency of the moiré pattern", 0.5);
knob("angle",    "Angle",    "Rotation angle of the overlaid grid",    0.5);
knob("contrast", "Contrast", "Contrast intensity of the pattern",      0.5);
knob("hue",      "Hue",      "Hue to render",                          0.5);
knob("sat",      "Saturation","Saturation to render",                  0.5);
knob("brt",      "Brightness","Brightness to render",                  0.5);

toggle("animate", "Animate", "Enable animation of the moiré effect", true);

/**
 * Generate a dynamic moiré pattern with overlaying grid structures.
 * @param {LXPoint} point  - The point to render (uses point.xn, point.yn normalized coords).
 * @param {number}  deltaMs - Milliseconds elapsed since the previous frame.
 * @return {number} Color value from an LXColor method like hsb().
 */
function renderPoint(point, deltaMs) {
    var baseFreq = 5 + frequency * 20;        // Base frequency of patterns (range ~5 to 25)
    var rotation = angle * Math.PI * 2;       // Rotation angle for secondary grid (0 to 2π)
    
    // Primary grid layer (uses normalized X and Y coordinates)
    var gx1 = Math.sin(point.xn * baseFreq);
    var gy1 = Math.sin(point.yn * baseFreq);
    
    // Secondary grid layer (offset by a small amount and rotated by the angle knob)
    var offset = 0.1;
    var gx2 = Math.sin((point.xn + offset) * baseFreq * Math.cos(rotation));
    var gy2 = Math.sin((point.yn + offset) * baseFreq * Math.sin(rotation));
    
    // Compute moiré interference by combining the two grid signals
    var moire = Math.abs(gx1 * gy1 - gx2 * gy2);
    
    // Apply contrast and brightness adjustments to the moiré intensity
    var level = brt * Math.pow(moire, contrast * 2);
    
    // Return the color for this point using HSV (hue, saturation, brightness)
    return hsb(hue * 360, sat * 100, level * 100);
}
