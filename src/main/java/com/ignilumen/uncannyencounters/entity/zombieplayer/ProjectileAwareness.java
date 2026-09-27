package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Observed trajectories only: turning away retains a short, fallible extrapolation. */
final class ProjectileAwareness {
    private static final int MEMORY_TICKS = 6;
    private final ZombiePlayer mob;
    private final Map<UUID, Seen> seen = new HashMap<>();
    private long updatedAt = Long.MIN_VALUE;
    private record Seen(Vec3 position, Vec3 velocity, long observedAt, long reactAt, double drag, double gravity) {}
    private record Flight(Vec3 position, Vec3 velocity) {
        Flight next(double drag, double gravity) {
            return new Flight(position.add(velocity), velocity.scale(drag).add(0, -gravity, 0));
        }
    }
    private record Threat(double time, Vec3 closest, Vec3 velocity) {}

    ProjectileAwareness(ZombiePlayer mob) { this.mob = mob; }

    void observe(ServerLevel level) {
        long now = level.getGameTime();
        if (updatedAt == now) return;
        updatedAt = now;
        for (Projectile arrow : level.getEntitiesOfClass(Projectile.class, mob.getBoundingBox().inflate(32),
                p -> p.getOwner() != mob && p.getDeltaMovement().lengthSqr() > 0.25)) {
            Vec3 position = arrow.getBoundingBox().getCenter();
            boolean visible = mob.actions().seesProjectile(position);
            if (visible) remember(arrow.getUUID(), position, arrow.getDeltaMovement(), mob.getBoundingBox().getCenter(),
                    now, 3 + mob.getRandom().nextInt(3),
                    arrow instanceof AbstractArrow && !arrow.isInWater() ? 0.99 : 1,
                    arrow instanceof AbstractArrow && !arrow.isInWater() && !arrow.isNoGravity() ? 0.05 : 0);
        }
    }

    /** Call only with an actual visual observation. Hidden projectiles never refresh this memory. */
    void remember(UUID id, Vec3 position, Vec3 velocity, Vec3 body, long now, int reactionTicks) {
        remember(id, position, velocity, body, now, reactionTicks, 1, 0);
    }

    void remember(UUID id, Vec3 position, Vec3 velocity, Vec3 body, long now, int reactionTicks, double drag, double gravity) {
        expire(now);
        Seen previous = seen.get(id);
        if (previous == null && threat(new Flight(position, velocity), body, drag, gravity) == null) return;
        long reaction = previous == null ? now + reactionTicks : previous.reactAt;
        seen.put(id, new Seen(position, velocity, now, reaction, drag, gravity));
    }

    private void expire(long now) { seen.values().removeIf(value -> now - value.observedAt > MEMORY_TICKS); }

    private static @Nullable Threat threat(Flight flight, Vec3 body, double drag, double gravity) {
        for (int tick = 0; tick < 20; tick++) {
            if (flight.velocity.lengthSqr() < 1.0E-6) return null;
            double along = body.subtract(flight.position).dot(flight.velocity) / flight.velocity.lengthSqr();
            Vec3 closest = flight.position.add(flight.velocity.scale(Math.clamp(along, 0, 1)));
            if (along > 0 && closest.distanceToSqr(body) <= 1.3 * 1.3)
                return new Threat(tick + Math.min(along, 1), closest, flight.velocity);
            flight = flight.next(drag, gravity);
        }
        return null;
    }

    @Nullable Vec3 dodge(ServerLevel level, int preferredSide) {
        observe(level);
        return dodge(level.getGameTime(), mob.getBoundingBox().getCenter(), preferredSide);
    }

    @Nullable Vec3 dodge(long now, Vec3 body, int preferredSide) {
        expire(now);
        Vec3 best = null;
        double earliest = Double.MAX_VALUE;
        for (Seen arrow : seen.values()) {
            if (now < arrow.reactAt) continue;
            Flight flight = new Flight(arrow.position, arrow.velocity);
            for (long tick = arrow.observedAt; tick < now; tick++) flight = flight.next(arrow.drag, arrow.gravity);
            Threat threat = threat(flight, body, arrow.drag, arrow.gravity);
            if (threat == null || threat.time >= earliest) continue;
            Vec3 across = new Vec3(-threat.velocity.z, 0, threat.velocity.x).normalize();
            if (across.lengthSqr() < 1.0E-4) across = new Vec3(1, 0, 0);
            double side = body.subtract(threat.closest).dot(across);
            best = across.scale(side == 0 ? preferredSide : Math.signum(side));
            earliest = threat.time;
        }
        return best;
    }
}
