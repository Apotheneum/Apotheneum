package scripts;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;

public class QuasicrystalPattern extends ApotheneumPattern {
    
    public static final String NAME = "Quasicrystal Pattern";
    
    public QuasicrystalPattern(LX lx) {
        super(lx);
        addParameter("mirrorNSFaces", new heronarts.lx.parameter.BooleanParameter("Mirror North/South Faces", false));
        addParameter("symmetry", new heronarts.lx.parameter.CompoundParameter("Symmetry", 0.5));
        addParameter("scale", new heronarts.lx.parameter.CompoundParameter("Scale", 0.5));
        addParameter("rotation", new heronarts.lx.parameter.CompoundParameter("Rotation", 0.5));
        addParameter("hue1", new heronarts.lx.parameter.CompoundParameter("Hue A", 0.6));
        addParameter("hue2", new heronarts.lx.parameter.CompoundParameter("Hue B", 0.3));
        addParameter("hue3", new heronarts.lx.parameter.CompoundParameter("Hue C", 0.1));
        addParameter("sat", new heronarts.lx.parameter.CompoundParameter("Saturation", 0.85));
        addParameter("brt", new heronarts.lx.parameter.CompoundParameter("Brightness", 1.25));
        addParameter("contrast", new heronarts.lx.parameter.CompoundParameter("Contrast", 1.1));
    }
    
    @Override
    protected void render(double deltaMs) {
        boolean mirrorNS = getBooleanParameter("mirrorNSFaces").getValueb();
        double symmetryFactor = Math.round(1 + getParameter("symmetry").getValue() * 6);
        double effectiveScale = 0.2 + getParameter("scale").getValue() * 1.5;
        double angle = getParameter("rotation").getValue() * Math.PI * 2;
        double hue1 = getParameter("hue1").getValue();
        double hue2 = getParameter("hue2").getValue();
        double hue3 = getParameter("hue3").getValue();
        double sat = getParameter("sat").getValue();
        double brt = getParameter("brt").getValue();
        double contrast = getParameter("contrast").getValue();
        
        for (LXPoint p : Apotheneum.cube.front.points) {
            double u = p.xn;
            double v = p.yn;
            
            double px = ((u * symmetryFactor * effectiveScale) % 1.0 - 0.5);
            double py = ((v * symmetryFactor * effectiveScale) % 1.0 - 0.5);
            
            double q = (2.0 / 3.0) * px;
            double r = (-1.0 / 3.0) * px + (Math.sqrt(3) / 3.0) * py;
            double s = -q - r;
            
            double tileValue = Math.cos(symmetryFactor * Math.atan2(s, q) + angle)
            + Math.cos(symmetryFactor * Math.atan2(r, s) + angle);
            
            double blend = 0.5 + 0.5 * Math.sin(tileValue * Math.PI);
            double hue = (blend < 0.5)
            ? (hue1 * (1 - 2 * blend) + hue2 * (2 * blend))
            : (hue2 * (2 - 2 * blend) + hue3 * (2 * blend - 1));
            
            double level = brt * Math.abs(tileValue) * contrast;
            
            this.colors[p.index] = LXColor.hsb(hue * 360, sat * 100, level * 100);
        }
        
        // Mirror logic: Copy to South face flipped if enabled, else copy as-is
        if (mirrorNS && Apotheneum.cube.south != null && Apotheneum.cube.front != null) {
            LXPoint[] from = Apotheneum.cube.front.points;
            LXPoint[] to = Apotheneum.cube.south.points;
            for (int i = 0; i < from.length && i < to.length; ++i) {
                this.colors[to[to.length - 1 - i].index] = this.colors[from[i].index];
            }
        } else {
            copy(Apotheneum.cube.front, Apotheneum.cube.south);
        }
        
        // Copy to remaining faces and cylinder
        copy(Apotheneum.cube.front, Apotheneum.cube.back);
        copy(Apotheneum.cube.front, Apotheneum.cube.left);
        copy(Apotheneum.cube.front, Apotheneum.cube.right);
        copy(Apotheneum.cube.front, Apotheneum.cylinder.exterior);
    }
}
