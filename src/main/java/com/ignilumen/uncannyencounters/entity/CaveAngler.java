package com.ignilumen.uncannyencounters.entity;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

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
        if (level.getDifficulty() == Difficulty.PEACEFUL || pos.getY() >= 48) return false;
        ServerLevel serverLevel = level.getLevel();
        BlockPos ceiling = findCeiling(serverLevel, type, Vec3.atBottomCenterOf(pos), null);
        if (ceiling == null) return false;
        Vec3 point = attachedPosition(type, ceiling);
        BlockPos feet = BlockPos.containing(point);
        // Validate the actual destination, not just the random position below it.
        if (point.y >= 48 || level.canSeeSky(feet) || !Monster.isDarkEnoughToSpawn(level, feet, random)
                || !level.getBlockState(feet.below()).isAir()
                || !level.getBlockState(feet.below(2)).isAir()) return false;
        if (reason == EntitySpawnReason.NATURAL) {
            Player nearest = serverLevel.getNearestPlayer(point.x, point.y, point.z, -1, false);
            if (nearest == null) return false;
            double distance = nearest.distanceToSqr(point);
            int maximum = type.getCategory().getDespawnDistance();
            if (distance <= 24 * 24 || distance > maximum * maximum) return false;
            var spawn = serverLevel.getRespawnData();
            if (spawn.dimension() == serverLevel.dimension() && spawn.pos().closerToCenterThan(point, 24)) return false;
        }
        return true;
    }

    private static boolean supports(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.DOWN);
    }

    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        // NaturalSpawner calls this before checking the entity's final obstruction.
        if (reason == EntitySpawnReason.NATURAL && level instanceof ServerLevel serverLevel) {
            findAnchor(serverLevel);
            return anchor != null;
        }
        return true;
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

    private static Vec3 attachedPosition(EntityType<?> type, BlockPos ceiling) {
        return new Vec3(ceiling.getX() + 0.5, ceiling.getY() - type.getHeight(), ceiling.getZ() + 0.5);
    }

    private static @Nullable BlockPos findCeiling(ServerLevel level, EntityType<?> type, Vec3 from, @Nullable Entity ignore) {
        BlockPos start = BlockPos.containing(from);
        // Shared by natural spawning, eggs and commands. Stop at the first solid
        // surface so the search cannot jump through a floor into another cave.
        for (int i = 0; i <= ANCHOR_SEARCH_RANGE; i++) {
            BlockPos candidate = start.above(i);
            if (candidate.getY() > level.getMaxY() || !level.hasChunkAt(candidate)) return null;
            var shape = level.getBlockState(candidate).getCollisionShape(level, candidate);
            if (!shape.isEmpty() && candidate.getY() + shape.max(Direction.Axis.Y) > from.y + 0.001) {
                if (supports(level, candidate)) {
                    AABB space = type.getSpawnAABB(attachedPosition(type, candidate));
                    if (level.noCollision(ignore, space) && !level.containsAnyLiquid(space)
                            && level.isUnobstructed(ignore, Shapes.create(space))) return candidate;
                }
                return null;
            }
        }
        return null;
    }

    private void findAnchor(ServerLevel level) {
        BlockPos ceiling = findCeiling(level, getType(), position(), this);
        if (ceiling == null) return;
        anchor = ceiling;
        everAnchored = true;
        setPos(attachedPosition(getType(), ceiling));
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
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

    @Override public void die(DamageSource source) {
        boolean transform = !isRemoved() && !dead;
        super.die(source);
        if (transform && dead && level() instanceof ServerLevel level) {
            release(false);
            var state = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(SpeleothemBlock.TIP_DIRECTION, Direction.DOWN)
                    .setValue(SpeleothemBlock.THICKNESS, SpeleothemThickness.TIP);
            // FallingBlockEntity.fall removes a world block. Decode the vanilla
            // entity state instead, since the stalactite originates from a mob.
            CompoundTag data = new CompoundTag();
            data.put("BlockState", NbtUtils.writeBlockState(state));
            FallingBlockEntity spike = new FallingBlockEntity(EntityTypes.FALLING_BLOCK, level);
            spike.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
            spike.snapTo(getX(), getY(), getZ(), 0, 0);
            spike.setStartPos(spike.blockPosition());
            spike.blocksBuilding = true;
            // Match 26.3's vanilla falling stalactite tip damage and landing sound.
            spike.setHurtsEntities(6, 40);
            spike.disableDrop();
            spike.dropItem = false;
            level.addFreshEntity(spike);
            remove(RemovalReason.KILLED);
        }
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
