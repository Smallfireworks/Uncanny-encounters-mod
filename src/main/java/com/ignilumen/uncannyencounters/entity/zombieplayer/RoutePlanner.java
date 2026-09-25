package com.ignilumen.uncannyencounters.entity.zombieplayer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Incremental A* over feet cells. Edges may open doors, mine blocks, bridge gaps, pillar up, dig
 * down or swim, each costed in the ticks a player would need, so the zombie tunnels only when that
 * is actually faster than walking around. A search is spread over several ticks.
 */
public final class RoutePlanner {
    /** Ticks per block when sprinting like a player (5.612 blocks per second). */
    static final double WALK = 1 / 0.2806;
    private static final double SWIM = WALK * 2.5, JUMP_COST = 2, FALL_COST = 1.5, PLACE_COST = 6 + 6,
            PILLAR_COST = 12, AVOID_COST = 60, HEURISTIC = 1.2, DIAGONAL = Math.sqrt(2);
    private static final int RANGE = 48, MAX_NODES = 6000, MAX_FALL_SCAN = 20;

    /** Arrival area: horizontal radius around {@code center} and a vertical tolerance in cells. */
    public record Goal(BlockPos center, double radius, int vertical) {
        boolean reached(int x, int y, int z) {
            double dx = x - center.getX(), dz = z - center.getZ();
            return dx * dx + dz * dz <= radius * radius && Math.abs(y - center.getY()) <= vertical;
        }

        double distance(int x, int y, int z) {
            double dx = x - center.getX(), dy = y - center.getY(), dz = z - center.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        boolean sameAs(Goal other) {
            return center.distSqr(other.center) <= 2 && radius == other.radius && vertical == other.vertical;
        }
    }

    private static final class Node {
        final int x, y, z;
        double floor, g = Double.MAX_VALUE, h;
        boolean swim, closed;
        @Nullable Node parent;
        @Nullable Step via;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        double base() {
            return swim ? y : floor;
        }

        BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    private record Open(Node node, double g, double f) {}

    private final Terrain terrain;
    private final Goal goal;
    private final LongSet avoid;
    private final Node start;
    private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
    private final PriorityQueue<Open> open = new PriorityQueue<>(Comparator.comparingDouble(Open::f));
    private Node best;
    private int expanded;
    private @Nullable List<Step> result;
    private boolean complete;

    RoutePlanner(Terrain terrain, BlockPos from, double floor, boolean swimming, Goal goal, LongSet avoid) {
        this.terrain = terrain;
        this.goal = goal;
        this.avoid = avoid;
        start = new Node(from.getX(), from.getY(), from.getZ());
        start.floor = floor;
        start.swim = swimming;
        start.g = 0;
        start.h = heuristic(start.x, start.y, start.z);
        nodes.put(from.asLong(), start);
        open.add(new Open(start, 0, start.h));
        best = start;
    }

    Goal goal() {
        return goal;
    }

    /** Expands up to {@code budget} nodes; true once {@link #result()} is ready. */
    boolean run(int budget) {
        while (result == null && budget-- > 0) {
            Open entry = open.poll();
            if (entry == null || expanded >= MAX_NODES) {
                // No full route: take the explored cell closest to the goal if it is a real improvement.
                result = best != start && best.h < start.h - WALK * HEURISTIC ? build(best) : List.of();
                break;
            }
            Node node = entry.node;
            if (node.closed || entry.g > node.g) continue;
            node.closed = true;
            expanded++;
            if (goal.reached(node.x, node.y, node.z)) {
                complete = true;
                result = build(node);
                break;
            }
            if (node.h < best.h || node.h == best.h && node.g < best.g) best = node;
            expand(node);
        }
        return result != null;
    }

    /** The planned steps; empty when no progress towards the goal is possible. */
    List<Step> result() {
        return result == null ? List.of() : result;
    }

    /** Whether {@link #result()} ends inside the goal rather than at the closest reachable cell. */
    boolean complete() {
        return complete;
    }

    private List<Step> build(Node end) {
        List<Step> steps = new ArrayList<>();
        for (Node node = end; node.via != null; node = node.parent) steps.add(node.via);
        Collections.reverse(steps);
        return steps;
    }

    private void expand(Node node) {
        terrain.simulate(build(node));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int dx = direction.getStepX(), dz = direction.getStepZ();
            walk(node, dx, dz);
            ascend(node, dx, dz);
            descend(node, dx, dz);
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) diagonal(node, dx, dz);
        }
        pillar(node);
        digDown(node);
        swimVertically(node);
    }

