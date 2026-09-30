package com.ignilumen.uncannyencounters.block;

import com.ignilumen.uncannyencounters.entity.lightmoth.MothLights;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Original support, collision and water behavior, without flame or block light. */
public final class DimmedLights {
    private static void check(BlockState state, ServerLevel level, BlockPos pos) {
        MothLights.get(level).check(level,pos);
        if (level.getBlockState(pos).is(state.getBlock())) level.scheduleTick(pos,state.getBlock(),20);
    }
    public static final class Torch extends TorchBlock {
        public Torch(BlockBehaviour.Properties properties) { super(ParticleTypes.FLAME, properties); }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {}
        @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            super.onPlace(state,level,pos,old,moved);
            if (!level.isClientSide()) level.scheduleTick(pos,this,20);
        }
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { check(state,level,pos); }
        @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) { return new ItemStack(Items.TORCH); }
    }
    public static final class WallTorch extends WallTorchBlock {
        public WallTorch(BlockBehaviour.Properties properties) { super(ParticleTypes.FLAME, properties); }
        @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {}
        @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            super.onPlace(state,level,pos,old,moved);
            if (!level.isClientSide()) level.scheduleTick(pos,this,20);
        }
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { check(state,level,pos); }
        @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) { return new ItemStack(Items.TORCH); }
    }
    public static final class Lantern extends LanternBlock {
        public Lantern(BlockBehaviour.Properties properties) { super(properties); }
        @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            super.onPlace(state,level,pos,old,moved);
            if (!level.isClientSide()) level.scheduleTick(pos,this,20);
        }
        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { check(state,level,pos); }
        @Override protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) { return new ItemStack(Items.LANTERN); }
    }
    private DimmedLights() {}
}
