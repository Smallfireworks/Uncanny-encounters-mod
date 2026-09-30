package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Worldgen records plans in its own chunk; the server spawns residents after entity data is ready. */
public final class GeodeFrogSpawns {
    private static final int MAX_SITES = 32, CHUNKS_PER_PASS = 16;

    public record Plan(BlockPos origin, List<BlockPos> sites, int count, int completed) {
        public static final Codec<Plan> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("origin").forGetter(Plan::origin),
                BlockPos.CODEC.listOf(0, MAX_SITES).fieldOf("sites").forGetter(Plan::sites),
                Codec.intRange(1, 2).fieldOf("count").forGetter(Plan::count),
                Codec.intRange(0, 3).fieldOf("completed").forGetter(Plan::completed)
        ).apply(i, Plan::new));

        public Plan { sites = List.copyOf(sites); }
        public boolean done() { return (completed & ((1 << count) - 1)) == (1 << count) - 1; }
    }

    public static final AttachmentType<List<Plan>> PLANS = AttachmentRegistry.createPersistent(
            UncannyEncounters.id("geode_frog_plans"), Plan.CODEC.listOf());
    // Accessed only by server lifecycle callbacks, never from generation worker threads.
    private static final Map<ServerLevel, Set<ChunkPos>> WAITING = new WeakHashMap<>();

    public static void initialize() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> queue(level, chunk));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            Set<ChunkPos> chunks = WAITING.get(level);
            if (chunks != null) chunks.remove(chunk.getPos());
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level.getGameTime() % 20 != 0) return;
            Set<ChunkPos> waiting = WAITING.get(level);
            if (waiting == null || waiting.isEmpty()) return;
            // Rotate waiting chunks so a missing neighbor cannot starve unrelated geodes.
            List<ChunkPos> batch = waiting.stream().limit(CHUNKS_PER_PASS).toList();
            for (ChunkPos pos : batch) {
                waiting.remove(pos);
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
                if (chunk != null && !processChunk(level, chunk)) waiting.add(pos);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAITING.clear());
    }

    private static void queue(ServerLevel level, LevelChunk chunk) {
        List<Plan> plans = chunk.getAttached(PLANS);
        if (plans != null && plans.stream().anyMatch(plan -> !plan.done()))
            WAITING.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(chunk.getPos());
    }

    /** Interior positions come from this geode's actual filling writes, not a scan of nearby caves. */
    public static void recordGeode(WorldGenLevel level, BlockPos origin, List<BlockPos> interior) {
        if (!level.getLevel().dimension().equals(Level.OVERWORLD)) return;
        List<BlockPos> sites = new ArrayList<>();
        for (BlockPos pos : interior) {
            if (amethystFloor(level.getBlockState(pos.below())) && candidateCell(level.getBlockState(pos))) sites.add(pos.immutable());
        }
        if (sites.isEmpty()) return; // A clipped/failed cavity with no amethyst floor is not a habitable geode.
        RandomSource random = RandomSource.create(level.getSeed() ^ origin.asLong() ^ 0x5F726F6747656F64L);
        for (int i = sites.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            BlockPos swap = sites.get(i);
            sites.set(i, sites.get(j));
            sites.set(j, swap);
        }
        int count = Math.min(sites.size(), 1 + random.nextInt(2));
        if (sites.size() > MAX_SITES) sites = new ArrayList<>(sites.subList(0, MAX_SITES));
        ChunkPos owner = level instanceof WorldGenRegion region ? region.getCenter() : ChunkPos.containing(origin);
        var chunk = level.getChunk(owner.x(), owner.z());
        List<Plan> old = chunk.getAttachedOrElse(PLANS, List.of());
        if (old.stream().anyMatch(plan -> plan.origin.equals(origin))) return;
        List<Plan> next = new ArrayList<>(old);
        next.add(new Plan(origin.immutable(), sites, count, 0));
        // Persistent attachments are transferred from ProtoChunk to LevelChunk and mark it dirty.
        chunk.setAttached(PLANS, List.copyOf(next));
        if (level instanceof ServerLevel server && chunk instanceof LevelChunk loaded) queue(server, loaded);
    }

    static boolean processChunk(ServerLevel level, LevelChunk owner) {
        List<Plan> plans = owner.getAttached(PLANS);
        if (plans == null || plans.stream().allMatch(Plan::done)) return true;
        if (!level.getGameRules().get(GameRules.SPAWN_MOBS)) return false;
        List<Plan> updated = new ArrayList<>(plans.size());
        boolean changed = false;
        for (Plan plan : plans) {
            Plan next = plan.done() || !sitesReady(level, plan.sites) ? plan : populate(level, plan);
            updated.add(next);
            changed |= next != plan;
        }
        if (changed) owner.setAttached(PLANS, List.copyOf(updated));
        return updated.stream().allMatch(Plan::done);
    }

    private static boolean sitesReady(ServerLevel level, List<BlockPos> sites) {
        for (BlockPos site : sites) {
            ChunkPos pos = ChunkPos.containing(site);
            if (level.getChunkSource().getChunkNow(pos.x(), pos.z()) == null || !level.areEntitiesLoaded(pos.pack())) return false;
        }
        return true;
    }

    private static Plan populate(ServerLevel level, Plan plan) {
        int completed = plan.completed;
        for (int slot = 0; slot < plan.count; slot++) {
            if ((completed & 1 << slot) != 0) continue;
            UUID id = residentId(level.getSeed(), plan.origin, slot);
            if (level.getEntity(id) != null) {
                completed |= 1 << slot;
                continue;
            }
            Vec3 spot = null;
            for (BlockPos site : plan.sites) {
                Vec3 candidate = spawnSpot(level, site);
                if (candidate == null) continue;
                var box = ModEntities.CRYSTAL_FROG.getSpawnAABB(candidate);
                if (level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive).isEmpty()) { spot = candidate; break; }
            }
            if (spot == null) break; // Retry when the loaded cave has room, never put a frog inside terrain.
            CrystalFrog frog = ModEntities.CRYSTAL_FROG.create(level, EntitySpawnReason.CHUNK_GENERATION);
            if (frog == null) break;
            frog.setUUID(id);
            frog.snapTo(spot, level.getRandom().nextFloat() * 360, 0);
            frog.finalizeSpawn(level, level.getCurrentDifficultyAt(frog.blockPosition()), EntitySpawnReason.CHUNK_GENERATION, null);
            frog.setPersistenceRequired();
            if (level.addFreshEntity(frog)) completed |= 1 << slot;
        }
        if (completed == plan.completed) return plan;
        boolean done = completed == (1 << plan.count) - 1;
        // Retain the tiny completion marker so reloads or repeated feature placement cannot restock it.
        return new Plan(plan.origin, done ? List.of() : plan.sites, plan.count, completed);
    }

    public static UUID residentId(long seed, BlockPos origin, int slot) {
        return UUID.nameUUIDFromBytes(("uncannyencounters:geode:" + seed + ":" + origin.asLong() + ":" + slot).getBytes(StandardCharsets.UTF_8));
    }

    static @Nullable Vec3 spawnSpot(ServerLevel level, BlockPos site) {
        if (!level.isLoaded(site) || !level.isLoaded(site.above()) || !level.isLoaded(site.below())
                || !level.getWorldBorder().isWithinBounds(site) || !amethystFloor(level.getBlockState(site.below()))) return null;
        BlockState state = level.getBlockState(site);
        if (!candidateCell(state)) return null;
        var shape = state.getCollisionShape(level, site);
        double height = shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
        Vec3 feet = Vec3.atBottomCenterOf(site).add(0, height, 0);
        var box = ModEntities.CRYSTAL_FROG.getSpawnAABB(feet);
        return level.noCollision(box) ? feet : null;
    }

    private static boolean amethystFloor(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.BUDDING_AMETHYST);
    }

    private static boolean candidateCell(BlockState state) {
        return state.isAir() || state.getFluidState().is(FluidTags.WATER)
                || state.is(Blocks.SMALL_AMETHYST_BUD) || state.is(Blocks.MEDIUM_AMETHYST_BUD)
                || state.is(Blocks.LARGE_AMETHYST_BUD) || state.is(Blocks.AMETHYST_CLUSTER);
    }

    private GeodeFrogSpawns() {}
}
