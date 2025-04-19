/**
 * Quasicrystal Tiling Generator
 * This script iterates across tiling symmetries from 1-fold to 10-fold
 * Each pattern is generated dynamically based on the selected symmetry.
 * @author Theresa Silver
 */

knob("symmetry", "Symmetry", "Choose tiling symmetry (1 to 10)", 0.5);
knob("scale", "Scale", "Scaling factor for the tiling pattern", 0.5);
knob("rotation", "Rotation", "Rotation angle", 0.5);
knob("hue", "Hue", "Hue to render", 0.5);
knob("sat", "Saturation", "Saturation to render", 0.5);
knob("brt", "Brightness", "Brightness to render", 0.5);

toggle("fade", "Fade", "Fade brightness from center", true);

toggle("quasiperiodic", "Quasiperiodic", "Enable quasiperiodic tiling", true);

/**
 * Generate a quasicrystal tiling pattern with variable-fold symmetry
 * @param {LXPoint} point - The point to render
 * @param {number} deltaMs - Milliseconds elapsed since previous frame
 * @return {number} Color value returned from an LXColor method like hsb/rgb
 */
function renderPoint(point, deltaMs) {
    var angle = rotation * Math.PI * 2;
    var effectiveScale = 0.2 + scale * 1.5;
    var symmetryFactor = Math.round(1 + symmetry * 9); // Map knob to range 1-10
    
    var px = ((point.xn * symmetryFactor) % 1 - 0.5) * effectiveScale;
    var py = ((point.yn * symmetryFactor) % 1 - 0.5) * effectiveScale;
    
    var q = (2/3 * px);
    var r = (-1/3 * px + Math.sqrt(3)/3 * py);
    var s = -q - r;
    
    var tileValue = Math.cos(symmetryFactor * Math.atan2(s, q) + angle) + Math.cos(symmetryFactor * Math.atan2(r, s) + angle);
    var level = brt * (quasiperiodic ? Math.abs(tileValue) : 1);
    
    if (fade) {
        level *= clamp(1 - LXUtils.dist(0.5, 0.5, point.xn, point.yn), 0, 1);
    }
    
    return hsb(hue * 360, sat * 100, level * 100);
}
