# common-math

Deterministic 2D/3D math for true-sim: vectors, quaternions, matrices, colors, curves, easing and
interpolation. Every result is bit-identical on every platform, and hot paths do not allocate.

```kotlin
dependencies {
    implementation("com.truesim.common:common-math:0.1.0")
}
```

## Design

**Immutable values for all math.** `Vec2`, `Vec3`, `Vec4`, `Quat`, `Mat3`, `Mat4`, `Color` and
`Scalar` are records. Temporary values are removed by the JIT, so chains like the one below
allocate nothing. When Valhalla ships, they become `value record`s without API changes.

```java
Vec3 next = position.addScaled(velocity, dt);
Quat facing = Quat.lookRotation(target.sub(position), Vec3.UP);
Mat4 model = Mat4.trs(position, facing, Vec3.ONE);
Mat4 mvp = projection.mul(view).mul(model);
```

Values are value-based: don't compare them with `==` or synchronize on them. Note that records
treat `0.0` and `-0.0` as different; use `approxEquals` for numeric comparison.

**Storage for state that changes every step.** Array types (`Vec3Array`, `QuatArray`,
`Mat4Array`, `ColorArray`, ...) keep values back to back in memory (off-heap via an `Arena`, or
on the heap). Reading and writing values allocates nothing.

```java
try (Arena arena = Arena.ofConfined()) {
    Vec3Array positions = Vec3Array.allocate(arena, 10_000);
    Vec3Array velocities = Vec3Array.onHeap(10_000);
    for (int i = 0; i < positions.size(); i++) {
        positions.set(i, positions.get(i).addScaled(velocities.get(i), dt));
    }
    positions.writeFloats(vertexBuffer, 0);   // float layout for the GPU
}
```

Every value also reads and writes single values: `v.write(segment, offset)`,
`Vec3.read(segment, offset)`, plus `ByteBuffer`, `DoubleBuffer` and float variants. Each type
publishes its `LAYOUT` (doubles) and `FLOAT_LAYOUT` as a `java.lang.foreign.StructLayout`.

**Matrices in memory.** Reading a whole `Mat4` back out of memory is more than the JIT can
optimize away, so `Mat4Array.get` allocates in hot code. Use the operations that work directly
in memory instead:

```java
locals.set(i, Mat4.trs(t, r, s));                 // set is free
world.setProduct(i, world, parent[i], locals, i); // world[i] = world[parent] * local[i]
world.transformPoints(i, vertices, first, count); // in place
```

## Allocation-free on HotSpot

The JIT only removes temporary values if it inlines the methods that create them, and its size
limits stop it for larger operations such as `Mat4.mul`. On first use, the library therefore
installs a compiler directive (the runtime equivalent of `jcmd Compiler.directives_add`) that
always inlines its value types. See `JitInlining`. Results are identical with or without it.

- Disable with `-Dtruesim.math.inline=false`.
- On JVMs without HotSpot's diagnostic commands it is skipped (some operations then allocate).
- The directive matches all methods, so it takes precedence over compiler directives added before it.

Allocation is verified by tests (`AllocationFreeTest`), which measure real heap allocation after
the JIT has compiled the code.

## Deterministic math

`java.lang.Math` may give different results on different CPUs, so this library never uses it
(enforced by build-logic's platform-independence check). `Maths` replaces it with pure-Java
implementations of the fdlibm algorithms: `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`,
`exp`, `expm1`, `log`, `log1p`, `log2`, `log10`, `pow`, `cbrt`, `hypot`, `sinh`, `cosh`, `tanh`.
They are within 1 to 4 ulps of `StrictMath` (verified by tests) and bit-identical everywhere.
Plus helpers: `clamp`, `lerp`, `inverseLerp`, `remap`, `smoothstep`, `smootherstep`, `wrap`,
`wrapAngle`, `deltaAngle`, `lerpAngle`, `moveTowards` and frame-rate independent `damp`.

## Colors

`Color` is linear RGB with straight alpha. Conversions: sRGB hex (`Color.srgb(0xFF8800)`),
HSV, Oklab and Oklch. Blend with `mix(other, t, ColorSpace.OKLAB)` for perceptually even
gradients. `writeRgba8` writes 8-bit sRGB texels.

## Easing and interpolation

Every value implements `Interpolatable<T>` (`interpolate(to, t)`): linear for vectors and
colors, slerp for quaternions, translation/rotation/scale for matrices. Vectors, colors and
`Scalar` also implement `Linear<T>` (add, sub, scale), which curves and springs build on.

```java
Vec3 p = a.interpolate(b, Easing.CUBIC_IN_OUT.apply(t));   // all Penner easings + CSS cubic-bezier, steps

Tween<Quat> turn = Tween.of(from, to, 0.5, Easing.BACK_OUT);
Keyframes<Color> sky = Keyframes.<Color>builder().key(0, night).key(6, dawn, Easing.SINE_IN_OUT).key(12, noon).build();
Motion<Vec3> camera = new Spring(0.3).step(camera, target, dt);   // critically damped follow
```

## Curves

Generic over any `Linear` value: `QuadraticBezier`, `CubicBezier` (with splitting), `Hermite`,
`CatmullRom` splines (uniform, centripetal, chordal; open or closed) and `ArcLength` for
moving along a curve at constant speed.

```java
CatmullRom<Vec3> track = CatmullRom.of(points, Parametrization.CENTRIPETAL, true);
ArcLength<Vec3> path = ArcLength.of(track, 1024);
Vec3 car = path.at(distanceDriven % path.length());
```

## Building

Uses [build-logic](https://github.com/true-sim/build-logic) from GitHub Packages (see its README
for credentials). Builds enforce 100% line and branch coverage, platform independence and the
allocation-free rules. To develop against a local build-logic checkout:

```bash
./gradlew build -Ptruesim.build-logic=../build-logic
```
