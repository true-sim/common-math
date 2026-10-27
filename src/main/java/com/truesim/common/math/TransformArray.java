package com.truesim.common.math;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * Fixed-size array of {@link Transform} values stored back to back in memory, ten doubles each (see
 * {@link Transform#LAYOUT}).
 *
 * <p>{@link #set} is allocation-free; {@link #get} may allocate in hot code (a transform is three objects read from
 * ten values). In hot code, use the operations that work directly in memory:
 *
 * <pre>{@code
 * // world[i] = world[parent[i]] * local[i]
 * world.setProduct(i, world, parent[i], local, i);
 * world.transformPoints(i, vertices, first, count);
 * }</pre>
 */
public final class TransformArray extends StructArray {

    private TransformArray(MemorySegment segment) {
        super(segment, Transform.LAYOUT);
    }

    /** Off-heap array of {@code size} identity transforms, freed when {@code arena} is closed. */
    public static TransformArray allocate(Arena arena, int size) {
        TransformArray array = new TransformArray(StructArray.allocate(arena, Transform.LAYOUT, size));
        array.fill(Transform.IDENTITY);
        return array;
    }

    /** Heap array of {@code size} identity transforms, backed by a {@code double[]}. */
    public static TransformArray onHeap(int size) {
        TransformArray array = new TransformArray(StructArray.heap(Transform.LAYOUT, size));
        array.fill(Transform.IDENTITY);
        return array;
    }

    /** Views existing memory as transforms. Its size must be a multiple of {@link Transform#LAYOUT}. */
    public static TransformArray wrap(MemorySegment segment) {
        return new TransformArray(segment);
    }

    /** The transform at {@code index}. May allocate in hot code; prefer the in-memory operations there. */
    public Transform get(int index) {
        return Transform.read(segment(), offset(index));
    }

    public void set(int index, Transform value) {
        value.write(segment(), offset(index));
    }

    public void fill(Transform value) {
        for (int i = 0; i < size(); i++) {
            set(i, value);
        }
    }

    // ---------------------------------------------------------------- in-memory operations

    /**
     * Sets transform {@code index} to {@code parent[parentIndex] * child[childIndex]} (see {@link Transform#mul}).
     * Any of the three may be the same transform.
     */
    public void setProduct(int index, TransformArray parent, int parentIndex, TransformArray child, int childIndex) {
        MemorySegment p = parent.segment();
        long po = parent.offset(parentIndex);
        MemorySegment c = child.segment();
        long co = child.offset(childIndex);
        double ptx = d(p, po, 0);
        double pty = d(p, po, 1);
        double ptz = d(p, po, 2);
        double px = d(p, po, 3);
        double py = d(p, po, 4);
        double pz = d(p, po, 5);
        double pw = d(p, po, 6);
        double psx = d(p, po, 7);
        double psy = d(p, po, 8);
        double psz = d(p, po, 9);
        double cx = d(c, co, 3);
        double cy = d(c, co, 4);
        double cz = d(c, co, 5);
        double cw = d(c, co, 6);
        // translation: parent applied to the child's translation
        double vx = d(c, co, 0) * psx;
        double vy = d(c, co, 1) * psy;
        double vz = d(c, co, 2) * psz;
        double tx = 2 * (py * vz - pz * vy);
        double ty = 2 * (pz * vx - px * vz);
        double tz = 2 * (px * vy - py * vx);
        double sx = d(c, co, 7) * psx;
        double sy = d(c, co, 8) * psy;
        double sz = d(c, co, 9) * psz;
        MemorySegment s = segment();
        long o = offset(index);
        put(s, o, 0, ptx + vx + pw * tx + (py * tz - pz * ty));
        put(s, o, 1, pty + vy + pw * ty + (pz * tx - px * tz));
        put(s, o, 2, ptz + vz + pw * tz + (px * ty - py * tx));
        // rotation: parent * child
        put(s, o, 3, pw * cx + px * cw + py * cz - pz * cy);
        put(s, o, 4, pw * cy - px * cz + py * cw + pz * cx);
        put(s, o, 5, pw * cz + px * cy - py * cx + pz * cw);
        put(s, o, 6, pw * cw - px * cx - py * cy - pz * cz);
        put(s, o, 7, sx);
        put(s, o, 8, sy);
        put(s, o, 9, sz);
    }

    /** Transforms {@code count} points of {@code points}, starting at {@code from}, in place by transform {@code index}. */
    public void transformPoints(int index, Vec3Array points, int from, int count) {
        Objects.checkFromIndexSize(from, count, points.size());
        MemorySegment s = segment();
        long o = offset(index);
        double tx = d(s, o, 0);
        double ty = d(s, o, 1);
        double tz = d(s, o, 2);
        double qx = d(s, o, 3);
        double qy = d(s, o, 4);
        double qz = d(s, o, 5);
        double qw = d(s, o, 6);
        double sx = d(s, o, 7);
        double sy = d(s, o, 8);
        double sz = d(s, o, 9);
        MemorySegment v = points.segment();
        for (int i = from; i < from + count; i++) {
            long p = points.offset(i);
            double x = v.get(Memory.DOUBLE, p) * sx;
            double y = v.get(Memory.DOUBLE, p + 8) * sy;
            double z = v.get(Memory.DOUBLE, p + 16) * sz;
            double ux = 2 * (qy * z - qz * y);
            double uy = 2 * (qz * x - qx * z);
            double uz = 2 * (qx * y - qy * x);
            v.set(Memory.DOUBLE, p, tx + x + qw * ux + (qy * uz - qz * uy));
            v.set(Memory.DOUBLE, p + 8, ty + y + qw * uy + (qz * ux - qx * uz));
            v.set(Memory.DOUBLE, p + 16, tz + z + qw * uz + (qx * uy - qy * ux));
        }
    }

    private static double d(MemorySegment s, long offset, int component) {
        return s.get(Memory.DOUBLE, offset + 8L * component);
    }

    private static void put(MemorySegment s, long offset, int component, double value) {
        s.set(Memory.DOUBLE, offset + 8L * component, value);
    }
}
