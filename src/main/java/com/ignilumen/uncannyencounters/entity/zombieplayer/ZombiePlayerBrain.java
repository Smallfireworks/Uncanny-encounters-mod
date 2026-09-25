package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Combat, weapon scavenging and home roaming share one terrain pilot. */
public final class ZombiePlayerBrain {
    /** Headings tried for a retreat, in radians away from the threat: straight back first, then fanning out. */
    private static final double[] RETREAT_TURNS = {0, 0.5, -0.5, 1.0, -1.0, 1.5, -1.5};
    private static final int[] RETREAT_RISES = {0, 1, -1, 2, -2};
    private static final int RETREAT_DISTANCE = 6;
    private final ZombiePlayer mob;
    private final Pilot pilot;
    private int attackCooldown, idleTicks, roamDelay, targetDelay, unseenTicks;
    private @Nullable BlockPos roam, retreat;
    private boolean returning;

    public ZombiePlayerBrain(ZombiePlayer mob) {
        this.mob = mob;
        pilot = new Pilot(mob);
        roamDelay = -60;
    }

    public void stop(ServerLevel level) {
        pilot.stop(level);
        retreat = null;
        mob.setSprinting(false);
        mob.setEdgeGuard(mob.onGround());
        mob.steering().steer(mob.position(), 1, true);
    }

    /** While eating: walk (never mine or build) away from whoever is fighting it. */
    public void evade(ServerLevel level) {
        mob.setAggressive(false);
        LivingEntity threat = mob.getTarget();
        LivingEntity attacker = mob.getLastHurtByMob();
        if (!validTarget(threat)) threat = validTarget(attacker) ? attacker : null;
        if (threat == null) {
            stop(level);
            return;
        }
        if (retreat == null || mob.blockPosition().distSqr(retreat) <= 2) retreat = retreatSpot(level, threat);
        if (retreat == null) {
            stop(level);
            mob.getLookControl().setLookAt(threat, 45, 45);
            return;
        }
        pilot.follow(new RoutePlanner.Goal(retreat, 1, 1), false, false);
        pilot.tick(level);
    }

    private @Nullable BlockPos retreatSpot(ServerLevel level, LivingEntity threat) {
        double dx = mob.getX() - threat.getX(), dz = mob.getZ() - threat.getZ();
        double away = dx * dx + dz * dz < 1.0E-4 ? mob.getRandom().nextDouble() * Math.PI * 2 : Math.atan2(dz, dx);
        Terrain terrain = new Terrain(level, false);
        for (double turn : RETREAT_TURNS) {
            int x = (int)Math.round(Math.cos(away + turn) * RETREAT_DISTANCE), z = (int)Math.round(Math.sin(away + turn) * RETREAT_DISTANCE);
            for (int dy : RETREAT_RISES) {
                BlockPos pos = mob.blockPosition().offset(x, dy, z);
                if (standable(terrain, pos)) return pos;
            }
        }
        return null;
    }

    /** Solid floor, no hazard and room for the body, without editing anything. */
    private static boolean standable(Terrain terrain, BlockPos pos) {
        double floor = terrain.floor(pos.getX(), pos.getY(), pos.getZ());
        if (Double.isNaN(floor) || terrain.hazard(pos.getX(), pos.getY(), pos.getZ())) return false;
        return terrain.clearColumn(pos.getX(), pos.getZ(), floor + 0.01, floor + Terrain.BODY, new Terrain.Clearing());
    }

    public void tick(ServerLevel level) {
        if (attackCooldown > 0) attackCooldown--;
        LivingEntity target = mob.getTarget();
        if (!validTarget(target)) { mob.setTarget(null); target = null; }
        if (--targetDelay <= 0) {
            targetDelay = 10;
            LivingEntity attacker = mob.getLastHurtByMob();
            if (validTarget(attacker) && mob.tickCount - mob.getLastHurtByMobTimestamp() < 200) target = attacker;
            else if (target == null) target = level.players().stream().filter(this::validTarget)
                    .filter(player -> mob.distanceToSqr(player) <= 40 * 40)
                    .min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            mob.setTarget(target);
        }
        if (target != null) {
            idleTicks = 0;
            returning = false;
            roam = null;
            boolean visible = mob.getSensing().hasLineOfSight(target);
            if (visible) unseenTicks = 0;
            else if (++unseenTicks > 200 && pilot.unreachable()) {
                mob.setTarget(null);
                targetDelay = 100;
                returnHome(level);
                return;
            }
            if (!scavenge(level, target)) combat(level, target, visible);
        } else {
            mob.setAggressive(false);
            if (mob.isUsingItem()) mob.stopUsingItem();
            unseenTicks = 0;
            idleTicks++;
            if (!scavenge(level, null)) roam(level);
        }
        pilot.tick(level);
        target = mob.getTarget();
        if (target != null && !pilot.handlingBlock()) mob.getLookControl().setLookAt(target, 45, 45);
    }

