package com.ignilumen.uncannyencounters;

import com.ignilumen.uncannyencounters.entity.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Runs against the actual 26.3 server, including entity ticks, collisions and fall damage. */
public final class CaveAnglerGameTests {
    private record Scene(CaveAngler angler, Pig prey, double floor) {}

    private Scene scene(GameTestHelper test) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            test.setBlock(x, 0, z, Blocks.STONE);
            test.setBlock(x, 9, z, Blocks.STONE);
        }
        CaveAngler angler = test.spawn(ModEntities.CAVE_ANGLER, new Vec3(3.5, 6, 3.5));
        Pig pig = test.spawn(EntityTypes.PIG, new Vec3(3.5, 1, 3.8125));
        // NoAI also disables normal movement simulation in 26.3. Keep physics active.
        pig.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        angler.setLastHurtByMob(pig);
        return new Scene(angler, pig, test.absoluteVec(new Vec3(0, 1, 0)).y);
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 60, padding = 25)
    public void suspendsOneBlockAboveFloor(GameTestHelper test) {
        Scene scene = scene(test);
        test.runAtTickTime(25, () -> {
            test.assertTrue(scene.angler.captive() == scene.prey, "The hostile mob must be caught");
            test.assertTrue(Math.abs(scene.prey.getY() - scene.floor - 1) < 0.2, "Feet must be one block above the floor");
            test.assertTrue(scene.angler.tongue() != null && scene.angler.tongue().isPickable(), "Tongue must have a hitbox");
            test.assertTrue(!scene.prey.isNoGravity(), "Capture must not mutate the victim's gravity flag");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 370, padding = 25)
    public void severingReleasesAndRecovers(GameTestHelper test) {
        Scene scene = scene(test);
        test.runAtTickTime(25, () -> {
            var tongue = scene.angler.tongue();
            test.assertTrue(tongue != null, "Tongue must exist before damage");
            tongue.hurtServer(test.getLevel(), test.getLevel().damageSources().generic(), 1);
            test.assertTrue(scene.angler.captive() == null && tongue.isRetracting() && tongue.isSevered()
                    && !tongue.isPickable(), "Damage must release victim and start non-interactive red retraction");
            test.assertTrue(scene.angler.recoveryTicks() == 300, "Severing must start a 15-second cooldown");
            test.runAfterDelay(AnglerTongue.RETRACT_TICKS + 1,
                    () -> test.assertTrue(tongue.isRemoved(), "Tongue must be removed after retraction"));
        });
        test.runAtTickTime(300, () -> test.assertTrue(scene.angler.captive() == null, "Cannot recapture before recovery"));
        // Vanilla clears recent-attacker memory before 15 seconds; provoke it again.
        test.runAtTickTime(330, () -> scene.angler.setLastHurtByMob(scene.prey));
        test.runAtTickTime(340, () -> {
            test.assertTrue(scene.angler.captive() == scene.prey, "Capture must work again after recovery");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 220, padding = 25)
    public void noHelpersCausesRealFallDamage(GameTestHelper test) {
        Scene scene = scene(test);
        float health = scene.prey.getHealth();
        test.runAtTickTime(150, () -> {
            test.assertTrue(scene.prey.getHealth() < health, "Must cause natural fall damage; health=" + scene.prey.getHealth()
                    + ", y=" + (scene.prey.getY() - scene.floor) + ", phase=" + scene.angler.phase());
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 70, padding = 25)
    public void lostCeilingReleasesPrey(GameTestHelper test) {
        Scene scene = scene(test);
        test.runAtTickTime(25, () -> {
            test.assertTrue(scene.angler.captive() != null, "Must capture before support removal");
            test.setBlock(3, 9, 3, Blocks.AIR);
        });
        test.runAtTickTime(30, () -> {
            test.assertTrue(scene.angler.captive() == null && scene.angler.tongue() == null, "Lost anchor must release and clean up");
            test.assertTrue(!scene.angler.isNoGravity(), "Unsupported angler must fall");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 150, padding = 25)
    public void existingEnemyRespondsToShriek(GameTestHelper test) {
        Scene scene = scene(test);
        scene.prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        scene.prey.setHealth(100);
        var zombie = test.spawn(EntityTypes.ZOMBIE, new Vec3(1.5, 1, 3.8125));
        zombie.setLastHurtByMob(scene.prey);
        zombie.setTarget(scene.prey);
        test.runAtTickTime(100, () -> {
            test.assertTrue(scene.angler.phase() == CaveAngler.HOLDING, "Responding helper must prevent early hoisting");
            test.assertTrue(zombie.getTarget() == scene.prey && scene.prey.getHealth() < 100,
                    "A responding monster must actually attack the suspended victim");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 70, padding = 25)
    public void obstructionBreaksCapture(GameTestHelper test) {
        Scene scene = scene(test);
        test.runAtTickTime(25, () -> test.setBlock(3, 4, 3, Blocks.STONE));
        test.runAtTickTime(30, () -> {
            test.assertTrue(scene.angler.captive() == null, "Tongue cannot pass through newly placed solid blocks");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 70, padding = 25)
    public void removalCleansUpTongue(GameTestHelper test) {
        Scene scene = scene(test);
        test.runAtTickTime(25, () -> {
            var tongue = scene.angler.tongue();
            test.assertTrue(tongue != null, "Must capture before removal");
            scene.angler.discard();
            test.assertTrue(tongue.isRemoved() && scene.angler.captive() == null, "Removing owner must clean up immediately");
            test.assertTrue(!scene.prey.isNoGravity(), "Released victim keeps normal gravity");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 30, padding = 25)
    public void preyRulesExcludeNeutralAndCreative(GameTestHelper test) {
        Scene scene = scene(test);
        Pig neutral = test.spawn(EntityTypes.PIG, new BlockPos(6, 1, 6));
        test.assertTrue(!scene.angler.considersPrey(neutral), "Neutral animals are not prey");
        test.assertTrue(scene.angler.considersPrey(scene.prey), "A recent attacker is prey");
        var creative = test.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        test.assertTrue(!scene.angler.considersPrey(creative), "Creative players must be excluded");
        test.assertTrue(scene.angler.considersPrey(test.makeMockPlayer(GameType.SURVIVAL)), "Survival players are prey");
        test.succeed();
    }
}
