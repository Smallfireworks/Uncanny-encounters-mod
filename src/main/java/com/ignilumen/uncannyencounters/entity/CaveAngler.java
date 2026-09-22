package com.ignilumen.uncannyencounters.entity;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.*;

/** A stationary ceiling ambusher. All capture state and movement are server-authoritative. */
public final class CaveAngler extends Monster {
    public static final int IDLE = 0, HOLDING = 1, HOISTING = 2, RECOVERING = 3;
    public static final int RECOVERY_TICKS = 15 * 20;
    public static final double REACH = 12;
    private static final int ANCHOR_SEARCH_RANGE = 32, ANCHOR_RETRY_TICKS = 100;
    private static final int CALL_WAIT = 60, MAX_HOLD = 240;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(CaveAngler.class, EntityDataSerializers.INT);
    private BlockPos anchor;
    private boolean everAnchored;
    private LivingEntity captive;
    private AnglerTongue tongue;
    private int phaseTicks, cooldown, unattendedTicks;
    private double floorY;
    private Vec3 previousCaptivePosition;

    public CaveAngler(EntityType<? extends CaveAngler> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override protected void registerGoals() {}

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, IDLE);
    }

    public int phase() { return entityData.get(PHASE); }
    public int recoveryTicks() { return cooldown; }
    public LivingEntity captive() { return captive; }
    public AnglerTongue tongue() { return tongue; }
    public Vec3 mouth() { return position().add(0, 0.18, 0.3125); }

    private void phase(int phase) {
        entityData.set(PHASE, phase);
        phaseTicks = 0;
    }

    public static boolean canSpawn(EntityType<CaveAngler> type, ServerLevelAccessor level,
                                   EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        // NaturalSpawner samples air positions at all heights; this is deliberately not ON_GROUND.
        if (level.getDifficulty() == Difficulty.PEACEFUL || pos.getY() >= 48 || level.canSeeSky(pos)
                || !Monster.isDarkEnoughToSpawn(level, pos, random)) return false;
        BlockPos ceiling = pos.above(2);
        if (!supports(level, ceiling)) return false;
        AABB space = new AABB(pos.getX() - 0.25, ceiling.getY() - 1.75, pos.getZ() - 0.25,
                pos.getX() + 1.25, ceiling.getY(), pos.getZ() + 1.25);
        return level.noCollision(space) && level.getBlockState(pos.below()).isAir()
                && level.getBlockState(pos.below(2)).isAir() && level.getFluidState(pos).isEmpty();
    }

    private static boolean supports(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.DOWN);
    }

    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        return true; // The registered ceiling predicate replaces Mob's ground-block check.
    }

    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity entity) {}

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                                   EntitySpawnReason reason, SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        // Both SpawnEggItem -> EntityType.create and /summon call this after
        // setting the spawn position. Attach here before egg entity-data loading
        // and the first gravity tick, then serialize/restore that anchor normally.
        if (anchor == null && !everAnchored) findAnchor(level.getLevel());
        return result;
    }

    private void findAnchor(ServerLevel level) {
        BlockPos start = BlockPos.containing(getX(), getY(), getZ());
        // Spawn eggs apply a ground-placement offset; when used on the underside
        // of a ceiling the initial body may overlap the clicked block. Include
        // the current block and resolve the anchor before the first physics tick.
        for (int i = 0; i <= ANCHOR_SEARCH_RANGE; i++) {
            BlockPos candidate = start.above(i);
            if (!level.hasChunkAt(candidate)) return;
            var shape = level.getBlockState(candidate).getCollisionShape(level, candidate);
            if (!shape.isEmpty() && candidate.getY() + shape.max(Direction.Axis.Y) > getY() + 0.001) {
                if (supports(level, candidate)) {
                    Vec3 point = new Vec3(candidate.getX() + 0.5, candidate.getY() - getBbHeight(), candidate.getZ() + 0.5);
                    if (level.noCollision(this, getBoundingBox().move(point.subtract(position())))) {
                        anchor = candidate;
                        everAnchored = true;
                        setPos(point);
                        setNoGravity(true);
                        setDeltaMovement(Vec3.ZERO);
                        resetFallDistance();
                    }
                }
                return;
            }
        }
    }

    @Override public void tick() {
        if (level() instanceof ServerLevel level && isAlive() && anchor == null && !everAnchored
                && tickCount <= ANCHOR_RETRY_TICKS && (tickCount <= 1 || tickCount % 10 == 0)) {
            findAnchor(level);
        }
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (!isAlive()) { release(false); return; }
        if (anchor == null || !supports(level, anchor)) {
            release(false);
            anchor = null;
            setNoGravity(false);
            return;
        }
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        setPos(anchor.getX() + 0.5, anchor.getY() - getBbHeight(), anchor.getZ() + 0.5);
        setYRot(0);
        yBodyRot = 0;
        yHeadRot = 0;
        if (cooldown > 0) {
            if (--cooldown == 0) phase(IDLE);
            return;
        }
        if (captive == null) {
            if (tickCount % 5 == 0) seekPrey(level);
        } else {
            holdPrey(level);
        }
    }

    private boolean canHold(LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target.isInvulnerable() || target.isPassenger()
                || target instanceof CaveAngler || isAlliedTo(target)) return false;
        return !(target instanceof Player player) || !player.isCreative();
    }

    public boolean considersPrey(LivingEntity target) {
        if (!canHold(target)) return false;
        if (target instanceof Player) return true;
        return (target == getLastHurtByMob() && tickCount - getLastHurtByMobTimestamp() < 600)
                || (target instanceof Mob mob && mob.getTarget() == this);
    }

    private void seekPrey(ServerLevel level) {
        Vec3 mouth = mouth();
        AABB area = new AABB(mouth.x - 0.85, mouth.y - REACH, mouth.z - 0.85,
                mouth.x + 0.85, mouth.y - 1, mouth.z + 0.85);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, this::considersPrey)
                .stream().sorted(Comparator.comparingDouble(this::distanceToSqr)).toList()) {
            // A victim cannot be owned by two anglers at once.
            if (!level.getEntitiesOfClass(CaveAngler.class, target.getBoundingBox().inflate(REACH + 2),
                    other -> other != this && other.captive == target).isEmpty()) continue;
            Vec3 end = target.getEyePosition();
            if (end.y > mouth.y - 0.5 || !clearLine(level, mouth, end)) continue;
            BlockHitResult floor = level.clip(new ClipContext(
                    new Vec3(mouth.x, target.getY() + 0.05, mouth.z),
                    new Vec3(mouth.x, target.getY() - REACH, mouth.z),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
            if (floor.getType() != HitResult.Type.BLOCK || floor.getDirection() != Direction.UP) continue;
            double ground = floor.getLocation().y;
            if (ground + 1 + target.getBbHeight() > mouth.y - 0.25) continue;
            Vec3 held = new Vec3(mouth.x, ground + 1, mouth.z);
            if (!level.noCollision(target, target.getBoundingBox().move(held.subtract(target.position())))) continue;
            captive = target;
            previousCaptivePosition = target.position();
            floorY = ground;
            unattendedTicks = 0;
            phase(HOLDING);
            tongue = new AnglerTongue(ModEntities.ANGLER_TONGUE, level);
            tongue.attach(this, target.getEyePosition().y);
            level.addFreshEntity(tongue);
            shriek(level);
            return;
        }
    }

    private boolean clearLine(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    private void holdPrey(ServerLevel level) {
        if (!canHold(captive) || captive.level() != level || captive.isRemoved()
                || (captive instanceof ServerPlayer player && player.hasDisconnected())
                || captive.distanceToSqr(this) > (REACH + 3) * (REACH + 3)
                || previousCaptivePosition == null || captive.position().distanceToSqr(previousCaptivePosition) > 9
                || tongue == null || tongue.isRemoved()) {
            release(false);
            return;
        }
        phaseTicks++;
        Vec3 mouth = mouth();
        if (!clearLine(level, mouth, captive.getEyePosition())) { release(false); return; }
        double destinationY = phase() == HOISTING ? mouth.y - captive.getBbHeight() - 0.3 : floorY + 1;
        Vec3 destination = new Vec3(mouth.x, destinationY, mouth.z);
        // Advance the desired suspension position independently of the victim's
        // incoming movement packets and gravity. Otherwise falling can cancel
        // every upward step and the hoist never gains any height for a player.
        double nextY = previousCaptivePosition.y
                + Math.clamp(destinationY - previousCaptivePosition.y, -0.18, 0.18);
        Vec3 next = new Vec3(destination.x, nextY, destination.z);
        // Keep the victim centered under the tongue even while sprinting; only the
        // vertical lift is gradual. A normalized pull can be outrun horizontally.
        Vec3 step = next.subtract(captive.position());
        // Test the entire swept volume, not just the destination, so thin obstacles cannot be crossed.
        if (!level.noCollision(captive, captive.getBoundingBox().expandTowards(step))) { release(false); return; }
        moveCaptive(next, Vec3.ZERO);
        captive.resetFallDistance();
        previousCaptivePosition = next;
        tongue.attach(this, captive.getEyePosition().y);

        if (phase() == HOLDING) {
            if (phaseTicks % 40 == 0) shriek(level);
            if (phaseTicks % 10 == 0) {
                if (callHelpers(level)) unattendedTicks = 0;
                else unattendedTicks += 10;
            }
            if (unattendedTicks >= CALL_WAIT || phaseTicks >= MAX_HOLD) phase(HOISTING);
        } else if (Math.abs(destinationY - next.y) < 0.05 || phaseTicks > 120) {
            release(true);
        }
    }

    private void moveCaptive(Vec3 position, Vec3 velocity) {
        captive.setDeltaMovement(velocity);
        if (captive instanceof ServerPlayer player) {
            // Relative.ROTATION preserves the player's look direction, but no
            // DELTA flags: the packet must also replace client-side velocity.
            player.connection.teleport(new PositionMoveRotation(position, velocity, 0, 0), Relative.ROTATION);
        } else {
            captive.teleportTo(position.x, position.y, position.z);
        }
    }

    private void shriek(ServerLevel level) {
        level.playSound(null, blockPosition(), SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 2.5F, 1.65F);
        callHelpers(level);
    }

    private boolean callHelpers(ServerLevel level) {
        if (captive == null) return false;
        boolean responding = false;
        for (Monster mob : level.getEntitiesOfClass(Monster.class, captive.getBoundingBox().inflate(20),
                mob -> mob != this && mob != captive && !(mob instanceof CaveAngler) && mob.isAlive())) {
            // Do not manufacture hostility between unrelated mobs. For a non-player victim,
            // only monsters already fighting it are eligible responders.
            if (!mob.canAttack(captive) || mob.isAlliedTo(captive) || mob.isNoAi()
                    || (!(captive instanceof Player) && mob.getTarget() != captive)) continue;
            if (!mob.hasLineOfSight(captive)) continue;
            // Require a path, or current melee reach, so sealed rooms do not count as help.
            var path = mob.getNavigation().createPath(BlockPos.containing(captive.getX(), floorY, captive.getZ()), 1);
            if (!(mob instanceof RangedAttackMob) && !mob.isWithinMeleeAttackRange(captive)
                    && (path == null || !path.canReach())) continue;
            mob.setTarget(captive);
            if (path != null) mob.getNavigation().moveTo(path, 1.1);
            responding = true;
        }
        return responding;
    }

    public void severTongue() {
        if (!(level() instanceof ServerLevel level) || captive == null) return;
        release(false, true);
        cooldown = RECOVERY_TICKS;
        phase(RECOVERING);
        level.playSound(null, blockPosition(), SoundEvents.SLIME_HURT, SoundSource.HOSTILE, 1.2F, 0.7F);
    }

    private void release(boolean drop) {
        release(drop, false);
    }

    private void release(boolean drop, boolean severed) {
        if (captive != null && captive.isAlive() && captive.level() == level()) {
            // Victims' gravity/AI flags are never changed, so disconnects and unloads cannot
            // leave a persistent no-gravity or no-AI entity behind.
            captive.resetFallDistance();
            if (drop) moveCaptive(captive.position(), new Vec3(0, -0.15, 0));
        }
        captive = null;
        previousCaptivePosition = null;
        if (tongue != null) {
            if (isAlive() && !isRemoved() && !tongue.isRemoved()) tongue.retract(severed);
            else tongue.discard();
            tongue = null;
        }
        if (phase() == HOLDING || phase() == HOISTING) {
            cooldown = 40;
            phase(RECOVERING);
        }
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (!isAlive()) release(false);
        return hurt;
    }

    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            if (tongue != null) tongue.discard();
            release(false);
        }
        super.remove(reason);
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (anchor != null) output.store("Anchor", BlockPos.CODEC, anchor);
        output.putBoolean("EverAnchored", everAnchored);
        output.putInt("TongueCooldown", Math.max(cooldown, captive != null ? 40 : 0));
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        anchor = input.read("Anchor", BlockPos.CODEC).orElse(null);
        everAnchored = input.getBooleanOr("EverAnchored", anchor != null);
        cooldown = Math.clamp(input.getIntOr("TongueCooldown", 0), 0, RECOVERY_TICKS);
        phase(cooldown > 0 ? RECOVERING : IDLE);
    }
}
