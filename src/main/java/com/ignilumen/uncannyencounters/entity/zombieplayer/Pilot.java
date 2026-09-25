package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Carries out {@link RoutePlanner} routes one step at a time: open doors, mine, build, then move.
 * A new route is searched over several ticks while the current step keeps running and is spliced
 * after it, so replanning never makes the zombie stand and think. Blocks are only placed from solid
 * footing against an existing face, and every careful move keeps the sneak-style edge guard on.
 */
public final class Pilot {
    private static final int PLAN_BUDGET = 400, PLAN_INTERVAL = 10, RETRY_DELAY = 30, AVOID_TICKS = 200,
            STALL_TICKS = 30, STEP_TIMEOUT = 600, BREAK_DELAY = 5, MAX_JUMPS = 4;
    private static final double REACH = 4.5, SNEAK = 0.3;

    private final ZombiePlayer mob;
    private final PreciseMoveControl steering;
    private final Long2LongOpenHashMap avoid = new Long2LongOpenHashMap();
    private RoutePlanner.@Nullable Goal desired, planned;
    private boolean editing, sprint, unreachable;
    private @Nullable RoutePlanner search;
    private @Nullable Step anchor;
    private List<Step> path = List.of();
    private int index, planDelay;
    // Progress of the current step.
    private int stepTicks, stallTicks, breakDelay, jumps, crackStage = -1;
    private double bestDistance = Double.MAX_VALUE;
    private boolean placed;
    private @Nullable BlockPos mining;
    private @Nullable BlockState miningState;
    private float miningProgress;
    private int swingTicks, progressPhase;
    private static final int MOVING = 0, BRIDGING = 1, APPROACHING = 2;

    public Pilot(ZombiePlayer mob) {
        this.mob = mob;
        this.steering = mob.steering();
    }

    /** Move towards the goal; {@code editing} allows mining and building on the way. Call every tick. */
    public void follow(RoutePlanner.Goal goal, boolean editing, boolean sprint) {
        this.sprint = sprint;
        if (this.editing != editing) planned = null;
        this.editing = editing;
        desired = goal;
    }

    /** Hand movement back to the caller. */
    public void stop(ServerLevel level) {
        desired = planned = null;
        search = null;
        unreachable = false;
        clearPath(level);
        hold();
    }

    public boolean active() {
        return desired != null;
    }

    /** The last search could only get closer, not reach the goal. */
    boolean unreachable() {
        return unreachable;
    }

    /** Working on a block, so the head must keep looking at it. */
    public boolean handlingBlock() {
        Step step = current();
        return mining != null || step != null && step.place() != null && !placed;
    }

    public void tick(ServerLevel level) {
        if (desired == null) return;
        long now = level.getGameTime();
        avoid.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
        if (planDelay > 0) planDelay--;
        boolean arrived = current() == null && desired.reached(feetCell().getX(), feetCell().getY(), feetCell().getZ());
        if (search == null && planDelay <= 0 && !arrived && (planned == null || !planned.sameAs(desired) || current() == null)) {
            startSearch(level);
        }
        if (search != null && search.run(PLAN_BUDGET)) finishSearch(level);
        Step step = current();
        if (step != null) execute(level, step);
        else hold();
    }

    private @Nullable Step current() {
        return index < path.size() ? path.get(index) : null;
    }

    private void startSearch(ServerLevel level) {
        Step step = current();
        BlockPos from;
        double floor;
        boolean swimming;
        if (step != null) {
            // Plan from where the running step ends; the result is spliced in after it.
            from = step.to();
            floor = step.floor();
            swimming = Double.isNaN(floor);
        } else {
            if (!mob.onGround() && !mob.isInWater()) return; // replan once landed
            from = feetCell();
            swimming = mob.isInWater() && !mob.onGround();
            floor = swimming ? Double.NaN : mob.getY();
        }
        anchor = step;
        planned = desired;
        Terrain terrain = new Terrain(level, editing);
        if (step != null) terrain.commit(step);
        search = new RoutePlanner(terrain, from, floor, swimming, desired, avoid.keySet());
        planDelay = PLAN_INTERVAL;
    }

