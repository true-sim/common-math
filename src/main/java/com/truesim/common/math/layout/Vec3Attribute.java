package com.truesim.common.math.layout;

import com.truesim.common.math.Mat4;
import com.truesim.common.math.Vec3;

/**
 * A {@link Vec3} per element, e.g. a position or a normal. Formats: {@code DOUBLE3}, {@code FLOAT3}, the four-component
 * {@code HALF4}, {@code SNORM8_4} and {@code SNORM16_4} (w written as 0, for GPU-friendly padding), and {@code OCT16}
 * for unit vectors such as normals (direction only: values are normalized when written).
 */
public final class Vec3Attribute extends Attribute {

    private final boolean octahedral;
    private final boolean padded;

    Vec3Attribute(ElementBuffer buffer, Field<?> field) {
        super(buffer, field, accepts(field.format()));
        octahedral = field.format() == Format.OCT16;
        padded = field.format().components() == 4;
    }

    static boolean accepts(Format format) {
        return switch (format) {
            case DOUBLE3, FLOAT3, HALF4, SNORM8_4, SNORM16_4, OCT16 -> true;
            default -> false;
        };
    }

    public Vec3 get(int index) {
        long address = address(index);
        double x = read(address, 0);
        double y = read(address, 1);
        double z = octahedral ? 1 - Math.abs(x) - Math.abs(y) : read(address, 2);
        double scale = 1;
        if (octahedral) {
            // Unfold the octahedron: the lower half was folded over the upper one.
            double px = x;
            x = z < 0 ? (1 - Math.abs(y)) * (px >= 0 ? 1 : -1) : px;
            y = z < 0 ? (1 - Math.abs(px)) * (y >= 0 ? 1 : -1) : y;
            scale = 1 / Math.sqrt(x * x + y * y + z * z);
        }
        // One value built in one place, so escape analysis can remove it.
        return new Vec3(x * scale, y * scale, z * scale);
    }

    public double x(int index) {
        return octahedral ? get(index).x() : component(index, 0);
    }

    public double y(int index) {
        return octahedral ? get(index).y() : component(index, 1);
    }

    public double z(int index) {
        return octahedral ? get(index).z() : component(index, 2);
    }

    public void set(int index, Vec3 value) {
        set(index, value.x(), value.y(), value.z());
    }

    public void set(int index, double x, double y, double z) {
        long address = address(index);
        if (octahedral) {
            // Project onto the octahedron |x| + |y| + |z| = 1 and fold the lower half over the upper.
            double l1 = Math.abs(x) + Math.abs(y) + Math.abs(z);
            double px = l1 == 0 ? 0 : x / l1;
            double py = l1 == 0 ? 0 : y / l1;
            write(address, 0, z < 0 ? (1 - Math.abs(py)) * (px >= 0 ? 1 : -1) : px);
            write(address, 1, z < 0 ? (1 - Math.abs(px)) * (py >= 0 ? 1 : -1) : py);
            return;
        }
        write(address, 0, x);
        write(address, 1, y);
        write(address, 2, z);
        if (padded) {
            write(address, 3, 0);
        }
    }

    /** Transforms every element as a point by an affine matrix (w = 1). */
    public void transformPoints(Mat4 m) {
        for (int i = 0; i < size(); i++) {
            Vec3 p = get(i);
            set(i,
                m.m00() * p.x() + m.m01() * p.y() + m.m02() * p.z() + m.m03(),
                m.m10() * p.x() + m.m11() * p.y() + m.m12() * p.z() + m.m13(),
                m.m20() * p.x() + m.m21() * p.y() + m.m22() * p.z() + m.m23());
        }
    }

    /** Transforms every element as a direction (w = 0): translation does not apply. */
    public void transformDirections(Mat4 m) {
        for (int i = 0; i < size(); i++) {
            Vec3 d = get(i);
            set(i,
                m.m00() * d.x() + m.m01() * d.y() + m.m02() * d.z(),
                m.m10() * d.x() + m.m11() * d.y() + m.m12() * d.z(),
                m.m20() * d.x() + m.m21() * d.y() + m.m22() * d.z());
        }
    }
}
