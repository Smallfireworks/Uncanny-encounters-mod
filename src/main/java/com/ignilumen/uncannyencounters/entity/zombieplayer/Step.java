package com.ignilumen.uncannyencounters.entity.zombieplayer;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * One planned edge between feet cells. {@code floor} is the height the feet rest at in {@code to}
 * (NaN while swimming). Blocks in {@code opens} are opened, {@code breaks} mined, and {@code place}
 * filled with a zombie block before the body moves.
 */
record Step(Kind kind, BlockPos from, BlockPos to, double floor, List<BlockPos> opens, List<BlockPos> breaks,
            @Nullable BlockPos place) {
    enum Kind { WALK, DIAGONAL, ASCEND, DESCEND, PILLAR, DIG_DOWN, SWIM }

    boolean hasActions() {
        return !opens.isEmpty() || !breaks.isEmpty() || place != null;
    }

    /** Whether the body must come to rest on {@code to} before the next step starts. */
    boolean stopsBefore(@Nullable Step next) {
        return next == null || next.hasActions() || next.kind == Kind.PILLAR || next.kind == Kind.DIG_DOWN
                || kind == Kind.DESCEND || kind == Kind.PILLAR || kind == Kind.DIG_DOWN;
    }
}
