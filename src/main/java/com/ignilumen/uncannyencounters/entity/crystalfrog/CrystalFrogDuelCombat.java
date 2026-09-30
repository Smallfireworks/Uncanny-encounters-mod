package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Duel-only actions. Ordinary navigation and actual collision/flight decide whether attacks connect. */
public final class CrystalFrogDuelCombat {
    public static final byte NORMAL = 0, WINDUP = 1, POUNCE = 2, RECOVERY = 3, GUARDING = 4, COUNTER = 5, EVADE = 6;
    public static final int WINDUP_TICKS = 10, GUARD_TICKS = 18, COUNTER_TICKS = 4;
    private static final Identifier STRIKE_BONUS = UncannyEncounters.id("frog_duel_strike");
    private final CrystalFrog frog;
    private byte action;
    private int remaining, attackCooldown, pounceCooldown, guardCooldown, nextPathAt, actionAge;
    private int side = 1;
    private @Nullable Vec3 aim;
    private boolean hopStarted;

    public CrystalFrogDuelCombat(CrystalFrog frog) { this.frog = frog; }
    public byte action() { return action; }
    public boolean keepsHopMomentum() {
        return action == POUNCE || action == EVADE && hopStarted && frog.getNavigation().isDone();
    }

    public void reset() {
        action = NORMAL;
        remaining = attackCooldown = pounceCooldown = guardCooldown = nextPathAt = actionAge = 0;
        aim = null;
        hopStarted = false;
        side = frog.getRandom().nextBoolean() ? 1 : -1;
        frog.setDuelPose(NORMAL);
        frog.getNavigation().stop();
        frog.getMoveControl().setWait();
    }

    public void tick() {
        if (!(frog.getTarget() instanceof CrystalFrog other) || !frog.isDuelingWith(other)) { reset(); return; }
        if (attackCooldown > 0) attackCooldown--;
        if (pounceCooldown > 0) pounceCooldown--;
        if (guardCooldown > 0) guardCooldown--;
        actionAge++;
        if (action != NORMAL) {
            tickAction(other);
            return;
        }
        face(other, 25);
        switch (frog.duelStyle()) {
            case POUNCER -> pouncer(other);
            case SKIRMISHER -> skirmisher(other);
            case GUARD -> guard(other);
        }
    }

    private void pouncer(CrystalFrog other) {
        double distance = frog.distanceToSqr(other);
        if (pounceCooldown == 0 && distance >= 0.5 && distance <= 4.5 * 4.5 && canHop(other.position())) {
            aim = other.position();
            begin(WINDUP, WINDUP_TICKS);
            hold();
        } else if (attackCooldown == 0 && canHit(other)) {
            strike(other, 1);
            if (frog.isDueling()) begin(RECOVERY, 8);
        } else approach(other.position(), 1.1);
    }

    private void skirmisher(CrystalFrog other) {
        boolean blockingFront = other.duelCombat().action() == GUARDING && other.duelCombat().inFront(frog);
        if (!blockingFront && attackCooldown == 0 && canHit(other)) {
            strike(other, 1);
            if (frog.isDueling()) evade(other);
            return;
        }
        if (frog.distanceToSqr(other) < 4 * 4 && (blockingFront || attackCooldown > 0)) {
            Vec3 outward = horizontalFrom(other);
            double angle = side * 1.05;
            Vec3 around = new Vec3(outward.x * Math.cos(angle) - outward.z * Math.sin(angle), 0,
                    outward.x * Math.sin(angle) + outward.z * Math.cos(angle));
            Vec3 flank = other.position().add(around.scale(1.25));
            if (safeLanding(flank)) {
                if (!approach(flank, 1.15)) side = -side;
                return;
            }
            side = -side;
        }
        approach(other.position(), 1.15);
    }