    private void finishSearch(ServerLevel level) {
        RoutePlanner done = search;
        search = null;
        List<Step> result = done.result();
        unreachable = !done.complete();
        if (result.isEmpty()) planDelay = RETRY_DELAY;
        Step step = current();
        // A search anchored to a step already passed must not send the body backwards.
        if (anchor != null && step != anchor && !feetCell().equals(anchor.to())) {
            planned = null;
            planDelay = 0;
            return;
        }
        List<Step> next = new ArrayList<>(result.size() + 1);
        if (step != null && step == anchor) {
            next.add(step);
        } else if (step != null && result.isEmpty()) {
            return; // keep following the old route rather than stopping
        } else {
            resetStep(level);
        }
        next.addAll(result);
        path = next;
        index = 0;
    }

    private void execute(ServerLevel level, Step step) {
        if (offTrack(step)) {
            fail(level, step);
            return;
        }
        if (breakDelay > 0) breakDelay--;
        for (BlockPos door : step.opens()) {
            BlockState state = level.getBlockState(door);
            if (!Terrain.openable(state)) continue;
            if (!within(door, 3)) {
                approach(level, step);
                return;
            }
            open(level, door, state);
        }
        for (BlockPos pos : step.breaks()) {
            BlockState state = level.getBlockState(pos);
            if (state.getCollisionShape(level, pos).isEmpty()) continue;
            mine(level, step, pos, state); // mining time is bounded by the block itself, not the step timeout
            return;
        }
        if (++stepTicks > STEP_TIMEOUT) {
            fail(level, step);
        } else if (step.kind() == Step.Kind.PILLAR) {
            pillar(level, step);
        } else if (step.place() != null && !placed) {
            bridge(level, step);
        } else {
            move(level, step);
        }
    }

