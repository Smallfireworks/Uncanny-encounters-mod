package com.ignilumen.uncannyencounters.entity;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogAi;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogDefense;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogDuels;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogDuelCombat;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogCombatAnimation;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogStyle;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalents;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogHopAnimation;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogMoveControl;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogOwnerSupport;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalent;
import com.ignilumen.uncannyencounters.entity.crystalfrog.FrogGenetics;
import com.ignilumen.uncannyencounters.entity.crystalfrog.FrogKingSwallow;
import com.ignilumen.uncannyencounters.entity.frogkeeper.FrogKeeperEncounters;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.core.UUIDUtil;
import java.util.List;
import java.util.UUID;
import com.ignilumen.uncannyencounters.item.ModItems;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Neutral amphibian. Owners can trigger crystal reflection but never active retaliation. */
public final class CrystalFrog extends TamableAnimal {
    public static final float SIZE_SCALE = 1.15F;
    public static final int MIN_HEALTH = 16, MAX_HEALTH = 28, MIN_ATTACK = 2, MAX_ATTACK = 5;
    public static final float FEED_HEAL = 4;
    public static final ResourceKey<DamageType> REFLECTION_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, UncannyEncounters.id("crystal_reflection"));
    public static final ResourceKey<DamageType> SLIME_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, UncannyEncounters.id("crystal_slime"));
    public static final ResourceKey<DamageType> TALENT_BURST_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, UncannyEncounters.id("crystal_burst"));
    public static final TagKey<Block> HABITAT = TagKey.create(Registries.BLOCK, UncannyEncounters.id("crystal_frog_habitat"));
    private static final byte REFLECT_EVENT = 61;
    public static final byte SHELL_HIT_EVENT_BASE = 70, SHELL_BREAK_EVENT = 76;
    private static final EntityDataAccessor<Byte> HOP_PHASE = SynchedEntityData.defineId(CrystalFrog.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DUEL_POSE = SynchedEntityData.defineId(CrystalFrog.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> TALENT_CASTING = SynchedEntityData.defineId(CrystalFrog.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TALENT_SHELL = SynchedEntityData.defineId(CrystalFrog.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> TALENT_SHELL_HEALTH = SynchedEntityData.defineId(CrystalFrog.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TONGUE_TARGET = SynchedEntityData.defineId(CrystalFrog.class, EntityDataSerializers.INT);
    private static final Identifier KING_BONUS = UncannyEncounters.id("frog_king");
    private int responseTicks, reflectionTicks;
    private boolean traitsInitialized;
    private @Nullable CrystalFrogStyle duelStyle;
    private CrystalFrogDuels.@Nullable Match duel;
    private int victoryTicks;
    private final CrystalFrogHopAnimation hopAnimation = new CrystalFrogHopAnimation();
    private final CrystalFrogDuelCombat duelCombat = new CrystalFrogDuelCombat(this);
    private final CrystalFrogCombatAnimation combatAnimation = new CrystalFrogCombatAnimation();
    private final CrystalFrogTalents talents = new CrystalFrogTalents(this);
    private final FrogKingSwallow swallow = new FrogKingSwallow(this);
    private @Nullable UUID keeperId;
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

    @Override public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                            EntitySpawnReason reason, @Nullable SpawnGroupData group) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group);
        initializeTraits();
        return result;
    }

    private void initializeTraits() {
        if (level().isClientSide()) return;
        talents.initializeTalent();
        if (traitsInitialized) return;
        traitsInitialized = true;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(MIN_HEALTH + random.nextInt(MAX_HEALTH - MIN_HEALTH + 1));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(MIN_ATTACK + random.nextInt(MAX_ATTACK - MIN_ATTACK + 1));
        setHealth(getMaxHealth());
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        initializeTraits();
        super.addAdditionalSaveData(output);
        output.putBoolean("CrystalFrogTraitsInitialized", traitsInitialized);
        output.putString("CrystalFrogStyle", duelStyle().id());
        if (keeperId != null) output.store("FrogKeeper", UUIDUtil.CODEC, keeperId);
        swallow.save(output);
        talents.save(output);
        // Matches do not resume after unloading; a reloaded participant rests safely.
        if (duel != null) {
            output.putBoolean("Sitting", true);
            output.putFloat("CrystalShellHealth", 0);
            output.putLong("CrystalShellExpires", 0);
        }
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // Existing saves (and deliberately supplied health/attributes) keep their values.
        // An empty /summon tag is a new frog and still receives individual traits.
        traitsInitialized = input.getBooleanOr("CrystalFrogTraitsInitialized",
                input.read("Health", Codec.FLOAT).isPresent() || input.childrenList("attributes").isPresent());
        duelStyle = CrystalFrogStyle.from(input.getStringOr("CrystalFrogStyle", ""), getUUID());
        duel = null;
        duelCombat.reset();
        talents.load(input, traitsInitialized);
        // Vanilla restores Health before our transient king modifiers exist.
        setHealth(input.getFloatOr("Health", getHealth()));
        keeperId = input.read("FrogKeeper", UUIDUtil.CODEC).orElse(null);
        swallow.load(input);
    }

    @Override protected void registerGoals() { CrystalFrogAi.register(this, goalSelector); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HOP_PHASE, CrystalFrogHopAnimation.GROUNDED);
        builder.define(DUEL_POSE, CrystalFrogDuelCombat.NORMAL);
        builder.define(TALENT_CASTING, false);
        builder.define(TALENT_SHELL, false);
        builder.define(TALENT_SHELL_HEALTH, 0F);
        builder.define(TONGUE_TARGET, -1);
    }
    @Override protected PathNavigation createNavigation(Level level) { return new AmphibiousPathNavigation(this, level); }
    @Override protected void customServerAiStep(ServerLevel level) {
        ownerSupport.tick();
        super.customServerAiStep(level);
    }

    public boolean assistOwner(@Nullable LivingEntity target) {
        LivingEntity owner = getOwner();
        if (isBaby() || isKeeperFrog() || !isTame() || owner == null || isOrderedToSit() || isDueling() || getHealth() < getMaxHealth() * 0.5F
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
                || !level.noCollision(type.getSpawnAABB(Vec3.atBottomCenterOf(pos)))) return false;
        // Bounded local search only during spawn attempts, never during entity ticks.
        for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-4, -4, -4), pos.offset(4, 4, 4))) {
            if (level.hasChunkAt(nearby) && level.getBlockState(nearby).is(HABITAT)) return true;
        }
        return false;
    }

    @Override public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos.below()).is(HABITAT) ? 5 : 0;
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isDeadOrDying() || isInvulnerableTo(level, source)) return false;
        if (isKeeperFrog() && !FrogKeeperEncounters.get(level).defeated(keeperId) && !isKeeperMatchDamage(source)) return false;
        float previousHealth = getHealth();
        if (duel != null && amount > 0 && Float.isFinite(amount)) {
            CrystalFrogDuels.Match match = duel;
            if (source.getEntity() instanceof CrystalFrog attacker
                    && (source.getDirectEntity() == attacker || source.getDirectEntity() instanceof CrystalSlimeShot shot && shot.validFor(this))
                    && attacker.duelMatch() == match
                    && match.opponents(this, attacker)) {
                float floor = getMaxHealth() * CrystalFrogDuels.DEFEAT_FRACTION;
                float safeDamage = Math.max(0, getHealth() - floor);
                Vec3 incoming = source.getDirectEntity() instanceof CrystalSlimeShot shot ? shot.incomingPoint(this) : attacker.position();
                float defendedDamage = source.is(TALENT_BURST_DAMAGE) ? amount : duelCombat.defend(attacker, incoming, amount);
                boolean hurt = safeDamage > 0 && super.hurtServer(level, source, Math.min(defendedDamage, safeDamage));
                if (hurt) talents.afterHurt(previousHealth);
                if (getHealth() <= floor + 0.001F) match.defeated(this);
                return hurt;
            }
            match.cancel("interrupted");
        }
        // Before vanilla damage: a fatal hit must still alert the surviving partner.
        if (isKeeperFrog() && amount > 0 && Float.isFinite(amount) && source.getEntity() instanceof LivingEntity attacker)
            FrogKeeperEncounters.get(level).provoke(level, keeperId, attacker);
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
        if (hurt && isAlive()) {
            talents.afterHurt(previousHealth);
            reactToDamage(source);
        }
        return hurt;
    }

    @Override protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        float damage = super.getDamageAfterMagicAbsorb(source, amount);
        return source.is(DamageTypeTags.BYPASSES_EFFECTS) ? damage : talents.absorb(damage, source.getSourcePosition());
    }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) talents.afterMeleeHit(living);
        return hit;
    }

    private void reactToDamage(DamageSource source) {
        if (isKeeperFrog()) return; // The shared pact owns retaliation, including below half health.
        responseTicks = 200;
        threatPosition = source.getSourcePosition();
        provoker = source.getEntity() instanceof LivingEntity attacker && canRetaliateAgainst(attacker) ? attacker : null;
        setTarget(provoker);
        setOrderedToSit(false);
        setInSittingPose(false);
    }
    private boolean canRetaliateAgainst(LivingEntity attacker) {
        return attacker != this && !isOwnedBy(attacker) && attacker.isAlive() && attacker.level() == level() && !attacker.isSpectator()
                && !(attacker instanceof Player player && player.isCreative());
    }
    public boolean wantsRetaliation() { return isDueling() || (isKeeperFrog() ? getTarget() != null
            : !isBaby() && responseTicks > 0 && getTarget() != null && getHealth() >= getMaxHealth() * 0.5F); }
    public boolean isFrightened() { return !isKeeperFrog() && !isDueling() && responseTicks > 0 && (isBaby() || getHealth() < getMaxHealth() * 0.5F || getTarget() == null); }
    public @Nullable Vec3 threatPosition() { return threatPosition; }
    public boolean isDueling() { return duel != null; }
    public boolean isDuelingWith(CrystalFrog other) { return duel != null && duel.opponents(this, other); }
    public CrystalFrogDuels.@Nullable Match duelMatch() { return duel; }
    public CrystalFrogTalents talents() { return talents; }
    public FrogKingSwallow swallow() { return swallow; }
    public int tongueTargetId() { return entityData.get(TONGUE_TARGET); }
    public void setTongueTarget(int id) { entityData.set(TONGUE_TARGET, id); }
    public boolean isKing() { return talents.has(CrystalFrogTalent.FROG_KING); }
    public boolean isKeeperFrog() { return keeperId != null; }
    public @Nullable UUID keeperId() { return keeperId; }
    public void setKeeperRetaliationTarget(@Nullable LivingEntity target) {
        if (!isKeeperFrog() || isDueling()) return;
        if (getTargetUnchecked() != target) {
            talents.clearActive();
            swallow.clear();
            navigation.stop();
            moveControl.setWait();
        }
        responseTicks = 0;
        provoker = null;
        threatPosition = null;
        setTarget(target);
        BlockPos home = FrogKeeperEncounters.get((ServerLevel)level()).home(keeperId, true);
        boolean resting = target == null && (home == null || distanceToSqr(Vec3.atBottomCenterOf(home)) <= 4);
        setOrderedToSit(resting);
        setInSittingPose(resting && onGround() && !isInWater());
    }
    public void bindKeeper(FrogKeeper keeper) {
        keeperId = keeper.getUUID();
        setTame(true, false);
        setOwner(keeper);
        setPersistenceRequired();
        sit(true);
    }
    private boolean isKeeperMatchDamage(DamageSource source) {
        return duel != null && source.getEntity() instanceof CrystalFrog attacker && attacker.duelMatch() == duel
                && duel.opponents(this, attacker) && (source.getDirectEntity() == attacker
                || source.getDirectEntity() instanceof CrystalSlimeShot shot && shot.validFor(this));
    }
    public void applyKingAttributes() {
        for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.SCALE)) {
            var instance = getAttribute(attribute);
            if (instance == null) continue;
            instance.removeModifier(KING_BONUS);
            if (isKing()) instance.addTransientModifier(new AttributeModifier(KING_BONUS,
                    attribute == Attributes.SCALE ? 0.5 : 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
    public void configureTraits(double health, double attack, CrystalFrogStyle style, List<CrystalFrogTalent> inherited) {
        traitsInitialized = true;
        duelStyle = style;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Attributes.MAX_HEALTH.value().sanitizeValue(health));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Attributes.ATTACK_DAMAGE.value().sanitizeValue(attack));
        talents.setTalents(inherited);
        setHealth(getMaxHealth());
    }
    public boolean isTalentCasting() { return entityData.get(TALENT_CASTING); }
    public void setTalentCasting(boolean casting) { entityData.set(TALENT_CASTING, casting); }
    public boolean hasTalentShell() { return entityData.get(TALENT_SHELL); }
    public float talentShellHealth() { return entityData.get(TALENT_SHELL_HEALTH); }
    public void setTalentShellHealth(float health) {
        float value = Math.max(0, health);
        entityData.set(TALENT_SHELL_HEALTH, value);
        entityData.set(TALENT_SHELL, value > 0);
    }
    public boolean isTalentTarget(LivingEntity target) {
        if (!target.isAlive() || target.level() != level() || target.isSpectator() || !canAttack(target)
                || target instanceof Player player && player.isCreative() || distanceToSqr(target) > 24 * 24) return false;
        if (isDueling()) return target instanceof CrystalFrog other && isDuelingWith(other);
        if (target instanceof CrystalFrog other && other.isDueling()) return false;
        LivingEntity owner = getOwner();
        return wantsRetaliation() && getTarget() == target && !isOrderedToSit() && !isAlliedTo(target)
                && (owner == null || wantsToAttack(target, owner));
    }
    public CrystalFrogDuelCombat duelCombat() { return duelCombat; }
    public CrystalFrogCombatAnimation combatAnimation() { return combatAnimation; }
    public void setDuelPose(byte pose) { entityData.set(DUEL_POSE, pose); }
    public CrystalFrogStyle duelStyle() {
        if (duelStyle == null) duelStyle = CrystalFrogStyle.from("", getUUID());
        return duelStyle;
    }

    public void beginDuel(CrystalFrogDuels.Match match, CrystalFrog opponent) {
        duel = match;
        duelCombat.reset();
        talents.beginDuel();
        swallow.clear();
        resetLove();
        victoryTicks = responseTicks = 0;
        provoker = null;
        threatPosition = null;
        setOrderedToSit(false);
        setInSittingPose(false);
        navigation.stop();
        setTarget(opponent);
    }

    public void cancelDuel(String reason) { if (duel != null) duel.cancel(reason); }

    public void finishDuel(CrystalFrogDuels.Match match, boolean won) {
        if (duel != match) return;
        duel = null;
        duelCombat.reset();
        talents.clearActive();
        swallow.clear();
        sit(true);
        if (won) {
            victoryTicks = 120;
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
        }
    }

    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive()) return InteractionResult.PASS;
        initializeTraits();
        ItemStack stack = player.getItemInHand(hand);
        if (isKeeperFrog()) {
            if (stack.is(Items.STICK) && getOwner() instanceof FrogKeeper keeper) keeper.challenge(player);
            return InteractionResult.SUCCESS;
        }
        if (isTame() && stack.isEmpty() && player.isShiftKeyDown()) {
            if (!level().isClientSide()) player.sendSystemMessage(Component.translatable(
                    "message.uncannyencounters.crystal_frog.stats", getDisplayName(),
                    stat(getHealth()), stat(getMaxHealth()), stat(getAttributeValue(Attributes.ATTACK_DAMAGE)),
                    duelStyle().description(), talents.description()));
            return InteractionResult.SUCCESS.withoutItem();
        }
        if (isTame() && stack.is(ModItems.ACTIVATED_AMETHYST)) {
            if (isDueling()) {
                if (!level().isClientSide()) CrystalFrogDuels.tell(player, "no_feeding");
                return InteractionResult.SUCCESS;
            }
            if (!level().isClientSide() && getHealth() < getMaxHealth()) {
                stack.consume(1, player);
                heal(FEED_HEAL);
                playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.3F);
                ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY(0.8), getZ(),
                        3, 0.2, 0.15, 0.2, 0, 0, 0);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.STICK)) {
            CrystalFrogDuels.useStick(player, this);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.AMETHYST_SHARD)) {
            if (isKing() && !isBaby() || isDueling() || wantsRetaliation() || isFrightened()) {
                if (!level().isClientSide()) player.sendSystemMessage(Component.translatable(
                        "message.uncannyencounters.crystal_frog." + (isKing() ? "infertile" : "breeding_busy")));
                return InteractionResult.SUCCESS;
            }
            InteractionResult result = super.mobInteract(player, hand);
            if (!level().isClientSide() && isInLove()) {
                setOrderedToSit(false);
                setInSittingPose(false);
            }
            return result;
        }
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

    private static String stat(double value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }

    private void sit(boolean sitting) {
        if (duel != null) duel.cancel("cancelled");
        if (sitting) {
            talents.clearActive();
            swallow.clear();
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
        if (reflectionTicks > 0) reflectionTicks--;
        if (!level().isClientSide()) {
            initializeTraits();
            if (keeperId != null && isAlive()) {
                var ledger = FrogKeeperEncounters.get((ServerLevel)level());
                ledger.registerFrog(this);
                if (ledger.frogDead(keeperId)) { discard(); return; }
                if (!isDueling() && ledger.defeated(keeperId))
                    setKeeperRetaliationTarget(ledger.retaliationTarget((ServerLevel)level(), keeperId, this));
            }
            if (duel != null) duel.tick();
            if (victoryTicks > 0 && --victoryTicks % 5 == 0) {
                ServerLevel server = (ServerLevel) level();
                for (int i = 0; i < 6; i++) {
                    double angle = tickCount * 0.12 + i * Math.PI / 3;
                    server.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(angle) * 0.5,
                            getY() + getBbHeight() + 0.3, getZ() + Math.sin(angle) * 0.5,
                            1, 0, 0, 0, 0, 0.01, 0);
                }
            }
            if (responseTicks > 0 && --responseTicks == 0) {
                threatPosition = null;
                provoker = null;
                setTarget(null);
            }
            LivingEntity target = getTargetUnchecked();
            if (!isKeeperFrog() && target != null && (getTarget() == null || !canRetaliateAgainst(target) || distanceToSqr(target) > 24 * 24)) {
                setTarget(null);
                if (getHealth() >= getMaxHealth() * 0.5F) {
                    responseTicks = 0;
                    provoker = null;
                    threatPosition = null;
                }
            }
        }
        super.tick();
        if (!level().isClientSide()) {
            talents.tick();
            swallow.checkContext();
            talents.afterMovement();
        }
        boolean animationDisabled = isInWater() || isInSittingPose() || isPassenger() || !isAlive();
        if (level().isClientSide()) {
            hopAnimation.tick(animationDisabled ? CrystalFrogHopAnimation.DISABLED : entityData.get(HOP_PHASE));
            combatAnimation.tick(entityData.get(DUEL_POSE), isTalentCasting(), talentShellHealth());
        } else {
            // Sample authoritative physics after travel; remote position interpolation
            // is not the animation clock. Metadata also initializes newly tracking clients.
            entityData.set(HOP_PHASE, CrystalFrogHopAnimation.phase(onGround(), animationDisabled, getDeltaMovement().y));
        }
    }

    /** Real jumps, using vanilla collision and gravity. */
    public void hop(double x, double z, boolean uphill) {
        float power = getJumpPower(uphill ? 0.5F / 0.42F : 0.36F / 0.42F);
        setDeltaMovement(x, Math.max(power, getDeltaMovement().y), z);
        needsSync = true;
        playSound(SoundEvents.FROG_LONG_JUMP, 0.25F, 1.15F + random.nextFloat() * 0.2F);
    }

    @Override public void handleEntityEvent(byte event) {
        if (event == REFLECT_EVENT) reflectionTicks = 6;
        else if (event >= SHELL_HIT_EVENT_BASE && event < SHELL_HIT_EVENT_BASE + 6) combatAnimation.shellHit(event - SHELL_HIT_EVENT_BASE);
        else if (event == SHELL_BREAK_EVENT) combatAnimation.shellBroken();
        else super.handleEntityEvent(event);
    }
    public CrystalFrogHopAnimation hopAnimation() { return hopAnimation; }
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
    @Override public void die(DamageSource source) {
        if (keeperId != null && level() instanceof ServerLevel server)
            FrogKeeperEncounters.get(server).frogDied(keeperId, server.getGameTime());
        super.die(source);
    }
    @Override public boolean canBeLeashed() { return !isKeeperFrog() && super.canBeLeashed(); }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return !isKeeperFrog() && super.canUsePortal(ignorePassenger); }
    @Override protected boolean canRide(Entity vehicle) { return !isKeeperFrog() && super.canRide(vehicle); }
    @Override protected boolean canBeABaby() { return true; }
    @Override public boolean isFood(ItemStack stack) { return stack.is(Items.AMETHYST_SHARD); }
    public boolean breedingAvailable() {
        return isAlive() && !isKing() && !isKeeperFrog() && !isBaby() && getAge() == 0
                && !isDueling() && !wantsRetaliation() && !isFrightened();
    }
    @Override public boolean canFallInLove() { return breedingAvailable() && super.canFallInLove(); }
    @Override public boolean canMate(Animal partner) {
        return partner instanceof CrystalFrog other && breedingAvailable() && other.breedingAvailable() && super.canMate(partner);
    }
    @Override public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        if (!(partner instanceof CrystalFrog other) || !breedingAvailable() || !other.breedingAvailable()) return null;
        initializeTraits();
        other.initializeTraits();
        CrystalFrog child = ModEntities.CRYSTAL_FROG.create(level, EntitySpawnReason.BREEDING);
        if (child == null) return null;
        CrystalFrogStyle style = random.nextBoolean() ? duelStyle() : other.duelStyle();
        if (random.nextFloat() < 0.05F) {
            CrystalFrogStyle original = style;
            var alternatives = java.util.Arrays.stream(CrystalFrogStyle.values()).filter(s -> s != original).toList();
            style = alternatives.get(random.nextInt(alternatives.size()));
        }
        child.configureTraits(FrogGenetics.inheritStat(random, getAttributeBaseValue(Attributes.MAX_HEALTH),
                        other.getAttributeBaseValue(Attributes.MAX_HEALTH), true),
                FrogGenetics.inheritStat(random, getAttributeBaseValue(Attributes.ATTACK_DAMAGE),
                        other.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), false), style,
                FrogGenetics.inheritTalents(random, talents.all(), other.talents.all()));
        var owner = getOwnerReference();
        if (isTame() && other.isTame() && owner != null && other.getOwnerReference() != null
                && owner.getUUID().equals(other.getOwnerReference().getUUID())) {
            child.setTame(true, false);
            child.setOwnerReference(owner);
        }
        child.setPersistenceRequired();
        return child;
    }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.FROG_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.FROG_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.FROG_DEATH; }
}
