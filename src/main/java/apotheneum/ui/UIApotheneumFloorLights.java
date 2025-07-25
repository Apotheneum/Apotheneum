/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
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

package apotheneum.ui;

import static org.lwjgl.bgfx.BGFX.*;

import java.nio.ByteBuffer;
import java.util.List;

import heronarts.glx.DynamicVertexBuffer;
import heronarts.glx.VertexBuffer;
import heronarts.glx.VertexDeclaration;
import heronarts.glx.View;
import heronarts.glx.shader.ShaderProgram;
import heronarts.glx.shader.VertexFill;
import heronarts.glx.ui.UI;
import heronarts.glx.ui.UI3dComponent;
import heronarts.lx.LXEngine;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.utils.LXUtils;

public class UIApotheneumFloorLights extends UI3dComponent {

  private static final float[] POSITION_DATA = {
    -70f,123.20765814496f,1f,-70f,123.20765814496f,2f,-54f,94.396769012504f,1f,-70f,123.20765814496f,2f,-54f,94.396769012504f,1f,-54f,94.396769012504f,2f,-54f,94.396769012504f,1f,-54f,94.396769012504f,2f,-38f,123.20765814496f,1f,-54f,94.396769012504f,2f,-38f,123.20765814496f,1f,-38f,123.20765814496f,2f,-38f,123.20765814496f,1f,-38f,123.20765814496f,2f,-70f,123.20765814496f,1f,-38f,123.20765814496f,2f,-70f,123.20765814496f,1f,-70f,123.20765814496f,2f,-52f,94.396769012504f,1f,-52f,94.396769012504f,2f,-20f,94.396769012504f,1f,-52f,94.396769012504f,2f,-20f,94.396769012504f,1f,-20f,94.396769012504f,2f,-20f,94.396769012504f,1f,-20f,94.396769012504f,2f,-36f,123.20765814496f,1f,-20f,94.396769012504f,2f,-36f,123.20765814496f,1f,-36f,123.20765814496f,2f,-36f,123.20765814496f,1f,-36f,123.20765814496f,2f,-52f,94.396769012504f,1f,-36f,123.20765814496f,2f,-52f,94.396769012504f,1f,-52f,94.396769012504f,2f,-34f,123.20765814496f,1f,-34f,123.20765814496f,2f,-18f,94.396769012504f,1f,-34f,123.20765814496f,2f,-18f,94.396769012504f,1f,-18f,94.396769012504f,2f,-18f,94.396769012504f,1f,-18f,94.396769012504f,2f,-2f,123.20765814496f,1f,-18f,94.396769012504f,2f,-2f,123.20765814496f,1f,-2f,123.20765814496f,2f,-2f,123.20765814496f,1f,-2f,123.20765814496f,2f,-34f,123.20765814496f,1f,-2f,123.20765814496f,2f,-34f,123.20765814496f,1f,-34f,123.20765814496f,2f,-16f,94.396769012504f,1f,-16f,94.396769012504f,2f,16f,94.396769012504f,1f,-16f,94.396769012504f,2f,16f,94.396769012504f,1f,16f,94.396769012504f,2f,16f,94.396769012504f,1f,16f,94.396769012504f,2f,0f,123.20765814496f,1f,16f,94.396769012504f,2f,0f,123.20765814496f,1f,0f,123.20765814496f,2f,0f,123.20765814496f,1f,0f,123.20765814496f,2f,-16f,94.396769012504f,1f,0f,123.20765814496f,2f,-16f,94.396769012504f,1f,-16f,94.396769012504f,2f,2f,123.20765814496f,1f,2f,123.20765814496f,2f,18f,94.396769012504f,1f,2f,123.20765814496f,2f,18f,94.396769012504f,1f,18f,94.396769012504f,2f,18f,94.396769012504f,1f,18f,94.396769012504f,2f,34f,123.20765814496f,1f,18f,94.396769012504f,2f,34f,123.20765814496f,1f,34f,123.20765814496f,2f,34f,123.20765814496f,1f,34f,123.20765814496f,2f,2f,123.20765814496f,1f,34f,123.20765814496f,2f,2f,123.20765814496f,1f,2f,123.20765814496f,2f,20f,94.396769012504f,1f,20f,94.396769012504f,2f,52f,94.396769012504f,1f,20f,94.396769012504f,2f,52f,94.396769012504f,1f,52f,94.396769012504f,2f,52f,94.396769012504f,1f,52f,94.396769012504f,2f,36f,123.20765814496f,1f,52f,94.396769012504f,2f,36f,123.20765814496f,1f,36f,123.20765814496f,2f,36f,123.20765814496f,1f,36f,123.20765814496f,2f,20f,94.396769012504f,1f,36f,123.20765814496f,2f,20f,94.396769012504f,1f,20f,94.396769012504f,2f,38f,123.20765814496f,1f,38f,123.20765814496f,2f,54f,94.396769012504f,1f,38f,123.20765814496f,2f,54f,94.396769012504f,1f,54f,94.396769012504f,2f,54f,94.396769012504f,1f,54f,94.396769012504f,2f,70f,123.20765814496f,1f,54f,94.396769012504f,2f,70f,123.20765814496f,1f,70f,123.20765814496f,2f,70f,123.20765814496f,1f,70f,123.20765814496f,2f,38f,123.20765814496f,1f,70f,123.20765814496f,2f,38f,123.20765814496f,1f,38f,123.20765814496f,2f,-52f,92.030743608719f,1f,-52f,92.030743608719f,2f,-36f,63.219854476264f,1f,-52f,92.030743608719f,2f,-36f,63.219854476264f,1f,-36f,63.219854476264f,2f,-36f,63.219854476264f,1f,-36f,63.219854476264f,2f,-20f,92.030743608719f,1f,-36f,63.219854476264f,2f,-20f,92.030743608719f,1f,-20f,92.030743608719f,2f,-20f,92.030743608719f,1f,-20f,92.030743608719f,2f,-52f,92.030743608719f,1f,-20f,92.030743608719f,2f,-52f,92.030743608719f,1f,-52f,92.030743608719f,2f,-34f,63.219854476264f,1f,-34f,63.219854476264f,2f,-2f,63.219854476264f,1f,-34f,63.219854476264f,2f,-2f,63.219854476264f,1f,-2f,63.219854476264f,2f,-2f,63.219854476264f,1f,-2f,63.219854476264f,2f,-18f,92.030743608719f,1f,-2f,63.219854476264f,2f,-18f,92.030743608719f,1f,-18f,92.030743608719f,2f,-18f,92.030743608719f,1f,-18f,92.030743608719f,2f,-34f,63.219854476264f,1f,-18f,92.030743608719f,2f,-34f,63.219854476264f,1f,-34f,63.219854476264f,2f,-16f,92.030743608719f,1f,-16f,92.030743608719f,2f,0f,63.219854476264f,1f,-16f,92.030743608719f,2f,0f,63.219854476264f,1f,0f,63.219854476264f,2f,0f,63.219854476264f,1f,0f,63.219854476264f,2f,16f,92.030743608719f,1f,0f,63.219854476264f,2f,16f,92.030743608719f,1f,16f,92.030743608719f,2f,16f,92.030743608719f,1f,16f,92.030743608719f,2f,-16f,92.030743608719f,1f,16f,92.030743608719f,2f,-16f,92.030743608719f,1f,-16f,92.030743608719f,2f,2f,63.219854476264f,1f,2f,63.219854476264f,2f,34f,63.219854476264f,1f,2f,63.219854476264f,2f,34f,63.219854476264f,1f,34f,63.219854476264f,2f,34f,63.219854476264f,1f,34f,63.219854476264f,2f,18f,92.030743608719f,1f,34f,63.219854476264f,2f,18f,92.030743608719f,1f,18f,92.030743608719f,2f,18f,92.030743608719f,1f,18f,92.030743608719f,2f,2f,63.219854476264f,1f,18f,92.030743608719f,2f,2f,63.219854476264f,1f,2f,63.219854476264f,2f,20f,92.030743608719f,1f,20f,92.030743608719f,2f,36f,63.219854476264f,1f,20f,92.030743608719f,2f,36f,63.219854476264f,1f,36f,63.219854476264f,2f,36f,63.219854476264f,1f,36f,63.219854476264f,2f,52f,92.030743608719f,1f,36f,63.219854476264f,2f,52f,92.030743608719f,1f,52f,92.030743608719f,2f,52f,92.030743608719f,1f,52f,92.030743608719f,2f,20f,92.030743608719f,1f,52f,92.030743608719f,2f,20f,92.030743608719f,1f,20f,92.030743608719f,2f,-34f,60.85382907248f,1f,-34f,60.85382907248f,2f,-18f,32.042939940024f,1f,-34f,60.85382907248f,2f,-18f,32.042939940024f,1f,-18f,32.042939940024f,2f,-18f,32.042939940024f,1f,-18f,32.042939940024f,2f,-2f,60.85382907248f,1f,-18f,32.042939940024f,2f,-2f,60.85382907248f,1f,-2f,60.85382907248f,2f,-2f,60.85382907248f,1f,-2f,60.85382907248f,2f,-34f,60.85382907248f,1f,-2f,60.85382907248f,2f,-34f,60.85382907248f,1f,-34f,60.85382907248f,2f,-16f,32.042939940024f,1f,-16f,32.042939940024f,2f,16f,32.042939940024f,1f,-16f,32.042939940024f,2f,16f,32.042939940024f,1f,16f,32.042939940024f,2f,16f,32.042939940024f,1f,16f,32.042939940024f,2f,0f,60.85382907248f,1f,16f,32.042939940024f,2f,0f,60.85382907248f,1f,0f,60.85382907248f,2f,0f,60.85382907248f,1f,0f,60.85382907248f,2f,-16f,32.042939940024f,1f,0f,60.85382907248f,2f,-16f,32.042939940024f,1f,-16f,32.042939940024f,2f,2f,60.85382907248f,1f,2f,60.85382907248f,2f,18f,32.042939940024f,1f,2f,60.85382907248f,2f,18f,32.042939940024f,1f,18f,32.042939940024f,2f,18f,32.042939940024f,1f,18f,32.042939940024f,2f,34f,60.85382907248f,1f,18f,32.042939940024f,2f,34f,60.85382907248f,1f,34f,60.85382907248f,2f,34f,60.85382907248f,1f,34f,60.85382907248f,2f,2f,60.85382907248f,1f,34f,60.85382907248f,2f,2f,60.85382907248f,1f,2f,60.85382907248f,2f,-16f,29.67691453624f,1f,-16f,29.67691453624f,2f,0f,0.86602540378444f,1f,-16f,29.67691453624f,2f,0f,0.86602540378444f,1f,0f,0.86602540378444f,2f,0f,0.86602540378444f,1f,0f,0.86602540378444f,2f,16f,29.67691453624f,1f,0f,0.86602540378444f,2f,16f,29.67691453624f,1f,16f,29.67691453624f,2f,16f,29.67691453624f,1f,16f,29.67691453624f,2f,-16f,29.67691453624f,1f,16f,29.67691453624f,2f,-16f,29.67691453624f,1f,-16f,29.67691453624f,2f
  };

