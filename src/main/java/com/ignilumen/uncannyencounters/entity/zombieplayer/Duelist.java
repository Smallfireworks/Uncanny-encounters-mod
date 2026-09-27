package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Close combat of an evolved zombie player, modelled on how skilled players duel. Sprint hits carry
 * extra knockback and are followed by a W-tap (releasing sprint for a moment) so the next hit gets it
 * again; close trades are won with falling crits instead. While the attack recharges it keeps just
 * outside the opponent's reach and circles; it sidesteps projectiles, flanks so that knockback carries
 * the target into lava or off a drop. Incoming rushes can be interrupted by one aimed obstacle.
 */
final class Duelist {
    /** Player entity reach, from the eyes to the target's hitbox. */
    static final double REACH = 3.0;
    private static final double SPACING = 3.3, ENGAGE = 7, FLANK = 2.6;
    private static final int WTAP_TICKS = 3, HAZARD_SCAN = 3, CLIFF = 4;
    private static final AttributeModifier CRIT = new AttributeModifier(UncannyEncounters.id("zombie_player_crit"),
            0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeModifier SPRINT_HIT = new AttributeModifier(UncannyEncounters.id("zombie_player_sprint_hit"),
            1, AttributeModifier.Operation.ADD_VALUE);

    private final ZombiePlayer mob;
    private final PreciseMoveControl steering;
    private final Interception interception;
    private final ProjectileAwareness projectiles;
    private int wtap, strafeTicks, strafeSide = 1, hazardTicks;
    private boolean critJump;
    private Vec3 dodge = Vec3.ZERO;
    private @Nullable Vec3 hazard;
    private final ArcherAwareness archers = new ArcherAwareness();

    Duelist(ZombiePlayer mob) {
        this.mob = mob;
        steering = mob.steering();
        interception = new Interception(mob);
        projectiles = new ProjectileAwareness(mob);
    }

    static boolean inReach(ZombiePlayer mob, LivingEntity target) {
        return target.getBoundingBox().distanceToSqr(mob.getEyePosition()) <= REACH * REACH;
    }

    /** Close, visible and on roughly the same level: fought directly instead of following a route. */
    boolean engages(LivingEntity target, boolean visible) {
        return visible && mob.distanceToSqr(target) <= ENGAGE * ENGAGE && Math.abs(target.getY() - mob.getY()) < 2.5
                && !mob.isInWater() && mob.footing().canApproach(target.position());
    }

    void observe(ServerLevel level) { projectiles.observe(level); }

    void watchArcher(LivingEntity target, boolean visible) {
        if (!visible || !aiming(target)) return;
        Vec3 toward = mob.getBoundingBox().getCenter().subtract(target.getEyePosition()).normalize();
        if (target.getLookAngle().dot(toward) < 0.8) return;
        long now = mob.level().getGameTime();
        archers.notice(target.getUUID(), now, 3 + mob.getRandom().nextInt(3));
    }

    boolean waryOfArcher(LivingEntity target) {
        return archers.wary(target.getUUID(), mob.level().getGameTime());
    }

    void reset() {
        interception.cancel();
        wtap = 0;
        critJump = false;
        hazard = null;
        hazardTicks = 0;
    }

    /** One tick of melee. Returns the attack cooldown after this tick. */
    int melee(ServerLevel level, LivingEntity target, int cooldown, Vec3 observedVelocity) {
        projectiles.observe(level);
        boolean building = interception.tick(level, target, observedVelocity, cooldown);
        if (--hazardTicks <= 0) {
            hazardTicks = 10;
            hazard = hazardDirection(level, target);
        }
        float face = yawTo(target.position());
        if (!building) mob.getLookControl().setLookAt(target, 45, 45);
        Vec3 offset = flat(target.position().subtract(mob.position()));
        double distance = offset.length();
        boolean airborne = !mob.onGround() && !mob.isInWater() && !mob.onClimbable();
        if (!airborne) critJump = false;

        if (!building && cooldown == 0 && inReach(mob, target) && mob.actions().canAttack(target, REACH)) {
            boolean falling = airborne && mob.getDeltaMovement().y < 0;
            // Mid crit jump: hold the swing until the fall, unless the target is about to slip out of reach.
            boolean holdForCrit = critJump && airborne && !falling && distance < REACH - 0.6;
            if (!holdForCrit) {
                boolean sprintHit = mob.isSprinting() && !falling;
                hit(level, target, falling, sprintHit);
                if (sprintHit) {
                    wtap = WTAP_TICKS;
                }
                cooldown = cooldown(mob);
            }
        }

        if (dodgeProjectile(level)) {
            move(mob.position().add(dodge.scale(2)), true, face);
        } else if (archers.ready(target.getUUID(), level.getGameTime()) && distance > REACH + 1) {
            if (++strafeTicks > 8 + mob.getRandom().nextInt(9) || mob.horizontalCollision) {
                strafeTicks = 0;
                strafeSide = -strafeSide;
            }
            Vec3 heading = offset.normalize();
            Vec3 side = new Vec3(-heading.z, 0, heading.x).scale(strafeSide);
            move(mob.position().add(heading.scale(1.5)).add(side.scale(2)), true, face);
        } else if (wtap > 0) {
            // W-tap: let go of forward for a moment so sprint (and its knockback) comes back on the next hit.
            wtap--;
            mob.setSprinting(false);
            move(mob.position().subtract(offset.normalize().scale(0.3)), false, face);
        } else if (cooldown <= 2) {
            approach(target, offset, distance, face);
        } else {
            space(target, offset, distance, cooldown, face);
        }
        return cooldown;
    }

    /** Ready to strike: close in, around the target first if a push could send it into a hazard. */
    private void approach(LivingEntity target, Vec3 offset, double distance, float face) {
        Vec3 goal = target.position();
        if (hazard != null && flankable(target)) {
            Vec3 stand = target.position().subtract(hazard.scale(FLANK));
            Vec3 heading = offset.normalize();
            // Knockback follows the attacker's facing, so line up behind the target first.
            if (heading.dot(hazard) < Math.cos(Math.toRadians(35))) goal = stand;
        }
        boolean trading = trading(target);
        if (trading && mob.onGround() && distance < REACH) {
            jumpForCrit();
            move(goal, false, face);
        } else {
            move(goal, !trading && distance > 1.5, face);
        }
    }

    /** Recharging: stay just outside the opponent's reach and circle, then time a crit jump when trading. */
    private void space(LivingEntity target, Vec3 offset, double distance, int cooldown, float face) {
        if (++strafeTicks > 20 + mob.getRandom().nextInt(25) || mob.horizontalCollision) {
            strafeTicks = 0;
            strafeSide = -strafeSide;
        }
        Vec3 heading = distance < 1.0E-4 ? Vec3.ZERO : offset.scale(1 / distance);
        Vec3 side = new Vec3(-heading.z, 0, heading.x).scale(strafeSide);
        if (trading(target) && cooldown <= 7 && distance < REACH + 0.3 && mob.onGround()) {
            // A jump takes about as long as the recharge, so the swing lands on the way down.
            jumpForCrit();
            move(target.position(), false, face);
            return;
        }
        Vec3 radial = distance < SPACING - 0.4 ? heading.scale(-1) : distance > SPACING + 0.8 ? heading : Vec3.ZERO;
        move(mob.position().add(radial.add(side.scale(0.8)).scale(2)), distance > SPACING + 2.5, face);
    }

    /** Bow in hand: keep a firing distance, backing off from a rusher and strafing to spoil its aim. */
    void kite(LivingEntity target) {
        projectiles.observe((ServerLevel)mob.level());
        if (++strafeTicks > 30 + mob.getRandom().nextInt(30) || mob.horizontalCollision) {
            strafeTicks = 0;
            strafeSide = -strafeSide;
        }
        float face = yawTo(target.position());
        Vec3 offset = flat(target.position().subtract(mob.position()));
        double distance = offset.length();
        Vec3 heading = distance < 1.0E-4 ? Vec3.ZERO : offset.scale(1 / distance);
        Vec3 side = new Vec3(-heading.z, 0, heading.x).scale(strafeSide);
        Vec3 radial = distance < 8 ? heading.scale(-1.5) : distance > 14 ? heading : Vec3.ZERO;
        if (dodgeProjectile((ServerLevel)mob.level())) side = dodge;
        mob.getLookControl().setLookAt(target, 60, 60);
        move(mob.position().add(radial.add(side).scale(2)), false, face);
    }

    private void move(Vec3 point, boolean sprint, float face) {
        mob.setSprinting(sprint);
        mob.setCrouching(false);
        mob.setEdgeGuard(mob.onGround()); // fight near a drop without walking off it
        steering.steer(mob.footing().constrain(point), 1.0, false, face);
    }

    private void jumpForCrit() {
        if (!mob.onGround() || !mob.footing().roomToJump()) return;
        mob.setSprinting(false); // a sprinting swing is never a crit
        mob.getJumpControl().jump();
        critJump = true;
    }

    /** The opponent is hitting back right now: crits win trades, sprint knockback wins spacing. */
    private boolean trading(LivingEntity target) {
        return mob.getLastHurtByMob() == target && mob.tickCount - mob.getLastHurtByMobTimestamp() < 30;
    }

    private void hit(ServerLevel level, LivingEntity target, boolean crit, boolean sprint) {
        var damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        var knockback = mob.getAttribute(Attributes.ATTACK_KNOCKBACK);
        if (crit && damage != null) damage.addTransientModifier(CRIT);
        if (sprint && knockback != null) knockback.addTransientModifier(SPRINT_HIT);
        mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        boolean hurt;
        try {
            hurt = mob.doHurtTarget(level, target);
        } finally {
            if (damage != null) damage.removeModifier(CRIT.id());
            if (knockback != null) knockback.removeModifier(SPRINT_HIT.id());
        }
        if (!hurt) return;
        if (crit) {
            level.getChunkSource().sendToTrackingPlayersAndSelf(target, new ClientboundAnimatePacket(target, ClientboundAnimatePacket.CRITICAL_HIT));
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, mob.getSoundSource(), 1, 1);
        } else if (sprint) {
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, mob.getSoundSource(), 1, 1);
            mob.setSprinting(false);
        }
    }

    static int cooldown(ZombiePlayer mob) {
        return Math.max(5, (int)Math.ceil(20 / Math.max(0.1, mob.getAttributeValue(Attributes.ATTACK_SPEED))));
    }

    private boolean dodgeProjectile(ServerLevel level) {
        Vec3 direction = projectiles.dodge(level, strafeSide);
        if (direction == null) return false;
        if (mob.footing().connected(mob.position().add(direction.scale(1.5)))) dodge = direction;
        else if (mob.footing().connected(mob.position().subtract(direction.scale(1.5)))) dodge = direction.scale(-1);
        else return false;
        return true;
    }

    @Nullable Vec3 projectileEscape(ServerLevel level) {
        return dodgeProjectile(level) ? mob.position().add(dodge.scale(1.5)) : null;
    }

    /** An opponent drawing a bow or holding a loaded crossbow: approach in a zigzag. */
    static boolean aiming(LivingEntity target) {
        ItemStack item = target.getUseItem();
        return target.isUsingItem() && item.getItem() instanceof BowItem
                || target.getMainHandItem().getItem() instanceof CrossbowItem && CrossbowItem.isCharged(target.getMainHandItem());
    }

    /** Direction from the target towards the nearest lava or dangerous drop within a few blocks, if the way there is open. */
    private @Nullable Vec3 hazardDirection(ServerLevel level, LivingEntity target) {
        if (!target.onGround()) return null;
        BlockPos feet = target.blockPosition();
        Vec3 best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int d = 1; d <= HAZARD_SCAN && d < bestDistance; d++) {
                    BlockPos cell = feet.offset(dx * d, 0, dz * d);
                    if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                            || !level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()) break;
                    if (dangerous(level, cell)) {
                        best = new Vec3(dx, 0, dz).normalize();
                        bestDistance = d;
                        break;
                    }
                }
            }
        }
        return best;
    }

    private static boolean dangerous(ServerLevel level, BlockPos cell) {
        if (level.getFluidState(cell).is(FluidTags.LAVA) || level.getFluidState(cell.below()).is(FluidTags.LAVA)) return true;
        for (int k = 1; k <= CLIFF; k++) {
            BlockPos below = cell.below(k);
            if (below.getY() < level.getMinY()) return true;
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() || level.getFluidState(below).is(FluidTags.WATER)) return false;
        }
        return true;
    }

    /** The flanking spot has footing and is not itself dangerous. */
    private boolean flankable(LivingEntity target) {
        BlockPos stand = BlockPos.containing(target.position().subtract(hazard.scale(FLANK)));
        ServerLevel level = (ServerLevel)mob.level();
        return !level.getBlockState(stand.below()).getCollisionShape(level, stand.below()).isEmpty() && !dangerous(level, stand);
    }

    private float yawTo(Vec3 point) {
        return (float)(Mth.atan2(point.z - mob.getZ(), point.x - mob.getX()) * Mth.RAD_TO_DEG) - 90;
    }

    private static Vec3 flat(Vec3 vector) {
        return new Vec3(vector.x, 0, vector.z);
    }
}
