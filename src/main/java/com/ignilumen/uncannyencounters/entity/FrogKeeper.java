package com.ignilumen.uncannyencounters.entity;

import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogDuels;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogStyle;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalent;
import com.ignilumen.uncannyencounters.entity.frogkeeper.FrogKeeperEncounters;
import com.ignilumen.uncannyencounters.entity.frogkeeper.ReturnToFrogCourtGoal;
import com.ignilumen.uncannyencounters.entity.frogkeeper.FrogCourtSafety;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A neutral host; winning the duel opens his armor, but attacking either partner angers both. */
public final class FrogKeeper extends PathfinderMob {
    private @Nullable UUID frogId;
    private @Nullable BlockPos altar;
    private boolean defeated;
    private final ServerBossEvent bossBar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.uncannyencounters.frog_keeper"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    public FrogKeeper(EntityType<? extends FrogKeeper> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }
    public static AttributeSupplier.Builder createAttributes() {
        // Vindicator-like effective melee damage, delivered unarmed rather than through equipment.
        return createMobAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.ARMOR, 0)
                .add(Attributes.MOVEMENT_SPEED, 0.35).add(Attributes.FOLLOW_RANGE, 12)
                .add(Attributes.ATTACK_DAMAGE, 13).add(Attributes.KNOCKBACK_RESISTANCE, 0);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new MeleeAttackGoal(this, 1, false) {
            @Override public boolean canUse() { return defeated && super.canUse(); }
            @Override public boolean canContinueToUse() { return defeated && super.canContinueToUse(); }
        });
        goalSelector.addGoal(3, new ReturnToFrogCourtGoal(this, this::getUUID));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }
    public BlockPos altar() { return altar == null ? blockPosition().north(3) : altar; }
    public void setAltar(BlockPos pos) { altar = pos.immutable(); }
    public boolean challengeDefeated() { return defeated; }
    public @Nullable UUID companionId() { return frogId; }
    public void setRetaliationTarget(@Nullable LivingEntity target) {
        if (getTargetUnchecked() != target) getNavigation().stop();
        setTarget(target);
        setAggressive(target != null);
    }
    public boolean insideArena(CrystalFrog frog) {
        return allowsDuelPosition(frog, frog.position());
    }
    public boolean allowsDuelPosition(CrystalFrog frog, Vec3 point) {
        return frog.level() == level() && FrogCourtSafety.inside(altar(), point, frog.getBbWidth());
    }
    public @Nullable CrystalFrog companion() {
        return level() instanceof ServerLevel server && frogId != null && server.getEntity(frogId) instanceof CrystalFrog frog ? frog : null;
    }
    public void challenge(Player player) {
        if (!(level() instanceof ServerLevel) || player.isSpectator() || !isAlive()) return;
        if (defeated) { tell(player, "unsealed"); return; }
        CrystalFrog frog = companion();
        if (frog == null) { tell(player, "companion_unavailable"); return; }
        if (!frog.isDueling() && !insideArena(frog) && !FrogCourtSafety.recall(frog)) {
            tell(player, "recall_blocked"); return;
        }
        CrystalFrogDuels.challengeKeeper(player, this, frog);
    }
    public static void tell(Player player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.uncannyencounters.frog_keeper." + key, args));
    }
    public void wonChallenge() {
        if (defeated || !(level() instanceof ServerLevel server)) return;
        defeated = true;
        FrogKeeperEncounters.get(server).defeated(this);
        playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1, 0.6F);
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.5), getZ(), 30, 0.5, 0.9, 0.5, 0.02, 0.02, 0.02);
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(Items.STICK)) challenge(player);
        else if (!level().isClientSide()) tell(player, defeated ? "unsealed" : "instructions");
        return InteractionResult.SUCCESS;
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isDeadOrDying() || isInvulnerableTo(level, source)) return false;
        if (!defeated) {
            if (source.getEntity() instanceof Player player) tell(player, "sealed");
            return false;
        }
        if (amount > 0 && Float.isFinite(amount) && source.getEntity() instanceof LivingEntity attacker)
            FrogKeeperEncounters.get(level).provoke(level, getUUID(), attacker);
        return super.hurtServer(level, source, amount);
    }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }
    @Override public boolean canBeLeashed() { return false; }
    @Override protected boolean canRide(net.minecraft.world.entity.Entity vehicle) { return false; }
    @Override public void die(DamageSource source) {
        if (level() instanceof ServerLevel server) {
            FrogKeeperEncounters.get(server).died(this, server.getGameTime());
            CrystalFrog frog = companion();
            if (frog != null) frog.cancelDuel("interrupted");
            bossBar.removeAllPlayers();
        }
        super.die(source);
    }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        if (altar == null) altar = blockPosition().north(3).immutable();
        var ledger = FrogKeeperEncounters.get(server);
        ledger.register(this);
        if (ledger.keeperDead(getUUID())) { discard(); return; }
        defeated |= ledger.defeated(getUUID());
        setRetaliationTarget(ledger.retaliationTarget(server, getUUID(), this));
        if (frogId == null && tickCount % 20 == 1) createCompanion(server);
        CrystalFrog frog = companion();
        bossBar.setName(Component.translatable(defeated ? "entity.uncannyencounters.frog_keeper" : "boss.uncannyencounters.frog_keeper.king"));
        bossBar.setProgress(defeated ? getHealth() / getMaxHealth() : frog == null ? 1 : frog.getHealth() / frog.getMaxHealth());
        if (!defeated && tickCount % 20 == 0)
            server.sendParticles(ParticleTypes.WITCH, getX(), getY(0.5), getZ(), 2, 0.3, 0.6, 0.3, 0, 0.01, 0);
    }
    private void createCompanion(ServerLevel server) {
        CrystalFrog frog = ModEntities.CRYSTAL_FROG.create(server, EntitySpawnReason.TRIGGERED);
        if (frog == null) return;
        frog.configureTraits(60, 8, CrystalFrogStyle.GUARD, List.of(CrystalFrogTalent.FROG_KING, CrystalFrogTalent.RETALIATING_SHELL));
        frog.bindKeeper(this);
        Vec3 pos = Vec3.atBottomCenterOf(altar().south(6));
        frog.snapTo(pos, 180, 0);
        if (!server.hasChunkAt(frog.blockPosition()) || !server.noCollision(frog) || !server.getFluidState(frog.blockPosition()).isEmpty()) return;
        if (server.addFreshEntity(frog)) {
            frogId = frog.getUUID();
            FrogKeeperEncounters.get(server).register(this);
        }
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (frogId != null) output.store("KeeperFrog", UUIDUtil.CODEC, frogId);
        output.store("Altar", BlockPos.CODEC, altar());
        output.putBoolean("ChallengeDefeated", defeated);
        output.putInt("KeeperStatsVersion", 1);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        frogId = input.read("KeeperFrog", UUIDUtil.CODEC).orElse(null);
        altar = input.read("Altar", BlockPos.CODEC).orElse(null);
        defeated = input.getBooleanOr("ChallengeDefeated", false);
        if (input.getIntOr("KeeperStatsVersion", 0) < 1) {
            float healthFraction = getHealth() / getMaxHealth();
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(24);
            getAttribute(Attributes.ARMOR).setBaseValue(0);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
            getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(12);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(13);
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            setHealth(getMaxHealth() * healthFraction);
        }
    }
}
