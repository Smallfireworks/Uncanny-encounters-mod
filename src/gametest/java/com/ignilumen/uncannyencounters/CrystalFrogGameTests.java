package com.ignilumen.uncannyencounters;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogOwnerSupport;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Integration cases for manual server GameTest runs; compile alone does not execute these. */
public final class CrystalFrogGameTests {
    private CrystalFrog frog(GameTestHelper test) {
        CrystalFrog frog = test.spawn(ModEntities.CRYSTAL_FROG, new Vec3(3.5, 1, 3.5));
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
    public void slowHitsChooseReactionFromRemainingHealthIncludingOwner(GameTestHelper test) {
        CrystalFrog frog = frog(test);
        Player owner = attacker(test, Vec3.ZERO);
        frog.tame(owner);
        frog.setHealth(14);
        frog.hurtServer(test.getLevel(), test.getLevel().damageSources().playerAttack(owner), 4);
        test.assertTrue(frog.getHealth() == 10 && frog.wantsRetaliation() && frog.getTarget() == owner,
                "At exactly half health the frog must be able to retaliate against its owner");
        frog.mobInteract(owner, InteractionHand.MAIN_HAND);
        test.assertTrue(frog.isOrderedToSit() && !frog.wantsRetaliation() && !frog.isFrightened(),
                "Owner's empty-hand sit command must end the current response");
        CrystalFrog weak = frog(test);
        weak.setHealth(12);
        weak.hurtServer(test.getLevel(), test.getLevel().damageSources().playerAttack(owner), 4);
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
}