    /** Same-level move; bridges over a gap by building under the next cell. */
    private void walk(Node node, int dx, int dz) {
        int x = node.x + dx, y = node.y, z = node.z + dz;
        if (!inRange(x, z) || terrain.hazard(x, y, z) || terrain.hazard(x, y + 1, z)) return;
        double from = node.base(), floor = terrain.floor(x, y, z);
        boolean swim = Double.isNaN(floor) && terrain.water(x, y, z);
        BlockPos place = null;
        double cost = node.swim || swim ? SWIM : WALK;
        if (Double.isNaN(floor) && !swim) {
            if (node.swim || !terrain.placeable(x, y - 1, z)) return;
            place = new BlockPos(x, y - 1, z);
            floor = y;
            cost += PLACE_COST;
        } else if (!swim && Math.abs(floor - from) > Terrain.STEP) {
            return;
        }
        double body = swim ? y : floor, top = Math.max(from, body);
        Terrain.Clearing clearing = new Terrain.Clearing();
        if (!terrain.clearSweep(node.x, node.z, x, z, top + 0.01, top + Terrain.BODY, clearing)
                || !terrain.clearColumn(x, z, body + 0.01, body + Terrain.BODY, clearing)) return;
        if ((swim || node.swim) && !clearing.breaks.isEmpty()) return;
        cost += clearing.cost + (swim ? 0 : terrain.floorPenalty(x, y, z));
        consider(node, x, y, z, swim ? Double.NaN : floor, swim, cost,
                new Step(swim || node.swim ? Step.Kind.SWIM : Step.Kind.WALK, node.pos(), new BlockPos(x, y, z),
                        swim ? Double.NaN : floor, clearing.opens, clearing.breaks, place));
    }

    /** Onto the next cell one level up, jumping when the rise is more than a step. */
    private void ascend(Node node, int dx, int dz) {
        int x = node.x + dx, y = node.y + 1, z = node.z + dz;
        if (!inRange(x, z) || terrain.hazard(x, y, z) || terrain.hazard(x, y + 1, z)) return;
        double from = node.base(), floor = terrain.floor(x, y, z);
        if (Double.isNaN(floor) || floor <= from || floor - from > Terrain.JUMP) return;
        Terrain.Clearing clearing = new Terrain.Clearing();
        if (!terrain.clearColumn(node.x, node.z, from + 0.01, floor + Terrain.BODY, clearing)
                || !terrain.clearSweep(node.x, node.z, x, z, floor + 0.01, floor + Terrain.BODY, clearing)
                || !terrain.clearColumn(x, z, floor + 0.01, floor + Terrain.BODY, clearing)) return;
        if (node.swim && !clearing.breaks.isEmpty()) return;
        double cost = WALK + (floor - from > Terrain.STEP ? JUMP_COST : 0) + clearing.cost + terrain.floorPenalty(x, y, z);
        consider(node, x, y, z, floor, false, cost,
                new Step(Step.Kind.ASCEND, node.pos(), new BlockPos(x, y, z), floor, clearing.opens, clearing.breaks, null));
    }

    /** Off an edge into the neighbouring column: a mined step down, a safe drop, or a splash into water. */
    private void descend(Node node, int dx, int dz) {
        if (node.swim) return;
        int x = node.x + dx, z = node.z + dz;
        if (!inRange(x, z)) return;
        double from = node.floor;
        Terrain.Clearing clearing = new Terrain.Clearing();
        if (!terrain.clearSweep(node.x, node.z, x, z, from + 0.01, from + Terrain.BODY, clearing)) return;
        for (int k = 1; k <= MAX_FALL_SCAN; k++) {
            int y = node.y - k;
            if (y <= terrain.level.getMinY() || terrain.hazard(x, y, z)) return;
            if (terrain.water(x, y, z)) {
                if (!terrain.clearColumn(x, z, y + 0.01, from + Terrain.BODY, clearing) || !clearing.breaks.isEmpty()) return;
                consider(node, x, y, z, Double.NaN, true, WALK + FALL_COST * k + clearing.cost,
                        new Step(Step.Kind.DESCEND, node.pos(), new BlockPos(x, y, z), Double.NaN, clearing.opens, clearing.breaks, null));
                return;
            }
            double floor = terrain.floor(x, y, z);
            if (Double.isNaN(floor) && k == 1 && terrain.mayEdit) floor = terrain.floorBelow(x, y, z);
            if (!Double.isNaN(floor)) {
                if (from - floor > Terrain.SAFE_FALL || floor >= from || terrain.hazard(x, y + 1, z)) return;
                if (!terrain.clearColumn(x, z, floor + 0.01, from + Terrain.BODY, clearing)) return;
                if (k > 1 && !clearing.breaks.isEmpty()) return; // only a one-block step down may be mined
                double cost = WALK + FALL_COST * k + clearing.cost + terrain.floorPenalty(x, y, z);
                consider(node, x, y, z, floor, false, cost,
                        new Step(Step.Kind.DESCEND, node.pos(), new BlockPos(x, y, z), floor, clearing.opens, clearing.breaks, null));
                return;
            }
            // Nothing to land on here: the body falls through this cell, which therefore has to be open already.
            Terrain.Clearing probe = new Terrain.Clearing();
            if (!terrain.clearColumn(x, z, y, y + 1, probe) || !probe.breaks.isEmpty() || !probe.opens.isEmpty()) return;
        }
    }

