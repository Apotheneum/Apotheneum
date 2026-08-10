package apotheneum.thesilveresa;

import apotheneum.Apotheneum;
import apotheneum.Apotheneum.Cube;
import apotheneum.Apotheneum.Cube.Face;
import apotheneum.Apotheneum.Cube.Row;
import apotheneum.Apotheneum.Cylinder.Ring;
import apotheneum.Apotheneum.Cylinder;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;

import java.util.Random;

@LXCategory("Apotheneum/thesilveresa")
@LXComponent.Name("Nucleation")
public class Nucleation extends ApotheneumPattern {

  // Probability texture

  final CompoundParameter flicker =
    new CompoundParameter("Flicker", 0.02, 0.0, 0.25)
      .setDescription("Fraction of pixels lit in the uncommitted field");

  final CompoundParameter level =
    new CompoundParameter("Level", 2.0, 0.2, 12.0)
      .setDescription("Flicker brightness (percent)");

  final CompoundParameter life =
    new CompoundParameter("Life", 50.0, 20.0, 400.0)
      .setDescription("Flicker lifetime (ms)");

  // Commitment dynamics

  final DiscreteParameter seeds =
    new DiscreteParameter("Seeds", 0, 0, 7)
      .setDescription("Number of committed regions");

  final CompoundParameter growth =
    new CompoundParameter("Growth", 0.5, 0.05, 4.0)
      .setDescription("Commitment resolve speed");

  final CompoundParameter spread =
    new CompoundParameter("Spread", 0.0, 0.0, 1.0)
      .setDescription("Avalanche probability per second");

  final CompoundParameter hold =
    new CompoundParameter("Hold", 1.0, 0.5, 1.0)
      .setDescription("Commitment persistence (1=permanent)");

  final CompoundParameter resolve =
    new CompoundParameter("Resolve", 70.0, 10.0, 100.0)
      .setDescription("Committed brightness (percent)");

  // Boundary negotiation

  final CompoundParameter shimmer =
    new CompoundParameter("Shimmer", 0.0, 0.0, 1.0)
      .setDescription("Boundary indecision at region frontiers");

  // Color

  final CompoundParameter hue =
    new CompoundParameter("Hue", 220.0, 0.0, 360.0)
      .setDescription("Committed region hue");

  final CompoundParameter hueSplit =
    new CompoundParameter("HueSplit", 12.0, 0.0, 90.0)
      .setDescription("Hue offset between regions");

  final CompoundParameter sat =
    new CompoundParameter("Sat", 15.0, 0.0, 100.0)
      .setDescription("Committed region saturation");

  final TriggerParameter reset =
    new TriggerParameter("Reset", this::reseed)
      .setDescription("Reset field and reseed");

  // Simulation state

  private static final int CUBE_W = Apotheneum.GRID_WIDTH * 4; // 200, wrapped
  private static final int CUBE_H = Apotheneum.GRID_HEIGHT;    // 45
  private static final int CYL_W = Apotheneum.RING_LENGTH;     // 120, wrapped
  private static final int CYL_H = Apotheneum.CYLINDER_HEIGHT; // 43

  private final Lattice cubeLattice = new Lattice(CUBE_W, CUBE_H);
  private final Lattice cylLattice = new Lattice(CYL_W, CYL_H);

  private final Random random = new Random();

  private class Lattice {
    final int width, height, size;
    final float[] commit;        // 0..1 resolve progress
    final byte[] owner;          // 0 = uncommitted, 1..N = region id
    final float[] flickerLife;   // remaining ms of transient flicker
    final float[] flickerBright; // brightness of transient flicker, 0..1
    final int[] seedCells;       // lattice index of each seed, -1 if unset
    int seeded = 0;

    Lattice(int width, int height) {
      this.width = width;
      this.height = height;
      this.size = width * height;
      this.commit = new float[size];
      this.owner = new byte[size];
      this.flickerLife = new float[size];
      this.flickerBright = new float[size];
      this.seedCells = new int[8];
      java.util.Arrays.fill(this.seedCells, -1);
    }

    void clear() {
      java.util.Arrays.fill(this.commit, 0f);
      java.util.Arrays.fill(this.owner, (byte) 0);
      java.util.Arrays.fill(this.seedCells, -1);
      this.seeded = 0;
    }

