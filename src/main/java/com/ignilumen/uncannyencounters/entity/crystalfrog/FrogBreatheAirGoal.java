package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Surface before air runs out. Unlike vanilla BreathAirGoal, travel still moves the body only once. */
public final class FrogBreatheAirGoal extends Goal {
    private final CrystalFrog frog;
    private Vec3 destination = Vec3.ZERO;
    private int searchCooldown;

    public FrogBreatheAirGoal(CrystalFrog frog) {
        this.frog = frog;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }
    @Override public boolean canUse() {
        return frog.isAlive() && !frog.isPassenger() && frog.isEyeInFluid(FluidTags.WATER) && frog.getAirSupply() < 200;
    }
    @Override public boolean canContinueToUse() {
        return frog.isAlive() && !frog.isPassenger() && frog.isInWater() && frog.getAirSupply() < frog.getMaxAirSupply();
    }
    @Override public boolean isInterruptable() { return false; }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        searchCooldown = 0;
        frog.setSeekingAir(true);
        frog.setInSittingPose(false);
        frog.getNavigation().stop();
    }
    @Override public void stop() {
        frog.setSeekingAir(false);
        frog.getNavigation().stop();
        frog.getMoveControl().setWait();
    }
    @Override public void tick() {
        frog.setInSittingPose(false);
        if (!frog.isEyeInFluid(FluidTags.WATER)) {
            // Stay at the surface until air has recovered instead of immediately diving after the owner.
            frog.getNavigation().stop();
            frog.getMoveControl().setWait();
            return;
        }
        if (--searchCooldown <= 0) {
            searchCooldown = 20;
            destination = findSurface();
            frog.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.15);
        }
        if (visible(destination.add(0, frog.getEyeHeight(), 0)) || frog.getNavigation().isDone())
            frog.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.15);
    }
    private Vec3 findSurface() {
        Vec3 best = null;
        double score = Double.MAX_VALUE;
        for (int dy = 0; dy <= 32; dy++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos air = frog.blockPosition().offset(dx, dy, dz);
            if (!frog.level().isLoaded(air) || !frog.level().getFluidState(air).isEmpty()
                    || !frog.level().getBlockState(air).getCollisionShape(frog.level(), air).isEmpty()) continue;
            Vec3 feet = new Vec3(air.getX() + 0.5, air.getY() + 0.15 - frog.getEyeHeight(), air.getZ() + 0.5);
            double distance = frog.distanceToSqr(feet);
            if (distance >= score) continue;
            var box = frog.getBoundingBox().move(feet.subtract(frog.position()));
            if (!frog.level().isLoaded(BlockPos.containing(box.minX, box.minY, box.minZ))
                    || !frog.level().isLoaded(BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                    || !frog.level().noCollision(frog, box)) continue;
            double cost = distance + (visible(feet.add(0, frog.getEyeHeight(), 0)) ? 0 : 1024);
            if (cost < score) { score = cost; best = feet; }
        }
        // In deep water, climb in stages until a surface enters the bounded search.
        return best == null ? frog.position().add(0, 4, 0) : best;
    }
    private boolean visible(Vec3 point) {
        return frog.level().clip(new ClipContext(frog.getEyePosition(), point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, frog)).getType() == HitResult.Type.MISS;
    }
}
