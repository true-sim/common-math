package com.truesim.common.math;

import com.truesim.annotations.AllowAllocation;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.management.ObjectName;

/**
 * Makes the value types allocation-free on HotSpot.
 *
 * <p>The JIT removes temporary values (escape analysis) only when it inlines the methods that create them, and its
 * size limits stop it from inlining larger operations such as {@link Mat4#mul} or {@link Mat4#inverse}. On first use,
 * this library therefore installs a compiler directive (the runtime equivalent of {@code jcmd Compiler.directives_add})
 * that always inlines the methods of its value types and the methods of its array types that create values. Methods
 * that only read and write memory are left to the JIT's normal heuristics: forcing those inline produces compilation
 * units too large for the JIT. The transcendental functions behind {@link Maths} are never inlined: they return plain
 * doubles, and inlining them would use up the JIT's per-method budget before the value code. Results are identical
 * either way; only allocation changes.
 *
 * <p>Disable with {@code -Dtruesim.math.inline=false}. On JVMs without HotSpot's diagnostic commands the directive is
 * skipped. The directive matches all methods, so it takes precedence over other compiler directives added before it.
 */
public final class JitInlining {

    /** System property that disables the directive when set to {@code false}. */
    public static final String PROPERTY = "truesim.math.inline";

    static final String DIAGNOSTIC_COMMAND = "com.sun.management:type=DiagnosticCommand";

    static final String DIRECTIVES = """
        [{
          match: "*.*",
          inline: [
            "-com/truesim/common/math/Fdlibm.*",
            "+com/truesim/common/math/Vec2.*", "+com/truesim/common/math/Vec3.*", "+com/truesim/common/math/Vec4.*",
            "+com/truesim/common/math/Quat.*", "+com/truesim/common/math/Mat3.*", "+com/truesim/common/math/Mat4.*",
            "+com/truesim/common/math/Scalar.*", "+com/truesim/common/math/color/Color.*",
            "+com/truesim/common/math/Transform.*", "+com/truesim/common/math/Transform2D.*",
            "+com/truesim/common/math/Vec2i.*", "+com/truesim/common/math/Vec3i.*",
            "+com/truesim/common/math/TransformArray.get", "+com/truesim/common/math/TransformArray.set",
            "+com/truesim/common/math/geometry/Ray.*", "+com/truesim/common/math/geometry/Plane.*",
            "+com/truesim/common/math/geometry/Aabb.*", "+com/truesim/common/math/geometry/Sphere.*",
            "+com/truesim/common/math/geometry/Triangle.*", "+com/truesim/common/math/geometry/Segment.*",
            "+com/truesim/common/math/geometry/Rect.*", "+com/truesim/common/math/geometry/Obb.*",
            "+com/truesim/common/math/geometry/Frustum.*",
            "+com/truesim/common/math/curve/Frame.*", "+com/truesim/common/math/numeric/Roots.*",
            "+com/truesim/common/math/random/Rng.next*",
            "+com/truesim/common/math/Vec2Array.get", "+com/truesim/common/math/Vec2Array.set",
            "+com/truesim/common/math/Vec3Array.get", "+com/truesim/common/math/Vec3Array.set",
            "+com/truesim/common/math/Vec4Array.get", "+com/truesim/common/math/Vec4Array.set",
            "+com/truesim/common/math/QuatArray.get", "+com/truesim/common/math/QuatArray.set",
            "+com/truesim/common/math/Mat3Array.get", "+com/truesim/common/math/Mat3Array.set",
            "+com/truesim/common/math/Mat4Array.set",
            "+com/truesim/common/math/color/ColorArray.get", "+com/truesim/common/math/color/ColorArray.set"
          ]
        }]
        """;

    private static final boolean ACTIVE = install(System.getProperty(PROPERTY, "true"), DIAGNOSTIC_COMMAND);

    private JitInlining() {
    }

    /**
     * Installs the directive if that has not happened yet. The value types call this when they are first used;
     * call it at startup to have it in place before any code is compiled.
     *
     * @return whether the directive is active
     */
    public static boolean install() {
        return ACTIVE;
    }

    @AllowAllocation("one-time setup")
    static boolean install(String enabled, String diagnosticCommand) {
        if (!Boolean.parseBoolean(enabled)) {
            return false;
        }
        try {
            Path file = Files.createTempFile("truesim-math-directives", ".json");
            try {
                Files.writeString(file, DIRECTIVES);
                ManagementFactory.getPlatformMBeanServer().invoke(
                    new ObjectName(diagnosticCommand), "compilerDirectivesAdd",
                    new Object[] {new String[] {file.toString()}}, new String[] {String[].class.getName()});
                return true;
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (Exception | LinkageError e) {
            return false; // not HotSpot, or management unavailable: everything still works, with some allocation
        }
    }
}
