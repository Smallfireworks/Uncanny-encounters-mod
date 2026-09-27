package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Short combat moves require real support; longer or discontinuous travel belongs to the pilot. */
public final class CombatFooting {
    private final ZombiePlayer mob;
    public CombatFooting(ZombiePlayer mob) { this.mob = mob; }

    public @Nullable Vec3 floor(Vec3 point) {
        double top = Double.NEGATIVE_INFINITY, bottom = Double.POSITIVE_INFINITY;
        for (double x : new double[]{-0.27, 0.27}) {
            for (double z : new double[]{-0.27, 0.27}) {
                Vec3 from = point.add(x, 0.1, z), to = point.add(x, -3, z);
                var hit = mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
                if (hit.getType() != HitResult.Type.BLOCK || hit.isInside()
                        || mob.level().getFluidState(hit.getBlockPos().above()).is(FluidTags.LAVA)) return null;
                top = Math.max(top, hit.getLocation().y);
                bottom = Math.min(bottom, hit.getLocation().y);
            }
        }
        if (top - bottom > 0.6) return null;
        AABB body = new AABB(point.x - 0.29, top + 0.01, point.z - 0.29, point.x + 0.29, top + 1.8, point.z + 0.29);
        if (mob.level().getBlockCollisions(mob, body).iterator().hasNext()) return null;
        return new Vec3(point.x, top, point.z);
    }

    public boolean connected(Vec3 to) {
        return connected(mob.position(), to, this::floor);
    }

    static boolean connected(Vec3 from, Vec3 to, Function<Vec3, @Nullable Vec3> sampleFloor) {
        return connectedFloor(from, to, sampleFloor) != null;
    }

    private static @Nullable Vec3 connectedFloor(Vec3 from, Vec3 to, Function<Vec3, @Nullable Vec3> sampleFloor) {
        Vec3 previous = sampleFloor.apply(from);
        if (previous == null) return null;
        int steps = Math.max(1, (int)Math.ceil(to.subtract(from).horizontalDistance() / 0.4));
        for (int i = 1; i <= steps; i++) {
            Vec3 point = from.lerp(to, (double)i / steps);
            Vec3 next = sampleFloor.apply(new Vec3(point.x, previous.y, point.z));
            if (next == null || Math.abs(next.y - previous.y) > 0.6) return null;
            previous = next;
        }
        return previous;
    }

    public boolean canApproach(Vec3 target) {
        return canApproach(mob.position(), target, this::floor);
    }

    static boolean canApproach(Vec3 from, Vec3 target, Function<Vec3, @Nullable Vec3> sampleFloor) {
        Vec3 offset = target.subtract(from).multiply(1, 0, 1);
        double distance = offset.length();
        Vec3 stop = from.add(offset.normalize().scale(Math.max(0, distance - 1.8)));
        Vec3 approach = connectedFloor(from, stop, sampleFloor), destination = sampleFloor.apply(target);
        // Stopping short of a ledge is not completing its ascent. A jump by the target does
        // not count as a ledge: compare its supporting floor rather than its airborne feet.
        return approach != null && destination != null && Math.abs(approach.y - destination.y) <= 0.6;
    }

    public boolean roomToJump() {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if ((x != 0 || z != 0) && !connected(mob.position().add(x * 1.5, 0, z * 1.5))) return false;
        }
        return true;
    }

    /** Shorten a sidestep/retreat at an unsupported edge, without cancelling external knockback. */
    public Vec3 constrain(Vec3 wanted) {
        Vec3 delta = wanted.subtract(mob.position());
        for (double fraction : new double[]{1, 0.6, 0.3}) {
            Vec3 candidate = mob.position().add(delta.scale(fraction));
            if (connected(candidate)) return candidate;
        }
        return mob.position();
    }

    public boolean dangerousBelow(BlockPos feet) {
        for (int depth = 1; depth <= 3; depth++) {
            BlockPos below = feet.below(depth);
            if (mob.level().getFluidState(below).is(FluidTags.LAVA)) return true;
            if (!mob.level().getBlockState(below).getCollisionShape(mob.level(), below).isEmpty()) return false;
        }
        return true;
    }
}
