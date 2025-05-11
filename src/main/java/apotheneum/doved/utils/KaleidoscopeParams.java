package apotheneum.doved.utils;

import heronarts.lx.parameter.CompoundParameter;

public class KaleidoscopeParams {
        public CompoundParameter segments = new CompoundParameter("KSegments", 2, 1, 8)
                        .setDescription("Kaleidoscope Segments");

        public CompoundParameter rotateTheta = new CompoundParameter("rTheta", 0, 0, Math.PI * 2)
                        .setDescription("Kaleidoscope Rotate Angle");

        public CompoundParameter rotatePhi = new CompoundParameter("rPhi", 0, 0, Math.PI * 2)
                        .setUnits(CompoundParameter.Units.DEGREES)
                        .setDescription("Kaleidoscope Rotate Phi");

        public CompoundParameter x = new CompoundParameter("KX", 0.5, 0, 1)
                        .setDescription("Kaleidoscope X");

        public CompoundParameter y = new CompoundParameter("KY", 0.5, 0, 1)
                        .setDescription("Kaleidoscope Y");

        public CompoundParameter z = new CompoundParameter("KZ", 0.5, 0, 1)
                        .setDescription("Kaleidoscope Z");
}
