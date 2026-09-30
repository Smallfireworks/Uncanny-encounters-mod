package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** Manual server tests for geode residency; generation itself is verified in new terrain in-game. */
public final class GeodeFrogGameTests {
    private BlockPos site(GameTestHelper test, int x) {
        BlockPos site = test.absolutePos(new BlockPos(x, 2, 3));
        test.getLevel().setBlock(site.below(), Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        test.getLevel().setBlock(site, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        test.getLevel().setBlock(site.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return site;
    }

    private GeodeFrogSpawns.Plan plan(LevelChunk chunk, BlockPos origin) {
        return chunk.getAttachedOrThrow(GeodeFrogSpawns.PLANS).stream().filter(p -> p.origin().equals(origin)).findFirst().orElseThrow();
    }

    private void resetFixture(ServerLevel level, LevelChunk chunk, BlockPos origin) {
        chunk.setAttached(GeodeFrogSpawns.PLANS, chunk.getAttachedOrElse(GeodeFrogSpawns.PLANS, List.of())
                .stream().filter(p -> !p.origin().equals(origin)).toList());
        for (int slot = 0; slot < 2; slot++) {
            var old = level.getEntity(GeodeFrogSpawns.residentId(level.getSeed(), origin, slot));
            if (old != null) old.discard();
        }
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 80)
    public void markedGeodePopulatesOnceAndReloadDoesNotRestock(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        BlockPos origin = site(test, 3), second = site(test, 5);
        LevelChunk chunk = level.getChunkAt(origin);
        resetFixture(level, chunk, origin);
        GeodeFrogSpawns.recordGeode(level, origin, List.of(origin, second));
        int count = plan(chunk, origin).count();
        test.assertTrue(count >= 1 && count <= 2, "Every recorded geode must schedule one or two residents");
        test.succeedWhen(() -> {
            GeodeFrogSpawns.processChunk(level, chunk);
            test.assertTrue(plan(chunk, origin).done(), "Wait for entity data before creating the residents");
            List<CrystalFrog> residents = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                var entity = level.getEntity(GeodeFrogSpawns.residentId(level.getSeed(), origin, i));
                test.assertTrue(entity instanceof CrystalFrog, "Each planned UUID must belong to a crystal frog");
                CrystalFrog frog = (CrystalFrog) entity;
                test.assertTrue(frog.isPersistenceRequired() && frog.getMaxHealth() >= 16 && frog.getMaxHealth() <= 28,
                        "Geode residents must persist and retain ordinary individual stat generation");
                residents.add(frog);
            }
            var codec = GeodeFrogSpawns.PLANS.persistenceCodec();
            var encoded = codec.encodeStart(NbtOps.INSTANCE, chunk.getAttachedOrThrow(GeodeFrogSpawns.PLANS)).getOrThrow();
            chunk.setAttached(GeodeFrogSpawns.PLANS, codec.parse(NbtOps.INSTANCE, encoded).getOrThrow());
            GeodeFrogSpawns.recordGeode(level, origin, List.of(origin, second));
            GeodeFrogSpawns.processChunk(level, chunk);
            test.assertTrue(chunk.getAttachedOrThrow(GeodeFrogSpawns.PLANS).stream().filter(p -> p.origin().equals(origin)).count() == 1,
                    "Repeated generation hooks must not create a second plan for the same geode");
            residents.forEach(CrystalFrog::discard);
            GeodeFrogSpawns.processChunk(level, chunk);
            for (int i = 0; i < count; i++) {
                var entity = level.getEntity(GeodeFrogSpawns.residentId(level.getSeed(), origin, i));
                test.assertTrue(entity == null || entity.isRemoved(), "Completed plans must never replenish removed residents");
            }
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 80)
    public void blockedSitesWaitAndPartialProgressDoesNotDuplicateAResident(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        BlockPos first = site(test, 3), second = site(test, 5);
        LevelChunk chunk = level.getChunkAt(first);
        resetFixture(level, chunk, first);
        List<GeodeFrogSpawns.Plan> plans = new ArrayList<>(chunk.getAttachedOrElse(GeodeFrogSpawns.PLANS, List.of()));
        plans.add(new GeodeFrogSpawns.Plan(first, List.of(first, second), 2, 0));
        chunk.setAttached(GeodeFrogSpawns.PLANS, List.copyOf(plans));
        level.setBlock(first, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(second, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        GeodeFrogSpawns.processChunk(level, chunk);
        test.assertTrue(plan(chunk, first).completed() == 0, "A blocked geode must wait, without spawning inside solid blocks");
        level.setBlock(first, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(second, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        CrystalFrog previous = ModEntities.CRYSTAL_FROG.create(level, EntitySpawnReason.CHUNK_GENERATION);
        previous.setUUID(GeodeFrogSpawns.residentId(level.getSeed(), first, 0));
        previous.snapTo(Vec3.atBottomCenterOf(first), 0, 0);
        previous.finalizeSpawn(level, level.getCurrentDifficultyAt(first), EntitySpawnReason.CHUNK_GENERATION, null);
        previous.setPersistenceRequired();
        level.addFreshEntity(previous);
        test.succeedWhen(() -> {
            GeodeFrogSpawns.processChunk(level, chunk);
            test.assertTrue(plan(chunk, first).done(), "Both resident slots must eventually be completed");
            test.assertTrue(level.getEntity(previous.getUUID()) == previous, "An already-saved resident must be reused, not duplicated");
            test.assertTrue(level.getEntity(GeodeFrogSpawns.residentId(level.getSeed(), first, 1)) instanceof CrystalFrog,
                    "The second slot must still be populated");
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void unmarkedOldAmethystDoesNotTriggerRetroactiveSpawning(GameTestHelper test) {
        BlockPos origin = site(test, 3);
        LevelChunk chunk = test.getLevel().getChunkAt(origin);
        resetFixture(test.getLevel(), chunk, origin);
        GeodeFrogSpawns.processChunk(test.getLevel(), chunk);
        test.assertTrue(chunk.getAttachedOrElse(GeodeFrogSpawns.PLANS, List.of()).stream().noneMatch(p -> p.origin().equals(origin)),
                "Loading old amethyst blocks must not synthesize a new-geode marker");
        test.assertTrue(test.getLevel().getEntity(GeodeFrogSpawns.residentId(test.getLevel().getSeed(), origin, 0)) == null,
                "An old cave must not receive a retroactive resident");
        test.succeed();
    }
}