  private static final int VERTICES_PER_TRIANGLE = 18;
  private static final int NUM_TRIANGLES = 96;
  private static final int NUM_VERTICES = VERTICES_PER_TRIANGLE * NUM_TRIANGLES;

  private final ShaderProgram program;
  private final VertexBuffer vertices;
  private final DynamicVertexBuffer colors;
  private boolean auxiliary;

  private final int[] gammaLut;

  public UIApotheneumFloorLights(UI ui, boolean auxiliary) {
    super();
    this.auxiliary = auxiliary;

    this.vertices = new VertexBuffer(ui.lx, NUM_VERTICES, VertexDeclaration.ATTRIB_POSITION) {
      @Override
      protected void bufferData(ByteBuffer buffer) {
        final LXMatrix matrix = new LXMatrix();
        for (int s = 0; s < 6; ++s) {
          matrix.identity();
          matrix.translate(245.5f, 34f, 245.5f);
          matrix.rotateY(s * Math.PI / 3.);
          matrix.rotateX(.5f * Math.PI);
          LXPoint p = new LXPoint();
          for (int i = 0; i < POSITION_DATA.length; i += 3) {
            float x = POSITION_DATA[i];
            float y = POSITION_DATA[i+1];
            float z = POSITION_DATA[i+2];
            p.set(x, y, z);
            buffer.putFloat(matrix.x(p));
            buffer.putFloat(matrix.y(p));
            buffer.putFloat(matrix.z(p));
          }
        }
      }
    };

    this.colors = new DynamicVertexBuffer(ui.lx, NUM_VERTICES, VertexDeclaration.ATTRIB_COLOR0);

    this.program = new VertexFill(ui.lx) {
      @Override
      protected void setVertexBuffers(View view) {
        bgfx_set_vertex_buffer(0, vertices.getHandle(), 0, NUM_VERTICES);
        bgfx_set_dynamic_vertex_buffer(1, colors.getHandle(), 0, NUM_VERTICES);
      }
    };

    this.gammaLut = new int[256];
    this.gammaLut[0] = 0;
    final int gammaFloor = 24;
    for (int i = 1; i < this.gammaLut.length; ++i) {
      double lerp = (i-1.) / (this.gammaLut.length-2.);
      this.gammaLut[i] = (int) Math.round(LXUtils.lerp(gammaFloor, 255, lerp));
    }
  }

