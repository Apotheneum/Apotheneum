package apotheneum.drmrrdmr;

import apotheneum.ApotheneumPattern;
import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cube.Face;
import apotheneum.Apotheneum.Cube.Row;
import apotheneum.Apotheneum.Cylinder;
import apotheneum.Apotheneum.Cylinder.Ring;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;

/**
 * A performance-optimized fork of thesilveresa's Dust Pulse, kept as its own
 * pattern (not a same-package "-Optimized" sibling) specifically so the
 * original never has to change - existing shows referencing it are
 * completely unaffected, at the cost of not benefiting from these changes
 * unless a project is updated to use this pattern instead.
 *
 * Every cube face (exterior AND interior alike) and every cylinder
 * orientation run through the exact same u/v grid and the same global
 * particle/geometry state - nothing in the per-pixel math is face-specific.
 * So instead of redoing the expensive part (the 3-sample particle loop,
 * noise lookups, etc.) up to 8 times for the cube and 2 times for the
 * cylinder every frame, it's computed once per surface type and copied out.
 *
 * The one piece of real per-face randomness is the sparkle flourish (a 10%
 * chance per pixel to boost brightness when a particle is near its peak) -
 * caching a final packed color would either bake one face's sparkle roll
 * into every face, or drop it entirely. So the pre-sparkle hue/sat/brightness
 * and the particleBrightness needed to gate it are cached as separate floats
 * (not a packed color, to avoid any RGB round-trip precision loss), and the
 * sparkle roll is redone per-face against that cache - independent per face,
 * same as the original.
 *
 * New here: a Sat knob. At 0 ("whiteout"), the hue/saturation determination
 * is skipped entirely - the per-type hue selection, the noise-based
 * saturation variation, and (notably) a Math.sin() call for the geometry's
 * saturation - since the output is grayscale regardless of what those values
 * would have been. Above 0, hue/sat are computed normally and the result is
 * scaled by Sat/100.
 */
@LXCategory("Apotheneum/drmrrdmr")
@LXComponent.Name("Dust Pulse Whiteout")
public class DustPulseWhiteout extends ApotheneumPattern {

  private final CompoundParameter speed = new CompoundParameter("Speed", 0.7, 0.1, 3.0)
    .setDescription("Particle pulse speed");
  private final CompoundParameter density = new CompoundParameter("Density", 0.5, 0.1, 0.9)
    .setDescription("Particle density");
  private final CompoundParameter scatter = new CompoundParameter("Scatter", 0.8, 0.2, 2.0)
    .setDescription("Particle randomness and spread");
  private final CompoundParameter pulse = new CompoundParameter("Pulse", 0.8, 0.1, 1.5)
    .setDescription("Pulse intensity");
  private final CompoundParameter decay = new CompoundParameter("Decay", 0.85, 0.5, 0.98)
    .setDescription("Brightness decay rate");
  private final BooleanParameter coherent = new BooleanParameter("Coherent", false)
    .setDescription("Synchronized particle behavior");
  private final CompoundParameter hueShift = new CompoundParameter("Hue", 0.0, 0.0, 360.0)
    .setDescription("Base hue offset");
  private final CompoundParameter dispersion = new CompoundParameter("Dispersion", 0.6, 0.1, 1.0)
    .setDescription("How particles break away from geometry");
  private final CompoundParameter intensity = new CompoundParameter("Intensity", 0.9, 0.3, 2.0)
    .setDescription("Overall brightness multiplier");
  private final CompoundParameter saturation = new CompoundParameter("Sat", 100.0, 0.0, 100.0)
    .setDescription("Overall color saturation - at 0 ('whiteout'), skips hue/saturation computation entirely (including a Math.sin call) and renders pure grayscale");

  // Enhanced optimization: Larger lookup tables for smoother effects
  private static final int NOISE_TABLE_SIZE = 512;
  private static final int PULSE_TABLE_SIZE = 256;
  private static final int WAVE_TABLE_SIZE = 128;
  private float[] noiseTable;
  private float[] pulseTable;
  private float[] waveTable;

  // Enhanced particle system with more properties
  private float[] particlePhases;
  private float[] particleBrightness;
  private float[] particleVelocityX;
  private float[] particleVelocityY;
  private float[] particleLifetime;
  private float[] particleSize;
  private boolean[] particleActive;
  private int[] particleType; // Different particle behaviors

