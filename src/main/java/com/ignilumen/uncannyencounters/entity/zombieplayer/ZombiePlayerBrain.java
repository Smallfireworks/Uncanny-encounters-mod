package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Combat, weapon scavenging and home roaming share one terrain pilot. */
public final class ZombiePlayerBrain {
    /** Headings tried for a retreat, in radians away from the threat: straight back first, then fanning out. */
    private static final double[] RETREAT_TURNS = {0, 0.5, -0.5, 1.0, -1.0, 1.5, -1.5};
    private static final int[] RETREAT_RISES = {0, 1, -1, 2, -2};
    private static final int RETREAT_DISTANCE = 6, PATIENCE = 30 * 20, IGNORE_TICKS = 30 * 20, SEARCH_TICKS = 15 * 20,
            BLOCKED_TICKS = 10, DUEL_PAUSE = 60;
    private static final double REACH = 4.5, BREACH_RANGE = 5.5;
    private final ZombiePlayer mob;
    private final Pilot pilot;
    private final Duelist duelist;
    /** Targets it gave up on, until the game time they may be picked again unless seen or attacking. */
    private final Object2LongOpenHashMap<UUID> ignored = new Object2LongOpenHashMap<>();
    private int attackCooldown, idleTicks, roamDelay, targetDelay, searchTicks, blockedTicks, duelPause;
    private @Nullable BlockPos roam, retreat, lastSeen, searching;
    private @Nullable UUID chasing;
    private double closest;
    private long progressAt;
    private Vec3 targetPos = Vec3.ZERO, targetVelocity = Vec3.ZERO;
    private boolean returning, dueling, walled, sheltered;

    public ZombiePlayerBrain(ZombiePlayer mob) {
        this.mob = mob;
        pilot = new Pilot(mob);
        duelist = new Duelist(mob);
        roamDelay = -60;
    }

    public void stop(ServerLevel level) {
        hold(level);
        retreat = null;
    }

    private void hold(ServerLevel level) {
        endDuel();
        pilot.stop(level);
        mob.setSprinting(false);
        mob.setEdgeGuard(mob.onGround());
        mob.steering().steer(mob.position(), 1, true);
    }

    /** Starts a meal: drop whatever it was doing; an evolved zombie may wall itself off first. */
    public void beginMeal(ServerLevel level) {
        stop(level);
        walled = sheltered = false;
    }

    /** While eating: shelter behind a quick wall (evolved), else walk away from whoever is fighting it, never mining. */
    public void evade(ServerLevel level) {
        mob.setAggressive(false);
        LivingEntity threat = mob.getTarget();
        LivingEntity attacker = mob.getLastHurtByMob();
        if (!validTarget(threat)) threat = validTarget(attacker) ? attacker : null;
        if (threat == null) {
            stop(level);
            return;
        }
        if (mob.evolved() && !walled && mob.onGround()) {
            walled = true;
            sheltered = wallUp(level, threat);
        }
        if (sheltered) {
            hold(level);
            mob.getLookControl().setLookAt(threat, 45, 45);
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

    /** A three-wide, two-high wall of zombie blocks between it and the threat. */
    private boolean wallUp(ServerLevel level, LivingEntity threat) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) return false;
        Vec3 offset = threat.position().subtract(mob.position());
        Direction toward = Direction.getApproximateNearest(offset.x, 0, offset.z);
        BlockPos front = mob.blockPosition().relative(toward);
        TemporaryEdits edits = TemporaryEdits.get(level);
        boolean built = false;
        for (BlockPos base : new BlockPos[]{front, front.relative(toward.getClockWise()), front.relative(toward.getCounterClockWise())}) {
            for (BlockPos pos : new BlockPos[]{base, base.above()}) {
                if (buildable(level, pos)) built |= edits.placeBlock(level, pos, mob);
            }
        }
        if (built) mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        return built;
    }

