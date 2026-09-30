package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** One bounded scan at birth (or explicit stat inspection), never a per-tick breeding aura. */
public final class FrogBreedingHabitat {
    public static int points(BlockState state) {
        if (state.is(Blocks.BUDDING_AMETHYST)) return 4;
        if (state.is(Blocks.AMETHYST_CLUSTER)) return 2;
        return state.is(Blocks.AMETHYST_BLOCK) ? 1 : 0;
    }
    public static int score(ServerLevel level, BlockPos birth) {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(birth.offset(-6, -3, -6), birth.offset(6, 3, 6))) {
            if (!level.hasChunkAt(pos)) continue;
            result += points(level.getBlockState(pos));
            if (result >= 32) return 32;
        }
        return result;
    }
    public static int scoreForBirth(CrystalFrog first, CrystalFrog second) {
        if (!(first.level() instanceof ServerLevel level)
                || !first.talents().has(CrystalFrogTalent.CRYSTAL_NURSERY) && !second.talents().has(CrystalFrogTalent.CRYSTAL_NURSERY)) return 0;
        // Animal.spawnChildFromBreeding places the offspring at the initiating parent's position.
        return score(level, first.blockPosition());
    }
    private FrogBreedingHabitat() {}
}
