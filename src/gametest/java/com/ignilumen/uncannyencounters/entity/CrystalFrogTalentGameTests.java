package com.ignilumen.uncannyencounters.entity;

import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogDuels;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalent;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalents;
import com.ignilumen.uncannyencounters.entity.crystalfrog.FrogTalentAttack;
import com.mojang.authlib.GameProfile;
import java.util.EnumMap;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Manual integration tests: talent rarity, persistence, daily combat and exact-match cleanup. */
public final class CrystalFrogTalentGameTests {
    private void arena(GameTestHelper test) {
        for (int x = 1; x <= 8; x++) for (int z = 1; z <= 8; z++) {
            test.getLevel().setBlock(test.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            for (int y = 1; y <= 3; y++) test.getLevel().setBlock(test.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private CrystalFrog frog(GameTestHelper test, CrystalFrogTalent talent, Vec3 pos) {
        CrystalFrog frog = test.spawn(ModEntities.CRYSTAL_FROG, pos);
        frog.finalizeSpawn(test.getLevel(), test.getLevel().getCurrentDifficultyAt(frog.blockPosition()), EntitySpawnReason.COMMAND, null);
        frog.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
        frog.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3);
        frog.setHealth(20);
        var saved = save(test, frog);
        saved.putString("CrystalFrogTalent", talent.id());
        saved.putString("CrystalFrogStyle", "guard");
        frog.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), saved));
        frog.setOnGround(true);
        return frog;
    }