    private boolean validTarget(@Nullable LivingEntity target) {
        return target != null && target != mob && target.isAlive() && mob.canAttack(target)
                && (!(target instanceof Player player) || !player.isCreative() && !player.isSpectator())
                && target.position().distanceToSqr(Vec3.atCenterOf(mob.home())) <= 64 * 64
                && mob.distanceToSqr(target) <= 64 * 64;
    }

    private boolean scavenge(ServerLevel level, @Nullable LivingEntity target) {
        if (!mob.canPickUpLoot() || !level.getGameRules().get(GameRules.MOB_GRIEFING) || !mob.getMainHandItem().isEmpty()
                || target != null && mob.distanceToSqr(target) < 9) return false;
        ItemEntity weapon = level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(8),
                        item -> !item.hasPickUpDelay() && ZombiePlayer.isWeapon(item.getItem()))
                .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
        if (weapon == null) return false;
        pilot.follow(new RoutePlanner.Goal(weapon.blockPosition(), 0, 0), true, false);
        return true;
    }

    private void combat(ServerLevel level, LivingEntity target, boolean visible) {
        mob.setAggressive(true);
        ItemStack weapon = mob.getMainHandItem();
        boolean bow = weapon.getItem() instanceof BowItem, crossbow = weapon.getItem() instanceof CrossbowItem;
        double distance = mob.distanceToSqr(target);
        if ((bow || crossbow) && visible && distance <= 16 * 16 && distance >= 9) {
            stop(level);
            if (attackCooldown > 0) return;
            if (crossbow && CrossbowItem.isCharged(weapon)) {
                mob.stopUsingItem();
                ((CrossbowItem)weapon.getItem()).performShooting(level, mob, InteractionHand.MAIN_HAND, weapon, 3.15F,
                        14 - level.getDifficulty().getId() * 4, target);
                attackCooldown = 30;
            } else if (!mob.isUsingItem()) {
                mob.startUsingItem(InteractionHand.MAIN_HAND);
            } else if (bow && mob.getTicksUsingItem() >= 20) {
                shootBow(level, target, weapon);
                mob.stopUsingItem();
                attackCooldown = 20;
            }
            return;
        }
        if (mob.isUsingItem()) mob.stopUsingItem();
        if (visible && mob.isWithinMeleeAttackRange(target)) {
            stop(level);
            if (attackCooldown == 0) {
                mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
                mob.doHurtTarget(level, target);
                attackCooldown = Math.max(5, (int)Math.ceil(20 / Math.max(0.1, mob.getAttributeValue(Attributes.ATTACK_SPEED))));
            }
        } else {
            // Aim for contact when sight is blocked so a wall is actually cleared.
            pilot.follow(new RoutePlanner.Goal(target.blockPosition(), 0, 0), true, true);
        }
    }

    private void shootBow(ServerLevel level, LivingEntity target, ItemStack bow) {
        ItemStack ammo = mob.getProjectile(bow);
        var arrow = ProjectileUtil.getMobArrow(mob, ammo, 1, bow);
        double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
        double dy = target.getY(1.0 / 3.0) - arrow.getY() + Math.sqrt(dx * dx + dz * dz) * 0.2;
        Projectile.spawnProjectileUsingShoot(arrow, level, ammo, dx, dy, dz, 1.6F, 14 - level.getDifficulty().getId() * 4);
        mob.playSound(SoundEvents.SKELETON_SHOOT, 1, 1);
    }

    private void returnHome(ServerLevel level) {
        returning = true;
        pilot.follow(new RoutePlanner.Goal(mob.home(), 3, 3), true, false);
        pilot.tick(level);
    }

    private void roam(ServerLevel level) {
        double homeDistance = mob.position().distanceToSqr(Vec3.atBottomCenterOf(mob.home()));
        if ((idleTicks == 200 && homeDistance > 9) || (idleTicks >= 200 && homeDistance > 30 * 30)
                || returning && homeDistance > 9) {
            returning = true;
            pilot.follow(new RoutePlanner.Goal(mob.home(), 3, 3), true, false);
            return;
        }
        returning = false;
        if (roam != null && mob.blockPosition().distSqr(roam) > 2 && roamDelay-- > 0) {
            pilot.follow(new RoutePlanner.Goal(roam, 1, 1), false, false);
            return;
        }
        stop(level);
        roam = null;
        if (--roamDelay > -60) return;
        roamDelay = 160;
        Terrain terrain = new Terrain(level, false);
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos pos = mob.blockPosition().offset(mob.getRandom().nextInt(17) - 8, mob.getRandom().nextInt(7) - 3,
                    mob.getRandom().nextInt(17) - 8);
            if (pos.distSqr(mob.home()) > 30 * 30) continue;
            if (standable(terrain, pos)) {
                roam = pos;
                break;
            }
        }
    }
}
