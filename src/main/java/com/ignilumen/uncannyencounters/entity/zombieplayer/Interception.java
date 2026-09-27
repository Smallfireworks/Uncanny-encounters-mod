package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** One obstacle on an observed approach, with a persistent decision and recovery clock. */
final class Interception {
    private final ZombiePlayer mob;
    private @Nullable BlockPos planned;
    private @Nullable UUID targetId;
    private long readyAt, expiresAt, nextAt;

    Interception(ZombiePlayer mob) { this.mob = mob; }

    void cancel() {
        if (planned != null) finish(mob.level().getGameTime());
    }

    boolean tick(ServerLevel level, LivingEntity target, Vec3 observedVelocity, int attackCooldown) {
        long now = level.getGameTime();
        // Once contact is available, attacking is worth more than continuing a stale obstacle plan.
        if (attackCooldown <= 2 && Duelist.inReach(mob, target)) {
            cancel();
            return false;
        }
        Vec3 velocity = observedVelocity.multiply(1, 0, 1);
        Vec3 toward = mob.position().subtract(target.position()).multiply(1, 0, 1);
        boolean approaching = velocity.dot(toward.normalize()) > 0.06;
        if (planned != null && (!target.getUUID().equals(targetId) || !approaching || now >= expiresAt
                || !onApproach(planned, target.position(), velocity))) finish(now);
        if (planned == null) {
            double distance = toward.length();
            if (now < nextAt || !approaching || !target.onGround() || distance < 2 || distance > 6) return false;
            // Leave enough lead for the reaction delay and the turn towards the floor.
            for (int ticks = 8; ticks <= 10; ticks++) {
                BlockPos candidate = BlockPos.containing(target.position().add(velocity.scale(ticks)));
                double advance = Vec3.atCenterOf(candidate).subtract(target.position()).dot(toward.normalize());
                if (advance <= 0 || advance >= distance - 0.6) continue;
                if (onApproach(candidate, target.position(), velocity) && mob.actions().canPlanPlacement(level, candidate)) {
                    planned = candidate;
                    targetId = target.getUUID();
                    readyAt = now + 3 + mob.getRandom().nextInt(3);
                    expiresAt = readyAt + 10;
                    break;
                }
            }
        }
        if (planned == null || now < readyAt) return false;
        // Keep this exact cell; changing course cannot teleport the obstacle to the new route.
        if (mob.actions().tryPlace(level, planned)) finish(now);
        return true;
    }

    private static boolean onApproach(BlockPos pos, Vec3 target, Vec3 velocity) {
        return Vec3.atCenterOf(pos).subtract(target).dot(velocity) > 0
                && new AABB(pos).inflate(0.3, 0, 0.3).clip(target.add(0, 0.1, 0), target.add(velocity.scale(10)).add(0, 0.1, 0)).isPresent();
    }

    private void finish(long now) {
        planned = null;
        targetId = null;
        nextAt = now + 12 + mob.getRandom().nextInt(9);
    }
}
