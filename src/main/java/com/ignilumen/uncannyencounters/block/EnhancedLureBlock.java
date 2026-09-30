package com.ignilumen.uncannyencounters.block;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A level-15 lantern with bounded, unobstructed extensions into nearby air. */
public class EnhancedLureBlock extends LanternBlock {
    public static final int EXTENSION_DISTANCE = 6;

    public EnhancedLureBlock(Properties properties) { super(properties); }

    public static void initialize() {
        // This also upgrades already placed plain enhanced lanterns, which formerly had no ticks.
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> chunk.findBlocks(
                state -> state.getBlock() instanceof EnhancedLureBlock || state.is(ModBlocks.LURE_LIGHT),
                (pos, state) -> level.scheduleTick(pos, state.getBlock(), 1)));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        extendLight(level, pos);
        level.scheduleTick(pos, this, 20);
    }

    public static void extendLight(ServerLevel level, BlockPos lamp) {
        if (!level.isLoaded(lamp) || !(level.getBlockState(lamp).getBlock() instanceof EnhancedLureBlock)) return;
        for (Direction direction : Direction.values()) {
            BlockPos end = lamp.relative(direction, EXTENSION_DISTANCE);
            if (!clearRay(level, lamp, direction)) continue;
            BlockState state = level.getBlockState(end);
            if (state.isAir()) level.setBlock(end, ModBlocks.LURE_LIGHT.defaultBlockState()
                    .setValue(LureLightBlock.TOWARD_LAMP, direction.getOpposite()), UPDATE_ALL);
        }
    }

    static boolean clearRay(ServerLevel level, BlockPos lamp, Direction direction) {
        for (int step = 1; step <= EXTENSION_DISTANCE; step++) {
            BlockPos pos = lamp.relative(direction, step);
            if (!level.isLoaded(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (state.getLightDampening() > 0 || !state.getOcclusionShape().isEmpty()) return false;
        }
        return true;
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        for (Direction direction : Direction.values()) {
            BlockPos light = pos.relative(direction, EXTENSION_DISTANCE);
            if (level.isLoaded(light) && level.getBlockState(light).is(ModBlocks.LURE_LIGHT)) LureLightBlock.refresh(level, light);
        }
    }
}
