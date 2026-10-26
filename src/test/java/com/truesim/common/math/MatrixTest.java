package com.truesim.common.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import org.junit.jupiter.api.Test;

class MatrixTest {

    private static final double EPS = 1e-12;
    private final Mat3 m3 = new Mat3(2, 0, 1, 1, 3, 0, 0, 1, 4);
    private final Mat4 m4 = new Mat4(2, 0, 1, 3, 1, 3, 0, -1, 0, 1, 4, 2, 0, 0, 0, 1);
    private final Quat rotation = Quat.fromYawPitchRoll(0.4, -0.3, 0.2);

    // ---------------------------------------------------------------- Mat3

    @Test
    void mat3Factories() {
        assertThat(Mat3.fromColumns(Vec3.UNIT_X, Vec3.UNIT_Y, Vec3.UNIT_Z)).isEqualTo(Mat3.IDENTITY);
        assertThat(Mat3.fromRows(m3.row(0), m3.row(1), m3.row(2))).isEqualTo(m3);
        assertThat(Mat3.fromColumns(m3.column(0), m3.column(1), m3.column(2))).isEqualTo(m3);
        assertThat(Mat3.rotationX(0.5).approxEquals(Mat3.rotation(Quat.fromAxisAngle(Vec3.UNIT_X, 0.5)), EPS)).isTrue();
        assertThat(Mat3.rotationY(0.5).approxEquals(Mat3.rotation(Quat.fromAxisAngle(Vec3.UNIT_Y, 0.5)), EPS)).isTrue();
        assertThat(Mat3.rotationZ(0.5).approxEquals(Mat3.rotation(Quat.fromAxisAngle(Vec3.UNIT_Z, 0.5)), EPS)).isTrue();
        assertThat(Mat3.scaling(Vec3.of(1, 2, 3)).transform(Vec3.ONE)).isEqualTo(Vec3.of(1, 2, 3));
        assertThat(Mat3.scaling(2).transform(Vec3.ONE)).isEqualTo(Vec3.splat(2));
        assertThat(Mat3.ZERO.determinant()).isEqualTo(0.0);
    }

    @Test
    void mat3TwoDimensional() {
        Mat3 transform = Mat3.translation2D(Vec2.of(10, 0))
            .mul(Mat3.rotation2D(Maths.HALF_PI))
            .mul(Mat3.scaling2D(Vec2.of(2, 2)));
        assertThat(transform.transformPoint2D(Vec2.UNIT_X).approxEquals(Vec2.of(10, 2), EPS)).isTrue();
        assertThat(transform.transformDirection2D(Vec2.UNIT_X).approxEquals(Vec2.of(0, 2), EPS)).isTrue();
    }

    @Test
    void mat3Operations() {
        assertThat(m3.mul(m3.inverse()).approxEquals(Mat3.IDENTITY, EPS)).isTrue();
        assertThat(m3.determinant()).isEqualTo(25.0);
        assertThat(m3.transpose().transpose()).isEqualTo(m3);
        assertThat(m3.transpose().get(0, 1)).isEqualTo(m3.get(1, 0));
        assertThatThrownBy(Mat3.ZERO::inverse).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> m3.get(3, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> m3.get(0, -1)).isInstanceOf(IndexOutOfBoundsException.class);
        double[] expected = {2, 0, 1, 1, 3, 0, 0, 1, 4};
        for (int i = 0; i < 9; i++) {
            assertThat(m3.get(i / 3, i % 3)).isEqualTo(expected[i]);
        }
        assertThat(m3.toMat4().toMat3()).isEqualTo(m3);
    }

    @Test
    void mat3Decomposition() {
        Mat3 rs = Mat3.rotation(rotation).mul(Mat3.scaling(Vec3.of(1, 2, 3)));
        assertThat(rs.extractScale().approxEquals(Vec3.of(1, 2, 3), EPS)).isTrue();
        assertThat(rs.extractRotation().approxSameRotation(rotation, EPS)).isTrue();
        Mat3 halfway = Mat3.IDENTITY.interpolate(rs, 0.5);
        assertThat(halfway.extractScale().approxEquals(Vec3.of(1, 1.5, 2), EPS)).isTrue();
        assertThat(halfway.extractRotation().approxSameRotation(Quat.IDENTITY.slerp(rotation, 0.5), EPS)).isTrue();
    }

