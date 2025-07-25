package apotheneum.thesilveresa;

import apotheneum.ApotheneumPattern;
import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cube.Face;
import apotheneum.Apotheneum.Cube.Row;
import apotheneum.Apotheneum.Cylinder;
import apotheneum.Apotheneum.Cylinder.Ring;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory("Apotheneum/thesilveresa")
@LXComponentName("Syntax Emergence")
public class SyntaxEmergence extends ApotheneumPattern {

  private final CompoundParameter speed = new CompoundParameter("Speed", 0.3, 0.1, 2.0)
    .setDescription("Glyph blinking speed");
  private final CompoundParameter density = new CompoundParameter("Density", 6.0, 2.0, 12.0)
    .setDescription("Number of glyph tiles");
  private final CompoundParameter coherence = new CompoundParameter("Sync", 0.7, 0.0, 1.0)
    .setDescription("Pattern synchronization (0=random, 1=synchronized)");
  private final CompoundParameter flashIntensity = new CompoundParameter("Flash", 80.0, 20.0, 100.0)
    .setDescription("Brightness of glyph flashes");
  private final CompoundParameter persistence = new CompoundParameter("Persist", 0.2, 0.0, 1.0)
    .setDescription("How long glyphs stay lit");
  private final BooleanParameter edgeMode = new BooleanParameter("Edges", false)
    .setDescription("Show only glyph edges");
  private final CompoundParameter sat = new CompoundParameter("Sat", 85.0, 0.0, 100.0)
    .setDescription("Saturation percentage");

  // Glyph pattern definitions - simple geometric forms
  private static final int[][] GLYPH_PATTERNS = {
    // Cross pattern
    {0,1,0, 1,1,1, 0,1,0},
    // L-shape
    {1,0,0, 1,0,0, 1,1,1},
    // T-shape
    {1,1,1, 0,1,0, 0,1,0},
    // Square
    {1,1,1, 1,0,1, 1,1,1},
    // Diagonal
    {1,0,0, 0,1,0, 0,0,1},
    // Dot cluster
    {1,0,1, 0,0,0, 1,0,1}
  };

  private static final float[] GLYPH_HUES = { 40f, 180f, 270f, 320f, 60f, 200f };
  private static final int GLYPH_SIZE = 3; // 3x3 grid per glyph

  // Timing state for each glyph type
  private float[] glyphPhases;
  private float[] glyphTimers;

  public SyntaxEmergence(LX lx) {
    super(lx);
    addParameter("Speed", this.speed);
    addParameter("Density", this.density);
    addParameter("Coherence", this.coherence);
    addParameter("Flash", this.flashIntensity);
    addParameter("Persist", this.persistence);
    addParameter("Edges", this.edgeMode);
    addParameter("Sat", this.sat);
    
    // Initialize glyph timing
    glyphPhases = new float[GLYPH_PATTERNS.length];
    glyphTimers = new float[GLYPH_PATTERNS.length];
    for (int i = 0; i < glyphPhases.length; i++) {
      glyphPhases[i] = (float)Math.random();
      glyphTimers[i] = 0f;
    }
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float)(deltaMs / 1000.0);
    float spd = speed.getValuef();
    float coh = coherence.getValuef();
    
    // Update glyph timing with coherence mixing
    for (int i = 0; i < glyphPhases.length; i++) {
      float individualSpeed = spd * (0.8f + 0.4f * (float)Math.random());
      float coherentSpeed = spd;
      float actualSpeed = individualSpeed * (1f - coh) + coherentSpeed * coh;
      
      glyphTimers[i] += dt * actualSpeed;
      if (glyphTimers[i] >= 1f) {
        glyphTimers[i] = 0f;
        glyphPhases[i] = (float)Math.random();
      }
    }

    // Render geometries
    Cube cube = Apotheneum.cube;
    if (cube != null) {
      for (Face face : cube.exterior.faces) {
        processFace(face);
      }
      if (cube.interior != null) {
        for (Face face : cube.interior.faces) {
          processFace(face);
        }
      }
    }
    
