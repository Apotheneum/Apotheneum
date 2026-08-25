package apotheneum.doved.components;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A static 3D line mesh loaded from a Wavefront OBJ resource.
 *
 * Only vertex ({@code v}) and polyline ({@code l}) records are read; face,
 * normal and material records are ignored. Positions live in flat float arrays
 * and edges as parallel index arrays, so a render pass can walk the geometry
 * without touching an object graph or allocating.
 */
public class Wireframe {

  /** Stand-in used when a resource fails to load, so rendering degrades to black rather than throwing. */
  public static final Wireframe EMPTY =
    new Wireframe(new float[0], new float[0], new float[0], new int[0], new int[0], new String[0]);

  public final float[] x;
  public final float[] y;
  public final float[] z;

  public final int[] edgeA;
  public final int[] edgeB;

  /** Name of the OBJ object each edge was declared under, empty for edges outside any {@code o} record. */
  public final String[] edgeGroup;

  public final int vertexCount;
  public final int edgeCount;

  private Wireframe(float[] x, float[] y, float[] z, int[] edgeA, int[] edgeB, String[] edgeGroup) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.edgeA = edgeA;
    this.edgeB = edgeB;
    this.edgeGroup = edgeGroup;
    this.vertexCount = x.length;
    this.edgeCount = edgeA.length;
  }

  /**
   * Indices of every edge whose group name starts with {@code prefix}, or of all
   * edges when the prefix is empty. Intended for construction time, so that a
   * render pass can walk a pre-selected subset without filtering per frame.
   */
  public int[] edgeGroupIndices(String prefix) {
    int matches = 0;
    for (int i = 0; i < this.edgeCount; ++i) {
      if (this.edgeGroup[i].startsWith(prefix)) {
        ++matches;
      }
    }
    final int[] indices = new int[matches];
    int next = 0;
    for (int i = 0; i < this.edgeCount; ++i) {
      if (this.edgeGroup[i].startsWith(prefix)) {
        indices[next++] = i;
      }
    }
    return indices;
  }

  /**
   * Indices of every vertex touched by an edge in the group, de-duplicated and
   * ascending. Intended for construction time, so a dot render can walk the
   * sculpture's own LED positions without filtering per frame.
   */
  public int[] vertexGroupIndices(String prefix) {
    final boolean[] used = new boolean[this.vertexCount];
    int count = 0;
    for (int i = 0; i < this.edgeCount; ++i) {
      if (!this.edgeGroup[i].startsWith(prefix)) {
        continue;
      }
      for (int vertex : new int[] { this.edgeA[i], this.edgeB[i] }) {
        if (!used[vertex]) {
          used[vertex] = true;
          ++count;
        }
      }
    }
    final int[] indices = new int[count];
    int next = 0;
    for (int i = 0; i < this.vertexCount; ++i) {
      if (used[i]) {
        indices[next++] = i;
      }
    }
    return indices;
  }

  /**
   * Loads a wireframe from an OBJ on the classpath.
   *
   * @param resourcePath absolute classpath location, e.g. {@code /models/robot-heart.obj}
   * @throws IOException if the resource is missing or malformed
   */
  public static Wireframe load(String resourcePath) throws IOException {
    final List<float[]> vertices = new ArrayList<float[]>();
    final List<int[]> edges = new ArrayList<int[]>();
    final List<String> edgeGroups = new ArrayList<String>();
    String group = "";

    try (InputStream stream = Wireframe.class.getResourceAsStream(resourcePath)) {
      if (stream == null) {
        throw new IOException("Wireframe resource not found: " + resourcePath);
      }
      final BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        ++lineNumber;
        final String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
          continue;
        }
        final String[] fields = trimmed.split("\\s+");
        try {
          if ("o".equals(fields[0]) || "g".equals(fields[0])) {
            group = (fields.length > 1) ? fields[1] : "";
          } else if ("v".equals(fields[0])) {
            vertices.add(new float[] {
              Float.parseFloat(fields[1]),
              Float.parseFloat(fields[2]),
              Float.parseFloat(fields[3])
            });
          } else if ("l".equals(fields[0])) {
            // An OBJ polyline is a chain of 1-based vertex indices; store it as its segments.
            for (int i = 2; i < fields.length; ++i) {
              edges.add(new int[] {
                Integer.parseInt(fields[i - 1]) - 1,
                Integer.parseInt(fields[i]) - 1
              });
              edgeGroups.add(group);
            }
          }
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException x) {
          throw new IOException(resourcePath + ":" + lineNumber + " malformed record: " + trimmed, x);
        }
      }
    }

    final int vertexCount = vertices.size();
    final float[] vx = new float[vertexCount];
    final float[] vy = new float[vertexCount];
    final float[] vz = new float[vertexCount];
    for (int i = 0; i < vertexCount; ++i) {
      final float[] v = vertices.get(i);
      vx[i] = v[0];
      vy[i] = v[1];
      vz[i] = v[2];
    }

    final int[] edgeA = new int[edges.size()];
    final int[] edgeB = new int[edges.size()];
    for (int i = 0; i < edges.size(); ++i) {
      final int[] e = edges.get(i);
      if ((e[0] < 0) || (e[0] >= vertexCount) || (e[1] < 0) || (e[1] >= vertexCount)) {
        throw new IOException(resourcePath + " edge references vertex outside 1.." + vertexCount);
      }
      edgeA[i] = e[0];
      edgeB[i] = e[1];
    }

    return new Wireframe(vx, vy, vz, edgeA, edgeB, edgeGroups.toArray(new String[0]));
  }

}