    int idx(int x, int y) {
      return y * this.width + x;
    }

    // Horizontal wrap, vertical clamp (returns -1 when off the top/bottom)
    int neighbor(int i, int dir) {
      int x = i % this.width;
      int y = i / this.width;
      switch (dir) {
        case 0: x = (x + 1) % this.width; break;
        case 1: x = (x + this.width - 1) % this.width; break;
        case 2: if (++y >= this.height) return -1; break;
        default: if (--y < 0) return -1; break;
      }
      return idx(x, y);
    }

    void addSeed(int region) {
      // Distribute seeds around the wrap with jitter, vertically centered-ish
      int x = (int) ((region - 1 + random.nextFloat() * 0.5f) * this.width / Math.max(1, seeds.getValuei())) % this.width;
      int y = this.height / 4 + random.nextInt(Math.max(1, this.height / 2));
      int i = idx(x, y);
      this.seedCells[region - 1] = i;
      this.owner[i] = (byte) region;
      this.commit[i] = Math.max(this.commit[i], 0.01f);
      this.seeded = region;
    }

    void removeSeed(int region) {
      // Release every pixel owned by this region
      for (int i = 0; i < this.size; ++i) {
        if (this.owner[i] == region) {
          this.owner[i] = 0;
          this.commit[i] = 0f;
        }
      }
      this.seedCells[region - 1] = -1;
      this.seeded = region - 1;
    }

    void step(double deltaMs) {
      final float dt = (float) (deltaMs / 1000.0);
      final float meanLife = life.getValuef();
      final float flickerTarget = flicker.getValuef();
      final float growthRate = growth.getValuef();
      final float spreadProb = spread.getValuef() * dt;
      final float releaseProb = (1f - hold.getValuef()) * dt;
      final float shimmerAmt = shimmer.getValuef();

      // Spawn transient flicker to hold steady-state coverage ~= Flicker.
      //    Expected spawns/frame = coverage * size * (dt / meanLife)
      float expected = flickerTarget * this.size * (dt * 1000f / meanLife);
      int spawns = (int) expected;
      if (random.nextFloat() < (expected - spawns)) {
        ++spawns;
      }
      for (int s = 0; s < spawns; ++s) {
        int i = random.nextInt(this.size);
        this.flickerLife[i] = meanLife * (0.5f + random.nextFloat());
        this.flickerBright[i] = 0.3f + 0.7f * random.nextFloat();
      }

      // Age flicker
      float ms = (float) deltaMs;
      for (int i = 0; i < this.size; ++i) {
        if (this.flickerLife[i] > 0) {
          this.flickerLife[i] -= ms;
        }
      }

      // Commitment dynamics
      for (int i = 0; i < this.size; ++i) {
        if (this.owner[i] != 0) {
          // Resolve toward full commitment
          if (this.commit[i] < 1f) {
            this.commit[i] = Math.min(1f, this.commit[i] + growthRate * dt);
          }
          // Stochastic release (Hold < 1): the field never fully decides
          if (releaseProb > 0 && random.nextFloat() < releaseProb && !isSeed(i)) {
            this.owner[i] = 0;
            this.commit[i] = 0f;
            continue;
          }
          // Avalanche: infect neighbors probabilistically
          if (spreadProb > 0 && this.commit[i] > 0.5f) {
            for (int dir = 0; dir < 4; ++dir) {
              int n = neighbor(i, dir);
              if (n >= 0 && this.owner[n] == 0 && random.nextFloat() < spreadProb) {
                this.owner[n] = this.owner[i];
                this.commit[n] = 0.01f;
              }
            }
          }
        }
      }

      // Boundary shimmer: frontier pixels between two different regions
      //    flicker in allegiance - the handshake stays undecided.
      if (shimmerAmt > 0) {
        float swapProb = shimmerAmt * 4f * dt;
        for (int i = 0; i < this.size; ++i) {
          byte me = this.owner[i];
          if (me == 0) continue;
          for (int dir = 0; dir < 4; ++dir) {
            int n = neighbor(i, dir);
            if (n >= 0 && this.owner[n] != 0 && this.owner[n] != me) {
              if (!isSeed(i) && random.nextFloat() < swapProb) {
                this.owner[i] = this.owner[n];
                this.commit[i] *= 0.6f; // exchanging pixels re-opens the question
              }
              break;
            }
          }
        }
      }
    }

