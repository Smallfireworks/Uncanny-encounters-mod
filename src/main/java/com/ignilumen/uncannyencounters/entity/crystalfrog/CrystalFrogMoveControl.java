package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/** Short land hops with a landing pause, and swimming on the same amphibious path. */
public final class CrystalFrogMoveControl extends MoveControl<CrystalFrog> {
    private int landingPause;
    private boolean airborne;

    public CrystalFrogMoveControl(CrystalFrog frog) { super(frog); }

    @Override public void tick() {
        mob.setSpeed(0);
        mob.setXxa(0);
        mob.setYya(0);
        mob.setZza(0);
        if (mob.onGround()) {
            if (airborne) landingPause = 2;
            airborne = false;
        } else airborne = true;
        // The AI launches before this controller ticks. Do not brake the initial impulse
        // merely because vanilla collision has not yet updated onGround for this tick.
        if (mob.isDueling() && mob.duelCombat().keepsHopMomentum()) {
            operation = Operation.WAIT;
            return;
        }
        boolean moving = operation == Operation.MOVE_TO && !mob.getNavigation().isDone()
                && !mob.isInSittingPose() && !mob.isPassenger();
        if (mob.isInWater()) {
            airborne = false;
            landingPause = 0;
            swim(moving);
            return;
        }
        mob.setXRot(0);
        if (mob.onGround() && landingPause > 0) {
            landingPause--;
            brake();
            return;
        }
        if (!moving) {
            if (mob.onGround()) brake();
            return;
        }
        // Navigation refreshes this point every tick; never retain a stopped path's command.
        operation = Operation.WAIT;
        double dx = wantedX - mob.getX(), dz = wantedZ - mob.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 0.08) {
            if (mob.onGround()) brake();
            return;
        }
        float yaw = (float)(Mth.atan2(dz, dx) * 180 / Math.PI) - 90;
        mob.setYRot(rotlerp(mob.getYRot(), yaw, 35));
        mob.yBodyRot = mob.getYRot();
        double speed = Math.min(distance, speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        double vx = dx / distance * speed, vz = dz / distance * speed;
        if (mob.onGround()) mob.hop(vx, vz, wantedY > mob.getY() + 0.4 || mob.horizontalCollision);
        else {
            Vec3 velocity = mob.getDeltaMovement();
            mob.setDeltaMovement(Mth.lerp(0.3, velocity.x, vx), velocity.y, Mth.lerp(0.3, velocity.z, vz));
        }
    }

    private void brake() {
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(motion.x * 0.35, motion.y, motion.z * 0.35);
    }

    private void swim(boolean moving) {
        if (!moving) {
            mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.005, 0));
            return;
        }
        operation = Operation.WAIT;
        Vec3 direction = new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ());
        if (direction.lengthSqr() < 0.01) return;
        float yaw = (float)(Mth.atan2(direction.z, direction.x) * 180 / Math.PI) - 90;
        mob.setYRot(rotlerp(mob.getYRot(), yaw, 15));
        mob.yBodyRot = mob.getYRot();
        mob.setXRot((float)Math.clamp(-Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())), -60, 60));
        Vec3 target = direction.normalize().scale(speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.7);
        mob.setDeltaMovement(mob.getDeltaMovement().lerp(target, 0.15));
        if (mob.horizontalCollision && wantedY > mob.getY() && mob.onGround()) mob.hop(target.x, target.z, true);
    }
}
