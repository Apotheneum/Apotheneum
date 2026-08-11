package apotheneum;

import org.junit.jupiter.api.AfterEach;

import heronarts.lx.LX;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;

/** Minimal reusable headless-LX fixture for Apotheneum component tests. */
public abstract class HeadlessLxTest {

  private LX lx;

  protected LX newHeadlessLx() {
    this.lx = new LX(newModel());
    return this.lx;
  }

  protected LXModel newModel() {
    // Immutable-model LX construction does not reset LXPoint's JVM-global indices.
    return new GridModel(2, 2).reindexPoints();
  }

  @AfterEach
  final void disposeLx() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }
}
