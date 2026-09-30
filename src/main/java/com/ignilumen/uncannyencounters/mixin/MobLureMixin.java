package com.ignilumen.uncannyencounters.mixin;

import com.ignilumen.uncannyencounters.entity.lightmoth.LuredMob;
import com.ignilumen.uncannyencounters.entity.lightmoth.MonsterLures;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobLureMixin implements LuredMob {
    @Unique private MonsterLures.@Nullable Attraction uncannyEncounters$attraction;

    @Override public MonsterLures.@Nullable Attraction uncannyEncounters$attraction() { return uncannyEncounters$attraction; }

    @Override public void uncannyEncounters$offer(MonsterLures.Source source) {
        if (uncannyEncounters$attraction == null) uncannyEncounters$attraction = new MonsterLures.Attraction();
        uncannyEncounters$attraction.offer((Mob) (Object) this, source);
    }

    @Override public void uncannyEncounters$provoke(LivingEntity attacker) {
        if (uncannyEncounters$attraction == null) uncannyEncounters$attraction = new MonsterLures.Attraction();
        uncannyEncounters$attraction.provoke((Mob) (Object) this, attacker);
    }

    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void uncannyEncounters$neutralUntilProvoked(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (MonsterLures.blocksAttack((Mob) (Object) this, target)) cir.setReturnValue(false);
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void uncannyEncounters$updateCombat(CallbackInfo ci) {
        if (uncannyEncounters$attraction != null) uncannyEncounters$attraction.updateCombat((Mob) (Object) this);
    }

    @Inject(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V"))
    private void uncannyEncounters$prepareNavigation(CallbackInfo ci) {
        if (uncannyEncounters$attraction != null) {
            uncannyEncounters$attraction.updateCombat((Mob) (Object) this);
            uncannyEncounters$attraction.prepareNavigation((Mob) (Object) this);
        }
    }

    @Inject(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V"))
    private void uncannyEncounters$steer(CallbackInfo ci) {
        if (uncannyEncounters$attraction != null) uncannyEncounters$attraction.steer((Mob) (Object) this);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void uncannyEncounters$saveRetaliation(ValueOutput output, CallbackInfo ci) {
        if (uncannyEncounters$attraction != null) uncannyEncounters$attraction.save(output);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void uncannyEncounters$loadRetaliation(ValueInput input, CallbackInfo ci) {
        if (uncannyEncounters$attraction == null) uncannyEncounters$attraction = new MonsterLures.Attraction();
        uncannyEncounters$attraction.load(input);
    }
}