  private float time = 0f;
  private float geometryPhase = 0f;
  private int maxParticles;
  private int activeParticles = 0;

  private float[] faceCacheHue;
  private float[] faceCacheSat;
  private float[] faceCacheBrightness; // pre-sparkle, same 0-1ish scale as the original's local `finalBrightness`
  private float[] faceCacheParticleBrightness;
  private boolean[] faceCacheBlack; // exact literal 0 (fully transparent), not LXColor.hsb(_,_,0) (opaque black)

  private float[] cylCacheHue;
  private float[] cylCacheSat;
  private float[] cylCacheBrightness;
  private float[] cylCacheParticleBrightness;
  private boolean[] cylCacheBlack;

  public DustPulseWhiteout(LX lx) {
    super(lx);
    addParameter("Speed", this.speed);
    addParameter("Density", this.density);
    addParameter("Scatter", this.scatter);
    addParameter("Pulse", this.pulse);
    addParameter("Decay", this.decay);
    addParameter("Coherent", this.coherent);
    addParameter("Hue", this.hueShift);
    addParameter("Dispersion", this.dispersion);
    addParameter("Intensity", this.intensity);
    addParameter("Sat", this.saturation);

    initializeLookupTables();
    initializeParticles();
  }

  private void initializeLookupTables() {
    // Enhanced noise table with multiple octaves for more interesting patterns
    noiseTable = new float[NOISE_TABLE_SIZE];
    for (int i = 0; i < NOISE_TABLE_SIZE; i++) {
      float t = (float)i / NOISE_TABLE_SIZE;
      float noise1 = (float)(0.5 + 0.5 * Math.sin(t * Math.PI * 2 * 3.7) * Math.cos(t * Math.PI * 2 * 2.3));
      float noise2 = (float)(0.3 * Math.sin(t * Math.PI * 2 * 7.1) * Math.cos(t * Math.PI * 2 * 5.7));
      float noise3 = (float)(0.2 * Math.sin(t * Math.PI * 2 * 13.3) * Math.cos(t * Math.PI * 2 * 11.1));
      noiseTable[i] = noise1 + noise2 + noise3;
    }

    // Enhanced pulse envelope with multiple peaks for more dynamic pulses
    pulseTable = new float[PULSE_TABLE_SIZE];
    for (int i = 0; i < PULSE_TABLE_SIZE; i++) {
      float t = (float)i / (PULSE_TABLE_SIZE - 1);
      float primary = (float)(Math.exp(-t * t * 6) * (1 + 0.4 * Math.sin(t * Math.PI * 8)));
      float secondary = (float)(0.3 * Math.exp(-(t-0.7) * (t-0.7) * 12) * Math.sin(t * Math.PI * 16));
      pulseTable[i] = Math.max(0, primary + secondary);
    }

    // Wave table for geometry dissolution effect
    waveTable = new float[WAVE_TABLE_SIZE];
    for (int i = 0; i < WAVE_TABLE_SIZE; i++) {
      float t = (float)i / (WAVE_TABLE_SIZE - 1);
      waveTable[i] = (float)(0.5 + 0.5 * Math.sin(t * Math.PI * 2) * Math.exp(-t * 2));
    }
  }

  private void initializeParticles() {
    // Increased particle count for denser effects
    maxParticles = 800;
    particlePhases = new float[maxParticles];
    particleBrightness = new float[maxParticles];
    particleVelocityX = new float[maxParticles];
    particleVelocityY = new float[maxParticles];
    particleLifetime = new float[maxParticles];
    particleSize = new float[maxParticles];
    particleActive = new boolean[maxParticles];
    particleType = new int[maxParticles];

    // Initialize with random properties
    for (int i = 0; i < maxParticles; i++) {
      particlePhases[i] = (float)Math.random();
      particleBrightness[i] = 0f;
      particleVelocityX[i] = (float)(Math.random() - 0.5) * 0.02f;
      particleVelocityY[i] = (float)(Math.random() - 0.5) * 0.02f;
      particleLifetime[i] = 0f;
      particleSize[i] = 0.5f + (float)Math.random() * 0.5f;
      particleActive[i] = false;
      particleType[i] = (int)(Math.random() * 3); // Three particle types
    }
  }

