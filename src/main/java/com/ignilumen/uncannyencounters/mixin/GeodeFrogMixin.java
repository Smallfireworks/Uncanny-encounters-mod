package com.ignilumen.uncannyencounters.mixin;

import com.ignilumen.uncannyencounters.entity.crystalfrog.GeodeFrogSpawns;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.GeodeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GeodeFeature.class)
public abstract class GeodeFrogMixin {
    // 26.3's first safeSetBlock call fills the interior; later calls build cracks, shells and buds.
    // Invocation-local storage is essential: GeodeFeature instances are shared by generation threads.
    @WrapOperation(method = "place", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/levelgen/feature/GeodeFeature;safeSetBlock(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Predicate;)V"))
    private void uncannyEncounters$rememberInterior(GeodeFeature feature, WorldGenLevel level, BlockPos pos,
                                                    BlockState state, Predicate<BlockState> canReplace, Operation<Void> original,
                                                    @Share("frogInterior") LocalRef<List<BlockPos>> interior) {
        original.call(feature, level, pos, state, canReplace);
        if (!state.isAir() || !level.getBlockState(pos).isAir()) return;
        if (interior.get() == null) interior.set(new ArrayList<>());
        if (interior.get().size() < 8192) interior.get().add(pos.immutable());
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void uncannyEncounters$planResidents(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                                                BlockPos origin, CallbackInfoReturnable<Boolean> cir,
                                                @Share("frogInterior") LocalRef<List<BlockPos>> interior) {
        if (cir.getReturnValueZ() && interior.get() != null) GeodeFrogSpawns.recordGeode(level, origin, interior.get());
    }
}