    private void open(ServerLevel level, BlockPos pos, BlockState state) {
        mob.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(mob, level, state, pos, true);
            return;
        }
        level.setBlock(pos, Terrain.opened(state), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        level.playSound(null, pos, state.getBlock() instanceof FenceGateBlock ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.WOODEN_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, 1, level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(mob, GameEvent.BLOCK_OPEN, pos);
    }

    private void mine(ServerLevel level, Step step, BlockPos pos, BlockState state) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            fail(level, step);
            return;
        }
        if (!within(pos, REACH)) {
            approach(level, step);
            return;
        }
        mob.setEdgeGuard(true);
        mob.setSprinting(false);
        brake();
        mob.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        if (breakDelay > 0) return;
        if (!pos.equals(mining) || state != miningState) {
            clearCrack(level);
            mining = pos.immutable();
            miningState = state;
            miningProgress = 0;
        }
        float progress = Terrain.progress(level, state, pos);
        if (progress <= 0) {
            fail(level, step);
            return;
        }
        // Same penalties as a player: airborne and underwater mining are five times slower.
        if (!mob.onGround()) progress /= 5;
        if (mob.isEyeInFluid(FluidTags.WATER)) progress /= 5;
        miningProgress += progress;
        if (swingTicks++ % 4 == 0) {
            mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            SoundType sound = state.getSoundType();
            level.playSound(null, pos, sound.getHitSound(), SoundSource.HOSTILE, (sound.getVolume() + 1) / 8, sound.getPitch() * 0.5F);
        }
        if (miningProgress >= 1) {
            clearCrack(level);
            mining = null;
            if (!TemporaryEdits.get(level).breakBlock(level, pos, mob)) {
                fail(level, step);
                return;
            }
            breakDelay = BREAK_DELAY;
            stallTicks = 0;
            return;
        }
        int stage = Math.min(9, (int)(miningProgress * 10));
        if (stage != crackStage) {
            crackStage = stage;
            level.destroyBlockProgress(mob.getId(), pos, stage);
        }
    }

    /** Sneak to the edge (the guard stops at the last supported spot) and build under the next cell. */
    private void bridge(ServerLevel level, Step step) {
        BlockPos pos = step.place();
        if (!level.getBlockState(pos).canBeReplaced()) {
            placed = true;
            return;
        }
        mob.setCrouching(true);
        mob.setEdgeGuard(true);
        mob.setSprinting(false);
        Vec3 edge = center(step.from()).add((step.to().getX() - step.from().getX()) * 0.8, 0, (step.to().getZ() - step.from().getZ()) * 0.8);
        steering.steer(edge, SNEAK, true);
        mob.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        double distance = horizontalDistance(edge);
        if (!mob.onGround() || distance > 0.35 && !stalled(BRIDGING, distance, 8)) return;
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), entity -> entity != mob).isEmpty()) return;
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING) || !TemporaryEdits.get(level).placeBlock(level, pos, mob)) {
            fail(level, step);
            return;
        }
        mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        placed = true;
    }

    private void pillar(ServerLevel level, Step step) {
        mob.setEdgeGuard(true);
        mob.setSprinting(false);
        BlockPos pos = step.place();
        if (placed) {
            steering.steer(center(step.to()), SNEAK, true);
            if (mob.onGround() && mob.getY() >= step.floor() - 0.01) advance(level);
            return;
        }
        Vec3 center = center(step.from());
        steering.steer(center, SNEAK, true);
        mob.getLookControl().setLookAt(Vec3.atCenterOf(pos.below()));
        if (mob.onGround()) {
            if (horizontalDistance(center) < 0.2 && mob.getDeltaMovement().horizontalDistanceSqr() < 0.004) {
                if (++jumps > MAX_JUMPS) fail(level, step);
                else mob.getJumpControl().jump();
            }
            return;
        }
        if (mob.getY() < pos.getY() + 1.0 || !level.getBlockState(pos).canBeReplaced()) return;
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), entity -> entity != mob).isEmpty()) return;
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING) || !TemporaryEdits.get(level).placeBlock(level, pos, mob)) {
            fail(level, step);
            return;
        }
        mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        placed = true;
    }

    private void move(ServerLevel level, Step step) {
        Step next = index + 1 < path.size() ? path.get(index + 1) : null;
        boolean stop = step.stopsBefore(next);
        Step.Kind kind = step.kind();
        boolean swimming = kind == Step.Kind.SWIM || Double.isNaN(step.floor());
        boolean careful = step.place() != null; // stepping onto a block just built
        mob.setEdgeGuard(kind != Step.Kind.DESCEND && kind != Step.Kind.DIG_DOWN && !swimming);
        mob.setCrouching(careful);
        mob.setSprinting(sprint && !careful && !swimming && (kind == Step.Kind.WALK || kind == Step.Kind.DIAGONAL || kind == Step.Kind.ASCEND));
        Vec3 target = center(step.to());
        steering.steer(target, careful ? SNEAK : 1.0, stop);
        double distance = horizontalDistance(target);
        if (mob.onGround() && !Double.isNaN(step.floor()) && step.floor() - mob.getY() > Terrain.STEP
                && (kind == Step.Kind.ASCEND && distance < 1.3 || mob.horizontalCollision)) {
            mob.getJumpControl().jump();
        }
        if (mob.isInWater() && step.to().getY() >= mob.getY() - 0.2) mob.getJumpControl().jump();
        mob.getLookControl().setLookAt(target.x, mob.getEyeY(), target.z);
        boolean straight = next != null && !stop && next.to().getX() - next.from().getX() == step.to().getX() - step.from().getX()
                && next.to().getZ() - next.from().getZ() == step.to().getZ() - step.from().getZ();
        if (feetCell().equals(step.to()) && (mob.onGround() || swimming && mob.isInWater())
                && distance < (stop ? 0.3 : straight ? 0.7 : 0.4)) {
            advance(level);
            return;
        }
        if (stalled(MOVING, distance, STALL_TICKS)) fail(level, step);
    }

    private void advance(ServerLevel level) {
        index++;
        resetStep(level);
    }

    /** Couldn't finish the step: steer the next search away from it for a while. */
    private void fail(ServerLevel level, Step step) {
        avoid.put(step.to().asLong(), level.getGameTime() + AVOID_TICKS);
        clearPath(level);
        search = null;
        anchor = null;
        planned = null;
        planDelay = 5;
    }

    private void clearPath(ServerLevel level) {
        path = List.of();
        index = 0;
        resetStep(level);
    }

    private void resetStep(ServerLevel level) {
        clearCrack(level);
        mining = null;
        miningState = null;
        placed = false;
        stepTicks = stallTicks = jumps = swingTicks = 0;
        progressPhase = -1;
        bestDistance = Double.MAX_VALUE;
        mob.setCrouching(false);
    }

    public void clearCrack(ServerLevel level) {
        if (mining != null && crackStage >= 0) level.destroyBlockProgress(mob.getId(), mining, -1);
        crackStage = -1;
    }

    /** No route yet (or waiting for one): come to a stop without leaving the block. */
    private void hold() {
        mob.setEdgeGuard(mob.onGround());
        mob.setSprinting(false);
        brake();
    }

    private void brake() {
        steering.steer(mob.position(), 1.0, true);
    }

    private void approach(ServerLevel level, Step step) {
        mob.setEdgeGuard(true);
        steering.steer(center(step.from()), 1.0, true);
        if (stalled(APPROACHING, horizontalDistance(center(step.from())), STALL_TICKS)) fail(level, step);
    }

    private boolean stalled(int phase, double distance, int ticks) {
        if (progressPhase != phase) {
            progressPhase = phase;
            bestDistance = Double.MAX_VALUE;
            stallTicks = 0;
        }
        if (distance < bestDistance - 0.02) {
            bestDistance = distance;
            stallTicks = 0;
            return false;
        }
        return ++stallTicks > ticks;
    }

    /** On the ground somewhere that is neither end of the step nor between them: knocked off course. */
    private boolean offTrack(Step step) {
        if (!mob.onGround() && !mob.isInWater()) return false;
        BlockPos cell = feetCell();
        if (cell.equals(step.from()) || cell.equals(step.to())) return false;
        double ax = step.from().getX() + 0.5, az = step.from().getZ() + 0.5;
        double bx = step.to().getX() + 0.5 - ax, bz = step.to().getZ() + 0.5 - az;
        double length = bx * bx + bz * bz;
        double t = length == 0 ? 0 : Math.clamp(((mob.getX() - ax) * bx + (mob.getZ() - az) * bz) / length, 0, 1);
        double ox = mob.getX() - (ax + bx * t), oz = mob.getZ() - (az + bz * t);
        return ox * ox + oz * oz > 1.2 * 1.2 || cell.getY() < Math.min(step.from().getY(), step.to().getY()) - 1
                || cell.getY() > Math.max(step.from().getY(), step.to().getY()) + 1;
    }

    private BlockPos feetCell() {
        return BlockPos.containing(mob.getX(), mob.getY() + 0.01, mob.getZ());
    }

    private Vec3 center(BlockPos cell) {
        return new Vec3(cell.getX() + 0.5, mob.getY(), cell.getZ() + 0.5);
    }

    private double horizontalDistance(Vec3 point) {
        double dx = point.x - mob.getX(), dz = point.z - mob.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean within(BlockPos pos, double reach) {
        return mob.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach;
    }

    /** Whether the open door or block still blocks, re-checked in case a player closed it again. */
    static boolean isOpen(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
    }
}