    boolean isSeed(int i) {
      for (int s = 0; s < this.seeded; ++s) {
        if (this.seedCells[s] == i) {
          return true;
        }
      }
      return false;
    }

    boolean isFrontier(int i) {
      byte me = this.owner[i];
      if (me == 0) return false;
      for (int dir = 0; dir < 4; ++dir) {
        int n = neighbor(i, dir);
        if (n >= 0 && this.owner[n] != 0 && this.owner[n] != me) {
          return true;
        }
      }
      return false;
    }

    int color(int i, double nowMs) {
      float commitLvl = this.commit[i];
      float flickerLvl = (this.flickerLife[i] > 0) ? this.flickerBright[i] : 0f;

      // Uncommitted: achromatic sub-perceptual texture
      float b = flickerLvl * level.getValuef();
      float h = 0f, s = 0f;

      if (this.owner[i] != 0) {
        h = (hue.getValuef() + (this.owner[i] - 1) * hueSplit.getValuef()) % 360f;
        s = sat.getValuef();
        float cb = commitLvl * resolve.getValuef();
        if (isFrontier(i)) {
          // Fast indecisive flicker on the negotiated boundary
          float wobble = 0.55f + 0.45f * (float) Math.sin(nowMs * 0.02 + i * 1.7);
          cb *= (1f - shimmer.getValuef() * (1f - wobble));
        }
        b = Math.max(b, cb);
      }
      return LXColor.hsb(h, s, Math.min(100f, b));
    }
  }

  public Nucleation(LX lx) {
    super(lx);
    addParameter("Flicker", this.flicker);
    addParameter("Level", this.level);
    addParameter("Life", this.life);
    addParameter("Seeds", this.seeds);
    addParameter("Growth", this.growth);
    addParameter("Spread", this.spread);
    addParameter("Hold", this.hold);
    addParameter("Resolve", this.resolve);
    addParameter("Shimmer", this.shimmer);
    addParameter("Hue", this.hue);
    addParameter("HueSplit", this.hueSplit);
    addParameter("Sat", this.sat);
    addParameter("Reset", this.reset);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.seeds) {
      syncSeeds(this.cubeLattice);
      syncSeeds(this.cylLattice);
    }
  }

  private void syncSeeds(Lattice lattice) {
    int target = this.seeds.getValuei();
    while (lattice.seeded < target) {
      lattice.addSeed(lattice.seeded + 1);
    }
    while (lattice.seeded > target) {
      lattice.removeSeed(lattice.seeded);
    }
  }

  private void reseed() {
    this.cubeLattice.clear();
    this.cylLattice.clear();
    syncSeeds(this.cubeLattice);
    syncSeeds(this.cylLattice);
  }

  private double nowMs = 0;

  @Override
  protected void render(double deltaMs) {
    this.nowMs += deltaMs;

    this.cubeLattice.step(deltaMs);
    this.cylLattice.step(deltaMs);

    Cube cube = Apotheneum.cube;
    if (cube != null) {
      int f = 0;
      for (Face face : cube.exterior.faces) {
        int xOffset = f * Apotheneum.GRID_WIDTH;
        for (int row = 0; row < face.rows.length; row++) {
          Row r = face.rows[row];
          for (int col = 0; col < Apotheneum.GRID_WIDTH; col++) {
            colors[r.points[col].index] = this.cubeLattice.color(
              this.cubeLattice.idx(xOffset + col, row), this.nowMs);
          }
        }
        ++f;
      }
    }

    Cylinder cylinder = Apotheneum.cylinder;
    if (cylinder != null) {
      Ring[] rings = cylinder.exterior.rings;
      for (int row = 0; row < rings.length; row++) {
        Ring ring = rings[row];
        for (int col = 0; col < ring.points.length; col++) {
          colors[ring.points[col].index] = this.cylLattice.color(
            this.cylLattice.idx(col % CYL_W, row % CYL_H), this.nowMs);
        }
      }
    }

    copyExterior();
  }
}