  private static final long BGFX_STATE = 0
    | BGFX_STATE_WRITE_RGB
    | BGFX_STATE_WRITE_A
    | BGFX_STATE_WRITE_Z
    | BGFX_STATE_DEPTH_TEST_LESS
    | BGFX_STATE_BLEND_ALPHA;

  @Override
  public void onDraw(UI ui, View view) {
    final LXEngine.Frame frame = ui.lx.uiFrame;
    final int[] colors = frame.getColors(auxiliary);
    final List<LXModel> lights = frame.getModel().sub("hapticLights");

    if (!lights.isEmpty()) {
      final ByteBuffer colorData = this.colors.getVertexData();
      colorData.rewind();
      boolean limit = false;
      int ti = 0;
      for (LXModel section : lights) {
        for (LXPoint p : section.points) {
          if (ti++ >= NUM_TRIANGLES) {
            limit = true;
            break;
          } else {
            final int r = this.gammaLut[colors[p.index] & 0xff];
            final int g = (r * 0xd0) >> 8;
            final int b = (r * 0xb0) >> 8;
            final int abgr = LXColor.ALPHA_MASK |
              b << 16 |
              g << 8 |
              r;
            for (int i = 0; i < VERTICES_PER_TRIANGLE; ++i) {
              colorData.putInt(abgr);
            }
          }
        }
        if (limit) {
          break;
        }
      }
      colorData.flip();
      this.colors.update();
      this.program.submit(view, BGFX_STATE);
    }
  }

  @Override
  public void dispose() {
    this.vertices.dispose();
    this.colors.dispose();
    this.program.dispose();
    super.dispose();
  }
}
