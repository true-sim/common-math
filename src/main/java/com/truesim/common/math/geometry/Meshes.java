package com.truesim.common.math.geometry;

import com.truesim.annotations.AllowAllocation;

/**
 * Math on indexed triangle meshes given as plain arrays (positions as x, y, z float triples, three indices per
 * triangle, counter-clockwise from the front): smooth vertex normals, welding vertices that share a position, and
 * checking that a mesh is closed. For generated meshes (rocks, terrain, branches) and their tests.
 *
 * <pre>{@code
 * Meshes.vertexNormals(positions, vertexCount, indices, indexCount, normals);   // area-weighted, unit length
 * int[] canonical = new int[vertexCount];
 * Meshes.weld(positions, vertexCount, canonical);                                // seams joined by position
 * Meshes.openEdges(indices, indexCount, canonical) == 0;                        // closed and consistently wound
 * }</pre>
 */
public final class Meshes {

    private Meshes() {
    }

    /**
     * Smooth normals of the first {@code vertexCount} vertices: the sum of the (unnormalized, so area-weighted) normals
     * of the triangles using each vertex, normalized; a vertex no triangle uses, or whose triangles cancel, gets +y.
     * Writes {@code 3 * vertexCount} values to {@code normals}.
     */
    @AllowAllocation("sums in double precision per call")
    public static void vertexNormals(float[] positions, int vertexCount, int[] indices, int indexCount,
        float[] normals) {
        double[] sums = new double[3 * vertexCount];
        for (int t = 0; t + 2 < indexCount; t += 3) {
            int a = 3 * indices[t];
            int b = 3 * indices[t + 1];
            int c = 3 * indices[t + 2];
            double ux = positions[b] - positions[a];
            double uy = positions[b + 1] - positions[a + 1];
            double uz = positions[b + 2] - positions[a + 2];
            double vx = positions[c] - positions[a];
            double vy = positions[c + 1] - positions[a + 1];
            double vz = positions[c + 2] - positions[a + 2];
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            for (int corner = 0; corner < 3; corner++) {
                int v = 3 * indices[t + corner];
                sums[v] += nx;
                sums[v + 1] += ny;
                sums[v + 2] += nz;
            }
        }
        for (int v = 0; v < 3 * vertexCount; v += 3) {
            double length = Math.sqrt(sums[v] * sums[v] + sums[v + 1] * sums[v + 1] + sums[v + 2] * sums[v + 2]);
            boolean none = length == 0;
            normals[v] = none ? 0 : (float) (sums[v] / length);
            normals[v + 1] = none ? 1 : (float) (sums[v + 1] / length);
            normals[v + 2] = none ? 0 : (float) (sums[v + 2] / length);
        }
    }

    /**
     * Joins vertices with bit-identical positions: {@code canonical[v]} becomes the lowest-numbered vertex at the
     * position of vertex {@code v} (itself if it is the first). Returns how many distinct positions there are. Use it
     * to look past UV and normal seams, which split vertices that are one point of the surface.
     */
    @AllowAllocation("a hash table per call")
    public static int weld(float[] positions, int vertexCount, int[] canonical) {
        int capacity = Integer.highestOneBit(Math.max(4, vertexCount * 2 - 1)) << 1;
        int[] table = new int[capacity];
        int distinct = 0;
        for (int v = 0; v < vertexCount; v++) {
            int x = Float.floatToIntBits(positions[3 * v]);
            int y = Float.floatToIntBits(positions[3 * v + 1]);
            int z = Float.floatToIntBits(positions[3 * v + 2]);
            int slot = ((x * 0x9E3779B1) ^ (y * 0x85EBCA77) ^ (z * 0xC2B2AE3D)) & (capacity - 1);
            while (true) {
                int entry = table[slot] - 1; // 0: empty
                if (entry < 0) {
                    table[slot] = v + 1;
                    canonical[v] = v;
                    distinct++;
                    break;
                }
                if (Float.floatToIntBits(positions[3 * entry]) == x
                    && Float.floatToIntBits(positions[3 * entry + 1]) == y
                    && Float.floatToIntBits(positions[3 * entry + 2]) == z) {
                    canonical[v] = entry;
                    break;
                }
                slot = (slot + 1) & (capacity - 1);
            }
        }
        return distinct;
    }

    /**
     * How many directed edges of the triangles are not matched by exactly one opposite edge, with vertices mapped
     * through {@code canonical} (e.g. from {@link #weld}; {@code null} uses the indices as they are). 0 means the mesh
     * is closed (watertight) and every edge joins exactly two triangles wound consistently: a closed, oriented
     * 2-manifold surface (vertex links aside). An edge from a vertex to itself (a degenerate triangle's) counts as open.
     */
    @AllowAllocation("a sorted edge list per call")
    public static int openEdges(int[] indices, int indexCount, int[] canonical) {
        int triangles = indexCount / 3;
        long[] edges = new long[3 * triangles];
        for (int t = 0; t < triangles; t++) {
            for (int corner = 0; corner < 3; corner++) {
                int a = vertex(indices[3 * t + corner], canonical);
                int b = vertex(indices[3 * t + (corner + 1) % 3], canonical);
                edges[3 * t + corner] = (long) a << 32 | b;
            }
        }
        java.util.Arrays.sort(edges);
        int open = 0;
        for (long edge : edges) {
            int a = (int) (edge >>> 32);
            int b = (int) edge;
            boolean matched = a != b && count(edges, edge) == 1 && count(edges, (long) b << 32 | a) == 1;
            open += matched ? 0 : 1;
        }
        return open;
    }

    private static int vertex(int index, int[] canonical) {
        return canonical == null ? index : canonical[index];
    }

    /** How often {@code key} occurs in the sorted {@code edges}. */
    private static int count(long[] edges, long key) {
        int low = 0;
        int high = edges.length;
        while (low < high) { // the first at least key
            int mid = (low + high) >>> 1;
            if (edges[mid] < key) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        int n = 0;
        while (low + n < edges.length && edges[low + n] == key) {
            n++;
        }
        return n;
    }
}
