package com.truesim.common.math;

import com.truesim.annotations.AllowAllocation;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
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
 *
 * <p>HotSpot applies only the most recently added directive, so other libraries must not add their own: they register
 * their patterns with {@link #include}, which installs one directive combining this library's patterns with all
 * included ones.
 */
public final class JitInlining {

    /** System property that disables the directive when set to {@code false}. */
    public static final String PROPERTY = "truesim.math.inline";

    static final String DIAGNOSTIC_COMMAND = "com.sun.management:type=DiagnosticCommand";

    // Inline patterns as in a compiler directive: + to always inline, - to never inline.
    private static final List<String> PATTERNS = List.of(
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
        "+com/truesim/common/math/geometry/Frustum.*", "+com/truesim/common/math/geometry/Capsule.*",
        "+com/truesim/common/math/geometry/AabbArray.get", "+com/truesim/common/math/geometry/AabbArray.set",
        "+com/truesim/common/math/curve/Frame.*", "+com/truesim/common/math/numeric/Roots.*",
        "+com/truesim/common/math/random/Rng.next*",
        "+com/truesim/common/math/Vec2Array.get", "+com/truesim/common/math/Vec2Array.set",
        "+com/truesim/common/math/Vec3Array.get", "+com/truesim/common/math/Vec3Array.set",
        "+com/truesim/common/math/Vec4Array.get", "+com/truesim/common/math/Vec4Array.set",
        "+com/truesim/common/math/QuatArray.get", "+com/truesim/common/math/QuatArray.set",
        "+com/truesim/common/math/Mat3Array.get", "+com/truesim/common/math/Mat3Array.set",
        "+com/truesim/common/math/Mat4Array.set",
        "+com/truesim/common/math/color/ColorArray.get", "+com/truesim/common/math/color/ColorArray.set");

    private static final Pattern VALID = Pattern.compile("[+-][A-Za-z0-9_$/.*]+");

    static final String DIRECTIVES = directives(List.of());

    private static final Set<String> INCLUDED = new LinkedHashSet<>();

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

    /**
     * Adds inline patterns of another library to the directive, e.g. {@code "+com/example/Query.run"} to always inline
     * {@code com.example.Query.run} into its callers, so that values its callers pass to it need no allocation. A
     * pattern is {@code +} (always inline) or {@code -} (never inline) followed by a class with {@code /} separators,
     * a dot and a method name; {@code *} matches anything. Call it once, e.g. from a static initializer.
     *
     * @return whether the directive with the new patterns is active
     * @throws IllegalArgumentException if a pattern is malformed
     */
    public static synchronized boolean include(String... patterns) {
        return include(ACTIVE, DIAGNOSTIC_COMMAND, patterns);
    }

    @AllowAllocation("one-time setup")
    static boolean include(boolean active, String diagnosticCommand, String... patterns) {
        for (String pattern : patterns) {
            if (!VALID.matcher(pattern).matches()) {
                throw new IllegalArgumentException("Not an inline pattern: " + pattern);
            }
        }
        if (!active) {
            return false;
        }
        int before = INCLUDED.size();
        INCLUDED.addAll(List.of(patterns));
        // The newest directive wins, so the combined one replaces the previous one.
        return INCLUDED.size() == before || add(diagnosticCommand, directives(INCLUDED));
    }

    @AllowAllocation("one-time setup")
    static String directives(Iterable<String> included) {
        List<String> quoted = new ArrayList<>();
        for (String pattern : PATTERNS) {
            quoted.add('"' + pattern + '"');
        }
        for (String pattern : included) {
            quoted.add('"' + pattern + '"');
        }
        return "[{ match: \"*.*\", inline: [" + String.join(", ", quoted) + "] }]";
    }

    static boolean install(String enabled, String diagnosticCommand) {
        return Boolean.parseBoolean(enabled) && add(diagnosticCommand, DIRECTIVES);
    }

    @AllowAllocation("one-time setup")
    private static boolean add(String diagnosticCommand, String directives) {
        try {
            Path file = Files.createTempFile("truesim-math-directives", ".json");
            try {
                Files.writeString(file, directives);
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
