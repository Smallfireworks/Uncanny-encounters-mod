package com.ignilumen.uncannyencounters.entity.zombieplayer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ordinary obstacle clearing must not remove the surface the body stands on or is jumping above. */
final class MiningSafety {
    private MiningSafety() {}

    static boolean supportsBody(AABB body, BlockPos pos, VoxelShape shape) {
        for (AABB box : shape.toAabbs()) {
            AABB part = box.move(pos);
            if (part.maxY >= body.minY - 1.5 && part.maxY <= body.minY + 0.05
                    && part.maxX > body.minX && part.minX < body.maxX
                    && part.maxZ > body.minZ && part.minZ < body.maxZ) return true;
        }
        return false;
    }
}
