package apotheneum.doved.ui;

import static org.lwjgl.bgfx.BGFX.BGFX_STATE_PT_LINES;
import static org.lwjgl.bgfx.BGFX.bgfx_set_transform;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.lwjgl.system.MemoryUtil;

import apotheneum.doved.patterns.Raybeam;
import heronarts.glx.VertexBuffer;
import heronarts.glx.VertexDeclaration;
import heronarts.glx.View;
import heronarts.glx.shader.ShaderProgram;
import heronarts.glx.ui.UI3dComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXNormalizationBounds;
import heronarts.lx.transform.LXMatrix;

/** Draws Raybeam's aim as a fixed-width line in the 3D preview. */
public class UIRaybeam extends UI3dComponent {

  private static final double TWO_PI = 2 * Math.PI;

  // This is the same line primitive state used by Chromatik's UIAxes component.
  private static final long LINE_STATE =
    ShaderProgram.DEFAULT_BGFX_STATE | BGFX_STATE_PT_LINES;

  private final Raybeam raybeam;
  private final boolean auxiliary;
  private VertexBuffer vertices;
  private final LXMatrix viewTransform = new LXMatrix();
  private FloatBuffer viewMatrixBuffer;

  public UIRaybeam(Raybeam raybeam, boolean auxiliary) {
    this.raybeam = raybeam;
    this.auxiliary = auxiliary;
  }

  private void initializeBuffers(heronarts.glx.ui.UI ui) {
    this.vertices = new VertexBuffer(
      ui.lx, 2, VertexDeclaration.Attribute.POSITION) {
      @Override
      protected void bufferData(ByteBuffer buffer) {
        putVertex(0, 0, 0);
        putVertex(1, 0, 0);
      }
    };
    this.viewMatrixBuffer = MemoryUtil.memAllocFloat(16);
  }

  @Override
  protected void onDraw(heronarts.glx.ui.UI ui, View view) {
    if (!this.raybeam.showRay.isOn() || !isPatternVisible()) {
      return;
    }
    if (this.vertices == null) {
      initializeBuffers(ui);
    }

    final double originX = this.raybeam.originX.getValue();
    final double originY = this.raybeam.originY.getValue();
    final double originZ = this.raybeam.originZ.getValue();
    final double azimuth =
      (this.raybeam.azimuth.getValue() + this.raybeam.azimuthOffset.getValue()) * TWO_PI;
    final double elevation = this.raybeam.elevation.getValue();
    final double cosElevation = Math.cos(elevation);
    final double directionX = Math.sin(azimuth) * cosElevation;
    final double directionY = Math.sin(elevation);
    final double directionZ = Math.cos(azimuth) * cosElevation;
    final double length = rayExitDistance(
      originX, originY, originZ, directionX, directionY, directionZ);

    final LXNormalizationBounds bounds =
      this.raybeam.getModelView().getNormalizationBounds();
    final LXModel orientation = bounds.getOrientation();
    if (orientation != null) {
      this.viewTransform.set(orientation.transform);
    } else {
      this.viewTransform.identity().translate(bounds.xMin, bounds.yMin, bounds.zMin);
    }
    this.viewTransform.scale(bounds.xRange, bounds.yRange, bounds.zRange);
    this.viewTransform.multiply(
      (float) (directionX * length), 0, 0, (float) originX,
      (float) (directionY * length), 1, 0, (float) originY,
      (float) (directionZ * length), 0, 1, (float) originZ,
      0, 0, 0, 1);
    this.viewTransform.put(
      this.viewMatrixBuffer, LXMatrix.BufferOrder.COLUMN_MAJOR);
    bgfx_set_transform(this.viewMatrixBuffer);

    ui.lx.program.uniformFill.submit(
      view, LINE_STATE, LXColor.RED, this.vertices);
  }

  private boolean isPatternVisible() {
    final LXPatternEngine engine = this.raybeam.getEngine();
    if (engine == null) {
      return false;
    }
    if (this.auxiliary) {
      return this.raybeam.auxActive.isOn();
    }
    if (this.raybeam.cueActive.isOn()) {
      return true;
    }
    if (engine.isComposite()) {
      return this.raybeam.getCompositeDampingLevel() > 0;
    }
    return (engine.getActivePattern() == this.raybeam)
      || (engine.getNextPattern() == this.raybeam);
  }

  static double rayExitDistance(
    double originX, double originY, double originZ,
    double directionX, double directionY, double directionZ) {

    double distance = Double.POSITIVE_INFINITY;
    if (directionX > 0) {
      distance = Math.min(distance, (1 - originX) / directionX);
    } else if (directionX < 0) {
      distance = Math.min(distance, -originX / directionX);
    }
    if (directionY > 0) {
      distance = Math.min(distance, (1 - originY) / directionY);
    } else if (directionY < 0) {
      distance = Math.min(distance, -originY / directionY);
    }
    if (directionZ > 0) {
      distance = Math.min(distance, (1 - originZ) / directionZ);
    } else if (directionZ < 0) {
      distance = Math.min(distance, -originZ / directionZ);
    }
    return Double.isFinite(distance) ? Math.max(0, distance) : 0;
  }

  @Override
  public void dispose() {
    if (this.vertices != null) {
      this.vertices.dispose();
      this.vertices = null;
    }
    if (this.viewMatrixBuffer != null) {
      MemoryUtil.memFree(this.viewMatrixBuffer);
      this.viewMatrixBuffer = null;
    }
    super.dispose();
  }
}
