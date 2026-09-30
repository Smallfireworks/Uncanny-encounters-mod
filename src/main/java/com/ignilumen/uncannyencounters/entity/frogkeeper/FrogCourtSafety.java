package com.ignilumen.uncannyencounters.entity.frogkeeper;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Shared court bounds and collision-checked recovery for an idle, undefeated NPC frog. */
public final class FrogCourtSafety {
    private static final double INNER_HALF_WIDTH = 11;
    private static final double HOP_TICKS = 12;

    public static boolean inside(BlockPos altar, Vec3 feet, double width) {
        Vec3 center = Vec3.atBottomCenterOf(altar.south(8));
        double limit = INNER_HALF_WIDTH - width / 2;
        return Math.abs(feet.x - center.x) <= limit && Math.abs(feet.z - center.z) <= limit
                && Math.abs(feet.y - center.y) <= 4;
    }

    /** Limit the initial impulse only: an airborne pounce still does not track its opponent. */
    public static Vec3 limitHop(BlockPos altar, Vec3 feet, double width, Vec3 motion) {
        Vec3 center = Vec3.atBottomCenterOf(altar.south(8));
        double limit = Math.max(0, INNER_HALF_WIDTH - width / 2 - 0.5);
        double x = Math.clamp(feet.x + motion.x * HOP_TICKS, center.x - limit, center.x + limit);
        double z = Math.clamp(feet.z + motion.z * HOP_TICKS, center.z - limit, center.z + limit);
        return new Vec3((x - feet.x) / HOP_TICKS, motion.y, (z - feet.z) / HOP_TICKS);
    }

    public static boolean recall(CrystalFrog frog) {
        if (!(frog.level() instanceof ServerLevel level) || frog.keeperId() == null || !frog.isAlive()
                || frog.isDueling() || frog.getTarget() != null || frog.isPassenger() || frog.isLeashed() || frog.isNoAi()) return false;
        var ledger = FrogKeeperEncounters.get(level);
        if (ledger.defeated(frog.keeperId())) return false;
        BlockPos home = ledger.home(frog.keeperId(), true);
        if (home == null) return false;
        // Prefer the original spot, then nearby clear floor. Do not load even an adjacent chunk.
        for (int radius = 0; radius <= 4; radius++) for (int dy : new int[]{0, 1, -1, 2})
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos pos = home.offset(dx, dy, dz);
                Vec3 feet = Vec3.atBottomCenterOf(pos);
                var box = frog.getBoundingBox().move(feet.subtract(frog.position()));
                if (!inside(home.north(6), feet, frog.getBbWidth())
                        || !level.hasChunkAt(pos.below()) || !level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                        || !level.hasChunkAt(BlockPos.containing(box.maxX, box.maxY, box.maxZ))) continue;
                var floor = level.getBlockState(pos.below());
                if (!floor.isFaceSturdy(level, pos.below(), Direction.UP) || frog.getType().isBlockDangerous(floor)
                        || !level.getFluidState(pos).isEmpty() || !level.noCollision(frog, box)) continue;
                frog.getNavigation().stop();
                frog.getMoveControl().setWait();
                frog.setDeltaMovement(Vec3.ZERO);
                frog.fallDistance = 0;
                frog.snapTo(feet, frog.getYRot(), frog.getXRot());
                frog.needsSync = true;
                frog.setKeeperRetaliationTarget(null);
                return true;
            }
        return false;
    }

    private FrogCourtSafety() {}
}