    /** Level diagonal move through open space only; corners are never mined or bridged. */
    private void diagonal(Node node, int dx, int dz) {
        if (node.swim) return;
        int x = node.x + dx, y = node.y, z = node.z + dz;
        if (!inRange(x, z) || terrain.hazard(x, y, z) || terrain.hazard(x, y + 1, z)) return;
        double floor = terrain.floor(x, y, z);
        if (Double.isNaN(floor) || Math.abs(floor - node.floor) > Terrain.STEP) return;
        double top = Math.max(floor, node.floor);
        Terrain.Clearing clearing = new Terrain.Clearing();
        if (!terrain.clearSweep(node.x, node.z, x, z, top + 0.01, top + Terrain.BODY, clearing)
                || !clearing.breaks.isEmpty() || !clearing.opens.isEmpty()) return;
        consider(node, x, y, z, floor, false, WALK * DIAGONAL + terrain.floorPenalty(x, y, z),
                new Step(Step.Kind.DIAGONAL, node.pos(), new BlockPos(x, y, z), floor, List.of(), List.of(), null));
    }

    /** Jump and build under the feet, mining the ceiling first if needed. */
    private void pillar(Node node) {
        if (node.swim || !terrain.placeable(node.x, node.y, node.z)) return;
        int y = node.y + 1;
        if (!inRange(node.x, node.z) || terrain.hazard(node.x, y, node.z) || terrain.hazard(node.x, y + 1, node.z)) return;
        Terrain.Clearing clearing = new Terrain.Clearing();
        if (!terrain.clearColumn(node.x, node.z, node.floor + 0.01, y + Terrain.BODY, clearing)) return;
        consider(node, node.x, y, node.z, y, false, PILLAR_COST + PLACE_COST + clearing.cost,
                new Step(Step.Kind.PILLAR, node.pos(), new BlockPos(node.x, y, node.z), y, clearing.opens, clearing.breaks, node.pos()));
    }

    /** Mine the block underfoot and drop onto the one below it. */
    private void digDown(Node node) {
        if (node.swim || !terrain.mayEdit || Math.abs(node.floor - node.y) > 1.0E-6) return;
        int y = node.y - 1;
        double floor = terrain.floorBelow(node.x, y, node.z);
        if (Double.isNaN(floor) || terrain.hazard(node.x, y, node.z)) return;
        Terrain.Clearing clearing = new Terrain.Clearing();
        if (!terrain.clearColumn(node.x, node.z, floor + 0.01, node.y + Terrain.BODY, clearing) || clearing.breaks.isEmpty()) return;
        consider(node, node.x, y, node.z, floor, false, FALL_COST + clearing.cost + terrain.floorPenalty(node.x, y, node.z),
                new Step(Step.Kind.DIG_DOWN, node.pos(), new BlockPos(node.x, y, node.z), floor, clearing.opens, clearing.breaks, null));
    }

    private void swimVertically(Node node) {
        if (!node.swim) return;
        for (int dy = -1; dy <= 1; dy += 2) {
            int y = node.y + dy;
            if (terrain.water(node.x, y, node.z)) {
                consider(node, node.x, y, node.z, Double.NaN, true, SWIM,
                        new Step(Step.Kind.SWIM, node.pos(), new BlockPos(node.x, y, node.z), Double.NaN, List.of(), List.of(), null));
            }
        }
    }

    private boolean inRange(int x, int z) {
        return Math.abs(x - start.x) <= RANGE && Math.abs(z - start.z) <= RANGE && terrain.loaded(x, z);
    }

    private double heuristic(int x, int y, int z) {
        return goal.distance(x, y, z) * WALK * HEURISTIC;
    }

    private void consider(Node from, int x, int y, int z, double floor, boolean swim, double cost, Step step) {
        if (Math.abs(y - start.y) > RANGE || terrain.level.isOutsideBuildHeight(y + 1)
                || terrain.level.isOutsideBuildHeight(y - 1)) return;
        long key = BlockPos.asLong(x, y, z);
        if (avoid.contains(key)) cost += AVOID_COST;
        double g = from.g + cost;
        Node node = nodes.get(key);
        if (node == null) {
            node = new Node(x, y, z);
            node.h = heuristic(x, y, z);
            nodes.put(key, node);
        } else if (node.closed || g >= node.g) {
            return;
        }
        node.g = g;
        node.floor = floor;
        node.swim = swim;
        node.parent = from;
        node.via = step;
        open.add(new Open(node, g, g + node.h));
    }
}
