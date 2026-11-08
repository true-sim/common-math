package com.truesim.common.math.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.truesim.common.math.Vec2;
import com.truesim.common.math.Vec3;
import org.junit.jupiter.api.Test;

/** Octahedral maps and the mesh helpers. */
class MeshesTest {

    /** A unit cube: 24 vertices (4 per face, so faces have their own normals), 12 triangles. */
    private static final float[] CUBE = new float[3 * 24];
    private static final int[] CUBE_INDICES = new int[36];

    static {
        float[][] faces = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
        };
        for (int f = 0; f < 6; f++) {
            float[] n = faces[f];
            // Two axes spanning the face, u x v = n
            float[] u = n[0] != 0 ? new float[] {0, 1, 0} : n[1] != 0 ? new float[] {0, 0, 1} : new float[] {1, 0, 0};
            float[] v = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]};
            float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int c = 0; c < 4; c++) {
                for (int k = 0; k < 3; k++) {
                    CUBE[3 * (4 * f + c) + k] = n[k] + corners[c][0] * u[k] + corners[c][1] * v[k];
                }
            }
            int[] quad = {0, 1, 2, 0, 2, 3};
            for (int i = 0; i < 6; i++) {
                CUBE_INDICES[6 * f + i] = 4 * f + quad[i];
            }
        }
    }

    @Test
    void octahedralMapsRoundTrip() {
        for (int i = 0; i < 500; i++) {
            Vec3 d = new Vec3(Math.sin(i * 1.3), Math.cos(i * 0.77), Math.sin(i * 2.9 + 1)).normalize();
            Vec2 uv = Octahedral.encode(d);
            assertThat(uv.x()).isBetween(0.0, 1.0);
            assertThat(uv.y()).isBetween(0.0, 1.0);
            assertThat(Octahedral.decode(uv.x(), uv.y()).approxEquals(d, 1e-12)).isTrue();
            Vec3 up = new Vec3(d.x(), Math.abs(d.y()), d.z());
            Vec2 h = Octahedral.encodeHemisphere(up);
            assertThat(Octahedral.decodeHemisphere(h.x(), h.y()).approxEquals(up, 1e-12)).isTrue();
        }
        assertThat(Octahedral.encode(new Vec3(0, 1, 0))).isEqualTo(new Vec2(0.5, 0.5));
        // The square's corners are the lower pole
        assertThat(Octahedral.decode(0, 0).approxEquals(new Vec3(0, -1, 0), 1e-12)).isTrue();
        assertThat(Octahedral.decode(1, 1).approxEquals(new Vec3(0, -1, 0), 1e-12)).isTrue();
        assertThat(Octahedral.decode(0.5, 0).approxEquals(new Vec3(0, 0, -1), 1e-12)).isTrue();
        assertThat(Octahedral.decode(1, 0.5).y()).isCloseTo(0, within(1e-12));
        // The hemisphere's corners are the horizon's diagonals, its edges' midpoints the axes
        assertThat(Octahedral.decodeHemisphere(1, 0.5).approxEquals(new Vec3(1, 0, 1).normalize(), 1e-12)).isTrue();
        assertThat(Octahedral.decodeHemisphere(1, 1).approxEquals(new Vec3(1, 0, 0), 1e-12)).isTrue();
        assertThat(Octahedral.decodeHemisphere(0.5, 0.5)).isEqualTo(new Vec3(0, 1, 0));
    }

    @Test
    void smoothNormalsOfAWeldedCubePointOutOfItsCorners() {
        float[] normals = new float[3 * 24];
        Meshes.vertexNormals(CUBE, 24, CUBE_INDICES, 36, normals);
        for (int v = 0; v < 24; v++) { // unwelded: each face's own normal
            Vec3 n = new Vec3(normals[3 * v], normals[3 * v + 1], normals[3 * v + 2]);
            assertThat(n.length()).isCloseTo(1, within(1e-6));
        }
        assertThat(normals[0]).isEqualTo(1);
        int[] canonical = new int[24];
        assertThat(Meshes.weld(CUBE, 24, canonical)).isEqualTo(8);
        int[] welded = new int[36];
        for (int i = 0; i < 36; i++) {
            welded[i] = canonical[CUBE_INDICES[i]];
        }
        Meshes.vertexNormals(CUBE, 24, welded, 36, normals);
        for (int v = 0; v < 24; v++) {
            if (canonical[v] == v) { // a corner: its normal points out diagonally, like its position
                Vec3 n = new Vec3(normals[3 * v], normals[3 * v + 1], normals[3 * v + 2]);
                Vec3 p = new Vec3(CUBE[3 * v], CUBE[3 * v + 1], CUBE[3 * v + 2]).normalize();
                assertThat(n.dot(p)).isGreaterThan(0.9);
            } else { // unused after welding
                assertThat(normals[3 * v + 1]).isEqualTo(1);
            }
        }
    }

    @Test
    void aWeldedCubeIsClosedAndAnOpenOneIsNot() {
        int[] canonical = new int[24];
        Meshes.weld(CUBE, 24, canonical);
        assertThat(Meshes.openEdges(CUBE_INDICES, 36, canonical)).isZero();
        // Unwelded, every face is a separate square: all its 4 border edges are open (2 per triangle)
        assertThat(Meshes.openEdges(CUBE_INDICES, 36, null)).isEqualTo(24);
        // Without one face: its border is open
        assertThat(Meshes.openEdges(CUBE_INDICES, 30, canonical)).isEqualTo(4);
        // A face wound the wrong way: its edges meet edges going the same way
        int[] flipped = CUBE_INDICES.clone();
        flipped[1] = CUBE_INDICES[2];
        flipped[2] = CUBE_INDICES[1];
        flipped[4] = CUBE_INDICES[5];
        flipped[5] = CUBE_INDICES[4];
        assertThat(Meshes.openEdges(flipped, 36, canonical)).isPositive();
        // A degenerate triangle's collapsed edge is open
        assertThat(Meshes.openEdges(new int[] {0, 0, 1}, 3, null)).isEqualTo(1);
        // An edge used by three triangles is not manifold
        assertThat(Meshes.openEdges(new int[] {0, 1, 2, 1, 0, 3, 1, 0, 4}, 9, null)).isPositive();
    }

    @Test
    void weldingFindsCollisionsAndKeepsDistinctPoints() {
        // Many points in a small table: probing past occupied slots
        int n = 1000;
        float[] positions = new float[3 * 2 * n];
        for (int i = 0; i < n; i++) {
            for (int copy = 0; copy < 2; copy++) {
                positions[3 * (2 * i + copy)] = i * 0.25f;
                positions[3 * (2 * i + copy) + 1] = -i;
                positions[3 * (2 * i + copy) + 2] = copy == 0 ? 0 : (i % 2 == 0 ? 0 : 1e-7f);
            }
        }
        int[] canonical = new int[2 * n];
        assertThat(Meshes.weld(positions, 2 * n, canonical)).isEqualTo(n + n / 2);
        assertThat(canonical[1]).isEqualTo(0);
        assertThat(canonical[3]).isEqualTo(3);
        // Points equal in two coordinates only stay apart
        float[] three = {0, 0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 0};
        assertThat(Meshes.weld(three, 4, new int[4])).isEqualTo(4);
        assertThat(Meshes.weld(new float[0], 0, new int[0])).isZero();
    }
}
