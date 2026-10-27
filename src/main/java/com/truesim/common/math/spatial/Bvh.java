package com.truesim.common.math.spatial;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.math.Vec3;
import com.truesim.common.math.geometry.Aabb;
import com.truesim.common.math.geometry.AabbArray;
import com.truesim.common.math.geometry.Frustum;
import com.truesim.common.math.geometry.Intersections;
import com.truesim.common.math.geometry.Plane;
import com.truesim.common.math.geometry.Ray;
import com.truesim.common.math.geometry.Sphere;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

/**
 * Bounding volume hierarchy over the boxes of an {@link AabbArray}, for fast raycasts and overlap queries over many
 * objects.
 *
 * <p>Nodes are stored flat in a {@link MemorySegment}, 64 bytes (one cache line) each, in depth-first order with
 * skip links: a query walks the array front to back, jumping over subtrees it can reject, with no stack and no
 * allocation. Queries are read-only and thread-safe. Built with a binned surface area heuristic; when objects move,
 * update their boxes and call {@link #refit()} instead of rebuilding.
 *
 * <pre>{@code
 * Bvh bvh = Bvh.build(bounds);
 * bvh.queryFrustum(frustum, object -> { draw(object); return true; });
 * bvh.raycast(ray, 1000, (object, ox, oy, oz, dx, dy, dz, max) -> shapes[object].intersect(...), hit);
 * }</pre>
 */
public final class Bvh {

    /** Visits primitives found by a query. Return {@code false} to stop the query. */
    @FunctionalInterface
    public interface Visitor {
        boolean visit(int primitive);
    }

    /** Intersects a ray with one primitive: its ray parameter, or {@link Double#POSITIVE_INFINITY} for a miss. */
    @FunctionalInterface
    public interface RayTest {
        double intersect(int primitive, double ox, double oy, double oz, double dx, double dy, double dz, double max);
    }

    /** Bytes per node: six doubles of bounds, then skip, first primitive and primitive count (0 for inner nodes). */
    public static final int NODE_BYTES = 64;

    private static final ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE_UNALIGNED;
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED;
    private static final int SKIP = 48;
    private static final int FIRST = 52;
    private static final int COUNT = 56;
    private static final int BINS = 12;
    private static final int DEFAULT_LEAF_SIZE = 4;

    private final AabbArray bounds;
    private final MemorySegment nodes;
    private final MemorySegment indices;
    private final int maxLeafSize;
    private final RayTest boundsTest;
    private int nodeCount;
    private double buildCost;
    private double cost;

    @AllowAllocation("one bounds test per hierarchy")
    private Bvh(AabbArray bounds, MemorySegment nodes, MemorySegment indices, int nodeCount, int maxLeafSize) {
        this.bounds = bounds;
        this.nodes = nodes;
        this.indices = indices;
        this.nodeCount = nodeCount;
        this.maxLeafSize = maxLeafSize;
        this.boundsTest = this::intersectBounds;
        this.cost = sahCost();
        this.buildCost = cost;
    }

    /** Builds a hierarchy over all boxes of {@code bounds}, at most 4 per leaf, in memory freed by the GC. */
    public static Bvh build(AabbArray bounds) {
        return build(Arena.ofAuto(), bounds, DEFAULT_LEAF_SIZE);
    }

    /**
     * Builds a hierarchy over all boxes of {@code bounds} with nodes in {@code arena}. Smaller leaves make queries
     * test fewer primitives but visit more nodes.
     */
    @AllowAllocation("building")
    public static Bvh build(Arena arena, AabbArray bounds, int maxLeafSize) {
        if (maxLeafSize < 1) {
            throw new IllegalArgumentException("Leaf size must be at least 1: " + maxLeafSize);
        }
        // Room for the largest possible tree (2n - 1 nodes), so rebuild() never needs new memory.
        MemorySegment nodes = arena.allocate((long) NODE_BYTES * Math.max(2L * bounds.size() - 1, 0), 8);
        MemorySegment indices = arena.allocate(4L * bounds.size(), 4);
        return new Bvh(bounds, nodes, indices, construct(bounds, maxLeafSize, nodes, indices), maxLeafSize);
    }

