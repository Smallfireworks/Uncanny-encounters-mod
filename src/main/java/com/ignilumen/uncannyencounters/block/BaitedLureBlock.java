package com.ignilumen.uncannyencounters.block;

import com.ignilumen.uncannyencounters.entity.lightmoth.LureBait;
import com.ignilumen.uncannyencounters.entity.lightmoth.MonsterLures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The block identity preserves the single bait through placement, loot and saves. */
public final class BaitedLureBlock extends LanternBlock {
    private final LureBait bait;

    public BaitedLureBlock(Properties properties, LureBait bait) {
        super(properties);
        this.bait = bait;
    }

    public LureBait bait() { return bait; }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MonsterLures.offerBlock(level, pos, bait);
        level.scheduleTick(pos, this, MonsterLures.SCAN_INTERVAL);
    }
}
