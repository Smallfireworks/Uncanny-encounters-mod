package com.ignilumen.uncannyencounters.worldgen;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.block.ModBlocks;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** A single saved piece, clipped to each generating chunk like vanilla surface temples. */
public final class FrogCourtStructure extends SinglePieceStructure {
    public static final MapCodec<FrogCourtStructure> CODEC = simpleCodec(FrogCourtStructure::new);
    public static final StructureType<FrogCourtStructure> TYPE = () -> CODEC;
    public static final StructurePieceType PIECE = (context, tag) -> new CourtPiece(tag);
    public FrogCourtStructure(StructureSettings settings) { super(CourtPiece::new, 25, 25, settings); }
    public static void initialize() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE, UncannyEncounters.id("frog_court"), TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_PIECE, UncannyEncounters.id("frog_court"), PIECE);
    }
    @Override public StructureType<?> type() { return TYPE; }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx : new int[]{0, 12, 24}) for (int dz : new int[]{0, 12, 24}) {
            int height = context.chunkGenerator().getFirstOccupiedHeight(context.chunkPos().getMinBlockX() + dx,
                    context.chunkPos().getMinBlockZ() + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            min = Math.min(min, height);
            max = Math.max(max, height);
        }
        if (max - min > 6 || min <= context.chunkGenerator().getSeaLevel()) return Optional.empty();
        return super.findGenerationPoint(context);
    }

    private static final class CourtPiece extends ScatteredFeaturePiece {
        CourtPiece(RandomSource random, int x, int z) { super(PIECE, x, 64, z, 25, 9, 25, Direction.SOUTH); }
        CourtPiece(CompoundTag tag) { super(PIECE, tag); }
        @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                          RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos reference) {
            if (!updateAverageGroundHeight(level, clip, -1)) return;
            generateAirBox(level, clip, 0, 1, 0, 24, 8, 24);
            for (int x = 0; x < 25; x++) for (int z = 0; z < 25; z++) {
                int dx = x - 12, dz = z - 12, radius = dx * dx + dz * dz;
                boolean edge = x == 0 || z == 0 || x == 24 || z == 24;
                BlockState floor = (edge || radius >= 45 && radius <= 64 ? Blocks.AMETHYST_BLOCK
                        : radius < 45 ? Blocks.SMOOTH_QUARTZ : Blocks.CALCITE).defaultBlockState();
                if (radius < 6 || Math.abs(dx) == 4 && Math.abs(dz) == 3) floor = Blocks.AMETHYST_BLOCK.defaultBlockState();
                placeBlock(level, floor, x, 0, z, clip);
                fillColumnDown(level, Blocks.SMOOTH_BASALT.defaultBlockState(), x, -1, z, clip);
                if (edge && !(x >= 10 && x <= 14) && !(z >= 10 && z <= 14))
                    placeBlock(level, Blocks.CALCITE.defaultBlockState(), x, 1, z, clip);
            }
            for (int x : new int[]{3, 21}) for (int z : new int[]{3, 12, 21}) {
                for (int y = 1; y <= 5; y++) placeBlock(level,
                        (y == 1 || y == 5 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.AMETHYST_BLOCK).defaultBlockState(), x, y, z, clip);
                placeBlock(level, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), x, 6, z, clip);
                placeBlock(level, Blocks.AMETHYST_CLUSTER.defaultBlockState(), x, 7, z, clip);
            }
            // Clear approach, raised altar and two crystal finials at the north entrance.
            placeBlock(level, ModBlocks.FROG_ALTAR.defaultBlockState(), 12, 1, 4, clip);
            for (int x : new int[]{10, 14}) {
                placeBlock(level, Blocks.AMETHYST_BLOCK.defaultBlockState(), x, 1, 4, clip);
                placeBlock(level, Blocks.AMETHYST_CLUSTER.defaultBlockState(), x, 2, 4, clip);
            }
        }
    }
}