    /** Builds the tree into {@code nodes} and {@code indices} and returns its node count. */
    @AllowAllocation("building")
    private static int construct(AabbArray bounds, int maxLeafSize, MemorySegment nodes, MemorySegment indices) {
        Builder builder = new Builder(bounds, maxLeafSize);
        int count = builder.build();
        MemorySegment.copy(MemorySegment.ofArray(builder.nodes), 0, nodes, 0, (long) NODE_BYTES * count);
        MemorySegment.copy(MemorySegment.ofArray(builder.order), 0, indices, 0, 4L * bounds.size());
        return count;
    }

    // ---------------------------------------------------------------- properties

    /** The boxes this hierarchy was built over; update them and call {@link #refit()} when objects move. */
    public AabbArray primitiveBounds() {
        return bounds;
    }

    public int primitiveCount() {
        return bounds.size();
    }

    public int nodeCount() {
        return nodeCount;
    }

    /** The node memory (see {@link #NODE_BYTES}), e.g. to upload to the GPU. */
    public MemorySegment nodes() {
        return nodes.asSlice(0, (long) NODE_BYTES * nodeCount).asReadOnly();
    }

    /** Bounds of everything, or {@link Aabb#EMPTY} if there are no primitives. */
    public Aabb bounds() {
        return nodeCount == 0 ? Aabb.EMPTY : Aabb.read(nodes, 0);
    }

    // ---------------------------------------------------------------- refit

    /**
     * Recomputes all node bounds from the current primitive boxes, keeping the tree structure. Much faster than a
     * rebuild; quality degrades if objects move far from where they were at build time. Not thread-safe with queries.
     */
    public void refit() {
        for (int node = nodeCount - 1; node >= 0; node--) {
            long o = (long) node * NODE_BYTES;
            int count = nodes.get(INT, o + COUNT);
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            if (count > 0) {
                int first = nodes.get(INT, o + FIRST);
                for (int k = first; k < first + count; k++) {
                    int p = primitive(k);
                    minX = Math.min(minX, bounds.get(p, 0));
                    minY = Math.min(minY, bounds.get(p, 1));
                    minZ = Math.min(minZ, bounds.get(p, 2));
                    maxX = Math.max(maxX, bounds.get(p, 3));
                    maxY = Math.max(maxY, bounds.get(p, 4));
                    maxZ = Math.max(maxZ, bounds.get(p, 5));
                }
            } else {
                long left = o + NODE_BYTES; // the left child follows its parent; the right one follows the left subtree
                long right = (long) nodes.get(INT, left + SKIP) * NODE_BYTES;
                minX = Math.min(nodes.get(DOUBLE, left), nodes.get(DOUBLE, right));
                minY = Math.min(nodes.get(DOUBLE, left + 8), nodes.get(DOUBLE, right + 8));
                minZ = Math.min(nodes.get(DOUBLE, left + 16), nodes.get(DOUBLE, right + 16));
                maxX = Math.max(nodes.get(DOUBLE, left + 24), nodes.get(DOUBLE, right + 24));
                maxY = Math.max(nodes.get(DOUBLE, left + 32), nodes.get(DOUBLE, right + 32));
                maxZ = Math.max(nodes.get(DOUBLE, left + 40), nodes.get(DOUBLE, right + 40));
            }
            nodes.set(DOUBLE, o, minX);
            nodes.set(DOUBLE, o + 8, minY);
            nodes.set(DOUBLE, o + 16, minZ);
            nodes.set(DOUBLE, o + 24, maxX);
            nodes.set(DOUBLE, o + 32, maxY);
            nodes.set(DOUBLE, o + 40, maxZ);
        }
        cost = sahCost();
    }

    // ---------------------------------------------------------------- quality

    /**
     * How much slower queries are expected to be than right after the last build: the current surface area heuristic
     * cost divided by the cost at build time. 1 after a build; grows as {@link #refit()} follows objects that move
     * apart. The owner decides when it is worth a {@link #rebuild()}, e.g. above 1.5.
     */
    public double degradation() {
        return buildCost == 0 ? 1 : cost / buildCost;
    }