  @Override
  protected void render(double deltaMs) {
    float dt = (float)(deltaMs / 1000.0);
    time += dt * speed.getValuef();
    geometryPhase += dt * speed.getValuef() * 0.3f;

    updateParticleSystem(dt);

    // Exterior and interior faces share the same u,v grid and the same
    // (position + global-state)-only math - compute once into a cache and
    // copy to every face, instead of redoing the same expensive per-pixel
    // work up to 8 times (4 exterior + 4 interior).
    Cube cube = Apotheneum.cube;
    if (cube != null) {
      Face referenceFace = cube.exterior.faces[0];
      computeFaceCache(referenceFace);

      for (Face face : cube.exterior.faces) {
        copyFromFaceCache(face);
      }
      if (cube.interior != null) {
        for (Face face : cube.interior.faces) {
          copyFromFaceCache(face);
        }
      }
    }

    // Same reasoning for the cylinder's exterior/interior.
    Cylinder cylinder = Apotheneum.cylinder;
    if (cylinder != null) {
      computeCylinderCache(cylinder.exterior);
      copyFromCylinderCache(cylinder.exterior);
      if (cylinder.interior != null) {
        copyFromCylinderCache(cylinder.interior);
      }
    }
  }

  private void updateParticleSystem(float dt) {
    float dens = density.getValuef();
    float sctr = scatter.getValuef();
    float decayRate = decay.getValuef();
    boolean isCoherent = coherent.getValueb();
    float disp = dispersion.getValuef();

    activeParticles = 0;

    for (int i = 0; i < maxParticles; i++) {
      if (particleActive[i]) {
        activeParticles++;

        // Update particle physics
        particleLifetime[i] += dt;
        particleBrightness[i] *= decayRate;

        // Apply velocity for scattering effect
        float velocityDecay = 0.98f;
        particleVelocityX[i] *= velocityDecay;
        particleVelocityY[i] *= velocityDecay;

        // Add some turbulence based on particle type
        if (particleType[i] == 1) {
          int noiseIdx = Math.abs((int)(time * 50 + i)) % NOISE_TABLE_SIZE;
          particleVelocityX[i] += noiseTable[noiseIdx] * dt * 0.01f;
          particleVelocityY[i] += noiseTable[(noiseIdx + 100) % NOISE_TABLE_SIZE] * dt * 0.01f;
        }

        // Deactivate particles that are too dim or too old
        if (particleBrightness[i] < 0.01f || particleLifetime[i] > 5.0f) {
          particleActive[i] = false;
        }
      } else if (Math.random() < dens * dt * 3) {
        // Spawn new particle with enhanced properties
        particleActive[i] = true;
        particlePhases[i] = isCoherent ? time : (float)Math.random();
        particleBrightness[i] = pulse.getValuef() * (0.3f + 0.7f * (float)Math.random());
        particleLifetime[i] = 0f;

        // Initial velocity based on dispersion and particle type
        float velMagnitude = disp * 0.05f * (0.5f + (float)Math.random());
        float angle = (float)Math.random() * 2f * (float)Math.PI;
        particleVelocityX[i] = (float)Math.cos(angle) * velMagnitude;
        particleVelocityY[i] = (float)Math.sin(angle) * velMagnitude;

        particleSize[i] = 0.3f + (float)Math.random() * 1.2f;
        particleType[i] = (int)(Math.random() * 3);
      }
    }
  }

