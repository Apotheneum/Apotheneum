/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package apotheneum.mcslee;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import apotheneum.Apotheneum;
import apotheneum.ApotheneumPattern;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory("Apotheneum/mcslee")
@LXComponent.Name("DNA Letters")
@LXComponent.Description("ACTG base pair letters on the cube faces")
public class DNALetters extends ApotheneumPattern implements ApotheneumPattern.Midi {

  public static final int LETTER_SIZE = 5;
  public static final int WIDTH = Apotheneum.GRID_WIDTH / LETTER_SIZE;
  public static final int HEIGHT = Apotheneum.GRID_HEIGHT / LETTER_SIZE;

  public final int[] A = {
    0, 1, 1, 1, 0,
    0, 1, 0, 1, 0,
    0, 1, 1, 1, 0,
    0, 1, 0, 1, 0,
    0, 1, 0, 1, 0
  };

  public final int[] C = {
    0, 1, 1, 1, 0,
    1, 0, 0, 0, 0,
    1, 0, 0, 0, 0,
    1, 0, 0, 0, 0,
    0, 1, 1, 1, 0
  };

  public final int[] T = {
    1, 1, 1, 1, 1,
    0, 0, 1, 0, 0,
    0, 0, 1, 0, 0,
    0, 0, 1, 0, 0,
    0, 0, 1, 0, 0
  };

  public final int[] G = {
    0, 1, 1, 1, 0,
    1, 0, 0, 0, 0,
    1, 0, 1, 1, 0,
    1, 0, 0, 0, 1,
    0, 1, 1, 1, 0
  };

  public final int[][] LETTERS = { A, C, T, G };

  private final boolean[] active = new boolean[WIDTH * HEIGHT];
  private final double[] level = new double[WIDTH * HEIGHT];
  private final int[] state = new int[WIDTH * HEIGHT];

  public final TriggerParameter reset =
    new TriggerParameter("Reset", this::reset)
    .setDescription("Resets the letters");

  public final TriggerParameter update =
    new TriggerParameter("Update", this::update)
    .setDescription("Updates the letters");

  public final CompoundDiscreteParameter numUpdate =
    new CompoundDiscreteParameter("Num", 0, this.state.length + 1)
    .setDescription("Number of places to change on each update");

  private final List<Integer> shuffle = new ArrayList<>();

  public DNALetters(LX lx) {
    super(lx);
    addParameter("reset", this.reset);
    addParameter("update", this.update);
    addParameter("numUpdate", this.numUpdate);
    randomize();
    for (int i = 0; i < this.state.length; ++i) {
      this.shuffle.add(i);
    }
  }

  private void reset() {
    Arrays.fill(this.active, false);
  }

  private void randomize() {
    for (int i = 0; i < this.state.length; ++i) {
      this.state[i] = LXUtils.randomi(LETTERS.length-1);
    }
  }

  private void update() {
    Collections.shuffle(this.shuffle);
    final int numUpdate = this.numUpdate.getValuei();
    for (int i = 0; i < numUpdate; ++i) {
      int idx = this.shuffle.get(i);
      this.active[idx] = true;
      this.state[idx] = (this.state[idx] + LXUtils.randomi(LETTERS.length-2)) % LETTERS.length;
    }
  }

  private void renderLetter(double level, int[] letter, int x, int y, Apotheneum.Cube.Face face) {
    int z = 0;
    final int color = LXColor.grayn(level);
    for (int j = 0; j < LETTER_SIZE; ++j) {
      for (int i = 0; i < LETTER_SIZE; ++i) {
        if (letter[z++] > 0) {
          int idx = (x+i) * Apotheneum.GRID_HEIGHT + (y+j);
          colors[face.model.points[idx].index] = color;
        }
      }
    }
  }

  @Override
  protected void render(double deltaMs) {
    setColors(LXColor.BLACK);

    for (int i = 0; i < this.level.length; ++i) {
      if (this.active[i]) {
        this.level[i] = LXUtils.min(1, this.level[i] + deltaMs / 500.);
      }
    }

    for (int x = 0; x < WIDTH; ++x) {
      for (int y = 0; y < HEIGHT; ++y) {
        final int idx = x * HEIGHT + y;
        if (this.active[idx]) {
          renderLetter(
            this.level[idx],
            LETTERS[this.state[idx]],
            x * LETTER_SIZE,
            y * LETTER_SIZE,
            Apotheneum.cube.exterior.front
          );
        }
      }
    }

    // Blit onto the other faces
    copy(Apotheneum.cube.exterior.front, Apotheneum.cube.exterior.right);
    copy(Apotheneum.cube.exterior.front, Apotheneum.cube.exterior.left);
    copy(Apotheneum.cube.exterior.front, Apotheneum.cube.exterior.back);
    copyCubeExterior();
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    if (note.getPitch() == 0) {
      reset();
    } else {
      update();
    }
  }

}