    /**
     * Rebuilds the tree from the current primitive boxes in the same memory, restoring full quality. Allocates
     * temporary build data; not thread-safe with queries.
     */
    @AllowAllocation("building")
    public void rebuild() {
        nodeCount = construct(bounds, maxLeafSize, nodes, indices);
        cost = sahCost();
        buildCost = cost;
    }

    /**
     * Expected cost of a query relative to testing the root box: each node's surface area relative to the root,
     * weighted by one for inner nodes and by the number of primitives for leaves.
     */
    private double sahCost() {
        if (nodeCount == 0) {
            return 0;
        }
        double sum = 0;
        for (int node = 0; node < nodeCount; node++) {
            long o = (long) node * NODE_BYTES;
            sum += area(o) * Math.max(1, nodes.get(INT, o + COUNT));
        }
        double root = area(0);
        return root > 0 ? sum / root : 0;
    }

    private double area(long o) {
        double x = nodes.get(DOUBLE, o + 24) - nodes.get(DOUBLE, o);
        double y = nodes.get(DOUBLE, o + 32) - nodes.get(DOUBLE, o + 8);
        double z = nodes.get(DOUBLE, o + 40) - nodes.get(DOUBLE, o + 16);
        return 2 * (x * y + y * z + z * x);
    }

    // ---------------------------------------------------------------- raycasts

    /**
     * Finds the nearest primitive hit by the ray within {@code maxDistance}; {@code test} intersects single
     * primitives. Returns whether anything was hit; details go to {@code hit}.
     */
    public boolean raycast(
        double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance, RayTest test, RayHit hit) {
        double best = maxDistance;
        int bestPrimitive = -1;
        int node = 0;
        while (node < nodeCount) {
            long o = (long) node * NODE_BYTES;
            double entry = Intersections.rayAabb(ox, oy, oz, dx, dy, dz,
                nodes.get(DOUBLE, o), nodes.get(DOUBLE, o + 8), nodes.get(DOUBLE, o + 16),
                nodes.get(DOUBLE, o + 24), nodes.get(DOUBLE, o + 32), nodes.get(DOUBLE, o + 40));
            if (entry < best) {
                int count = nodes.get(INT, o + COUNT);
                int first = nodes.get(INT, o + FIRST);
                for (int k = first; k < first + count; k++) {
                    int p = primitive(k);
                    double t = test.intersect(p, ox, oy, oz, dx, dy, dz, best);
                    if (t < best) {
                        best = t;
                        bestPrimitive = p;
                    }
                }
                node++; // into the subtree, or on past a leaf
            } else {
                node = nodes.get(INT, o + SKIP);
            }
        }
        hit.set(bestPrimitive, bestPrimitive < 0 ? Double.POSITIVE_INFINITY : best);
        return bestPrimitive >= 0;
    }

    public boolean raycast(Ray ray, double maxDistance, RayTest test, RayHit hit) {
        Vec3 o = ray.origin();
        Vec3 d = ray.direction();
        return raycast(o.x(), o.y(), o.z(), d.x(), d.y(), d.z(), maxDistance, test, hit);
    }

    /** Raycast against the primitive boxes themselves, e.g. for picking. */
    public boolean raycastBounds(Ray ray, double maxDistance, RayHit hit) {
        return raycast(ray, maxDistance, boundsTest, hit);
    }

    private double intersectBounds(int p, double ox, double oy, double oz, double dx, double dy, double dz, double max) {
        return Intersections.rayAabb(ox, oy, oz, dx, dy, dz,
            bounds.get(p, 0), bounds.get(p, 1), bounds.get(p, 2), bounds.get(p, 3), bounds.get(p, 4), bounds.get(p, 5));
    }

    // ---------------------------------------------------------------- overlap queries

    private static final int BOX = 0;
    private static final int SPHERE = 1;