    private void guard(CrystalFrog other) {
        if (guardCooldown == 0 && frog.onGround() && frog.distanceToSqr(other) <= 3.2 * 3.2
                && frog.getSensing().hasLineOfSight(other)) {
            begin(GUARDING, GUARD_TICKS);
            hold();
        } else if (attackCooldown == 0 && canHit(other)) {
            strike(other, 1);
            if (frog.isDueling()) begin(RECOVERY, 9);
        } else approach(other.position(), 0.95);
    }

    private void tickAction(CrystalFrog other) {
        remaining--;
        switch (action) {
            case WINDUP -> {
                hold();
                face(other, 12);
                // Aim locks for the final part of the crouch; flight itself never homes in.
                if (remaining > 4) aim = other.position();
                if (remaining <= 0) {
                    if (aim != null && canHop(aim)) {
                        launch(aim, 0.21);
                        begin(POUNCE, 18);
                    } else {
                        pounceCooldown = 30;
                        begin(RECOVERY, 16);
                    }
                }
            }
            case POUNCE -> {
                if (canHit(other) && inFront(other)) {
                    strike(other, 1.25);
                    if (frog.isDueling()) {
                        pounceCooldown = 32;
                        begin(RECOVERY, 16);
                    }
                } else if (remaining <= 0 || actionAge > 2 && frog.onGround()) {
                    pounceCooldown = 36;
                    begin(RECOVERY, 26);
                }
            }
            case GUARDING -> {
                hold();
                face(other, 4);
                if (remaining <= 0) {
                    guardCooldown = 36;
                    begin(RECOVERY, 7);
                }
            }
            case COUNTER -> {
                hold();
                face(other, 18);
                if (remaining <= 0) {
                    if (canHit(other)) strike(other, 1.2);
                    if (frog.isDueling()) begin(RECOVERY, 10);
                }
            }
            case EVADE -> {
                face(other, 12);
                if (!hopStarted && aim != null && frog.onGround()) {
                    hopStarted = true;
                    if (canHop(aim)) launch(aim, 0.21);
                    else approach(aim, 1.15);
                }
                if (remaining <= 0) begin(NORMAL, 0);
            }
            case RECOVERY -> {
                hold();
                if (remaining <= 0) begin(NORMAL, 0);
            }
            default -> begin(NORMAL, 0);
        }
    }

    /** The first frontal hit spends the guard and starts a visible counter windup. */
    public float defend(CrystalFrog attacker, float damage) {
        return defend(attacker, attacker.position(), damage);
    }

    public boolean canGuardFrom(CrystalFrog attacker, Vec3 origin) {
        Vec3 incoming = origin.subtract(frog.position());
        return action == GUARDING && frog.onGround() && frog.isDuelingWith(attacker)
                && facesAttack(frog.getYRot(), incoming.x, incoming.z);
    }

