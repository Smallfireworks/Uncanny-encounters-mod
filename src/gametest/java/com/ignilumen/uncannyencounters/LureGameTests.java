package com.ignilumen.uncannyencounters;

import com.ignilumen.uncannyencounters.block.ModBlocks;
import com.ignilumen.uncannyencounters.entity.lightmoth.LureBait;
import com.ignilumen.uncannyencounters.entity.lightmoth.LuredMob;
import com.ignilumen.uncannyencounters.entity.lightmoth.MonsterLures;
import com.ignilumen.uncannyencounters.item.ModItems;
import com.ignilumen.uncannyencounters.mixin.CubeMoveControlAccessor;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Manual server GameTests: real recipe data, hand selection and placed-lamp invalidation. */
public final class LureGameTests {
    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void everyBaitCraftsOnceInBothGridSizes(GameTestHelper test) {
        List<CraftingRecipe> recipes = new ArrayList<>();
        for (LureBait bait : LureBait.values()) {
            recipes.add((CraftingRecipe) test.getLevel().registryAccess().lookupOrThrow(Registries.RECIPE)
                    .getOrThrow(ResourceKey.create(Registries.RECIPE, UncannyEncounters.id(bait.blockId()))).value());
        }
        for (LureBait bait : LureBait.values()) {
            ItemStack material = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(bait.id())));
            CraftingRecipe recipe = recipes.get(bait.ordinal());
            for (int width : new int[]{2, 3}) {
                List<ItemStack> slots = new ArrayList<>(Collections.nCopies(width * width, ItemStack.EMPTY));
                slots.set(0, material);
                slots.set(slots.size() - 1, new ItemStack(ModItems.ENHANCED_MOTH_LURE));
                CraftingInput input = CraftingInput.of(width, width, slots);
                test.assertTrue(recipe.matches(input, test.getLevel()), "Bait recipe must work in either grid: " + bait);
                ItemStack crafted = recipe.assemble(input);
                test.assertTrue(crafted.getCount() == 1 && crafted.is(ModItems.BAITED_LURES.get(bait)), "Wrong bait output");
                for (LureBait second : LureBait.values()) {
                    CraftingInput repeated = CraftingInput.of(2, 1, List.of(crafted,
                            new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(second.id())))));
                    test.assertTrue(recipes.stream().noneMatch(r -> r.matches(repeated, test.getLevel())),
                            "An already baited lamp must reject all further bait recipes");
                }
            }
        }
        test.succeed();
    }

    private Player holder(GameTestHelper test) {
        return holder(test, GameType.CREATIVE);
    }

    private Player holder(GameTestHelper test, GameType mode) {
        Player player = new Player(test.getLevel(), new GameProfile(UUID.randomUUID(), "lure-test")) {
            @Override public GameType gameMode() { return mode; }
            @Override public boolean isClientAuthoritative() { return false; }
        };
        player.snapTo(test.absoluteVec(new Vec3(5, 1, 5)), 0, 0);
        return player;
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void bothHandsMatchIndependentlyAndReleaseWhenRemoved(GameTestHelper test) {
        var zombie = test.spawn(EntityTypes.ZOMBIE, new Vec3(3, 1, 3));
        var skeleton = test.spawn(EntityTypes.SKELETON, new Vec3(3, 1, 4));
        Player player = holder(test);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BAITED_LURES.get(LureBait.BONE)));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.BAITED_LURES.get(LureBait.ROTTEN_FLESH)));
        var flesh = new MonsterLures.Source(LureBait.ROTTEN_FLESH, player, null);
        var bone = new MonsterLures.Source(LureBait.BONE, player, null);
        test.assertTrue(flesh.valid(zombie) && !bone.valid(zombie), "Zombie must recognize only its offhand bait");
        test.assertTrue(bone.valid(skeleton) && !flesh.valid(skeleton), "Skeleton must recognize only its main-hand bait");
        ((LuredMob) zombie).uncannyEncounters$offer(flesh);
        test.assertTrue(MonsterLures.destination(zombie) != null, "Mob mixin must accept the valid source");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        test.assertTrue(MonsterLures.destination(zombie) == null && bone.valid(skeleton), "Removing one lamp must release only its family");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BAITED_LURES.get(LureBait.ROTTEN_FLESH)));
        test.assertTrue(flesh.valid(zombie), "The same bait must also work in the main hand");
        player.setPos(zombie.position().add(25, 0, 0));
        test.assertTrue(!flesh.valid(zombie), "Bait must not work beyond 24 blocks");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void placedBaitWorksHangingAndWaterloggedThenReleases(GameTestHelper test) {
        var enderman = test.spawn(EntityTypes.ENDERMAN, new Vec3(3, 1, 3));
        BlockPos pos = test.absolutePos(new BlockPos(5, 2, 5));
        test.getLevel().setBlock(pos.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var state = ModBlocks.BAITED_LURES.get(LureBait.ENDER_PEARL).defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true).setValue(BlockStateProperties.WATERLOGGED, true);
        test.getLevel().setBlock(pos, state, Block.UPDATE_ALL);
        MonsterLures.offerBlock(test.getLevel(), pos, LureBait.ENDER_PEARL);
        test.assertTrue(MonsterLures.destination(enderman) != null, "Placed bait must attract its family");
        test.assertTrue(enderman.getTarget() == null, "Attraction must not manufacture hostility in neutral mobs");
        test.assertTrue(LureBait.from(new ItemStack(state.getBlock())) == LureBait.ENDER_PEARL,
                "The block item must preserve the bait family");
        test.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        test.assertTrue(MonsterLures.destination(enderman) == null, "Removing the block must immediately invalidate its source");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void slimeAndMagmaCubeExposeTheirSeparateJumpControl(GameTestHelper test) {
        var slime = test.spawn(EntityTypes.SLIME, new Vec3(3, 1, 3));
        var magma = test.spawn(EntityTypes.MAGMA_CUBE, new Vec3(5, 1, 3));
        test.assertTrue(slime.getMoveControl() instanceof CubeMoveControlAccessor
                && magma.getMoveControl() instanceof CubeMoveControlAccessor, "26.3 cube movement hook must apply to both species");
        test.assertTrue(LureBait.SLIME_BALL.attracts(slime.getType()) && !LureBait.SLIME_BALL.attracts(magma.getType()),
                "Slime bait must not also attract magma cubes through inheritance");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void lurePacifiesPlayersButDamageOverridesEveryLampUntilAttackerLeaves(GameTestHelper test) {
        var zombie = test.spawn(EntityTypes.ZOMBIE, new Vec3(3, 1, 3));
        Player lanternHolder = holder(test, GameType.SURVIVAL);
        Player attacker = holder(test, GameType.SURVIVAL);
        zombie.setTarget(lanternHolder);
        lanternHolder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BAITED_LURES.get(LureBait.ROTTEN_FLESH)));
        var source = new MonsterLures.Source(LureBait.ROTTEN_FLESH, lanternHolder, null);
        ((LuredMob) zombie).uncannyEncounters$offer(source);
        test.assertTrue(!zombie.canAttack(lanternHolder) && !zombie.canAttack(attacker) && zombie.getTarget() == null,
                "A valid lure must stop both existing and new attacks on nearby players");
        zombie.hurtServer(test.getLevel(), test.getLevel().damageSources().playerAttack(attacker), 1);
        test.assertTrue(zombie.getTarget() == attacker && zombie.canAttack(attacker), "Taking player damage must immediately provoke retaliation");
        test.assertTrue(!zombie.canAttack(lanternHolder) && MonsterLures.destination(zombie) == null,
                "Retaliation must pursue the attacker and ignore the lamp, without attacking bystanders");
        lanternHolder.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        lanternHolder.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.BAITED_LURES.get(LureBait.ROTTEN_FLESH)));
        ((LuredMob) zombie).uncannyEncounters$offer(source);
        test.assertTrue(MonsterLures.destination(zombie) == null && zombie.getTarget() == attacker,
                "Reoffering a lamp in another hand must not erase retaliation");
        attacker.setPos(zombie.position().add(65, 0, 0));
        test.assertTrue(MonsterLures.destination(zombie) != null && !zombie.canAttack(lanternHolder),
                "After the attacker leaves 64 blocks, an existing lamp may pacify the mob again");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void pacificationStopsContactDamageAndCachedRangedTargets(GameTestHelper test) {
        Player player = holder(test, GameType.SURVIVAL);
        var slime = test.spawn(EntityTypes.SLIME, new Vec3(5, 1, 5));
        slime.setSize(2, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BAITED_LURES.get(LureBait.SLIME_BALL)));
        ((LuredMob) slime).uncannyEncounters$offer(new MonsterLures.Source(LureBait.SLIME_BALL, player, null));
        float health = player.getHealth();
        slime.playerTouch(player);
        test.assertTrue(player.getHealth() == health, "A pacified slime must not deal contact damage");
        slime.hurtServer(test.getLevel(), test.getLevel().damageSources().playerAttack(player), 1);
        slime.playerTouch(player);
        test.assertTrue(player.getHealth() < health, "Contact damage must resume against the player who attacked the slime");

        var breeze = test.spawn(EntityTypes.BREEZE, new Vec3(3, 1, 3));
        breeze.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BAITED_LURES.get(LureBait.BREEZE_ROD)));
        ((LuredMob) breeze).uncannyEncounters$offer(new MonsterLures.Source(LureBait.BREEZE_ROD, player, null));
        test.assertTrue(!breeze.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && !breeze.canAttack(player),
                "Pacification must clear a Breeze's cached attack memory");
        breeze.hurtServer(test.getLevel(), test.getLevel().damageSources().playerAttack(player), 1);
        test.assertTrue(breeze.getTarget() == player && MonsterLures.destination(breeze) == null,
                "A provoked Breeze must regain its brain attack target and ignore the lamp");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void lureDefusesAnUnprovokedCreeperAndEnvironmentalDamageDoesNotProvoke(GameTestHelper test) {
        Player player = holder(test, GameType.SURVIVAL);
        var creeper = test.spawn(EntityTypes.CREEPER, new Vec3(3, 1, 3));
        creeper.setTarget(player);
        creeper.setSwellDir(1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BAITED_LURES.get(LureBait.GUNPOWDER)));
        ((LuredMob) creeper).uncannyEncounters$offer(new MonsterLures.Source(LureBait.GUNPOWDER, player, null));
        test.assertTrue(creeper.getSwellDir() == -1 && !creeper.canAttack(player), "A calm creeper must stop swelling");
        creeper.hurtServer(test.getLevel(), test.getLevel().damageSources().fall(), 1);
        test.assertTrue(MonsterLures.destination(creeper) != null && !creeper.canAttack(player),
                "Environmental damage must not manufacture a player attacker");
        test.succeed();
    }
}
