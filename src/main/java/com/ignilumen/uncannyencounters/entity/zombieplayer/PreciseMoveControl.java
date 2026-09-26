package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Steering with friction-aware braking and lateral corrections, keeping bridge and pillar
 * approaches centred without teleporting the body or cancelling external knockback.
 */
public final class PreciseMoveControl extends MoveControl<ZombiePlayer> {
    private Vec3 target = Vec3.ZERO;
    private boolean stop;
    private float face = Float.NaN;

    public PreciseMoveControl(ZombiePlayer mob) {
        super(mob);
    }

    /** Heads for {@code point} this tick; with {@code stop} it arrives with no speed left. Call every tick. */
    public void steer(Vec3 point, double speedModifier, boolean stop) {
        steer(point, speedModifier, stop, Float.NaN);
    }

    /** As {@link #steer(Vec3, double, boolean)}, but the body keeps facing {@code yaw}, strafing or backing off as needed. */
    public void steer(Vec3 point, double speedModifier, boolean stop, float yaw) {
        target = point;
        this.speedModifier = speedModifier;
        this.stop = stop;
        face = yaw;
        operation = Operation.MOVE_TO;
    }

    @Override
    public void tick() {
        if (operation != Operation.MOVE_TO) {
            super.tick(); // strafing (ranged combat) and idling behave like vanilla
            return;
        }
        operation = Operation.WAIT;
        float speed = (float)(speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        mob.setSpeed(speed);
        double dx = target.x - mob.getX(), dz = target.z - mob.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (mob.isInWater() || mob.isInLava()) {
            // Fluid drag is different; swimming only needs a heading and full input.
            if (distance > 0.1) mob.setYRot(rotlerp(mob.getYRot(), (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90, 90));
            mob.setXxa(0);
            mob.setZza(distance > 0.2 ? 1 : 0);
            return;
        }
        // Mirror LivingEntity.travelInAir: accelerate by the input, then keep friction * 0.91 of the velocity.
        float friction = mob.onGround()
                ? mob.level().getBlockState(mob.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() : 1.0F;
        double keep = friction * 0.91F;
        double accel = mob.onGround() ? (friction > 0.6F ? speed * 0.216F / (friction * friction * friction) : speed) : mob.airAcceleration();
        double limit = accel * 0.98; // LivingEntity.applyInput scales input by 0.98
        double fullSpeed = limit / (1 - keep);
        // Whatever moves this tick keeps coasting keep/(1-keep) times as far, so move distance*(1-keep) to land on the point.
        double wanted = stop ? Math.min(fullSpeed, distance * (1 - keep)) : fullSpeed;
        double wx = distance < 1.0E-4 ? 0 : dx / distance * wanted, wz = distance < 1.0E-4 ? 0 : dz / distance * wanted;
        Vec3 velocity = mob.getDeltaMovement();
        double ax = wx - velocity.x, az = wz - velocity.z, needed = Math.sqrt(ax * ax + az * az);
        if (needed > limit) {
            ax *= limit / needed;
            az *= limit / needed;
        }
        if (!Float.isNaN(face)) {
            mob.setYRot(rotlerp(mob.getYRot(), face, 60));
        } else if (distance > 0.3 && wanted > 0.02) {
            mob.setYRot(rotlerp(mob.getYRot(), (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90, 90));
        }
        // Inverse of Entity.getInputVector's rotation; any remainder becomes strafing, so turning never drifts.
        float yaw = mob.getYRot() * Mth.DEG_TO_RAD, sin = Mth.sin(yaw), cos = Mth.cos(yaw);
        mob.setXxa(limit <= 0 ? 0 : (float)((ax * cos + az * sin) / limit));
        mob.setZza(limit <= 0 ? 0 : (float)((az * cos - ax * sin) / limit));
    }
}
