package com.ignilumen.uncannyencounters.entity.frogkeeper;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Surviving partners walk home after disengaging, without loading their home chunk. */
public final class ReturnToFrogCourtGoal extends Goal {
    private final PathfinderMob mob;
    private final Supplier<@Nullable UUID> keeperId;
    private @Nullable BlockPos home;
    private int cooldown;
    private int returnTicks;
    public ReturnToFrogCourtGoal(PathfinderMob mob, Supplier<@Nullable UUID> keeperId) {
        this.mob = mob;
        this.keeperId = keeperId;
        setFlags(EnumSet.of(Flag.MOVE));
    }
    @Override public boolean canUse() {
        UUID id = keeperId.get();
        if (id == null || mob.getTarget() != null || mob instanceof CrystalFrog frog && frog.isDueling()
                || !(mob.level() instanceof ServerLevel level)) return false;
        home = FrogKeeperEncounters.get(level).home(id, mob instanceof CrystalFrog);
        return home != null && level.hasChunkAt(home) && mob.distanceToSqr(Vec3.atBottomCenterOf(home)) > 4;
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void start() { cooldown = returnTicks = 0; }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void stop() { mob.getNavigation().stop(); }
    @Override public void tick() {
        if (++returnTicks >= 200 && mob instanceof CrystalFrog frog) {
            returnTicks = 0;
            if (FrogCourtSafety.recall(frog)) return;
        }
        if (home != null && --cooldown <= 0) {
            cooldown = adjustedTickDelay(20);
            mob.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1);
        }
    }
}