  private void computeFaceCache(Face face) {
    int cols = face.columns.length;
    int rows = face.rows.length;
    int n = cols * rows;

    if (faceCacheHue == null || faceCacheHue.length != n) {
      faceCacheHue = new float[n];
      faceCacheSat = new float[n];
      faceCacheBrightness = new float[n];
      faceCacheParticleBrightness = new float[n];
      faceCacheBlack = new boolean[n];
    }

    float invCols = 1.0f / Math.max(1, cols - 1);
    float invRows = 1.0f / Math.max(1, rows - 1);

    // Calculate geometry dissolution factor
    float dissolutionWave = (float)(0.5 + 0.5 * Math.sin(geometryPhase * 0.7));

    // Hoisted out of the per-pixel hot path - these were previously re-read
    // from their parameter objects on every pixel (hueShift up to 3x/pixel,
    // inside the particle sampling loop).
    float sctr = scatter.getValuef();
    float intensityMult = intensity.getValuef();
    float disp = dispersion.getValuef();
    float hue = hueShift.getValuef();
    float satParam = saturation.getValuef();
    float satScale = satParam / 100f;
    boolean whiteout = satParam <= 0f;

    int cacheIndex = 0;
    for (int rowIdx = 0; rowIdx < rows; rowIdx++) {
      for (int colIdx = 0; colIdx < cols; colIdx++) {
        float u = colIdx * invCols;
        float v = rowIdx * invRows;

        float geometryBrightness = calculateGeometryBrightness(u, v, dissolutionWave, disp);
        int particleColor = calculateEnhancedParticleColor(u, v, sctr, intensityMult, hue, whiteout);

        computeBlendPreSparkle(
          geometryBrightness, particleColor, u, v, disp, hue, intensityMult, whiteout, satScale,
          faceCacheHue, faceCacheSat, faceCacheBrightness, faceCacheParticleBrightness, faceCacheBlack, cacheIndex
        );
        cacheIndex++;
      }
    }
  }

  private void copyFromFaceCache(Face face) {
    int cols = face.columns.length;
    int cacheIndex = 0;

    for (Row row : face.rows) {
      for (int cx = 0; cx < cols; cx++) {
        LXPoint p = row.points[cx];
        colors[p.index] = applySparkleAndPack(
          faceCacheHue, faceCacheSat, faceCacheBrightness, faceCacheParticleBrightness, faceCacheBlack, cacheIndex
        );
        cacheIndex++;
      }
    }
  }

  private void computeCylinderCache(Cylinder.Orientation orientation) {
    Ring[] rings = orientation.rings;
    int numRings = rings.length;
    int pointsPerRing = (numRings > 0) ? rings[0].points.length : 0;
    int n = numRings * pointsPerRing;

    if (cylCacheHue == null || cylCacheHue.length != n) {
      cylCacheHue = new float[n];
      cylCacheSat = new float[n];
      cylCacheBrightness = new float[n];
      cylCacheParticleBrightness = new float[n];
      cylCacheBlack = new boolean[n];
    }

    float dissolutionWave = (float)(0.5 + 0.5 * Math.sin(geometryPhase * 0.7));

    float sctr = scatter.getValuef();
    float intensityMult = intensity.getValuef();
    float disp = dispersion.getValuef();
    float hue = hueShift.getValuef();
    float satParam = saturation.getValuef();
    float satScale = satParam / 100f;
    boolean whiteout = satParam <= 0f;

    int cacheIndex = 0;
    for (int ringIndex = 0; ringIndex < numRings; ringIndex++) {
      Ring ring = rings[ringIndex];
      int ringPointCount = ring.points.length;

      for (int pointIndex = 0; pointIndex < ringPointCount; pointIndex++) {
        float u = (float)pointIndex / ringPointCount;
        float v = (float)ringIndex / (numRings - 1);

        float geometryBrightness = calculateGeometryBrightness(u, v, dissolutionWave, disp);
        int particleColor = calculateEnhancedParticleColor(u, v, sctr, intensityMult, hue, whiteout);

        computeBlendPreSparkle(
          geometryBrightness, particleColor, u, v, disp, hue, intensityMult, whiteout, satScale,
          cylCacheHue, cylCacheSat, cylCacheBrightness, cylCacheParticleBrightness, cylCacheBlack, cacheIndex
        );
        cacheIndex++;
      }
    }
  }

  private void copyFromCylinderCache(Cylinder.Orientation orientation) {
    Ring[] rings = orientation.rings;
    int numRings = rings.length;
    int cacheIndex = 0;

    for (int ringIndex = 0; ringIndex < numRings; ringIndex++) {
      Ring ring = rings[ringIndex];
      int pointsPerRing = ring.points.length;

      for (int pointIndex = 0; pointIndex < pointsPerRing; pointIndex++) {
        LXPoint p = ring.points[pointIndex];
        colors[p.index] = applySparkleAndPack(
          cylCacheHue, cylCacheSat, cylCacheBrightness, cylCacheParticleBrightness, cylCacheBlack, cacheIndex
        );
        cacheIndex++;
      }
    }
  }

