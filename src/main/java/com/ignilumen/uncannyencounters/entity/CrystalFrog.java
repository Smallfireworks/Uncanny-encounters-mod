package com.ignilumen.uncannyencounters.entity;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogAi;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogDefense;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogMoveControl;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogOwnerSupport;
import com.ignilumen.uncannyencounters.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Neutral amphibian. Defense is independent of AI and applies to its owner too. */
public final class CrystalFrog extends TamableAnimal {
    public static final float SIZE_SCALE = 1.15F;
    public static final ResourceKey<DamageType> REFLECTION_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, UncannyEncounters.id("crystal_reflection"));
    public static final TagKey<Block> HABITAT = TagKey.create(Registries.BLOCK, UncannyEncounters.id("crystal_frog_habitat"));
    private static final byte HOP_EVENT = 1, REFLECT_EVENT = 61;
    private int responseTicks, hopAnimationTicks, reflectionTicks;
    private @Nullable Vec3 threatPosition;
    private @Nullable LivingEntity provoker;
    private final CrystalFrogOwnerSupport ownerSupport = new CrystalFrogOwnerSupport(this);

    public CrystalFrog(EntityType<? extends CrystalFrog> type, Level level) {
        super(type, level);
        moveControl = new CrystalFrogMoveControl(this);
        setPathfindingMalus(PathType.WATER, 0);
        setPathfindingMalus(PathType.TRAPDOOR, -1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.38).add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.ATTACK_KNOCKBACK, 2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.STEP_HEIGHT, 1);
    }

    @Override protected void registerGoals() { CrystalFrogAi.register(this, goalSelector); }
    @Override protected PathNavigation createNavigation(Level level) { return new AmphibiousPathNavigation(this, level); }
    @Override protected void customServerAiStep(ServerLevel level) {
        ownerSupport.tick();
        super.customServerAiStep(level);
    }

    public boolean assistOwner(@Nullable LivingEntity target) {
        LivingEntity owner = getOwner();
        if (!isTame() || owner == null || isOrderedToSit() || getHealth() < getMaxHealth() * 0.5F
                || isFrightened() || responseTicks > 0 && provoker != null || target == null
                || !canRetaliateAgainst(target) || !canAttack(target) || !wantsToAttack(target, owner)
                || distanceToSqr(target) > 24 * 24) return false;
        responseTicks = 200;
        threatPosition = target.position();
        setTarget(target);
        return getTarget() == target;
    }

    @Override public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target == this || target == owner || isAlliedTo(target)
                || target instanceof TamableAnimal pet && pet.isOwnedBy(owner)) return false;
        return !(target instanceof Player other && owner instanceof Player player && !player.canHarmPlayer(other));
    }

    public static boolean canSpawn(EntityType<CrystalFrog> type, ServerLevelAccessor level,
                                   EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (pos.getY() >= 48 || level.canSeeSky(pos) || !level.getFluidState(pos).isEmpty()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                || !level.getLevel().noCollision(type.getSpawnAABB(Vec3.atBottomCenterOf(pos)))) return false;
        // Bounded local search only during spawn attempts, never during entity ticks.
        for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-4, -4, -4), pos.offset(4, 4, 4))) {
            if (level.getLevel().hasChunkAt(nearby) && level.getBlockState(nearby).is(HABITAT)) return true;
        }
        return false;
    }

    @Override public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos.below()).is(HABITAT) ? 5 : 0;
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isDeadOrDying() || isInvulnerableTo(level, source)) return false;
        if (amount > 0 && Float.isFinite(amount) && source.is(DamageTypeTags.IS_PLAYER_ATTACK)
                && source.getEntity() instanceof Player player && source.getDirectEntity() == player) {
            Vec3 motion = player.getKnownSpeed();
            if (CrystalFrogDefense.isFastAttack(motion.x, motion.y, motion.z)) {
                reactToDamage(source);
                // Vanilla thorns scales with difficulty for a mob causer. Our type has
                // scaling=never, so armor receives exactly the incoming raw damage.
                DamageSource reflected = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                        .getOrThrow(REFLECTION_DAMAGE), this);
                player.hurtServer(level, reflected, amount);
                if (reflectionTicks == 0) {
                    level.broadcastEntityEvent(this, REFLECT_EVENT);
                    playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.25F);
                    level.sendParticles(ParticleTypes.WITCH, getX(), getY(0.6), getZ(), 12, 0.35, 0.2, 0.35, 0.02, 0.02, 0.02);
                    reflectionTicks = 6;
                }
                // No successful-hit enchantment hooks. Spear knockback is independent
                // of damage in 26.3 and keeps its vanilla behavior.
                return false;
            }
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) reactToDamage(source);
        return hurt;
    }

    private void reactToDamage(DamageSource source) {
        responseTicks = 200;
        threatPosition = source.getSourcePosition();
        provoker = source.getEntity() instanceof LivingEntity attacker && canRetaliateAgainst(attacker) ? attacker : null;
        setTarget(provoker);
        setOrderedToSit(false);
        setInSittingPose(false);
    }
    private boolean canRetaliateAgainst(LivingEntity attacker) {
        return attacker != this && attacker.isAlive() && attacker.level() == level() && !attacker.isSpectator()
                && !(attacker instanceof Player player && player.isCreative());
    }
    @Override public boolean canAttack(LivingEntity target) {
        // TamableAnimal normally rejects its owner in Mob.setTarget/getTarget.
        // Only an actual provocation may bypass that guard, with normal difficulty rules.
        if (target == provoker && isOwnedBy(target)) {
            return target.canBeSeenAsEnemy() && !(target instanceof Player && level().getDifficulty() == Difficulty.PEACEFUL);
        }
        return super.canAttack(target);
    }
    public boolean wantsRetaliation() { return responseTicks > 0 && getTarget() != null && getHealth() >= getMaxHealth() * 0.5F; }
    public boolean isFrightened() { return responseTicks > 0 && (getHealth() < getMaxHealth() * 0.5F || getTarget() == null); }
    public @Nullable Vec3 threatPosition() { return threatPosition; }

    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isTame() && stack.is(ModItems.ACTIVATED_AMETHYST)) {
            if (!level().isClientSide()) {
                stack.consume(1, player);
                if (random.nextInt(3) == 0) {
                    tame(player);
                    setPersistenceRequired();
                    responseTicks = 0;
                    provoker = null;
                    setTarget(null);
                    threatPosition = null;
                    sit(true);
                    level().broadcastEntityEvent(this, (byte)7);
                } else level().broadcastEntityEvent(this, (byte)6);
            }
            return InteractionResult.SUCCESS;
        }
        if (isTame() && isOwnedBy(player) && stack.isEmpty()) {
            if (!level().isClientSide()) sit(!isOrderedToSit());
            return InteractionResult.SUCCESS.withoutItem();
        }
        return super.mobInteract(player, hand);
    }

    private void sit(boolean sitting) {
        if (sitting) {
            responseTicks = 0;
            provoker = null;
            threatPosition = null;
            setTarget(null);
        }
        setOrderedToSit(sitting);
        setInSittingPose(sitting && onGround() && !isInWater());
        navigation.stop();
        moveControl.setWait();
        setJumping(false);
    }

    @Override public void tick() {
        if (hopAnimationTicks > 0) hopAnimationTicks--;
        if (reflectionTicks > 0) reflectionTicks--;
        if (!level().isClientSide()) {
            if (responseTicks > 0 && --responseTicks == 0) {
                threatPosition = null;
                provoker = null;
                setTarget(null);
            }
            LivingEntity target = getTargetUnchecked();
            if (target != null && (getTarget() == null || !canRetaliateAgainst(target) || distanceToSqr(target) > 24 * 24)) {
                setTarget(null);
                if (getHealth() >= getMaxHealth() * 0.5F) {
                    responseTicks = 0;
                    provoker = null;
                    threatPosition = null;
                }
            }
        }
        super.tick();
    }

    /** Real jumps, using vanilla collision and gravity. */
    public void hop(double x, double z, boolean uphill) {
        float power = getJumpPower(uphill ? 0.5F / 0.42F : 0.36F / 0.42F);
        setDeltaMovement(x, Math.max(power, getDeltaMovement().y), z);
        needsSync = true;
        hopAnimationTicks = 12;
        level().broadcastEntityEvent(this, HOP_EVENT);
        playSound(SoundEvents.FROG_LONG_JUMP, 0.25F, 1.15F + random.nextFloat() * 0.2F);
    }

    @Override public void handleEntityEvent(byte event) {
        if (event == HOP_EVENT) hopAnimationTicks = 12;
        else if (event == REFLECT_EVENT) reflectionTicks = 6;
        else super.handleEntityEvent(event);
    }
    public float hopProgress(float partialTick) {
        return hopAnimationTicks == 0 ? 0 : Math.clamp((12 - hopAnimationTicks + partialTick) / 12F, 0F, 1F);
    }
    public float reflectionProgress(float partialTick) { return Math.max(0, reflectionTicks - partialTick) / 6F; }

    @Override protected void travelInWater(Vec3 input, double gravity, boolean falling, double oldY) {
        moveRelative(getSpeed(), input);
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.9));
    }
    @Override protected int calculateFallDamage(double distance, float multiplier) {
        return Math.max(0, super.calculateFallDamage(distance, multiplier) - 5);
    }
    @Override protected int getBaseExperienceReward(ServerLevel level) { return 5; }
    @Override protected boolean canBeABaby() { return false; }
    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) { return null; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.FROG_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.FROG_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.FROG_DEATH; }
}
