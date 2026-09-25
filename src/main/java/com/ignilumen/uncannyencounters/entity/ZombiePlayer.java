package com.ignilumen.uncannyencounters.entity;

import com.ignilumen.uncannyencounters.entity.zombieplayer.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A persistent player-shaped undead with its own terrain-aware pilot. */
public final class ZombiePlayer extends Monster {
    private static final EntityDataAccessor<ResolvableProfile> PROFILE = SynchedEntityData.defineId(ZombiePlayer.class, EntityDataSerializers.RESOLVABLE_PROFILE);
    private static final EntityDataAccessor<Byte> SKIN_PARTS = SynchedEntityData.defineId(ZombiePlayer.class, EntityDataSerializers.BYTE);
    private final PreciseMoveControl steering;
    private final ZombiePlayerBrain tactics;
    private @Nullable UUID owner;
    private BlockPos home = BlockPos.ZERO;
    private long expireAt;
    private boolean tracked, initialized, edgeGuard, fleshRegeneration;
    private int eatTicks, eatCooldown;

    public ZombiePlayer(EntityType<? extends ZombiePlayer> type, Level level) {
        super(type, level);
        moveControl = steering = new PreciseMoveControl(this);
        tactics = new ZombiePlayerBrain(this);
        setPersistenceRequired();
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.1).add(Attributes.ATTACK_DAMAGE, 1)
                .add(Attributes.ATTACK_SPEED, 4).add(Attributes.ARMOR, 15)
                .add(Attributes.FOLLOW_RANGE, 40).add(Attributes.STEP_HEIGHT, 0.6);
    }

    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        // Static like Mannequin's default: an unresolved name would fetch that real account's skin.
        builder.define(PROFILE, ResolvableProfile.Static.EMPTY);
        builder.define(SKIN_PARTS, (byte)127);
    }

    public ResolvableProfile profile() { return entityData.get(PROFILE); }
    public byte skinParts() { return entityData.get(SKIN_PARTS); }
    public BlockPos home() { return home; }
    public PreciseMoveControl steering() { return steering; }
    public void setEdgeGuard(boolean value) { edgeGuard = value; }
    public void setCrouching(boolean value) { setShiftKeyDown(value); }
    @Override public boolean isCrouching() { return isShiftKeyDown(); }
    public float airAcceleration() { return getFlyingSpeed(); }
    @Override protected float getFlyingSpeed() { return isSprinting() ? 0.026F : 0.02F; }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }

    public static byte skinParts(Player player) {
        int mask = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) if (player.isModelPartShown(part)) mask |= part.getMask();
        return (byte)mask;
    }

    public void bind(UUID owner, ResolvableProfile profile, byte parts, boolean leftHanded,
                     BlockPos home, long expireAt, boolean tracked) {
        this.owner = owner;
        this.home = home.immutable();
        this.expireAt = expireAt;
        this.tracked = tracked;
        entityData.set(PROFILE, profile);
        entityData.set(SKIN_PARTS, parts);
        setLeftHanded(leftHanded);
        setCustomName(Component.translatable("entity.uncannyencounters.zombie_player.named", profile.partialProfile().name()));
        setCustomNameVisible(true);
    }

    @Override public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                            EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        boolean left = isLeftHanded();
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        if (owner != null) setLeftHanded(left);
        initializeLife(level.getLevel());
        return result;
    }

    private void initializeLife(ServerLevel level) {
        if (initialized) return;
        initialized = true;
        if (expireAt == 0) {
            home = blockPosition();
            expireAt = level.getGameTime() + ZombiePlayerSpawns.LIFETIME;
        }
        setCanPickUpLoot(random.nextFloat() < level.getDifficulty().getId() * 0.25F);
        getAttribute(Attributes.ARMOR).setBaseValue(10 + random.nextInt(11));
    }

    @Override public void tick() {
        if (level() instanceof ServerLevel level) {
            initializeLife(level);
            if (level.getGameTime() >= expireAt) {
                discard();
                return;
            }
            if (eatCooldown > 0) eatCooldown--;
        }
        super.tick();
    }

    @Override protected void customServerAiStep(ServerLevel level) {
        if (eatTicks > 0) {
            tactics.evade(level);
            if (--eatTicks == 0) finishMeal();
            return;
        }
        if (getHealth() <= 10 && eatCooldown == 0) {
            stopUsingItem();
            setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ROTTEN_FLESH));
            startUsingItem(InteractionHand.OFF_HAND);
            eatTicks = 32;
            eatCooldown = 300;
            tactics.stop(level);
            return;
        }
        tactics.tick(level);
    }

    private void finishMeal() {
        stopUsingItem();
        if (level().isClientSide()) return;
        setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        fleshRegeneration = true;
        try { addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1)); }
        finally { fleshRegeneration = false; }
        addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
        playSound(SoundEvents.PLAYER_BURP, 0.5F, 0.8F);
    }

    @Override protected void completeUsingItem() {
        // Rotten flesh uses the eating animation, but applies the zombie's own effects.
        if (getUseItem().is(Items.ROTTEN_FLESH) && getUsedItemHand() == InteractionHand.OFF_HAND) {
            eatTicks = 0;
            finishMeal();
        } else super.completeUsingItem();
    }

    @Override public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.is(MobEffects.REGENERATION)) return fleshRegeneration;
        return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }

    public static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS)
                || stack.is(Items.MACE) || stack.is(Items.TRIDENT)
                || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    @Override public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return canPickUpLoot() && isWeapon(stack) && getMainHandItem().isEmpty();
    }

    @Override protected void pickUpItem(ServerLevel level, ItemEntity item) {
        if (!wantsToPickUp(level, item.getItem())) return;
        setItemSlot(EquipmentSlot.MAINHAND, item.getItem().split(1));
        setGuaranteedDrop(EquipmentSlot.MAINHAND);
        onItemPickup(item);
        take(item, 1);
        if (item.getItem().isEmpty()) item.discard();
    }

    @Override public ItemStack getProjectile(ItemStack weapon) { return new ItemStack(Items.ARROW); }
    @Override public boolean canUseNonMeleeWeapon(ItemStack item) {
        return item.getItem() instanceof BowItem || item.getItem() instanceof CrossbowItem;
    }

    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean playerKilled) {
        setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        super.dropCustomDeathLoot(level, source, playerKilled);
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (isDeadOrDying() && tracked && owner != null && level() instanceof ServerLevel level)
            ZombiePlayerSpawns.get(level.getServer()).release(owner, getUUID());
    }

    @Override public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level && tactics != null) {
            tactics.stop(level);
            if (tracked && owner != null && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED))
                ZombiePlayerSpawns.get(level.getServer()).release(owner, getUUID());
        }
        super.remove(reason);
    }

    @Override protected Vec3 maybeBackOffFromEdge(Vec3 movement, MoverType type) {
        if (!edgeGuard || !onGround() || movement.y > 0 || type != MoverType.SELF) return movement;
        double x = movement.x, z = movement.z;
        while (x != 0 && unsupported(x, 0)) x = trim(x);
        while (z != 0 && unsupported(0, z)) z = trim(z);
        while (x != 0 && z != 0 && unsupported(x, z)) { x = trim(x); z = trim(z); }
        return new Vec3(x, movement.y, z);
    }
    private boolean unsupported(double x, double z) {
        return level().noCollision(this, getBoundingBox().move(x, -maxUpStep(), z));
    }
    private static double trim(double value) { return Math.abs(value) <= 0.05 ? 0 : value - Math.copySign(0.05, value); }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ZOMBIE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ZOMBIE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ZOMBIE_DEATH; }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (owner != null) output.store("Owner", UUIDUtil.CODEC, owner);
        output.store("Profile", ResolvableProfile.CODEC, profile());
        output.putByte("SkinParts", skinParts());
        output.store("Home", BlockPos.CODEC, home);
        output.putLong("ExpireAt", expireAt);
        output.putBoolean("Tracked", tracked);
        output.putBoolean("Initialized", initialized);
        output.putInt("EatCooldown", eatCooldown);
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        entityData.set(PROFILE, input.read("Profile", ResolvableProfile.CODEC).orElse(profile()));
        entityData.set(SKIN_PARTS, input.getByteOr("SkinParts", (byte)127));
        home = input.read("Home", BlockPos.CODEC).orElse(blockPosition());
        expireAt = input.getLongOr("ExpireAt", 0);
        tracked = input.getBooleanOr("Tracked", false);
        initialized = input.getBooleanOr("Initialized", false);
        eatCooldown = input.getIntOr("EatCooldown", 0);
        if (getOffhandItem().is(Items.ROTTEN_FLESH)) setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }
}
