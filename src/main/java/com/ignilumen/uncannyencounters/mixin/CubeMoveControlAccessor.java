package com.ignilumen.uncannyencounters.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob$CubeMobMoveControl")
public interface CubeMoveControlAccessor {
    @Invoker("setDirection") void uncannyEncounters$setDirection(float yaw, boolean aggressive);
    @Invoker("setWantedMovement") void uncannyEncounters$setWantedMovement(double speed);
}