  private float calculateGeometryBrightness(float u, float v, float dissolutionWave, float disp) {
    // Create geometric patterns that fade over time
    float geometricPattern = (float)(
      0.7 * Math.sin(u * Math.PI * 8 + time * 2) * Math.cos(v * Math.PI * 6 + time * 1.5) +
      0.3 * Math.sin(u * Math.PI * 16 + time * 3) * Math.cos(v * Math.PI * 12 + time * 2.5)
    );

    // Apply wave-based dissolution
    int waveIdx = Math.abs((int)((u + v + geometryPhase * 0.5f) * WAVE_TABLE_SIZE)) % WAVE_TABLE_SIZE;
    float dissolutionFactor = waveTable[waveIdx];

    // Geometry starts bright and fades as particles take over
    float geometryStrength = (1f - disp * dissolutionWave * dissolutionFactor);

    return Math.max(0, geometricPattern * geometryStrength * 0.6f);
  }

  private int calculateEnhancedParticleColor(float u, float v, float sctr, float intensityMult, float hue, boolean whiteout) {
    float maxBrightness = 0f;
    float bestHue = 0f;
    float bestSat = 0f;

    // Sample multiple particles for richer layering
    for (int sampleOffset = 0; sampleOffset < 3; sampleOffset++) {
      int hashX = (int)(u * 25) * 73 + sampleOffset * 17;
      int hashY = (int)(v * 25) * 37 + sampleOffset * 23;
      int hash = (hashX + hashY) % maxParticles;

      if (!particleActive[hash]) continue;

      // Enhanced scatter with multiple noise octaves
      int noiseIdx1 = Math.abs((int)((u + v + time * 0.3f + sampleOffset * 0.1f) * NOISE_TABLE_SIZE)) % NOISE_TABLE_SIZE;
      int noiseIdx2 = Math.abs((int)((u * 2 + v * 3 + time * 0.7f) * NOISE_TABLE_SIZE)) % NOISE_TABLE_SIZE;
      float scatter1 = noiseTable[noiseIdx1] * sctr;
      float scatter2 = noiseTable[noiseIdx2] * sctr * 0.5f;

      // Enhanced particle positioning with velocity
      float particleU = (hash % 29) / 29.0f + scatter1 * 0.15f + particleVelocityX[hash] * particleLifetime[hash];
      float particleV = (hash / 29 % 23) / 23.0f + scatter2 * 0.15f + particleVelocityY[hash] * particleLifetime[hash];

      // Wrap coordinates
      particleU = particleU - (float)Math.floor(particleU);
      particleV = particleV - (float)Math.floor(particleV);

      // Distance with size consideration
      float dx = u - particleU;
      float dy = v - particleV;

      // Handle wrapping for smoother effects
      if (dx > 0.5f) dx -= 1.0f;
      if (dx < -0.5f) dx += 1.0f;
      if (dy > 0.5f) dy -= 1.0f;
      if (dy < -0.5f) dy += 1.0f;

      float dist = dx * dx + dy * dy;
      float maxDist = 0.08f * particleSize[hash];

      if (dist > maxDist) continue;

      // Enhanced pulse calculation with particle type variations
      float phase = particlePhases[hash];
      if (particleType[hash] == 2) {
        phase += (float)Math.sin(time * 3 + hash * 0.1) * 0.2f; // Wobbly particles
      }

      int pulseIdx = Math.abs((int)(phase * (PULSE_TABLE_SIZE - 1))) % PULSE_TABLE_SIZE;
      float pulseBright = pulseTable[pulseIdx];

      // Distance falloff with softer edges
      float falloff = (float)(1f - Math.pow(dist / maxDist, 0.8));
      float brightness = particleBrightness[hash] * pulseBright * falloff * intensityMult;

      if (brightness > maxBrightness) {
        maxBrightness = brightness;

        // In whiteout mode the hue/sat this picks are irrelevant (saturation
        // is forced to 0 downstream regardless), so skip determining them -
        // this is the "disable complicated color calculation" fast path.
        if (!whiteout) {
          // Enhanced color calculation
          float baseHue = (hue + hash * 11.7f) % 360f;

          // Color variations by particle type
          switch (particleType[hash]) {
            case 0: // Standard particles
              bestHue = baseHue;
              bestSat = 75f + 20f * noiseTable[Math.abs(hash) % NOISE_TABLE_SIZE];
              break;
            case 1: // Turbulent particles - cooler colors
              bestHue = (baseHue + 180f) % 360f;
              bestSat = 85f + 15f * noiseTable[Math.abs(hash) % NOISE_TABLE_SIZE];
              break;
            case 2: // Wobbly particles - warmer colors
              bestHue = (baseHue + 60f) % 360f;
              bestSat = 90f + 10f * noiseTable[Math.abs(hash) % NOISE_TABLE_SIZE];
              break;
          }
        }
      }
    }

    if (maxBrightness < 0.05f) return 0;

    if (whiteout) {
      return LXColor.gray(Math.min(100f, maxBrightness * 120f));
    }
    return LXColor.hsb(bestHue, bestSat, Math.min(100f, maxBrightness * 120f));
  }

