package com.ignilumen.uncannyencounters;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogOwnerSupport;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogDuels;
import com.ignilumen.uncannyencounters.item.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
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
        var legacy = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        frog(test).saveWithoutId(legacy);
        var old = legacy.buildResult();
        old.remove("CrystalFrogTraitsInitialized");
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), old));
        loaded.finalizeSpawn(test.getLevel(), test.getLevel().getCurrentDifficultyAt(loaded.blockPosition()), EntitySpawnReason.COMMAND, null);
        test.assertTrue(loaded.getMaxHealth() == 20 && loaded.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3,
                "An old save must retain the previous fixed stats");
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
}
