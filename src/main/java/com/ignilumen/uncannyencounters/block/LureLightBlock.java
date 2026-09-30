package com.ignilumen.uncannyencounters.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible and replaceable; its owner direction survives saves without extra world data. */
public final class LureLightBlock extends Block {
    public static final EnumProperty<Direction> TOWARD_LAMP = BlockStateProperties.FACING;

    public LureLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TOWARD_LAMP, Direction.DOWN));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(TOWARD_LAMP); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected boolean propagatesSkylightDown(BlockState state) { return true; }
    @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) { return ItemStack.EMPTY; }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 10);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { refresh(level, pos); }

    public static void refresh(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.LURE_LIGHT)) return;
        Direction owner = state.getValue(TOWARD_LAMP);
        if (!hasOwner(level, pos, owner)) {
            owner = null;
            // Two lanterns may share this cell. Removing one must not extinguish the other's light.
            for (Direction direction : Direction.values()) if (hasOwner(level, pos, direction)) { owner = direction; break; }
            if (owner == null) { level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL); return; }
            level.setBlock(pos, state.setValue(TOWARD_LAMP, owner), UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, ModBlocks.LURE_LIGHT, 10);
    }

    private static boolean hasOwner(ServerLevel level, BlockPos light, Direction toward) {
        BlockPos lamp = light.relative(toward, EnhancedLureBlock.EXTENSION_DISTANCE);
        return level.isLoaded(lamp) && level.getBlockState(lamp).getBlock() instanceof EnhancedLureBlock
                && EnhancedLureBlock.clearRay(level, lamp, toward.getOpposite());
    }
}