    private CompoundTag save(GameTestHelper test, CrystalFrog frog) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        frog.saveWithoutId(output);
        return output.buildResult();
    }

    private Player owner(GameTestHelper test) {
        Player owner = new Player(test.getLevel(), new GameProfile(UUID.randomUUID(), "frog-talent-test")) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
            @Override public boolean isClientAuthoritative() { return false; }
        };
        owner.snapTo(test.absoluteVec(new Vec3(4, 1, 4)), 0, 0);
        return owner;
    }

    private void duel(GameTestHelper test, CrystalFrog a, CrystalFrog b) {
        Player owner = owner(test);
        a.tame(owner);
        b.tame(owner);
        CrystalFrogDuels.useStick(owner, a);
        CrystalFrogDuels.useStick(owner, b);
        test.assertTrue(a.isDuelingWith(b), "Fixture must start an approved match");
    }

    private LivingEntity dailyTarget(GameTestHelper test, CrystalFrog frog, Vec3 pos) {
        frog.tame(owner(test));
        var target = test.spawn(EntityTypes.PIG, pos);
        target.setNoAi(true);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        test.assertTrue(frog.assistOwner(target), "Talent must work through ordinary owner support");
        frog.removeFreeWill();
        return target;
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void fivePercentIsTheTotalProbability(GameTestHelper test) {
        var counts = new EnumMap<CrystalFrogTalent, Integer>(CrystalFrogTalent.class);
        for (int roll = 0; roll < 80; roll++) counts.merge(CrystalFrogTalent.fromRoll(roll), 1, Integer::sum);
        test.assertTrue(counts.get(CrystalFrogTalent.NONE) == 76, "95 percent of frogs must have no talent");
        for (CrystalFrogTalent talent : CrystalFrogTalent.values()) if (talent != CrystalFrogTalent.NONE)
            test.assertTrue(counts.getOrDefault(talent, 0) == (talent.wildTalent() ? 1 : 0),
                    "Wild rolls must only include the four original talents");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void reloadKeepsTalentAndOldFrogsNeverReceiveARetrofitRoll(GameTestHelper test) {
        CrystalFrog frog = frog(test, CrystalFrogTalent.CRYSTAL_ECHO, new Vec3(3, 1, 3));
        var saved = save(test, frog);
        CrystalFrog loaded = new CrystalFrog(ModEntities.CRYSTAL_FROG, test.getLevel());
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), saved));
        loaded.talents().initializeTalent();
        test.assertTrue(loaded.talents().talent() == CrystalFrogTalent.CRYSTAL_ECHO, "Reloading must retain a rare talent");
        saved.remove("CrystalFrogTalent");
        for (int i = 0; i < 20; i++) {
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), saved));
            loaded.talents().initializeTalent();
            test.assertTrue(loaded.talents().talent() == CrystalFrogTalent.NONE, "Existing frogs must never roll a missing talent");
        }
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void talentProjectileRespectsTheHealthFloorAndCannotEnterANewMatch(GameTestHelper test) {
        arena(test);
        CrystalFrog a = frog(test, CrystalFrogTalent.SLIME_SPIT, new Vec3(3, 1, 3));
        CrystalFrog b = frog(test, CrystalFrogTalent.NONE, new Vec3(3, 1, 6));
        duel(test, a, b);
        FrogTalentAttack attack = FrogTalentAttack.capture(a, b);
        CrystalSlimeShot shot = new CrystalSlimeShot(attack);
        test.assertTrue(attack.hurt(shot, 100) && b.getHealth() == 5 && b.isAlive(), "Talent damage must stop at the duel's 25 percent floor");
        test.assertTrue(!attack.valid() && !a.isDueling(), "The finishing hit must invalidate all delayed attacks from that match");
        a.setHealth(20);
        b.setHealth(20);
        duel(test, a, b);
        test.assertTrue(!attack.valid() && !attack.hurt(shot, 2), "A previous match's projectile must never damage a rematch");
        shot.tick();
        test.assertTrue(shot.isRemoved(), "An expired projectile must remove itself");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void frontGuardStopsSlimeAndUnguardedSlimeDoesNotStack(GameTestHelper test) {
        arena(test);
        CrystalFrog guard = frog(test, CrystalFrogTalent.NONE, new Vec3(3.5, 1, 3.5));
        CrystalFrog caster = frog(test, CrystalFrogTalent.SLIME_SPIT, new Vec3(3.5, 1, 5.5));
        duel(test, guard, caster);
        guard.duelCombat().tick();
        double speed = guard.getAttributeValue(Attributes.MOVEMENT_SPEED);
        CrystalSlimeShot blocked = new CrystalSlimeShot(FrogTalentAttack.capture(caster, guard));
        blocked.setDeltaMovement(0, 0, -0.65);
        blocked.onHit(new EntityHitResult(guard));
        test.assertTrue(Math.abs(guard.getHealth() - 19.3F) < 0.001F
                && guard.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed, "A frontal guard must reduce slime damage and prevent adhesion");
        FrogTalentAttack attack = FrogTalentAttack.capture(caster, guard);
        CrystalFrogTalents.applySlime(attack);
        CrystalFrogTalents.applySlime(attack);
        test.assertTrue(Math.abs(guard.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed * 0.8) < 0.0001,
                "Repeated slime must not stack the movement penalty");
        guard.cancelDuel("cancelled");
        test.runAfterDelay(2, () -> {
            test.assertTrue(Math.abs(guard.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 0.0001,
                    "The movement penalty must be removed after the match ends");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void crystalShellHasAFiniteBudgetAndDoesNotRearmBelowFullHealth(GameTestHelper test) {
        CrystalFrog frog = frog(test, CrystalFrogTalent.CRYSTAL_SHELL, new Vec3(3, 1, 3));
        var damage = test.getLevel().damageSources().fall();
        frog.hurtServer(test.getLevel(), damage, 11);
        test.assertTrue(frog.getHealth() == 9 && frog.hasTalentShell(), "Crossing half health must trigger the crystal shell");
        test.assertTrue(frog.getDamageAfterMagicAbsorb(damage, 3) == 0, "The shell must absorb an initial three damage");
        test.assertTrue(frog.getDamageAfterMagicAbsorb(damage, 3) == 2 && !frog.hasTalentShell(), "Only four total damage may be absorbed");
        frog.talents().afterHurt(20);
        test.assertTrue(!frog.hasTalentShell(), "The depleted shell must not rearm during the same injured state");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 30)
    public void echoWorksDuringOwnerSupportAndSittingCancelsPendingEcho(GameTestHelper test) {
        arena(test);
        CrystalFrog active = frog(test, CrystalFrogTalent.CRYSTAL_ECHO, new Vec3(2.5, 1, 3));
        LivingEntity victim = dailyTarget(test, active, new Vec3(2.5, 1, 4));
        active.doHurtTarget(test.getLevel(), victim);
        float afterMelee = victim.getHealth();
        CrystalFrog cancelled = frog(test, CrystalFrogTalent.CRYSTAL_ECHO, new Vec3(6.5, 1, 3));
        LivingEntity safe = dailyTarget(test, cancelled, new Vec3(6.5, 1, 4));
        cancelled.doHurtTarget(test.getLevel(), safe);
        float beforeCancel = safe.getHealth();
        cancelled.mobInteract((Player) cancelled.getOwner(), InteractionHand.MAIN_HAND);
        test.runAfterDelay(14, () -> {
            test.assertTrue(victim.getHealth() == afterMelee - 2, "An ordinary owner-support melee hit must produce its delayed echo");
            test.assertTrue(safe.getHealth() == beforeCancel, "Sitting must cancel the other frog's pending echo");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 30)
    public void landingShockCanBeAvoidedByLeavingTheGround(GameTestHelper test) {
        arena(test);
        CrystalFrog a = frog(test, CrystalFrogTalent.GROUND_SHOCK, new Vec3(2.5, 1, 3));
        LivingEntity grounded = dailyTarget(test, a, new Vec3(2.5, 1, 4.5));
        CrystalFrog b = frog(test, CrystalFrogTalent.GROUND_SHOCK, new Vec3(6.5, 1, 3));
        LivingEntity airborne = dailyTarget(test, b, new Vec3(6.5, 1, 4.5));
        for (CrystalFrog frog : new CrystalFrog[]{a, b}) {
            frog.setOnGround(false);
            for (int i = 0; i < 3; i++) frog.talents().afterMovement();
            frog.setOnGround(true);
            frog.talents().afterMovement();
        }
        float groundHealth = grounded.getHealth(), airHealth = airborne.getHealth();
        airborne.setPos(airborne.position().add(0, 0.4, 0));
        airborne.setNoGravity(true);
        airborne.setOnGround(false);
        test.runAfterDelay(8, () -> {
            test.assertTrue(grounded.getHealth() == groundHealth - 1, "The landing shock must hit a nearby grounded combat target");
            test.assertTrue(airborne.getHealth() == airHealth, "Airborne opponents must evade the ground shock");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void variantStoragePreservesIdentityAndDisablesBreeding(GameTestHelper test) {
        var frog = frog(test, CrystalFrogTalent.SCATTER_SLIME, new Vec3(3, 1, 3));
        var owner = owner(test);
        frog.tame(owner);
        frog.talents().setTalents(java.util.List.of(CrystalFrogTalent.SCATTER_SLIME, CrystalFrogTalent.DOUBLE_ECHO));
        frog.setHealth(13);
        var id = frog.getUUID();
        test.assertTrue(frog.convertVariant(com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogVariant.ECHO), "Normal frog can transform");
        test.assertTrue(!frog.convertVariant(com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogVariant.ENDER), "Variants must be mutually exclusive");
        var cage = com.ignilumen.uncannyencounters.entity.crystalfrog.FrogCageData.pack(test.getLevel(), frog);
        var loaded = com.ignilumen.uncannyencounters.entity.crystalfrog.FrogCageData.unpack(test.getLevel(), cage);
        test.assertTrue(loaded != null && loaded.getUUID().equals(id) && loaded.isOwnedBy(owner)
                && loaded.getHealth() == 13 && loaded.getMaxHealth() == 20
                && loaded.talents().all().equals(frog.talents().all()) && loaded.variant() == frog.variant(),
                "Cage must preserve variant, health, both talents, UUID and owner");
        test.assertTrue(!loaded.breedingAvailable() && !loaded.canFallInLove()
                && com.ignilumen.uncannyencounters.entity.crystalfrog.FrogCageData.summary(cage).infertile(), "Both world and cage must report infertility");
        test.assertTrue(!loaded.talents().canStartSpit(), "Echo must disable scatter slime without deleting it");
        var legacy = save(test, frog);
        legacy.remove("CrystalFrogVariant");
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), legacy));
        test.assertTrue(loaded.variant() == com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogVariant.NORMAL, "Old saves remain normal frogs");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void zombieConversionKeepsOwnerAndChangesFood(GameTestHelper test) {
        var frog = frog(test, CrystalFrogTalent.CRYSTAL_SHELL, new Vec3(3, 1, 3));
        var owner = owner(test);
        frog.tame(owner);
        var zombie = test.spawn(EntityTypes.ZOMBIE, new Vec3(6, 1, 6));
        zombie.setNoAi(true);
        // Select a known successful roll without relying on a particular RandomSource implementation.
        long seed = 0;
        while (true) {
            frog.getRandom().setSeed(seed);
            if (frog.getRandom().nextFloat() < 0.5F) break;
            seed++;
        }
        frog.getRandom().setSeed(seed);
        frog.setHealth(0);
        frog.die(test.getLevel().damageSources().mobAttack(zombie));
        test.assertTrue(frog.isAlive() && frog.getHealth() == 20 && frog.isOwnedBy(owner)
                && frog.variant() == com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogVariant.ZOMBIE,
                "Successful lethal conversion must restore health without losing the pet");
        frog.setHealth(10);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.ignilumen.uncannyencounters.item.ModItems.ACTIVATED_AMETHYST, 2));
        frog.mobInteract(owner, InteractionHand.MAIN_HAND);
        test.assertTrue(frog.getHealth() == 10 && owner.getMainHandItem().getCount() == 2, "Amethyst must have no effect");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 2));
        frog.mobInteract(owner, InteractionHand.MAIN_HAND);
        test.assertTrue(frog.getHealth() == 14 && owner.getMainHandItem().getCount() == 1, "Rotten flesh heals four");
        frog.setHealth(20);
        frog.mobInteract(owner, InteractionHand.MAIN_HAND);
        test.assertTrue(owner.getMainHandItem().getCount() == 1, "Full health must not consume food");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void sonicPassesWallsButStopsAtDuelFloor(GameTestHelper test) {
        arena(test);
        var a = frog(test, CrystalFrogTalent.NONE, new Vec3(3.5, 1, 3.5));
        var b = frog(test, CrystalFrogTalent.NONE, new Vec3(3.5, 1, 6.5));
        a.convertVariant(com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogVariant.ECHO);
        duel(test, a, b);
        for (int y = 1; y <= 3; y++) test.setBlock(new BlockPos(3, y, 5), Blocks.STONE);
        b.getAttribute(Attributes.ARMOR).setBaseValue(30);
        b.setHealth(6);
        test.assertTrue(a.variantCombat().canUse(), "Sonic can start through a wall without a ranged talent");
        a.variantCombat().start();
        for (int tick = 0; tick < 20; tick++) a.variantCombat().tick();
        test.assertTrue(b.getHealth() == 5 && b.isAlive() && !a.isDueling(), "Sonic bypasses armor and stops the match at 25 percent");
        float health = b.getHealth();
        a.variantCombat().tick();
        test.assertTrue(b.getHealth() == health, "Completed casts cannot damage again");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void enderAttackUsesSafeLandingAndCooldown(GameTestHelper test) {
        arena(test);
        var frog = frog(test, CrystalFrogTalent.CRYSTAL_ECHO, new Vec3(2.5, 1, 2.5));
        var target = dailyTarget(test, frog, new Vec3(6.5, 1, 6.5));
        frog.convertVariant(com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogVariant.ENDER);
        Vec3 start = frog.position();
        float health = target.getHealth();
        test.assertTrue(frog.variantCombat().canUse(), "Ender attack can start during owner support");
        frog.variantCombat().start();
        frog.variantCombat().tick();
        test.assertTrue(frog.distanceToSqr(start) > 1 && target.getHealth() < health,
                "Ender frog teleports near its target and deals ordinary melee damage");
        test.assertTrue(test.getLevel().noCollision(frog) && !frog.variantCombat().canUse(),
                "Landing must be unobstructed and teleport must enter cooldown");
        test.succeed();
    }
}