    private static boolean buildable(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).canBeReplaced() || !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) return false;
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (!level.getBlockState(next).getCollisionShape(level, next).isEmpty()) return true;
        }
        return false;
    }

    private @Nullable BlockPos retreatSpot(ServerLevel level, LivingEntity threat) {
        double dx = mob.getX() - threat.getX(), dz = mob.getZ() - threat.getZ();
        double away = dx * dx + dz * dz < 1.0E-4 ? mob.getRandom().nextDouble() * Math.PI * 2 : Math.atan2(dz, dx);
        Terrain terrain = new Terrain(level, false, mob.evolved());
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
        long now = level.getGameTime();
        if (attackCooldown > 0) attackCooldown--;
        if (duelPause > 0) duelPause--;
        LivingEntity target = mob.getTarget();
        if (!validTarget(target)) { mob.setTarget(null); target = null; }
        if (--targetDelay <= 0) {
            targetDelay = 10;
            ignored.object2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
            LivingEntity attacker = mob.getLastHurtByMob();
            if (validTarget(attacker) && mob.tickCount - mob.getLastHurtByMobTimestamp() < 200) target = attacker;
            else if (target == null) target = level.players().stream().filter(this::validTarget)
                    .filter(player -> mob.distanceToSqr(player) <= 40 * 40)
                    .filter(player -> !ignored.containsKey(player.getUUID()) || mob.getSensing().hasLineOfSight(player))
                    .min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            mob.setTarget(target);
        }
        if (target != null) {
            boolean visible = mob.getSensing().hasLineOfSight(target);
            track(target, visible, now);
            if (!visible && !pilot.handlingBlock() && now - progressAt > PATIENCE) {
                giveUp(target, now);
                target = null;
            } else {
                idleTicks = 0;
                returning = false;
                roam = null;
                searching = null;
                if (!scavenge(level, target)) combat(level, target, visible);
            }
        }
        if (target == null) {
            endDuel();
            mob.setAggressive(false);
            if (mob.isUsingItem()) mob.stopUsingItem();
            idleTicks++;
            if (searching != null) search(level);
            else if (!scavenge(level, null)) roam(level);
        }
        pilot.tick(level);
        target = mob.getTarget();
        if (target != null && !pilot.handlingBlock() && !dueling) mob.getLookControl().setLookAt(target, 45, 45);
    }

    /** Progress means seeing the target, working on a block, or getting closer; its velocity feeds interception. */
    private void track(LivingEntity target, boolean visible, long now) {
        if (!target.getUUID().equals(chasing)) {
            chasing = target.getUUID();
            closest = Double.MAX_VALUE;
            progressAt = now;
            targetPos = target.position();
            targetVelocity = Vec3.ZERO;
        }
        targetVelocity = targetVelocity.scale(0.6).add(target.position().subtract(targetPos).scale(0.4));
        targetPos = target.position();
        double distance = mob.distanceTo(target);
        if (visible) lastSeen = target.blockPosition();
        if (visible || pilot.handlingBlock() || distance < closest - 0.5) {
            progressAt = now;
            closest = Math.min(closest, distance);
        }
    }

    /**
     * Thirty seconds without any progress: leave this target alone for a while unless it shows up or
     * attacks. An evolved zombie first checks where it last saw the target, then heads home.
     */
    private void giveUp(LivingEntity target, long now) {
        ignored.put(target.getUUID(), now + IGNORE_TICKS);
        mob.setTarget(null);
        chasing = null;
        targetDelay = 20;
        endDuel();
        if (mob.evolved() && lastSeen != null && !mob.blockPosition().closerThan(lastSeen, 3)) {
            searching = lastSeen;
            searchTicks = SEARCH_TICKS;
        } else {
            returning = true;
        }
        lastSeen = null;
    }

    private void search(ServerLevel level) {
        if (--searchTicks <= 0 || mob.blockPosition().closerThan(searching, 2)) {
            searching = null;
            returning = true;
            roam(level);
            return;
        }
        pilot.follow(new RoutePlanner.Goal(searching, 1, 1), true, true);
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
        endDuel();
        pilot.follow(new RoutePlanner.Goal(weapon.blockPosition(), 0, 0), true, false);
        return true;
    }

    private void combat(ServerLevel level, LivingEntity target, boolean visible) {
        mob.setAggressive(true);
        ItemStack weapon = mob.getMainHandItem();
        boolean bow = weapon.getItem() instanceof BowItem, crossbow = weapon.getItem() instanceof CrossbowItem;
        double distance = mob.distanceToSqr(target);
        if ((bow || crossbow) && visible && distance <= 16 * 16 && distance >= 9) {
            if (mob.evolved()) {
                if (!dueling) pilot.stop(level);
                dueling = true;
                duelist.kite(target);
            } else {
                stop(level);
            }
            shoot(level, target, weapon, crossbow);
            return;
        }
        if (mob.isUsingItem()) mob.stopUsingItem();
        if (mob.evolved() && duelPause == 0 && engages(target, visible, distance)) {
            if (!dueling) pilot.stop(level);
            dueling = true;
            attackCooldown = duelist.melee(level, target, attackCooldown);
            // Closing in but walled off, or held at a drop by the edge guard: let the pilot find a way round for a while.
            boolean stuck = mob.horizontalCollision || mob.onGround() && mob.getDeltaMovement().horizontalDistanceSqr() < 0.0025;
            blockedTicks = stuck && attackCooldown <= 2 && !Duelist.inReach(mob, target) ? blockedTicks + 1 : 0;
            if (blockedTicks > BLOCKED_TICKS) {
                duelPause = DUEL_PAUSE;
                endDuel();
            }
            return;
        }
        endDuel();
        boolean inReach = mob.evolved() ? Duelist.inReach(mob, target) : mob.isWithinMeleeAttackRange(target);
        if (visible && inReach) {
            stop(level);
            if (attackCooldown == 0) {
                mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
                mob.doHurtTarget(level, target);
                attackCooldown = Duelist.cooldown(mob);
            }
            return;
        }
        BlockPos wall = visible ? null : breachBlock(level, target);
        if (wall == null && mob.evolved()) wall = undermine(level, target);
        if (wall != null && pilot.breach(level, wall)) return;
        // Aim for contact when sight is blocked so a wall is actually cleared.
        pilot.follow(chaseGoal(target, visible), true, true);
    }

    /** Evolved zombies duel in the open, and rush a distant archer head-on, dodging, rather than along a route. */
    private boolean engages(LivingEntity target, boolean visible, double distanceSqr) {
        return duelist.engages(target, visible) || visible && Duelist.aiming(target) && distanceSqr <= 24 * 24
                && Math.abs(target.getY() - mob.getY()) < 2.5 && !mob.isInWater();
    }

    private void endDuel() {
        if (!dueling) return;
        dueling = false;
        blockedTicks = 0;
        duelist.reset();
        mob.setSprinting(false);
        mob.setCrouching(false);
    }

    private void shoot(ServerLevel level, LivingEntity target, ItemStack weapon, boolean crossbow) {
        if (attackCooldown > 0) return;
        if (crossbow && CrossbowItem.isCharged(weapon)) {
            mob.stopUsingItem();
            ((CrossbowItem)weapon.getItem()).performShooting(level, mob, InteractionHand.MAIN_HAND, weapon, 3.15F,
                    14 - level.getDifficulty().getId() * 4, target);
            attackCooldown = 30;
        } else if (!mob.isUsingItem()) {
            mob.startUsingItem(InteractionHand.MAIN_HAND);
        } else if (!crossbow && mob.getTicksUsingItem() >= 20) {
            shootBow(level, target, weapon);
            mob.stopUsingItem();
            attackCooldown = 20;
        }
    }

    /**
     * Evolved zombies head for where a running target will be, not where it is: its recent velocity
     * times roughly the time needed to close half the gap.
     */
    private RoutePlanner.Goal chaseGoal(LivingEntity target, boolean visible) {
        double distance = mob.distanceTo(target);
        if (!mob.evolved() || !visible || distance < 6 || targetVelocity.horizontalDistanceSqr() < 0.01)
            return new RoutePlanner.Goal(target.blockPosition(), 0, 0);
        double lead = Math.min(20, distance / 0.28 * 0.5);
        BlockPos ahead = BlockPos.containing(target.position().add(targetVelocity.x * lead, 0, targetVelocity.z * lead));
        ServerLevel level = (ServerLevel)mob.level();
        boolean footing = !level.getBlockState(ahead.below()).getCollisionShape(level, ahead.below()).isEmpty();
        return footing ? new RoutePlanner.Goal(ahead, 1, 1) : new RoutePlanner.Goal(target.blockPosition(), 0, 0);
    }

    /**
     * The target is out of sight nearby and no full route exists (a sealed box, for example): mine the
     * quickest block on the line to its eyes or body, and strike through the hole once it opens.
     */
    private @Nullable BlockPos breachBlock(ServerLevel level, LivingEntity target) {
        if (!pilot.unreachable() || !level.getGameRules().get(GameRules.MOB_GRIEFING)) return null;
        Vec3 eye = mob.getEyePosition();
        if (eye.distanceToSqr(target.getEyePosition()) > BREACH_RANGE * BREACH_RANGE) return null;
        BlockPos best = null;
        float fastest = 0;
        for (Vec3 aim : new Vec3[]{target.getEyePosition(), target.getBoundingBox().getCenter()}) {
            BlockHitResult hit = level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            BlockPos pos = hit.getBlockPos();
            float speed = mineable(level, pos);
            if (speed > fastest) {
                fastest = speed;
                best = pos;
            }
        }
        return best;
    }

    /** Evolved: a target towering up on a pillar or ledge within reach loses the block under its feet. */
    private @Nullable BlockPos undermine(ServerLevel level, LivingEntity target) {
        if (!target.onGround() || target.getY() - mob.getY() < 2 || !level.getGameRules().get(GameRules.MOB_GRIEFING)) return null;
        BlockPos support = target.getOnPos();
        if (mineable(level, support) <= 0) return null;
        int neighbours = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos next = support.relative(direction);
            if (!level.getBlockState(next).getCollisionShape(level, next).isEmpty()) neighbours++;
        }
        return neighbours <= 1 ? support : null;
    }

    /** Break speed of a solid block within reach that is safe to open; zero if it must not be mined. */
    private float mineable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getCollisionShape(level, pos).isEmpty() || mob.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH
                || Terrain.nextToLava((x, y, z) -> level.getBlockState(new BlockPos(x, y, z)), pos.getX(), pos.getY(), pos.getZ())) return 0;
        return Terrain.progress(level, state, pos, mob.evolved());
    }

    private void shootBow(ServerLevel level, LivingEntity target, ItemStack bow) {
        ItemStack ammo = mob.getProjectile(bow);
        var arrow = ProjectileUtil.getMobArrow(mob, ammo, 1, bow);
        double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
        double dy = target.getY(1.0 / 3.0) - arrow.getY() + Math.sqrt(dx * dx + dz * dz) * 0.2;
        Projectile.spawnProjectileUsingShoot(arrow, level, ammo, dx, dy, dz, 1.6F, 14 - level.getDifficulty().getId() * 4);
        mob.playSound(SoundEvents.SKELETON_SHOOT, 1, 1);
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
        Terrain terrain = new Terrain(level, false, mob.evolved());
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