  // Pre-sparkle part of the original blendGeometryAndParticles - everything
  // that depends only on position + global state, cached so it can be shared
  // across every face/orientation instead of recomputed per-face.
  private void computeBlendPreSparkle(
    float geometryBrightness, int particleColor, float u, float v, float disp, float hue, float intensityMult,
    boolean whiteout, float satScale,
    float[] outHue, float[] outSat, float[] outBrightness, float[] outParticleBrightness, boolean[] outBlack, int idx
  ) {
    float particleBright = LXColor.b(particleColor) / 100.0f;

    float totalBrightness = Math.max(geometryBrightness, particleBright);
    outParticleBrightness[idx] = particleBright;

    if (totalBrightness < 0.02f) {
      // Distinct from LXColor.hsb(_, _, 0) (opaque black) - and since
      // particleBright must also be < 0.02 whenever totalBrightness is, the
      // sparkle gate (particleBright > 0.8f) could never fire in this branch
      // anyway, so marking it black up front changes nothing.
      outBlack[idx] = true;
      return;
    }
    outBlack[idx] = false;
    outBrightness[idx] = totalBrightness * intensityMult;

    if (whiteout) {
      // Saturation is forced to 0 regardless of what hue/sat would have
      // been, so skip determining them entirely - including a Math.sin()
      // call for the geometry's saturation and the particle/geometry
      // weighted blend below.
      outHue[idx] = 0f;
      outSat[idx] = 0f;
      return;
    }

    float particleHue = LXColor.h(particleColor);
    float particleSat = LXColor.s(particleColor);

    float geometryHue = (hue + u * 120f + v * 80f + time * 10f) % 360f;
    float geometrySat = 60f + 30f * (float)Math.sin(time * 0.5f + u * Math.PI * 4);

    float particleWeight = disp * (particleBright / (particleBright + geometryBrightness + 0.01f));
    float geometryWeight = 1f - particleWeight;

    outHue[idx] = particleWeight * particleHue + geometryWeight * geometryHue;
    outSat[idx] = (particleWeight * particleSat + geometryWeight * geometrySat) * satScale;
  }

  // Sparkle roll + final packing - done per real face/orientation (not
  // cached), so each one gets its own independent 10%-chance flourish, the
  // same as the original's per-face Math.random() call.
  private int applySparkleAndPack(
    float[] cacheHue, float[] cacheSat, float[] cacheBrightness, float[] cacheParticleBrightness, boolean[] cacheBlack, int idx
  ) {
    if (cacheBlack[idx]) {
      return 0;
    }

    float finalHue = cacheHue[idx];
    float finalSat = cacheSat[idx];
    float finalBrightness = cacheBrightness[idx];

    if (cacheParticleBrightness[idx] > 0.8f && Math.random() < 0.1) {
      finalBrightness *= 1.5f;
      finalSat = Math.min(100f, finalSat * 1.2f);
    }

    return LXColor.hsb(finalHue % 360f, Math.min(100f, finalSat), Math.min(100f, finalBrightness * 100f));
  }
}
