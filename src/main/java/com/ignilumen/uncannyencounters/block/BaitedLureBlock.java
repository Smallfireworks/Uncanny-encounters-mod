package com.ignilumen.uncannyencounters.block;

import com.ignilumen.uncannyencounters.entity.lightmoth.LureBait;
import com.ignilumen.uncannyencounters.entity.lightmoth.MonsterLures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** The block identity preserves the single bait through placement, loot and saves. */
public final class BaitedLureBlock extends EnhancedLureBlock {
    private final LureBait bait;

    public BaitedLureBlock(Properties properties, LureBait bait) {
        super(properties);
        this.bait = bait;
    }

    public LureBait bait() { return bait; }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MonsterLures.offerBlock(level, pos, bait);
        if (level.getGameTime() % 20 < MonsterLures.SCAN_INTERVAL) extendLight(level, pos);
        level.scheduleTick(pos, this, MonsterLures.SCAN_INTERVAL);
    }
}
