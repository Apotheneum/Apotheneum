package apotheneum.video;

import apotheneum.Apotheneum;

/**
 * What the video output reads from: one cube face, or all four stitched into a
 * single continuous strip that wraps the cube.
 *
 * <p>Faces are listed front, right, back, left — clockwise viewed from above,
 * matching the order {@link apotheneum.Apotheneum.Cube.Orientation} builds its
 * columns in, so a perimeter column index here means the same thing it does
 * elsewhere in the codebase.
 */
public enum VideoSource {

  EXTERIOR_FRONT("Ext Front", "cubeFrontExterior"),
  EXTERIOR_RIGHT("Ext Right", "cubeRightExterior"),
  EXTERIOR_BACK("Ext Back", "cubeBackExterior"),
  EXTERIOR_LEFT("Ext Left", "cubeLeftExterior"),
  INTERIOR_FRONT("Int Front", "cubeFrontInterior"),
  INTERIOR_RIGHT("Int Right", "cubeRightInterior"),
  INTERIOR_BACK("Int Back", "cubeBackInterior"),
  INTERIOR_LEFT("Int Left", "cubeLeftInterior"),

  EXTERIOR_PERIMETER("Ext Perimeter",
    "cubeFrontExterior", "cubeRightExterior", "cubeBackExterior", "cubeLeftExterior"),
  INTERIOR_PERIMETER("Int Perimeter",
    "cubeFrontInterior", "cubeRightInterior", "cubeBackInterior", "cubeLeftInterior");

  /** Widest source, in LED columns — four faces wrapped. */
  public static final int MAX_WIDTH = 4 * Apotheneum.GRID_WIDTH;

  public final String[] tags;

  private final String label;

  private VideoSource(String label, String... tags) {
    this.label = label;
    this.tags = tags;
  }

  /** Width of this source in LED columns. */
  public int width() {
    return this.tags.length * Apotheneum.GRID_WIDTH;
  }

  @Override
  public String toString() {
    return this.label;
  }

}