    Cylinder cylinder = Apotheneum.cylinder;
    if (cylinder != null) {
      processCylinder(cylinder);
    }
  }

  private void processFace(Face face) {
    int cols = face.columns.length;
    int rows = face.rows.length;
    float invCols = 1.0f / Math.max(1, cols - 1);
    float invRows = 1.0f / Math.max(1, rows - 1);
    float dens = density.getValuef();
    
    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        float u = cx * invCols;
        float v = row.index * invRows;
        
        int color = calculateGlyphColor(u, v, dens);
        colors[p.index] = color;
      }
    }
  }
  
  private void processCylinder(Cylinder cylinder) {
    Cylinder.Orientation[] faces = (cylinder.interior != null)
      ? new Cylinder.Orientation[]{ cylinder.exterior, cylinder.interior }
      : new Cylinder.Orientation[]{ cylinder.exterior };

    for (Cylinder.Orientation face : faces) {
      Ring[] rings = face.rings;
      int numRings = rings.length;
      final float stretchFactor = 0.4f;

      for (int ringIndex = 0; ringIndex < numRings; ringIndex++) {
        Ring ring = rings[ringIndex];
        int pointsPerRing = ring.points.length;

        for (int pointIndex = 0; pointIndex < pointsPerRing; pointIndex++) {
          LXPoint p = ring.points[pointIndex];

          float u = (float)pointIndex / pointsPerRing;
          float v = Math.min((float)ringIndex / (numRings - 1) * stretchFactor, 1.0f);
          
          int color = calculateGlyphColor(u, v, density.getValuef());
          colors[p.index] = color;
        }
      }
    }
  }
  
  private int calculateGlyphColor(float u, float v, float dens) {
    // Determine which glyph tile we're in
    int tileX = (int)(u * dens);
    int tileY = (int)(v * dens);
    
    // Local position within the tile (0-1)
    float localU = (u * dens) - tileX;
    float localV = (v * dens) - tileY;
    
    // Hash to determine glyph type for this tile
    int hash = (tileX * 73 + tileY * 37) % GLYPH_PATTERNS.length;
    int[] pattern = GLYPH_PATTERNS[hash];
    float hue = GLYPH_HUES[hash];
    
    // Position within the 3x3 glyph grid
    int glyphX = (int)(localU * GLYPH_SIZE);
    int glyphY = (int)(localV * GLYPH_SIZE);
    
    if (glyphX >= GLYPH_SIZE || glyphY >= GLYPH_SIZE) return 0;
    
    int patternIndex = glyphY * GLYPH_SIZE + glyphX;
    if (patternIndex >= pattern.length || pattern[patternIndex] == 0) return 0;
    
    // Calculate if this glyph should be lit based on timing
    float persist = persistence.getValuef();
    boolean isLit = glyphTimers[hash] < persist;
    
    if (!isLit) return 0;
    
    // Edge detection if enabled
    if (edgeMode.getValueb()) {
      boolean isEdge = isGlyphEdge(pattern, glyphX, glyphY);
      if (!isEdge) return 0;
    }
    
    // Flash effect based on timer
    float flashPhase = glyphTimers[hash] / persist;
    float brightness = flashIntensity.getValuef() * (1f - flashPhase);
    
    return LXColor.hsb(hue, sat.getValuef(), brightness);
  }
  
  private boolean isGlyphEdge(int[] pattern, int x, int y) {
    int index = y * GLYPH_SIZE + x;
    if (index >= pattern.length || pattern[index] == 0) return false;
    
    // Check 4-connected neighbors
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        if (dx == 0 && dy == 0) continue;
        if (Math.abs(dx) + Math.abs(dy) > 1) continue; // 4-connected only
        
        int nx = x + dx;
        int ny = y + dy;
        
        if (nx < 0 || nx >= GLYPH_SIZE || ny < 0 || ny >= GLYPH_SIZE) {
          return true; // Edge of glyph
        }
        
        int neighborIndex = ny * GLYPH_SIZE + nx;
        if (neighborIndex >= pattern.length || pattern[neighborIndex] == 0) {
          return true; // Adjacent to empty space
        }
      }
    }
    
    return false;
  }
}