    /** Visits every primitive whose box overlaps the box; returns how many were visited. */
    public int queryAabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Visitor visitor) {
        return query(BOX, minX, minY, minZ, maxX, maxY, maxZ, visitor);
    }

    public int queryAabb(Aabb box, Visitor visitor) {
        return queryAabb(box.min().x(), box.min().y(), box.min().z(), box.max().x(), box.max().y(), box.max().z(), visitor);
    }

    /** Visits every primitive whose box overlaps the sphere; returns how many were visited. */
    public int querySphere(double cx, double cy, double cz, double radius, Visitor visitor) {
        return query(SPHERE, cx, cy, cz, radius, 0, 0, visitor);
    }

    public int querySphere(Sphere sphere, Visitor visitor) {
        return querySphere(sphere.center().x(), sphere.center().y(), sphere.center().z(), sphere.radius(), visitor);
    }

    private int query(int shape, double a, double b, double c, double d, double e, double f, Visitor visitor) {
        int visited = 0;
        int node = 0;
        while (node < nodeCount) {
            long o = (long) node * NODE_BYTES;
            if (overlaps(shape, a, b, c, d, e, f, nodes, o)) {
                int count = nodes.get(INT, o + COUNT);
                int first = nodes.get(INT, o + FIRST);
                for (int k = first; k < first + count; k++) {
                    int p = primitive(k);
                    if (overlaps(shape, a, b, c, d, e, f, bounds.segment(), bounds.stride() * p)) {
                        visited++;
                        if (!visitor.visit(p)) {
                            return visited;
                        }
                    }
                }
                node++;
            } else {
                node = nodes.get(INT, o + SKIP);
            }
        }
        return visited;
    }

    /** Whether the query shape overlaps the box stored at {@code offset} in {@code memory}. */
    private static boolean overlaps(
        int shape, double a, double b, double c, double d, double e, double f, MemorySegment memory, long offset) {
        double minX = memory.get(DOUBLE, offset);
        double minY = memory.get(DOUBLE, offset + 8);
        double minZ = memory.get(DOUBLE, offset + 16);
        double maxX = memory.get(DOUBLE, offset + 24);
        double maxY = memory.get(DOUBLE, offset + 32);
        double maxZ = memory.get(DOUBLE, offset + 40);
        return shape == BOX
            ? Intersections.aabbAabb(a, b, c, d, e, f, minX, minY, minZ, maxX, maxY, maxZ)
            : Intersections.sphereAabb(a, b, c, d, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Visits every primitive whose box may be visible in the frustum; returns how many were visited. */
    public int queryFrustum(Frustum frustum, Visitor visitor) {
        if (nodeCount == 0) {
            return 0;
        }
        int result = frustumNode(frustum, visitor, 0, ALL_PLANES, 0);
        return result < 0 ? ~result : result;
    }

    private static final int ALL_PLANES = 0b111111;

    /**
     * Visits the primitives under {@code node} that are in the frustum, testing only the planes in {@code planes}: the
     * ones the parent's box straddles. A box entirely in front of a plane takes it off the mask for everything below
     * it, and a box entirely inside reports its whole subtree untested; both only skip tests that would pass, so the
     * results and their order are those of testing every box against every plane. Returns the visited count, or its
     * complement ({@code ~visited}, negative) when the visitor stopped the query. Recursion depth is the tree depth.
     */
    private int frustumNode(Frustum frustum, Visitor visitor, int node, int planes, int visited) {
        long o = (long) node * NODE_BYTES;
        int straddled = classify(frustum, nodes, o, planes);
        if (straddled < 0) {
            return visited;
        }
        int count = nodes.get(INT, o + COUNT);
        if (count > 0) {
            int first = nodes.get(INT, o + FIRST);
            for (int k = first; k < first + count; k++) {
                int p = primitive(k);
                if (straddled == 0 || classify(frustum, bounds.segment(), bounds.stride() * p, straddled) >= 0) {
                    visited++;
                    if (!visitor.visit(p)) {
                        return ~visited;
                    }
                }
            }
            return visited;
        }
        if (straddled == 0) {
            return reportSubtree(visitor, node, visited);
        }
        int left = node + 1;
        int result = frustumNode(frustum, visitor, left, straddled, visited);
        if (result < 0) {
            return result;
        }
        return frustumNode(frustum, visitor, nodes.get(INT, (long) left * NODE_BYTES + SKIP), straddled, result);
    }

    /** Visits every primitive under {@code node}, which lies entirely inside the frustum, in tree order. */
    private int reportSubtree(Visitor visitor, int node, int visited) {
        int end = nodes.get(INT, (long) node * NODE_BYTES + SKIP);
        for (int n = node; n < end; n++) {
            long o = (long) n * NODE_BYTES;
            int count = nodes.get(INT, o + COUNT);
            int first = nodes.get(INT, o + FIRST);
            for (int k = first; k < first + count; k++) {
                visited++;
                if (!visitor.visit(primitive(k))) {
                    return ~visited;
                }
            }
        }
        return visited;
    }

    /**
     * Tests the box at {@code offset} against the planes in {@code planes}: -1 if it is entirely behind one of them,
     * else the planes it is not entirely in front of (0: inside them all).
     */
    private static int classify(Frustum frustum, MemorySegment memory, long offset, int planes) {
        double minX = memory.get(DOUBLE, offset);
        double minY = memory.get(DOUBLE, offset + 8);
        double minZ = memory.get(DOUBLE, offset + 16);
        double maxX = memory.get(DOUBLE, offset + 24);
        double maxY = memory.get(DOUBLE, offset + 32);
        double maxZ = memory.get(DOUBLE, offset + 40);
        int straddled = planes;
        for (int i = 0; i < 6; i++) {
            int bit = 1 << i;
            if ((planes & bit) == 0) {
                continue;
            }
            Plane plane = switch (i) {
                case 0 -> frustum.left();
                case 1 -> frustum.right();
                case 2 -> frustum.bottom();
                case 3 -> frustum.top();
                case 4 -> frustum.near();
                default -> frustum.far();
            };
            Vec3 n = plane.normal();
            double nx = n.x();
            double ny = n.y();
            double nz = n.z();
            // The corner furthest along the normal decides whether the box is behind (as Intersections.aabbPlane),
            // the nearest corner whether it is entirely in front.
            double far = nx * (nx >= 0 ? maxX : minX) + ny * (ny >= 0 ? maxY : minY) + nz * (nz >= 0 ? maxZ : minZ)
                - plane.distance();
            if (far < 0) {
                return -1;
            }
            double near = nx * (nx >= 0 ? minX : maxX) + ny * (ny >= 0 ? minY : maxY) + nz * (nz >= 0 ? minZ : maxZ)
                - plane.distance();
            if (near >= 0) {
                straddled &= ~bit;
            }
        }
        return straddled;
    }

    private int primitive(int k) {
        return indices.getAtIndex(ValueLayout.JAVA_INT_UNALIGNED, k);
    }

    // ---------------------------------------------------------------- build

    /** Top-down build with a binned surface area heuristic, writing nodes in depth-first order. */
    @AllowAllocation("building")
    private static final class Builder {

        private final int maxLeafSize;
        private final double[] boxes;
        private final double[] centroids;
        private final int[] order;
        private final long[] nodes;
        private final MemorySegment nodeMemory;
        private int count;

        private final int[] binCounts = new int[BINS];
        private final double[] binBoxes = new double[6 * BINS];
        private final double[] rightAreas = new double[BINS];

        Builder(AabbArray bounds, int maxLeafSize) {
            this.maxLeafSize = maxLeafSize;
            int n = bounds.size();
            boxes = new double[6 * n];
            centroids = new double[3 * n];
            order = new int[n];
            for (int i = 0; i < n; i++) {
                for (int c = 0; c < 6; c++) {
                    boxes[6 * i + c] = bounds.get(i, c);
                }
                for (int axis = 0; axis < 3; axis++) {
                    centroids[3 * i + axis] = 0.5 * (boxes[6 * i + axis] + boxes[6 * i + 3 + axis]);
                }
                order[i] = i;
            }
            nodes = new long[Math.max(2 * n - 1, 0) * (NODE_BYTES / 8)];
            nodeMemory = MemorySegment.ofArray(nodes);
        }

        int build() {
            if (order.length > 0) {
                build(0, order.length);
            }
            return count;
        }

        private void build(int start, int end) {
            int node = count++;
            long o = (long) node * NODE_BYTES;
            double[] box = rangeBounds(start, end);
            for (int c = 0; c < 6; c++) {
                nodeMemory.set(DOUBLE, o + 8L * c, box[c]);
            }
            int mid = end - start <= maxLeafSize ? -1 : split(start, end);
            if (mid < 0) {
                nodeMemory.set(INT, o + FIRST, start);
                nodeMemory.set(INT, o + COUNT, end - start);
            } else {
                build(start, mid);
                build(mid, end);
            }
            nodeMemory.set(INT, o + SKIP, count); // first node after this subtree
        }

        private double[] rangeBounds(int start, int end) {
            double[] box = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int k = start; k < end; k++) {
                int b = 6 * order[k];
                for (int c = 0; c < 3; c++) {
                    box[c] = Math.min(box[c], boxes[b + c]);
                    box[c + 3] = Math.max(box[c + 3], boxes[b + c + 3]);
                }
            }
            return box;
        }

        /** Partitions [start, end) by the cheapest binned split and returns the split point. */
        private int split(int start, int end) {
            double bestCost = Double.POSITIVE_INFINITY;
            int bestAxis = -1;
            int bestBin = 0;
            double bestMin = 0;
            double bestScale = 0;
            for (int axis = 0; axis < 3; axis++) {
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (int k = start; k < end; k++) {
                    double c = centroids[3 * order[k] + axis];
                    min = Math.min(min, c);
                    max = Math.max(max, c);
                }
                if (max <= min) {
                    continue; // all centroids in one plane on this axis
                }
                double scale = BINS / (max - min);
                Arrays.fill(binCounts, 0);
                Arrays.fill(binBoxes, Double.NaN);
                for (int k = start; k < end; k++) {
                    int bin = bin(centroids[3 * order[k] + axis], min, scale);
                    grow(bin, 6 * order[k]);
                }
                // Sweep from the right to get the area of everything right of each split, then from the left. Both sides
                // are never empty: the smallest centroid lands in the first bin and the largest in the last.
                double[] right = emptyBox();
                int rightCount = 0;
                for (int bin = BINS - 1; bin > 0; bin--) {
                    rightCount += binCounts[bin];
                    include(right, bin);
                    rightAreas[bin] = area(right) * rightCount;
                }
                double[] left = emptyBox();
                int leftCount = 0;
                for (int bin = 0; bin < BINS - 1; bin++) {
                    leftCount += binCounts[bin];
                    include(left, bin);
                    double cost = area(left) * leftCount + rightAreas[bin + 1];
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestAxis = axis;
                        bestBin = bin;
                        bestMin = min;
                        bestScale = scale;
                    }
                }
            }
            if (bestAxis < 0) {
                return (start + end) >>> 1; // identical centroids: split the range in half
            }
            int i = start;
            int j = end - 1;
            while (i <= j) {
                if (bin(centroids[3 * order[i] + bestAxis], bestMin, bestScale) <= bestBin) {
                    i++;
                } else {
                    int t = order[i];
                    order[i] = order[j];
                    order[j--] = t;
                }
            }
            return i;
        }

        private static int bin(double centroid, double min, double scale) {
            return Math.min(BINS - 1, (int) ((centroid - min) * scale));
        }

        private void grow(int bin, int box) {
            binCounts[bin]++;
            int b = 6 * bin;
            for (int c = 0; c < 3; c++) {
                binBoxes[b + c] = binCounts[bin] == 1 ? boxes[box + c] : Math.min(binBoxes[b + c], boxes[box + c]);
                binBoxes[b + c + 3] = binCounts[bin] == 1 ? boxes[box + c + 3] : Math.max(binBoxes[b + c + 3], boxes[box + c + 3]);
            }
        }

        private void include(double[] box, int bin) {
            if (binCounts[bin] == 0) {
                return;
            }
            int b = 6 * bin;
            for (int c = 0; c < 3; c++) {
                box[c] = Math.min(box[c], binBoxes[b + c]);
                box[c + 3] = Math.max(box[c + 3], binBoxes[b + c + 3]);
            }
        }

        private static double[] emptyBox() {
            return new double[] {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        }

        private static double area(double[] box) {
            double x = box[3] - box[0];
            double y = box[4] - box[1];
            double z = box[5] - box[2];
            return 2 * (x * y + y * z + z * x);
        }
    }
}
