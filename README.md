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

**Matrices and transforms in memory.** Reading a whole `Mat4` or `Transform` back out of memory
is more than the JIT can optimize away, so `Mat4Array.get` and `TransformArray.get` may
allocate in hot code. Use the operations that work directly in memory instead:

```java
locals.set(i, Transform.of(position, rotation));  // set is free
world.setProduct(i, world, parent[i], locals, i); // world[i] = world[parent] * local[i]
world.transformPoints(i, vertices, first, count); // in place
```

## Allocation-free on HotSpot

The JIT only removes temporary values if it inlines the methods that create them, and its size
limits stop it for larger operations such as `Mat4.mul`. On first use, the library therefore
installs a compiler directive (the runtime equivalent of `jcmd Compiler.directives_add`) that
always inlines its value types and never inlines its transcendental functions (they return
doubles, and inlining them would use up the JIT's budget). See `JitInlining`. Results are
identical with or without it.

- Disable with `-Dtruesim.math.inline=false`.
- On JVMs without HotSpot's diagnostic commands it is skipped (some operations then allocate).
- Format conversions of `ElementBuffer` views are never inlined either: they return doubles, and
  keeping them out leaves the JIT room to remove the values around them.
- The directive matches all methods, so it takes precedence over compiler directives added before it.
- HotSpot applies only the newest directive, so other libraries register their own patterns with
  `JitInlining.include("+com/example/Query.run")` instead of adding a directive; the library then
  installs one directive that combines both.

Allocation is verified by tests (`AllocationFreeTest`), which measure real heap allocation after
the JIT has compiled the code.

### Writing allocation-free code

Escape analysis has limits per compiled method. In practice:

- **Keep hot loops focused.** A loop that culls 10,000 objects and also composes transforms
  and integrates rotations can exceed what the JIT optimizes in one method. Split work into
  separate methods or passes, as you would for cache efficiency anyway.
- **Use the in-memory operations for matrices and transforms** stored in arrays (above).
- **Use the raw-doubles layer for bulk queries.** `Intersections` takes plain doubles, so loops
  over data in arrays or memory never create objects at all, independent of the JIT.
- **Keep interfaces out of the innermost loops when they pass values.** An `Acceleration` or
  `Curve` call site that sees several implementations is not inlined, and the values it passes
  are allocated. Interfaces that return a `double`, like `Easing`, are always fine.
- **Measure.** Wrap hot paths in `assertAllocationFree` tests; build-logic runs them with
  synchronous JIT compilation, so they measure the optimizing compiler, not a timing race.

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

For tracks and paths: `ArcLength.nearestDistance(point)` gives progress along a curve (e.g. lap
distance), `RotationMinimizingFrames` give twist-free orientations along a 3D curve for road
meshes, banking (`Frame.banked`) and camera rails, and `QuatSpline` interpolates smoothly
through rotation keys (squad).

## Transforms

`Transform` (translation, rotation, scale) and `Transform2D` compose (`parent.mul(local)`),
invert, interpolate and apply to points and directions; lighter and more precise than `Mat4` for
scene graphs and rigid bodies. `Quat` also offers `toYawPitchRoll`, `rotateTowards`,
`integrate(angularVelocity, dt)` for rigid bodies, swing-twist decomposition and `log`/`exp`.
Integer vectors `Vec2i` and `Vec3i` cover grids and voxels.

## Geometry

`Ray`, `Plane`, `Aabb`, `Obb` (oriented box), `Sphere`, `Capsule`, `Triangle`, `Segment`, `Rect`
and `Frustum`, with containment, closest-point, overlap and ray queries (including
segment-segment distance for capsules). Ray queries return the hit
distance, or `Double.POSITIVE_INFINITY` on a miss, so the nearest hit is a plain `min`.

Shapes are in world space. To combine them with moving objects, either move the shape
(`localBox.transform(objectTransform)`) or move the query into the shape's space
(`localBox.intersect(ray.transform(objectTransform.inverse()))`).

Every overlap and ray test exists at three levels that compute the same thing:

```java
sphere.intersects(box);                                          // shapes
Intersections.sphereAabb(center, radius, min, max);              // vectors
Intersections.sphereAabb(cx, cy, cz, r, minX, minY, minZ, maxX, maxY, maxZ); // plain doubles
```

The plain-doubles level never creates objects: use it for bulk work over data in arrays or
memory (culling, broad phase). Oriented-box overlap is a branch-free separating-axis test.

## Bounding volume hierarchy

`Bvh` accelerates raycasts and overlap queries over many objects. Build it over the boxes in an
`AabbArray`; nodes are stored flat in a `MemorySegment`, one 64-byte cache line each, in depth-first
order with skip links, so queries walk memory front to back with no stack and no allocation, and
any number of threads can query at once. It is built with a binned surface area heuristic; when
objects move, update their boxes and call `refit()`.

```java
AabbArray bounds = AabbArray.allocate(arena, objects.length);   // one box per object
Bvh bvh = Bvh.build(bounds);

bvh.queryFrustum(frustum, object -> { draw(object); return true; });   // return false to stop
bvh.raycast(ray, maxDistance, (object, ox, oy, oz, dx, dy, dz, max) ->
    Intersections.rayCapsule(ox, oy, oz, dx, dy, dz, ...object's capsule...), hit);
bounds.set(i, newBox); bvh.refit();                                    // objects moved
```

The per-object callbacks take and return plain numbers, so they never cause allocations; create
them and the `RayHit` once and reuse them.

Refit keeps the tree structure, so quality drops as objects move far from where they were at
build time. `degradation()` reports it as the surface-area cost relative to right after the last
build (1 = as good as new), computed during `refit()` at almost no cost. Deciding when to rebuild
is left to the owner of the tree, which knows the frame budget and threading:

```java
bvh.refit();
if (bvh.degradation() > 1.5) {
    bvh.rebuild();   // in place, same memory; or build a new Bvh on another thread and swap
}
```

## Random numbers and noise

`Rng` is a seeded, deterministic generator (xoshiro256++ with SplitMix64 seeding): same seed,
same sequence on every platform, for replays and lockstep simulation. It samples ints, doubles,
Gaussians, points in or on circles and spheres, and uniform random rotations; `fork()` gives
independent streams and `state()`/`fromState` save and restore it. `Noise` provides seeded
Perlin and simplex noise in 2D and 3D plus fractal sums of octaves.

## Numerics

`Integrator.SEMI_IMPLICIT_EULER`, `VELOCITY_VERLET` and `RK4` advance a `Motion` under any
`Acceleration`; `Polynomials` solves quadratic and cubic equations (numerically stable);
`RootFinding` offers bisection and Newton's method.

## Interleaved data: vertices and particles

`ElementLayout` describes one element with several attributes (offsets and stride follow each
format's natural alignment). `ElementBuffer` puts elements in any `MemorySegment`: off-heap from an
`Arena`, on the heap, or wrapped around a mapped file or a buffer shared with native code or the GPU.
Typed views read and write one attribute across all elements without allocating.

```java
ElementLayout.Builder builder = ElementLayout.builder();
Field<Vec3Attribute> POSITION = builder.vec3("position", Format.FLOAT3);
Field<Vec3Attribute> NORMAL = builder.vec3("normal", Format.OCT16);     // unit vector in 4 bytes
Field<Vec2Attribute> UV = builder.vec2("uv", Format.HALF2);
Field<ColorAttribute> COLOR = builder.color("color", Format.SRGB8_4);  // same bytes as Color.writeRgba8
ElementLayout vertex = builder.build();  // 24 bytes per vertex; memoryLayout() for FFM

ElementBuffer vertices = ElementBuffer.allocate(arena, vertex, count);
Vec3Attribute position = vertices.attribute(POSITION); // typed handle: no names to misspell
Vec3Attribute normal = vertices.attribute(NORMAL);
position.set(i, p);
position.transformPoints(model);
normal.transformDirections(model);
```

| Format | Storage |
|---|---|
| `DOUBLE1`-`DOUBLE4`, `FLOAT1`-`FLOAT4`, `HALF2`, `HALF4` | 64, 32 and 16-bit floats |
| `UNORM8_4`, `UNORM16_2`, `UNORM16_4` | unsigned normalized integers, [0, 1] |
| `SNORM8_4`, `SNORM16_2`, `SNORM16_4` | signed normalized integers, [-1, 1] |
| `SRGB8_4` | color bytes, RGB sRGB-encoded, alpha linear |
| `OCT16` | unit vector, octahedral encoding in two `SNORM16` |
| `INT1` | 32-bit integer (ids, flags) |

Typed fields (`builder.vec3(name, format)` and friends) are handles: `attribute(field)` gives their
view without any lookup by name, and a format the view cannot hold fails when the layout is built.
For layouts read from data, `builder.attribute(name, format)` and `buffer.vec3(name)` work by name.

Views: `scalar`, `ints`, `vec2`, `vec3`, `vec4`, `quat`, `color`; each accepts the formats that can
hold its type (a `Vec3` fits `FLOAT3`, `OCT16`, or a padded four-component format). For a
structure-of-arrays particle system, use one buffer per attribute with `ElementLayout.of(name,
format)`: the views work the same. Conversions round to nearest and clamp, deterministically.

## Building

Uses [build-logic](https://github.com/true-sim/build-logic) from GitHub Packages (see its README
for credentials). Builds enforce 100% line and branch coverage, platform independence and the
allocation-free rules. To develop against a local build-logic checkout:

```bash
./gradlew build -Ptruesim.build-logic=../build-logic
```
