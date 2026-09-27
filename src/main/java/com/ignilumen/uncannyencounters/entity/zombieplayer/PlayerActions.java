package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import com.ignilumen.uncannyencounters.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/** Shared physical aim and input limits for every evolved building/combat action. */
public final class PlayerActions {
    public static final double BUILD_REACH = 4.5;
    private static final double[] FACE_SAMPLES = {0.5, 0.2, 0.8};
    private final ZombiePlayer mob;
    private final ActionTiming timing = new ActionTiming();
    private long cpsUntil, placementTick = Long.MIN_VALUE;
    private int cps = 12;

    public PlayerActions(ZombiePlayer mob) { this.mob = mob; }

    public LookControl lookControl() {
        ZombiePlayer actor = mob;
        return new LookControl(mob) {
            @Override public void setLookAt(double x, double y, double z, float yawSpeed, float pitchSpeed) {
                if (!actor.evolved()) super.setLookAt(x, y, z, yawSpeed, pitchSpeed);
                else if (placementTick != mob.level().getGameTime()) aim(new Vec3(x, y, z));
            }
            @Override public void tick() {
                if (!actor.evolved()) super.tick();
            }
        };
    }

    public void aim(Vec3 point) {
        Vec3 delta = point.subtract(mob.getEyePosition());
        float yaw = delta.horizontalDistanceSqr() < 1.0E-8 ? mob.getYRot()
                : (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float)(-Mth.atan2(delta.y, delta.horizontalDistance()) * Mth.RAD_TO_DEG);
        float dy = Mth.wrapDegrees(yaw - mob.getYRot()), dx = pitch - mob.getXRot();
        float distance = (float)Math.hypot(dy, dx);
        if (distance < 1.0E-5) return;
        float fraction = timing.turn(mob.level().getGameTime(), distance) / distance;
        mob.setYRot(mob.getYRot() + dy * fraction);
        mob.setYHeadRot(mob.getYRot());
        mob.setXRot(mob.getXRot() + dx * fraction);
    }

    public boolean canAttack(Entity target, double reach) {
        long now = mob.level().getGameTime();
        if (placementTick == now || !timing.canAttack(now)) return false;
        return attackRay(target, mob.getEyePosition().add(mob.getLookAngle().scale(reach)));
    }

    /** Is stopping to aim useful here? Distance alone cannot establish an unobstructed hit. */
    public boolean hasAttackLine(Entity target, double reach) {
        Vec3 eye = mob.getEyePosition();
        return attackRay(target, eye.add(target.getEyePosition().subtract(eye).normalize().scale(reach)));
    }

    private boolean attackRay(Entity target, Vec3 end) {
        Vec3 eye = mob.getEyePosition();
        var contact = target.getBoundingBox().clip(eye, end);
        if (contact.isEmpty() && !target.getBoundingBox().contains(eye)) return false;
        Vec3 hit = contact.orElse(eye);
        if (!clearSight(hit)) return false;
        return !entityBlocksRay(eye, hit, target);
    }

    private boolean entityBlocksRay(Vec3 eye, Vec3 end, @Nullable Entity intendedTarget) {
        double distance = eye.distanceToSqr(end);
        for (Entity other : mob.level().getEntities(mob, new AABB(eye, end).inflate(0.1),
                e -> e != intendedTarget && e.isPickable() && !e.isSpectator())) {
            if (other.getBoundingBox().contains(eye)
                    || other.getBoundingBox().clip(eye, end).filter(p -> eye.distanceToSqr(p) < distance).isPresent()) return true;
        }
        return false;
    }

    public void attacked() { timing.attacked(mob.level().getGameTime()); }

    public boolean clearSight(Vec3 point) {
        return mob.level().clip(new ClipContext(mob.getEyePosition(), point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
    }

    public boolean seesProjectile(Vec3 point) {
        return mob.getLookAngle().dot(point.subtract(mob.getEyePosition())) >= 0 && clearSight(point);
    }

    public boolean canPlanPlacement(ServerLevel level, BlockPos pos) {
        return level.getGameRules().get(GameRules.MOB_GRIEFING) && replaceable(level, pos) && placementFace(level, pos) != null;
    }

    /** A failed click still spends the shared input slot. Aiming does not promise success. */
    public boolean tryPlace(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        placementTick = now;
        Vec3 face = placementFace(level, pos);
        if (face != null) aim(face);
        if (now >= cpsUntil) {
            cps = 10 + mob.getRandom().nextInt(5);
            cpsUntil = now + 20;
        }
        if (!timing.click(now, cps)) return false;
        mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        if (mob.isUsingItem() || !level.getGameRules().get(GameRules.MOB_GRIEFING) || !replaceable(level, pos)) return false;
        Vec3 eye = mob.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(mob.getLookAngle().scale(BUILD_REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mob));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().relative(hit.getDirection()).equals(pos)) return false;
        if (entityBlocksRay(eye, hit.getLocation(), null)) return false;
        if (level.getBlockState(hit.getBlockPos()).getCollisionShape(level, hit.getBlockPos()).isEmpty()) return false;
        return TemporaryEdits.get(level).placeBlock(level, pos, mob);
    }

    private boolean replaceable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) return false;
        var state = level.getBlockState(pos);
        return state.canBeReplaced() && !state.hasBlockEntity()
                && level.isUnobstructed(ModBlocks.ZOMBIE_BLOCK.defaultBlockState(), pos, CollisionContext.of(mob));
    }

    /** Find a visible point on a neighbouring shape, not the centre of an empty destination. */
    private @Nullable Vec3 placementFace(ServerLevel level, BlockPos pos) {
        Vec3 eye = mob.getEyePosition(), best = null;
        double nearest = BUILD_REACH * BUILD_REACH;
        for (Direction side : Direction.values()) {
            BlockPos support = pos.relative(side);
            if (!level.isLoaded(support)) continue;
            var shape = level.getBlockState(support).getCollisionShape(level, support);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                AABB world = box.move(support);
                // A wall may hide the centre while leaving the edge of a face clickable.
                for (double u : FACE_SAMPLES) {
                    for (double v : FACE_SAMPLES) {
                        Vec3 point = facePoint(world, side, u, v);
                        double distance = eye.distanceToSqr(point);
                        if (distance > nearest) continue;
                        BlockHitResult hit = level.clip(new ClipContext(eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mob));
                        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                                && hit.getDirection() == side.getOpposite()) {
                            best = point;
                            nearest = distance;
                        }
                    }
                }
            }
        }
        return best;
    }

    static Vec3 facePoint(AABB box, Direction side, double u, double v) {
        double x = Mth.lerp(u, box.minX, box.maxX), z = Mth.lerp(v, box.minZ, box.maxZ);
        double y = Mth.lerp(u, box.minY, box.maxY);
        Vec3 surface = switch (side) {
            case DOWN -> new Vec3(x, box.maxY, z);
            case UP -> new Vec3(x, box.minY, z);
            case WEST -> new Vec3(box.maxX, y, z);
            case EAST -> new Vec3(box.minX, y, z);
            case NORTH -> new Vec3(x, Mth.lerp(v, box.minY, box.maxY), box.maxZ);
            case SOUTH -> new Vec3(x, Mth.lerp(v, box.minY, box.maxY), box.minZ);
        };
        return surface.add(side.getStepX() * 0.001, side.getStepY() * 0.001, side.getStepZ() * 0.001);
    }

}
