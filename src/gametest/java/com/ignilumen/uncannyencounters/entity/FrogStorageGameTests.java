package com.ignilumen.uncannyencounters.entity;

import com.ignilumen.uncannyencounters.block.FrogBreedingBoxBlockEntity;
import com.ignilumen.uncannyencounters.block.ModBlocks;
import com.ignilumen.uncannyencounters.entity.crystalfrog.*;
import com.ignilumen.uncannyencounters.item.FrogCageItem;
import com.ignilumen.uncannyencounters.item.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import static com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalent.*;

/** Manual server integration tests for capture identity, inventory exchange, box transactions and growth. */
public final class FrogStorageGameTests {
    private Player player(GameTestHelper test) {
        Player player = new Player(test.getLevel(), new GameProfile(UUID.randomUUID(), "frog-storage-test")) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
            @Override public boolean isClientAuthoritative() { return false; }
        };
        player.snapTo(test.absoluteVec(new Vec3(4, 1, 4)), 0, 0);
        return player;
    }
    private void floor(GameTestHelper test) {
        for (int x = 1; x <= 8; x++) for (int z = 1; z <= 8; z++) {
            test.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 3; y++) test.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }
    private CrystalFrog frog(GameTestHelper test, Player player, CrystalFrogTalent... talents) {
        var frog = test.spawn(ModEntities.CRYSTAL_FROG, new Vec3(3, 1, 3));
        frog.configureTraits(40, 6, CrystalFrogStyle.SKIRMISHER, List.of(talents));
        frog.tame(player);
        return frog;
    }
    private ItemStack store(GameTestHelper test, CrystalFrog frog) {
        ItemStack item = FrogCageData.pack(test.getLevel(), frog);
        frog.discard();
        return item;
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void cageRoundTripPreservesKingIdentityHealthNameOwnerAndCooldown(GameTestHelper test) {
        floor(test);
        Player owner = player(test);
        CrystalFrog king = frog(test, owner, FROG_KING, DOUBLE_ECHO);
        king.setCustomName(Component.literal("紫晶"));
        king.setHealth(55);
        king.setAge(6000);
        UUID identity = king.getUUID();
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FROG_CAGE));
        FrogCageItem.capture(owner, king, InteractionHand.MAIN_HAND);
        ItemStack cage = owner.getMainHandItem();
        test.assertTrue(king.isRemoved() && FrogCageData.filled(cage), "Capture must exchange the live frog for its filled cage");
        for (int i = 0; i < 20; i++) cage.getItem().inventoryTick(cage, test.getLevel(), owner, EquipmentSlot.MAINHAND);
        test.assertTrue(FrogCageData.summary(cage).age() == 6000, "Ordinary inventory must pause the breeding timer");
        test.runAfterDelay(1, () -> {
            BlockPos pos = test.absolutePos(new BlockPos(5, 0, 6));
            UseOnContext context = new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            cage.getItem().useOn(context);
            test.assertTrue(owner.getMainHandItem().is(ModItems.FROG_CAGE), "Release must return exactly one empty cage");
            var released = test.getLevel().getEntity(identity);
            test.assertTrue(released instanceof CrystalFrog, "Release must preserve the original UUID");
            CrystalFrog frog = (CrystalFrog)released;
            test.assertTrue(frog.isOwnedBy(owner) && frog.getCustomName().getString().equals("紫晶") && frog.getHealth() == 55
                    && frog.getMaxHealth() == 80 && frog.getAttributeValue(Attributes.ATTACK_DAMAGE) == 12
                    && frog.talents().all().equals(List.of(FROG_KING, DOUBLE_ECHO)) && frog.getAge() > 5950,
                    "Packing must not heal, reroll, multiply king bonuses or reset cooldown");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void fullInventoryCannotDestroyACapturedFrog(GameTestHelper test) {
        Player owner = player(test);
        CrystalFrog frog = frog(test, owner);
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++)
            owner.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FROG_CAGE, 2));
        FrogCageItem.capture(owner, frog, InteractionHand.MAIN_HAND);
        test.assertTrue(!frog.isRemoved() && owner.getMainHandItem().is(ModItems.FROG_CAGE) && owner.getMainHandItem().getCount() == 2,
                "Failed capture must preserve both the frog and the empty cages");
        Player stranger = player(test);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FROG_CAGE));
        FrogCageItem.capture(stranger, frog, InteractionHand.MAIN_HAND);
        test.assertTrue(!frog.isRemoved() && stranger.getMainHandItem().is(ModItems.FROG_CAGE), "Another player cannot capture this frog");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void boxBreedsOnceTicksTimersAndDropsItsContents(GameTestHelper test) {
        floor(test);
        Player owner = player(test);
        BlockPos relative = new BlockPos(4, 1, 2), pos = test.absolutePos(relative);
        test.setBlock(relative, ModBlocks.FROG_BREEDING_BOX);
        FrogBreedingBoxBlockEntity box = (FrogBreedingBoxBlockEntity)test.getLevel().getBlockEntity(pos);
        box.setItem(0, store(test, frog(test, owner, CRYSTAL_NURSERY)));
        box.setItem(1, store(test, frog(test, owner, CRYSTAL_ECHO)));
        box.setItem(2, new ItemStack(Items.AMETHYST_SHARD, 4));
        box.setItem(3, new ItemStack(ModItems.FROG_CAGE, 2));
        test.assertTrue(box.breed(owner), "Ready parents, shards and an empty cage must produce one baby");
        test.assertTrue(FrogCageData.summary(box.getItem(0)).age() == 6000 && FrogCageData.summary(box.getItem(1)).age() == 6000
                && FrogCageData.summary(box.getItem(4)).age() == -24000 && box.getItem(2).getCount() == 2 && box.getItem(3).getCount() == 1,
                "One birth must set both cooldowns and consume exactly two shards and one empty cage");
        test.assertTrue(!box.breed(owner) && box.getItem(5).isEmpty(), "Repeated button packets must not bypass cooldown");
        for (int i = 0; i < 20; i++) box.tickStoredFrogs();
        test.assertTrue(FrogCageData.summary(box.getItem(0)).age() == 5980 && FrogCageData.summary(box.getItem(4)).age() == -23980,
                "Loaded boxes must advance both parent cooldown and baby growth");
        test.assertTrue(FrogCageData.parentComparison(box.getItem(4)) != null, "The child's tooltip must retain the parental stat record without assigning numbers");
        for (int parent = 0; parent < 2; parent++) {
            CrystalFrog frog = FrogCageData.unpack(test.getLevel(), box.getItem(parent));
            frog.setAge(0);
            box.setItem(parent, FrogCageData.pack(test.getLevel(), frog));
        }
        for (int output = 5; output < 8; output++) box.setItem(output, box.getItem(4).copy());
        test.assertTrue(!box.breed(owner) && box.getItem(2).getCount() == 2 && box.getItem(3).getCount() == 1,
                "Full offspring slots must stop births without consuming materials");
        test.getLevel().destroyBlock(pos, true);
        long cages = test.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
                .filter(item -> item.getItem().is(ModItems.CAGED_CRYSTAL_FROG)).count();
        test.assertTrue(cages == 6, "Breaking the box must drop its six stored frogs exactly once");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void babiesHopLowerWithoutLosingMostOfTheirStepUpImpulse(GameTestHelper test) {
        Player owner = player(test);
        CrystalFrog adult = frog(test, owner), baby = frog(test, owner);
        baby.setBaby(true);
        adult.hop(0.2, 0, false);
        baby.hop(0.2, 0, false);
        test.assertTrue(Math.abs(baby.getDeltaMovement().y / adult.getDeltaMovement().y - 0.9) < 0.001,
                "Baby ordinary hop impulse must be ten percent lower");
        adult.setDeltaMovement(Vec3.ZERO);
        baby.setDeltaMovement(Vec3.ZERO);
        adult.hop(0.2, 0, true);
        baby.hop(0.2, 0, true);
        test.assertTrue(Math.abs(baby.getDeltaMovement().y / adult.getDeltaMovement().y - 0.95) < 0.001,
                "Uphill jumps must retain enough impulse for ordinary terrain");
        test.succeed();
    }
}
