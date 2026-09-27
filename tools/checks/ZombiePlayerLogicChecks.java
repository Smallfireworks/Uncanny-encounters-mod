package com.ignilumen.uncannyencounters.entity.zombieplayer;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** Offline checks of production logic; no Minecraft client, server or world is started. */
public final class ZombiePlayerLogicChecks {
    public static void main(String[] args) {
        for (int cps = 10; cps <= 14; cps++) {
            ActionTiming timing = new ActionTiming();
            int clicks = 0;
            for (long tick = 0; tick < 2000; tick++) {
                if (timing.click(tick, cps)) clicks++;
                check(!timing.click(tick, cps), "Multiple callers clicked in one tick");
            }
            check(Math.abs(clicks - cps * 100) <= 1, "CPS drift: " + cps + " -> " + clicks);
        }
        ActionTiming timing = new ActionTiming();
        check(timing.click(0, 10), "First click missing");
        check(!timing.click(1, 10), "Failed placement refunded its click");
        check(timing.click(1000, 10), "Did not resume after idle");
        check(!timing.click(1000, 10) && !timing.click(1001, 10), "Idle time banked clicks");
        check(timing.click(1002, 10), "Click clock did not resume");
        check(!timing.canAttack(1002), "Attack and placement in one tick");
        check(timing.canAttack(1003), "Unexpected extra attack cooldown");
        timing.attacked(1003);
        check(!timing.click(1003, 14), "Placement after attack in one tick");
        check(!timing.canAttack(1003), "Two attacks in one tick");
        check(timing.turn(0, 30) == 30, "First aim rejected");
        check(timing.turn(0, 90) == 15, "Second aim exceeded remaining budget");
        check(timing.turn(0, 90) == 0, "Third aim bypassed turn budget");
        check(timing.turn(1, 90) == 45, "Next tick did not restore turn budget");
        projectileReaction();
        archerRelease();
        combatFooting();
        miningSupport();
        ballisticPrediction();
        placementFaces();
        System.out.println("CPS, action exclusion, turns, projectile memory/ballistics, archer release, combat footing, mining support and placement rays: PASS");
    }

