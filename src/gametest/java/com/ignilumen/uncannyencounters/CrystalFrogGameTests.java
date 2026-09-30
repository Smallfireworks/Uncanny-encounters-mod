package com.ignilumen.uncannyencounters;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogOwnerSupport;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogDuels;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogDuelCombat;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogStyle;
import com.ignilumen.uncannyencounters.item.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Integration cases for manual server GameTest runs; compile alone does not execute these. */
public final class CrystalFrogGameTests {
    private CrystalFrog frog(GameTestHelper test) {
        CrystalFrog frog = test.spawn(ModEntities.CRYSTAL_FROG, new Vec3(3.5, 1, 3.5));
        frog.finalizeSpawn(test.getLevel(), test.getLevel().getCurrentDifficultyAt(frog.blockPosition()), EntitySpawnReason.COMMAND, null);
        // Existing combat cases use fixed fixtures; separate cases exercise generated traits.
        frog.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
        frog.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3);
        frog.setHealth(20);
        CompoundTag noTalent = new CompoundTag();
        noTalent.putString("CrystalFrogTalent", "none");
        frog.talents().load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), noTalent), true);
        frog.setNoAi(true);
        return frog;
    }

    private Player attacker(GameTestHelper test, Vec3 motion) {
        Player player = new Player(test.getLevel(), new GameProfile(UUID.randomUUID(), "crystal-frog-test")) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
            @Override public boolean isClientAuthoritative() { return false; }
            @Override public Vec3 getKnownSpeed() { return motion; }
        };
        player.snapTo(test.absoluteVec(new Vec3(3.5, 1, 5.5)), 0, 0);
        return player;
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void sprintReflectsFullDamageWithoutHurtingFrog(GameTestHelper test) {
        CrystalFrog frog = frog(test);
        Player player = attacker(test, new Vec3(0.28, 0, 0));
        float health = player.getHealth();
        boolean hit = frog.hurtServer(test.getLevel(), test.getLevel().damageSources().playerAttack(player), 6);
        test.assertTrue(!hit && frog.getHealth() == 20, "Fast hit must leave frog completely unharmed, even with NoAI");
        test.assertTrue(Math.abs(player.getHealth() - (health - 6)) < 0.001F, "Reflection must preserve raw damage regardless of difficulty");
        test.assertTrue(frog.wantsRetaliation() && frog.getTarget() == player, "Reflection must provoke retaliation");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void fallingMaceReflectsAndArmorStillProtectsAttacker(GameTestHelper test) {
        CrystalFrog frog = frog(test);
        Player player = attacker(test, new Vec3(0, -1, 0));
        player.getAttribute(Attributes.ARMOR).setBaseValue(20);
        float health = player.getHealth();
        frog.hurtServer(test.getLevel(), test.getLevel().damageSources().mace(player), 8);
        test.assertTrue(frog.getHealth() == 20, "Vertical mace hit must not damage frog");
        float damage = health - player.getHealth();
        test.assertTrue(damage > 0 && damage < 8, "Reflection must respect attacker's armor");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void slowHitsChooseReactionFromRemainingHealth(GameTestHelper test) {
        CrystalFrog frog = frog(test);
        Player player = attacker(test, Vec3.ZERO);
        frog.setHealth(14);
        frog.hurtServer(test.getLevel(), test.getLevel().damageSources().playerAttack(player), 4);
        test.assertTrue(frog.getHealth() == 10 && frog.wantsRetaliation() && frog.getTarget() == player,
                "At exactly half health the frog must retaliate against a non-owner");
        CrystalFrog weak = frog(test);
        weak.setHealth(12);
        weak.hurtServer(test.getLevel(), test.getLevel().damageSources().playerAttack(player), 4);
        test.assertTrue(weak.getHealth() == 8 && weak.isFrightened() && !weak.wantsRetaliation(),
                "A hit crossing below half health must select escape");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void fastProjectileOwnerDoesNotTurnArrowIntoReflectedMelee(GameTestHelper test) {
        CrystalFrog frog = frog(test);
        Player player = attacker(test, new Vec3(2, -2, 0));
        DamageSource arrow = new DamageSource(test.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(DamageTypes.ARROW), null, player);
        float health = player.getHealth();
        test.assertTrue(frog.hurtServer(test.getLevel(), arrow, 4) && frog.getHealth() == 16, "Arrow damage must remain effective");
        test.assertTrue(player.getHealth() == health, "Projectile owner's motion must not reflect projectile damage");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void ownerCombatTriggersSupportButSittingDoesNotReplayIt(GameTestHelper test) {
        CrystalFrog frog = frog(test);
        Player owner = attacker(test, Vec3.ZERO);
        frog.tame(owner);
        CrystalFrogOwnerSupport support = new CrystalFrogOwnerSupport(frog);
        support.tick();
        var enemy = test.spawn(EntityTypes.PIG, new Vec3(5, 1, 4));
        owner.tickCount = 10;
        owner.setLastHurtMob(enemy);
        support.tick();
        test.assertTrue(frog.getTarget() == enemy && frog.wantsRetaliation(), "Must assist an owner's new attack");
        frog.mobInteract(owner, InteractionHand.MAIN_HAND);
        owner.tickCount = 11;
        owner.setLastHurtMob(enemy);
        support.tick();
        test.assertTrue(frog.getTarget() == null, "Sitting frog must not join combat");
        frog.mobInteract(owner, InteractionHand.MAIN_HAND);
        support.tick();
        test.assertTrue(frog.getTarget() == null, "Standing must not replay combat observed while sitting");
        owner.tickCount = 12;
        owner.hurtServer(test.getLevel(), test.getLevel().damageSources().mobAttack(enemy), 2);
        support.tick();
        test.assertTrue(frog.getTarget() == enemy, "Must protect an owner who is attacked");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void supportRejectsLowHealthOwnerAndFriendlyPets(GameTestHelper test) {
        CrystalFrog frog = frog(test);
        Player owner = attacker(test, Vec3.ZERO);
        frog.tame(owner);
        var enemy = test.spawn(EntityTypes.PIG, new Vec3(5, 1, 4));
        frog.setHealth(9);
        test.assertTrue(!frog.assistOwner(enemy), "Low-health frog must not join owner's combat");
        frog.setHealth(20);
        CrystalFrog friend = frog(test);
        friend.tame(owner);
        test.assertTrue(!frog.assistOwner(friend) && !frog.assistOwner(owner) && !frog.assistOwner(frog),
                "Support must not attack owner, itself or another pet of the same owner");
        test.assertTrue(frog.assistOwner(enemy), "Healthy following frog must accept owner's enemy");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void ownerNeverBecomesAttackTargetButStillReceivesFastHitReflection(GameTestHelper test) {
        CrystalFrog frog = frog(test);
        Player owner = attacker(test, new Vec3(0.3, 0, 0));
        frog.tame(owner);
        float ownerHealth = owner.getHealth();
        frog.hurtServer(test.getLevel(), test.getLevel().damageSources().playerAttack(owner), 4);
        test.assertTrue(frog.getHealth() == 20 && owner.getHealth() == ownerHealth - 4,
                "Taming must preserve reflection against the owner");
        test.assertTrue(frog.getTarget() == null && !frog.canAttack(owner) && !frog.wantsRetaliation() && frog.isFrightened(),
                "A reflected owner hit must cause escape, never active retaliation");
        CrystalFrog other = frog(test);
        Player slowOwner = attacker(test, Vec3.ZERO);
        other.tame(slowOwner);
        other.hurtServer(test.getLevel(), test.getLevel().damageSources().playerAttack(slowOwner), 4);
        test.assertTrue(other.getHealth() == 16 && other.getTarget() == null && other.isFrightened(),
                "A slow owner hit must damage the pet but only make it flee");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void anyoneCanHealATamedFrogWithoutOverhealingOrConsumingAtFullHealth(GameTestHelper test) {
        CrystalFrog frog = frog(test);
        frog.tame(attacker(test, Vec3.ZERO));
        Player visitor = attacker(test, Vec3.ZERO);
        ItemStack food = new ItemStack(ModItems.ACTIVATED_AMETHYST, 3);
        visitor.setItemInHand(InteractionHand.OFF_HAND, food);
        frog.setHealth(15);
        frog.mobInteract(visitor, InteractionHand.OFF_HAND);
        test.assertTrue(frog.getHealth() == 19 && food.getCount() == 2, "Visitor's offhand food must heal four points and consume one item");
        frog.mobInteract(visitor, InteractionHand.OFF_HAND);
        frog.mobInteract(visitor, InteractionHand.OFF_HAND);
        test.assertTrue(frog.getHealth() == 20 && food.getCount() == 1, "Healing must cap at max health and full health must consume nothing");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void randomTraitsSurviveReloadAndOldSavesKeepTheirStats(GameTestHelper test) {
        CrystalFrog frog = test.spawn(ModEntities.CRYSTAL_FROG, new Vec3(3, 1, 3));
        frog.finalizeSpawn(test.getLevel(), test.getLevel().getCurrentDifficultyAt(frog.blockPosition()), EntitySpawnReason.COMMAND, null);
        float max = frog.getMaxHealth();
        double attack = frog.getAttributeValue(Attributes.ATTACK_DAMAGE);
        CrystalFrogStyle style = frog.duelStyle();
        test.assertTrue(max >= 16 && max <= 28 && attack >= 2 && attack <= 5, "Generated traits must respect the agreed ranges");
        frog.setHealth(max - 4);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        frog.saveWithoutId(output);
        var saved = output.buildResult();
        CrystalFrog loaded = new CrystalFrog(ModEntities.CRYSTAL_FROG, test.getLevel());
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), saved));
        loaded.finalizeSpawn(test.getLevel(), test.getLevel().getCurrentDifficultyAt(loaded.blockPosition()), EntitySpawnReason.COMMAND, null);
        test.assertTrue(loaded.getMaxHealth() == max && loaded.getAttributeValue(Attributes.ATTACK_DAMAGE) == attack
                && loaded.getHealth() == max - 4, "Reloading must not reroll traits or heal the frog");
        test.assertTrue(loaded.duelStyle() == style, "The fighting style must survive reloading with its stats");
        var legacy = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        frog(test).saveWithoutId(legacy);
        var old = legacy.buildResult();
        old.remove("CrystalFrogTraitsInitialized");
        old.remove("CrystalFrogStyle");
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), old));
        loaded.finalizeSpawn(test.getLevel(), test.getLevel().getCurrentDifficultyAt(loaded.blockPosition()), EntitySpawnReason.COMMAND, null);
        test.assertTrue(loaded.getMaxHealth() == 20 && loaded.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3,
                "An old save must retain the previous fixed stats");
        CrystalFrogStyle legacyStyle = loaded.duelStyle();
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), old));
        test.assertTrue(loaded.duelStyle() == legacyStyle, "Legacy style assignment must be stable even before the next save");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void multiplayerDuelRequiresConsentAndStopsWithoutKillingTheLoser(GameTestHelper test) {
        CrystalFrog first = frog(test), second = frog(test);
        first.setNoAi(false);
        second.setNoAi(false);
        first.snapTo(test.absoluteVec(new Vec3(3, 1, 3)), 0, 0);
        second.snapTo(test.absoluteVec(new Vec3(5, 1, 3)), 0, 0);
        Player a = attacker(test, Vec3.ZERO), b = attacker(test, Vec3.ZERO);
        first.tame(a);
        second.tame(b);
        CrystalFrogDuels.useStick(a, first);
        CrystalFrogDuels.useStick(a, second);
        test.assertTrue(!first.isDueling() && !second.isDueling(), "Another owner's frog must not enter combat before consent");
        CrystalFrogDuels.useStick(b, second);
        test.assertTrue(first.isDueling() && second.isDueling() && first.getTarget() == second && second.getTarget() == first,
                "Accepting the invitation must target only the two participants");
        second.setHealth(8);
        test.assertTrue(second.wantsRetaliation() && !second.isFrightened(), "Sparring must continue below half health");
        ItemStack food = new ItemStack(ModItems.ACTIVATED_AMETHYST, 2);
        b.setItemInHand(InteractionHand.MAIN_HAND, food);
        second.mobInteract(b, InteractionHand.MAIN_HAND);
        test.assertTrue(second.getHealth() == 8 && food.getCount() == 2, "Feeding during a duel must be blocked without consuming food");
        second.hurtServer(test.getLevel(), test.getLevel().damageSources().mobAttack(first), 100);
        test.assertTrue(second.isAlive() && second.getHealth() == 5 && !first.isDueling() && !second.isDueling(),
                "Even an oversized finishing hit must stop at 25 percent health and end the duel");
        test.assertTrue(first.getTarget() == null && second.getTarget() == null && first.isOrderedToSit() && second.isOrderedToSit(),
                "Both participants must stop fighting after the result");
        test.succeed();
    }

    private CrystalFrog styledFrog(GameTestHelper test, CrystalFrogStyle style, Vec3 position) {
        CrystalFrog frog = frog(test);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        frog.saveWithoutId(output);
        var saved = output.buildResult();
        saved.putString("CrystalFrogStyle", style.id());
        frog.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), saved));
        frog.setNoAi(false);
        frog.snapTo(test.absoluteVec(position), 0, 0);
        frog.setOnGround(true);
        return frog;
    }

    private void flatArena(GameTestHelper test) {
        for (int x = 1; x <= 8; x++) for (int z = 1; z <= 8; z++) {
            test.getLevel().setBlock(test.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            for (int y = 1; y <= 3; y++) test.getLevel().setBlock(test.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private void startSparring(GameTestHelper test, CrystalFrog first, CrystalFrog second) {
        Player owner = attacker(test, Vec3.ZERO);
        first.tame(owner);
        second.tame(owner);
        CrystalFrogDuels.useStick(owner, first);
        CrystalFrogDuels.useStick(owner, second);
        test.assertTrue(first.isDuelingWith(second), "The style test requires an owner-approved match");
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void guardingReducesOneFrontalHitButLeavesTheRearExposed(GameTestHelper test) {
        flatArena(test);
        CrystalFrog guard = styledFrog(test, CrystalFrogStyle.GUARD, new Vec3(3.5, 1, 3.5));
        CrystalFrog rival = styledFrog(test, CrystalFrogStyle.POUNCER, new Vec3(3.5, 1, 5.5));
        startSparring(test, guard, rival);
        guard.duelCombat().tick();
        test.assertTrue(guard.duelCombat().action() == CrystalFrogDuelCombat.GUARDING, "A nearby guard must visibly brace");
        guard.hurtServer(test.getLevel(), test.getLevel().damageSources().mobAttack(rival), 4);
        test.assertTrue(Math.abs(guard.getHealth() - 18.6F) < 0.001F
                        && guard.duelCombat().action() == CrystalFrogDuelCombat.COUNTER,
                "The first frontal strike must be reduced and start a counter windup");
        test.assertTrue(guard.duelCombat().defend(rival, 4) == 4, "One guard window must not block repeated strikes");
        guard.cancelDuel("cancelled");
        test.assertTrue(guard.duelCombat().action() == CrystalFrogDuelCombat.NORMAL
                && guard.duelCombat().defend(rival, 4) == 4, "Defence must end immediately with the duel");

        CrystalFrog rearGuard = styledFrog(test, CrystalFrogStyle.GUARD, new Vec3(6.5, 1, 3.5));
        CrystalFrog flanker = styledFrog(test, CrystalFrogStyle.SKIRMISHER, new Vec3(6.5, 1, 5.5));
        startSparring(test, rearGuard, flanker);
        rearGuard.duelCombat().tick();
        flanker.snapTo(test.absoluteVec(new Vec3(6.5, 1, 2.5)), 0, 0);
        rearGuard.hurtServer(test.getLevel(), test.getLevel().damageSources().mobAttack(flanker), 4);
        test.assertTrue(rearGuard.getHealth() == 16 && rearGuard.duelCombat().action() == CrystalFrogDuelCombat.GUARDING,
                "Rear attacks must deal ordinary damage instead of triggering a frontal guard");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void pounceHasAWindupAndItsLaunchSurvivesTheNormalMoveController(GameTestHelper test) {
        flatArena(test);
        CrystalFrog pouncer = styledFrog(test, CrystalFrogStyle.POUNCER, new Vec3(3.5, 1, 3.5));
        CrystalFrog rival = styledFrog(test, CrystalFrogStyle.SKIRMISHER, new Vec3(3.5, 1, 6.5));
        startSparring(test, pouncer, rival);
        pouncer.duelCombat().tick();
        test.assertTrue(pouncer.duelCombat().action() == CrystalFrogDuelCombat.WINDUP && rival.getHealth() == 20,
                "A pounce must telegraph before it can deal damage");
        for (int i = 0; i < CrystalFrogDuelCombat.WINDUP_TICKS; i++) pouncer.duelCombat().tick();
        Vec3 launch = pouncer.getDeltaMovement();
        test.assertTrue(pouncer.duelCombat().action() == CrystalFrogDuelCombat.POUNCE && launch.horizontalDistanceSqr() > 0.1,
                "Finishing the crouch must launch a real hop");
        pouncer.getMoveControl().tick();
        test.assertTrue(pouncer.getDeltaMovement().equals(launch), "The old idle brake must not erase a new pounce's momentum");
        pouncer.cancelDuel("cancelled");
        test.assertTrue(!pouncer.duelCombat().keepsHopMomentum(), "Cancelled fights must release special movement control");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void skirmisherSidestepsAfterStrikingWithoutChangingItsPermanentAttack(GameTestHelper test) {
        flatArena(test);
        CrystalFrog skirmisher = styledFrog(test, CrystalFrogStyle.SKIRMISHER, new Vec3(4.5, 1, 4.5));
        CrystalFrog rival = styledFrog(test, CrystalFrogStyle.POUNCER, new Vec3(4.5, 1, 5.5));
        startSparring(test, skirmisher, rival);
        skirmisher.duelCombat().tick();
        test.assertTrue(rival.getHealth() < 20 && skirmisher.duelCombat().action() == CrystalFrogDuelCombat.EVADE,
                "A skirmisher must follow its hit with a separate sidestep action");
        test.assertTrue(skirmisher.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3, "Strike modifiers must not change rolled attack stats");
        test.succeed();
    }
}