    public float defend(CrystalFrog attacker, Vec3 origin, float damage) {
        if (damage <= 0 || !Float.isFinite(damage) || !canGuardFrom(attacker, origin)) return damage;
        guardCooldown = 44;
        begin(COUNTER, COUNTER_TICKS);
        frog.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.8F, 1.4F);
        if (frog.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                frog.getX(), frog.getY(0.7), frog.getZ(), 6, 0.2, 0.15, 0.2, 0, 0.02, 0);
        return damage * 0.35F;
    }

    public boolean inFront(CrystalFrog other) {
        Vec3 offset = other.position().subtract(frog.position());
        return facesAttack(frog.getYRot(), offset.x, offset.z);
    }

    public static boolean facesAttack(float yaw, double dx, double dz) {
        double length = Math.hypot(dx, dz);
        if (length < 0.01) return true;
        double radians = Math.toRadians(yaw);
        return (-Math.sin(radians) * dx + Math.cos(radians) * dz) / length >= 0.5;
    }

    private void evade(CrystalFrog other) {
        Vec3 away = horizontalFrom(other);
        for (int sign : new int[]{side, -side}) {
            Vec3 destination = frog.position().add(away.scale(0.65)).add(-away.z * sign * 1.7, 0, away.x * sign * 1.7);
            if (safeLanding(destination)) {
                side = sign;
                aim = destination;
                hopStarted = false;
                begin(EVADE, 14);
                return;
            }
        }
        begin(RECOVERY, 8);
    }

    private Vec3 horizontalFrom(CrystalFrog other) {
        Vec3 offset = frog.position().subtract(other.position()).multiply(1, 0, 1);
        return offset.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : offset.normalize();
    }

    private boolean canHit(CrystalFrog other) {
        return frog.isDuelingWith(other) && frog.canAttack(other) && frog.isWithinMeleeAttackRange(other)
                && frog.getSensing().hasLineOfSight(other);
    }

    private void strike(CrystalFrog other, double multiplier) {
        if (!canHit(other) || !(frog.level() instanceof ServerLevel level)) return;
        attackCooldown = 22;
        frog.swingForAttack(InteractionHand.MAIN_HAND);
        var attack = frog.getAttribute(Attributes.ATTACK_DAMAGE);
        // Preserve vanilla damage/enchantment/knockback handling without changing permanent stats.
        attack.addTransientModifier(new AttributeModifier(STRIKE_BONUS, multiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        try { frog.doHurtTarget(level, other); }
        finally { attack.removeModifier(STRIKE_BONUS); }
    }

    private void begin(byte next, int ticks) {
        action = next;
        remaining = ticks;
        actionAge = 0;
        nextPathAt = 0;
        frog.setDuelPose(next);
        if (next != NORMAL) frog.getNavigation().stop();
    }

    private void hold() {
        frog.getNavigation().stop();
        frog.getMoveControl().setWait();
    }

    private boolean approach(Vec3 point, double speed) {
        if (!frog.level().isLoaded(BlockPos.containing(point))) return false;
        if (frog.tickCount < nextPathAt) return !frog.getNavigation().isDone();
        nextPathAt = frog.tickCount + 8;
        return frog.getNavigation().moveTo(point.x, point.y, point.z, 0, speed);
    }

    private boolean safeLanding(Vec3 point) {
        if (frog.duelMatch() != null && !frog.duelMatch().allowsPosition(frog, point)) return false;
        BlockPos feet = BlockPos.containing(point);
        if (!frog.level().isLoaded(feet) || !frog.level().isLoaded(feet.below())) return false;
        var floor = frog.level().getBlockState(feet.below());
        return frog.level().getFluidState(feet).isEmpty() && !frog.getType().isBlockDangerous(floor)
                && !floor.getCollisionShape(frog.level(), feet.below()).isEmpty()
                && frog.level().noCollision(frog, frog.getBoundingBox().move(point.subtract(frog.position())));
    }

    private boolean canHop(Vec3 point) {
        if (!frog.onGround() || frog.isInWater() || Math.abs(point.y - frog.getY()) > 0.6 || !safeLanding(point)) return false;
        Vec3 delta = point.subtract(frog.position());
        if (delta.horizontalDistanceSqr() > 5 * 5) return false;
        // Check the body along the arc, not just the endpoint. No blocks are removed or chunks loaded.
        for (int step = 1; step <= 6; step++) {
            double t = step / 6.0;
            Vec3 offset = delta.scale(t).add(0, Math.sin(Math.PI * t) * 0.7, 0);
            if (!frog.level().isLoaded(BlockPos.containing(frog.position().add(offset)))
                    || !frog.level().noCollision(frog, frog.getBoundingBox().move(offset))) return false;
        }
        return true;
    }

    private void launch(Vec3 point, double velocityScale) {
        hold();
        Vec3 offset = point.subtract(frog.position());
        frog.hop(offset.x * velocityScale, offset.z * velocityScale, false);
    }

    private void face(CrystalFrog other, float turn) {
        double dx = other.getX() - frog.getX(), dz = other.getZ() - frog.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        frog.setYRot(frog.getYRot() + Math.clamp(Mth.wrapDegrees(yaw - frog.getYRot()), -turn, turn));
        frog.yHeadRot = frog.yBodyRot = frog.getYRot();
        frog.getLookControl().setLookAt(other, turn, 20);
    }
}