    private static void projectileReaction() {
        Vec3 body = new Vec3(0, 1, 0), velocity = new Vec3(0, 0, -1);
        UUID arrow = UUID.randomUUID();
        ProjectileAwareness memory = new ProjectileAwareness(null);
        check(memory.dodge(0, body, 1) == null, "Dodged an unobserved projectile");
        memory.remember(arrow, new Vec3(0, 1, 10), velocity, body, 0, 5);
        for (int tick = 0; tick < 5; tick++)
            check(memory.dodge(tick, body, 1) == null, "Skipped initial reaction delay");
        check(memory.dodge(5, body, 1) != null, "Lost short-lived memory when looking away");
        check(memory.dodge(7, body, 1) == null, "Retained hidden projectile beyond memory window");
        memory = new ProjectileAwareness(null);
        memory.remember(arrow, new Vec3(0, 1, 10), velocity, body, 0, 3);
        memory.remember(arrow, new Vec3(0, 1, 9), velocity, body, 1, 5);
        check(memory.dodge(2, body, 1) == null, "New observation skipped original delay");
        check(memory.dodge(3, body, 1) != null, "New observation restarted original delay");
        memory = new ProjectileAwareness(null);
        memory.remember(arrow, new Vec3(0, 1, 2), velocity, body, 0, 5);
        check(memory.dodge(1, body, 1) == null, "Imminent hit skipped reaction delay");
        check(memory.dodge(5, body, 1) == null, "Dodged projectile that already passed");
        memory = new ProjectileAwareness(null);
        memory.remember(arrow, new Vec3(0, 1, 10), velocity.scale(-1), body, 0, 3);
        check(memory.dodge(3, body, 1) == null, "Fleeing projectile triggered dodge");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void archerRelease() {
        UUID archer = UUID.randomUUID(), other = UUID.randomUUID();
        ArcherAwareness memory = new ArcherAwareness();
        check(!memory.wary(archer, 0), "Unknown archer triggered warning");
        memory.notice(archer, 10, 5);
        memory.notice(archer, 11, 5);
        check(!memory.ready(archer, 14), "Aiming cue bypassed reaction delay");
        check(memory.ready(archer, 15), "Repeated cue restarted reaction delay");
        check(memory.ready(archer, 30), "Bow release lost archer warning");
        check(!memory.wary(other, 30), "Archer warning leaked to another target");
        check(!memory.wary(archer, 52), "Archer warning did not expire");
        memory.notice(archer, 60, 3);
        check(!memory.ready(archer, 62) && memory.ready(archer, 63), "New cue did not restore reaction delay");
    }

    private static void combatFooting() {
        Vec3 start = new Vec3(0.5, 20, 0.5), end = new Vec3(6.5, 20, 0.5);
        check(CombatFooting.connected(start, end, p -> new Vec3(p.x, 20, p.z)), "Open floor rejected");
        check(!CombatFooting.connected(start, end, p -> p.x > 1 && p.x < 6 ? null : new Vec3(p.x, 20, p.z)),
                "Same-height platform across five-block void admitted to melee");
        check(!CombatFooting.connected(start, end, p -> new Vec3(p.x, p.x > 1 && p.x < 6 ? 0 : 20, p.z)),
                "Ground far below mistaken for a connected combat floor");
        check(!CombatFooting.connected(start, end, p -> p.x > 3 && p.x < 3.5 ? null : new Vec3(p.x, 20, p.z)),
                "Path check skipped an intermediate hole");
        check(CombatFooting.connected(start, end, p -> new Vec3(p.x, p.x < 3 ? 20 : 19.5, p.z)),
                "Half-block descent rejected");
        Vec3 ledge = new Vec3(2.5, 21, 0.5);
        check(!CombatFooting.canApproach(start, ledge, p -> new Vec3(p.x, p.x < 2 ? 20 : 21, p.z)),
                "Standing below the final ledge incorrectly handed movement to melee");
        check(CombatFooting.canApproach(start, new Vec3(2.5, 21.2, 0.5), p -> new Vec3(p.x, 20, p.z)),
                "Target jumping above the same floor incorrectly required pillaring");
        check(CombatFooting.canApproach(start, new Vec3(2.5, 20, 0.5), p -> new Vec3(p.x, 20, p.z)),
                "Nearby target on the same floor rejected");
    }

    private static void miningSupport() {
        AABB body = new AABB(0.2, 20, 0.2, 0.8, 21.8, 0.8);
        BlockPos floor = new BlockPos(0, 19, 0);
        check(MiningSafety.supportsBody(body, floor, Shapes.block()), "Current footing could be mined as an obstacle");
        check(MiningSafety.supportsBody(body.move(0, 1.2, 0), floor, Shapes.block()), "Jumping lost landing support protection");
        check(!MiningSafety.supportsBody(body, new BlockPos(1, 20, 0), Shapes.block()), "Blocking side wall incorrectly protected");
        check(!MiningSafety.supportsBody(body, new BlockPos(0, 22, 0), Shapes.block()), "Ceiling incorrectly protected");
        check(!MiningSafety.supportsBody(body, floor, Shapes.empty()), "Empty shape treated as footing");
        check(MiningSafety.supportsBody(body.move(0, -0.5, 0), floor, Shapes.box(0, 0, 0, 1, 0.5, 1)),
                "Half-height footing could be mined as an obstacle");
    }

    private static void ballisticPrediction() {
        UUID arrow = UUID.randomUUID();
        Vec3 position = new Vec3(0, 5, 30), velocity = new Vec3(0, 0, -3);
        // Reproduce the observed air-arrow trajectory to make a real curved intercept.
        Vec3 impact = position, moving = velocity;
        for (int tick = 0; tick < 10; tick++) {
            impact = impact.add(moving);
            moving = moving.scale(0.99).add(0, -0.05, 0);
        }
        ProjectileAwareness memory = new ProjectileAwareness(null);
        memory.remember(arrow, position, velocity, impact, 0, 3, 0.99, 0.05);
        check(memory.dodge(2, impact, 1) == null, "Ballistic forecast bypassed reaction delay");
        check(memory.dodge(3, impact, 1) != null, "Gravity-curved incoming arrow was ignored");
        check(memory.dodge(6, impact, 1) != null, "Hidden extrapolation lost gravity/drag");
        memory = new ProjectileAwareness(null);
        memory.remember(arrow, position, velocity, new Vec3(5, impact.y, impact.z), 0, 3, 0.99, 0.05);
        check(memory.dodge(3, new Vec3(5, impact.y, impact.z), 1) == null, "Clear ballistic miss triggered dodge");
    }

    private static void placementFaces() {
        BlockPos support = new BlockPos(-4, 5, 12);
        for (double height : new double[]{1, 0.5}) {
            var shape = Shapes.box(0, 0, 0, 1, height, 1);
            AABB box = shape.bounds().move(support);
            for (Direction side : Direction.values()) {
                for (double u : new double[]{0.2, 0.5, 0.8}) {
                    for (double v : new double[]{0.2, 0.5, 0.8}) {
                        Vec3 point = PlayerActions.facePoint(box, side, u, v);
                        Vec3 eye = point.add(-side.getStepX() * 2, -side.getStepY() * 2, -side.getStepZ() * 2);
                        var hit = shape.clip(eye, point, support);
                        check(hit != null && hit.getDirection() == side.getOpposite(), "Incorrect attachment face: " + side);
                        check(hit.getBlockPos().relative(hit.getDirection()).equals(support.relative(side.getOpposite())),
                                "Face points to wrong placement cell");
                    }
                }
            }
        }
        Vec3 eye = new Vec3(0.45, 1.62, -0.5);
        AABB floor = new AABB(new BlockPos(1, -1, 0));
        AABB wall = new AABB(0, 0, 0, 1, 2, 1);
        Vec3 center = PlayerActions.facePoint(floor, Direction.DOWN, 0.5, 0.5);
        check(wall.clip(eye, center).isPresent(), "Partial occlusion fixture does not hide face centre");
        int visibleSamples = 0;
        for (double u : new double[]{0.2, 0.5, 0.8}) {
            for (double v : new double[]{0.2, 0.5, 0.8}) {
                Vec3 point = PlayerActions.facePoint(floor, Direction.DOWN, u, v);
                if (wall.clip(eye, point).isEmpty()) visibleSamples++;
            }
        }
        check(visibleSamples > 0, "Sampling missed exposed edges beside a defensive wall");
    }
}
