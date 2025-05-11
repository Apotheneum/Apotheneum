/**
 * Copyright 2023 Dan Oved
 */

package apotheneum.doved.utils;

import heronarts.lx.transform.LXVector;

public class Kaleidoscope {
  public static void kaleidoscopicShift(float x, float y, float z,
      float cx, float cy, float cz,
      float rotateAngle,
      float rotatePhi,
      float segments, LXVector result) {
    // algorithm source:
    // https://danielilett.com/2020-02-19-tut3-8-crazy-kaleidoscopes/

    // these units are 0-1, lets convert this xyz to polar coordinates, centered at
    // 0.5,0.5,0.5:
    // first half them offset from 0.5, 0.5 ,0.5:
    if (segments <= 1) {
      result.x = x;
      result.y = y;
      result.z = z;
      return;
    }

    // center around middle
    x = x - cx;
    y = y - cy;
    z = z - cz;

    // now convert to polar coordinates:
    float r = (float) Math.sqrt(x * x + y * y + z * z);
    float angle = (float) Math.atan2(y, x) + rotateAngle;
    float phi = (float) Math.acos(z / r) + rotatePhi;

    // We also need to know the angle taken up by a single segment.
    // We’re going to use this to determine which segment the current pixel is in,
    // then ‘reflect’ the pixel position across the segment boundaries until we are
    // in the ‘first’ segment. Let’s go step by step:

    // Calculate segment angle amount, which is 2 * PI / segments:
    float segmentAngle = (float) (2 * Math.PI / segments);

    // We’re working in radians rather than degrees, so there are 2π radians in a
    // full circle.
    // what we must do is divide that by _SegmentCount to get the segmentAngle.

    // Next, we take the pixel’s angle and subtract segmentAngle until we are inside
    // the ‘first segment’. It’s essentially a modulus operation:
    // Calculate which segment this angle is in:
    angle -= segmentAngle * Math.floor(angle / segmentAngle);

    // Now that we know our position relative to a single segment, let’s talk about
    // what one image segment
    // looks like. The full image will look like a single segment copy-pasted in a
    // circle, where each segment
    // is a wedge shape. If we converted back to Cartesian coordinates now, then the
    // kaleidoscope won’t look
    // right - there won’t be any reflection. Therefore, each segment must reflect
    // itself through the middle.
    // Using the min function, we’ll keep the angle unchanged if it is less than
    // halfway through a segment,
    // otherwise we’ll mirror it across the centre of the segment by subtracting it
    // from segmentAngle.

    // Each segment contains one reflection.
    angle = Math.min(angle, segmentAngle - angle);
    phi = Math.min(phi, segmentAngle - phi);

    // Now we can convert back to Cartesian coordinates. We’ll do the inverse of all
    // the transformations we made previously:
    // Convert back to Cartesian coordinates:
    result.x = (float) (r * Math.sin(phi) * Math.cos(angle)) + 0.5f;
    result.y = (float) (r * Math.sin(phi) * Math.sin(angle)) + 0.5f;
    result.z = (float) (r * Math.cos(phi)) + 0.5f;
  }

  // takes in argument for lambda expression of LXComponent's addParameter,
  // and adds all the kaleidoscope parameters to the component;
  public final KaleidoscopeParams params = new KaleidoscopeParams();

  // in memory vector to avoid allocations on each loop iteration
  private LXVector deformed = new LXVector(0.0f, 0.0f, 0.0f);

  public LXVector deform(float x, float y, float z) {
    float segments = this.params.segments.getValuef();
    float krtheta = this.params.rotateTheta.getValuef();
    float krphi = this.params.rotatePhi.getValuef();
    float tx = this.params.x.getValuef();
    float ty = this.params.y.getValuef();
    float tz = this.params.z.getValuef();

    kaleidoscopicShift(x, y, z, tx, ty, tz, krtheta, krphi, segments, deformed);

    return deformed;
  }

}