    @Test
    void mat3Comparisons() {
        assertThat(m3.approxEquals(m3, EPS)).isTrue();
        assertThat(m3.approxEquals(new Mat3(9, 0, 1, 1, 3, 0, 0, 1, 4), EPS)).isFalse();
        assertThat(m3.approxEquals(new Mat3(2, 9, 1, 1, 3, 0, 0, 1, 4), EPS)).isFalse();
        assertThat(m3.approxEquals(new Mat3(2, 0, 9, 1, 3, 0, 0, 1, 4), EPS)).isFalse();
    }

    @Test
    void mat3Memory() {
        assertThat(Mat3.LAYOUT.byteSize()).isEqualTo(72);
        assertThat(Mat3.FLOAT_LAYOUT.byteSize()).isEqualTo(36);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(128);
            m3.write(segment, 8);
            assertThat(segment.get(Memory.DOUBLE, 16)).isEqualTo(m3.m10()); // column-major
            assertThat(Mat3.read(segment, 8)).isEqualTo(m3);
            m3.writeFloats(segment, 4);
            assertThat(Mat3.readFloats(segment, 4)).isEqualTo(m3);
        }
        ByteBuffer bytes = ByteBuffer.allocate(128);
        m3.write(bytes, 8);
        assertThat(Mat3.read(bytes, 8)).isEqualTo(m3);
        DoubleBuffer doubles = DoubleBuffer.allocate(16);
        m3.write(doubles, 2);
        assertThat(Mat3.read(doubles, 2)).isEqualTo(m3);
    }

    // ---------------------------------------------------------------- Mat4

    @Test
    void mat4Transforms() {
        Mat4 trs = Mat4.trs(Vec3.of(1, 2, 3), rotation, Vec3.of(2, 2, 2));
        Mat4 composed = Mat4.translation(Vec3.of(1, 2, 3)).mul(Mat4.rotation(rotation)).mul(Mat4.scaling(2));
        assertThat(trs.approxEquals(composed, EPS)).isTrue();
        assertThat(Mat4.scaling(Vec3.of(1, 2, 3)).transformPoint(Vec3.ONE)).isEqualTo(Vec3.of(1, 2, 3));
        assertThat(Mat4.rotationX(0.5).toMat3()).isEqualTo(Mat3.rotationX(0.5));
        assertThat(Mat4.rotationY(0.5).toMat3()).isEqualTo(Mat3.rotationY(0.5));
        assertThat(Mat4.rotationZ(0.5).toMat3()).isEqualTo(Mat3.rotationZ(0.5));
        assertThat(trs.transformPoint(Vec3.ZERO)).isEqualTo(Vec3.of(1, 2, 3));
        assertThat(trs.transformDirection(Vec3.UNIT_X).length()).isCloseTo(2, within(EPS));
        assertThat(trs.transform(Vec3.ONE.extend(1)).xyz().approxEquals(trs.transformPoint(Vec3.ONE), EPS)).isTrue();
        assertThat(Mat4.fromColumns(trs.column(0), trs.column(1), trs.column(2), trs.column(3))).isEqualTo(trs);
        assertThat(Mat4.fromRows(trs.row(0), trs.row(1), trs.row(2), trs.row(3))).isEqualTo(trs);
    }

    @Test
    void mat4Camera() {
        Mat4 view = Mat4.lookAt(Vec3.of(0, 0, 5), Vec3.ZERO, Vec3.UP);
        assertThat(view.transformPoint(Vec3.ZERO).approxEquals(Vec3.of(0, 0, -5), EPS)).isTrue();
        Mat4 projection = Mat4.perspective(Maths.HALF_PI, 1, 1, 100);
        assertThat(projection.project(Vec3.of(0, 0, -1)).z()).isCloseTo(-1, within(EPS));
        assertThat(projection.project(Vec3.of(0, 0, -100)).z()).isCloseTo(1, within(EPS));
        Mat4 vulkan = Mat4.perspectiveZeroToOne(Maths.HALF_PI, 1, 1, 100);
        assertThat(vulkan.project(Vec3.of(0, 0, -1)).z()).isCloseTo(0, within(EPS));
        assertThat(vulkan.project(Vec3.of(0, 0, -100)).z()).isCloseTo(1, within(EPS));
        Mat4 ortho = Mat4.orthographic(-2, 2, -1, 1, 1, 10);
        assertThat(ortho.transformPoint(Vec3.of(2, 1, -10)).approxEquals(Vec3.of(1, 1, 1), EPS)).isTrue();
        Mat4 orthoZo = Mat4.orthographicZeroToOne(-2, 2, -1, 1, 1, 10);
        assertThat(orthoZo.transformPoint(Vec3.of(-2, -1, -1)).approxEquals(Vec3.of(-1, -1, 0), EPS)).isTrue();
    }

    @Test
    void mat4Operations() {
        assertThat(m4.mul(m4.inverse()).approxEquals(Mat4.IDENTITY, EPS)).isTrue();
        assertThat(m4.determinant()).isCloseTo(25, within(EPS));
        assertThat(m4.transpose().transpose()).isEqualTo(m4);
        assertThatThrownBy(Mat4.ZERO::inverse).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> m4.get(4, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        for (int i = 0; i < 16; i++) {
            assertThat(m4.get(i / 4, i % 4)).isEqualTo(m4.row(i / 4).dot(Vec4.UNIT_X.withX(0).add(unit(i % 4))));
        }
        Mat4 trs = Mat4.trs(Vec3.ZERO, rotation, Vec3.of(1, 2, 3));
        assertThat(trs.normalMatrix().approxEquals(trs.toMat3().inverse().transpose(), EPS)).isTrue();
    }

    private static Vec4 unit(int i) {
        return switch (i) {
            case 0 -> Vec4.UNIT_X;
            case 1 -> Vec4.UNIT_Y;
            case 2 -> Vec4.UNIT_Z;
            default -> Vec4.UNIT_W;
        };
    }

    @Test
    void mat4Decomposition() {
        Mat4 trs = Mat4.trs(Vec3.of(1, 2, 3), rotation, Vec3.of(2, 3, 4));
        assertThat(trs.extractTranslation()).isEqualTo(Vec3.of(1, 2, 3));
        assertThat(trs.extractScale().approxEquals(Vec3.of(2, 3, 4), EPS)).isTrue();
        assertThat(trs.extractRotation().approxSameRotation(rotation, EPS)).isTrue();
        Mat4 halfway = Mat4.IDENTITY.interpolate(trs, 0.5);
        assertThat(halfway.extractTranslation().approxEquals(Vec3.of(0.5, 1, 1.5), EPS)).isTrue();
        assertThat(halfway.extractScale().approxEquals(Vec3.of(1.5, 2, 2.5), EPS)).isTrue();
    }

    @Test
    void mat4Comparisons() {
        assertThat(m4.approxEquals(m4, EPS)).isTrue();
        for (int column = 0; column < 4; column++) {
            Vec4[] columns = {m4.column(0), m4.column(1), m4.column(2), m4.column(3)};
            columns[column] = columns[column].add(Vec4.ONE);
            assertThat(m4.approxEquals(Mat4.fromColumns(columns[0], columns[1], columns[2], columns[3]), EPS)).isFalse();
        }
    }

    @Test
    void mat4Memory() {
        assertThat(Mat4.LAYOUT.byteSize()).isEqualTo(128);
        assertThat(Mat4.FLOAT_LAYOUT.byteSize()).isEqualTo(64);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(256);
            m4.write(segment, 8);
            assertThat(segment.get(Memory.DOUBLE, 8 + 8 * 12)).isEqualTo(m4.m03()); // column-major
            assertThat(Mat4.read(segment, 8)).isEqualTo(m4);
            m4.writeFloats(segment, 4);
            assertThat(Mat4.readFloats(segment, 4)).isEqualTo(m4);
        }
        ByteBuffer bytes = ByteBuffer.allocate(256);
        m4.write(bytes, 8);
        assertThat(Mat4.read(bytes, 8)).isEqualTo(m4);
        DoubleBuffer doubles = DoubleBuffer.allocate(20);
        m4.write(doubles, 2);
        assertThat(Mat4.read(doubles, 2)).isEqualTo(m4);
    }
}
