package com.truesim.common.math.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class PolygonsTest {

    private static final double[] SQUARE_X = {0, 2, 2, 0};
    private static final double[] SQUARE_Y = {0, 0, 2, 2};
    /** The same square, clockwise. */
    private static final double[] CW_X = {0, 0, 2, 2};
    private static final double[] CW_Y = {0, 2, 2, 0};

    @Test
    void distancesToSegments() {
        assertThat(Polygons.length(3, 4)).isEqualTo(5);
        assertThat(Polygons.project(1, 5, 0, 0, 2, 0)).isEqualTo(0.5);
        assertThat(Polygons.project(-3, 0, 0, 0, 2, 0)).isZero();
        assertThat(Polygons.project(9, 0, 0, 0, 2, 0)).isEqualTo(1);
        assertThat(Polygons.project(9, 9, 1, 1, 1, 1)).isZero();
        assertThat(Polygons.segmentDistance(1, 5, 0, 0, 2, 0)).isEqualTo(5);
        assertThat(Polygons.segmentDistance(5, 4, 0, 0, 2, 0)).isEqualTo(5);
        assertThat(Polygons.cross(0, 0, 1, 0, 0, 1)).isPositive();
        assertThat(Polygons.cross(0, 0, 1, 0, 0, -1)).isNegative();
    }

    @Test
    void segmentsCrossOrTouch() {
        // an X
        assertThat(Polygons.crosses(0, 0, 2, 2, 0, 2, 2, 0)).isTrue();
        assertThat(Polygons.crosses(0, 2, 2, 0, 0, 0, 2, 2)).isTrue();
        // one straddles the other's line, not the other way
        assertThat(Polygons.crosses(0, -1, 0, 1, 1, 0, 2, 0)).isFalse();
        assertThat(Polygons.crosses(1, 0, 2, 0, 0, -1, 0, 1)).isFalse();
        // collinear: overlapping, apart (each comparison of the betweenness)
        assertThat(Polygons.crosses(0, 0, 2, 0, 1, 0, 3, 0)).isTrue();
        assertThat(Polygons.crosses(1, 0, 3, 0, 0, 0, 2, 0)).isTrue();
        assertThat(Polygons.crosses(0, 0, 1, 0, 2, 0, 3, 0)).isFalse();
        assertThat(Polygons.crosses(2, 0, 3, 0, 0, 0, 1, 0)).isFalse();
        assertThat(Polygons.crosses(0, 0, 0, 1, 0, 2, 0, 3)).isFalse();
        assertThat(Polygons.crosses(0, 2, 0, 3, 0, 0, 0, 1)).isFalse();
        // an end on the other segment
        assertThat(Polygons.crosses(0, 0, 2, 0, 1, 1, 1, 0)).isTrue();
        assertThat(Polygons.crosses(0, 0, 2, 0, 1, 0, 1, 1)).isTrue();
        assertThat(Polygons.crosses(1, 1, 1, 0, 0, 0, 2, 0)).isTrue();
        assertThat(Polygons.crosses(1, 0, 1, 1, 0, 0, 2, 0)).isTrue();
        // an end on the other's line but beyond it
        assertThat(Polygons.crosses(0, 0, 2, 0, 3, 1, 3, 0)).isFalse();
        assertThat(Polygons.segmentSegmentDistance(0, 0, 2, 2, 0, 2, 2, 0)).isZero();
        assertThat(Polygons.segmentSegmentDistance(0, 0, 2, 0, 0, 3, 2, 3)).isEqualTo(3);
        assertThat(Polygons.segmentSegmentDistance(0, 3, 2, 3, 0, 0, 2, 0)).isEqualTo(3);
        assertThat(Polygons.segmentSegmentDistance(0, 0, 1, 0, 5, -1, 5, 1)).isEqualTo(4);
        assertThat(Polygons.segmentSegmentDistance(5, -1, 5, 1, 0, 0, 1, 0)).isEqualTo(4);
    }

    @Test
    void polygonsAreaContainmentAndDepth() {
        assertThat(Polygons.signedArea(SQUARE_X, SQUARE_Y)).isEqualTo(4);
        assertThat(Polygons.signedArea(CW_X, CW_Y)).isEqualTo(-4);
        assertThat(Polygons.insideConvex(1, 1, SQUARE_X, SQUARE_Y)).isTrue();
        assertThat(Polygons.insideConvex(1, 1, CW_X, CW_Y)).isTrue();
        assertThat(Polygons.insideConvex(2, 1, SQUARE_X, SQUARE_Y)).isTrue();
        assertThat(Polygons.insideConvex(3, 1, SQUARE_X, SQUARE_Y)).isFalse();
        assertThat(Polygons.convexDepth(0.5, 1, SQUARE_X, SQUARE_Y)).isCloseTo(0.5, within(1e-12));
        assertThat(Polygons.convexDepth(0.5, 1, CW_X, CW_Y)).isCloseTo(0.5, within(1e-12));
        assertThat(Polygons.convexDepth(5, 1, SQUARE_X, SQUARE_Y)).isCloseTo(-3, within(1e-12));
        // a repeated corner (an edge of no length) is passed over
        double[] rx = {0, 2, 2, 2, 0};
        double[] ry = {0, 0, 0, 2, 2};
        assertThat(Polygons.convexDepth(1, 0.25, rx, ry)).isCloseTo(0.25, within(1e-12));
    }

    @Test
    void segmentsAndPolygons() {
        assertThat(Polygons.segmentPolygonDistance(1, 1, 5, 5, SQUARE_X, SQUARE_Y)).isZero();
        assertThat(Polygons.segmentPolygonDistance(5, 5, 1, 1, SQUARE_X, SQUARE_Y)).isZero();
        assertThat(Polygons.segmentPolygonDistance(-1, 1, 3, 1, SQUARE_X, SQUARE_Y)).isZero();
        assertThat(Polygons.segmentPolygonDistance(5, 0, 5, 2, SQUARE_X, SQUARE_Y)).isEqualTo(3);
    }

    @Test
    void convexPolygonsOverlap() {
        double[] ox = {1, 3, 3, 1};
        double[] oy = {1, 1, 3, 3};
        assertThat(Polygons.convexOverlap(SQUARE_X, SQUARE_Y, ox, oy, 0.01)).isTrue();
        // side by side: touching is no overlap, nor within the tolerance
        double[] tx = {2, 4, 4, 2};
        assertThat(Polygons.convexOverlap(SQUARE_X, SQUARE_Y, tx, SQUARE_Y, 0)).isFalse();
        assertThat(Polygons.convexOverlap(tx, SQUARE_Y, SQUARE_X, SQUARE_Y, 0)).isFalse();
        double[] nx = {1.995, 4, 4, 1.995};
        assertThat(Polygons.convexOverlap(SQUARE_X, SQUARE_Y, nx, SQUARE_Y, 0.01)).isFalse();
        // separated only along an axis of the second polygon (a diamond beyond the square's corner)
        double[] dx = {2.6, 3.6, 2.6, 1.6};
        double[] dy = {1.6, 2.6, 3.6, 2.6};
        assertThat(Polygons.convexOverlap(SQUARE_X, SQUARE_Y, dx, dy, 0)).isFalse();
        // a polygon with a repeated corner
        double[] rx = {1, 3, 3, 3, 1};
        double[] ry = {1, 1, 1, 3, 3};
        assertThat(Polygons.convexOverlap(rx, ry, SQUARE_X, SQUARE_Y, 0.01)).isTrue();
    }
}
