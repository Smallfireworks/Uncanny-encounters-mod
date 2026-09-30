package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.CrystalSlimeShot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Captures the exact match and target; delayed effects cannot leak into the next fight. */
public record FrogTalentAttack(CrystalFrog frog, LivingEntity target, CrystalFrogDuels.@Nullable Match match) {
    public static FrogTalentAttack capture(CrystalFrog frog, LivingEntity target) {
        return new FrogTalentAttack(frog, target, frog.duelMatch());
    }

    public boolean valid() {
        return frog.isAlive() && !frog.isNoAi() && target.isAlive() && frog.level() == target.level()
                && frog.duelMatch() == match && frog.isTalentTarget(target)
                && (match == null || target instanceof CrystalFrog other && other.duelMatch() == match);
    }

    public DamageSource damageSource(@Nullable Entity projectile) {
        var type = projectile instanceof CrystalSlimeShot ? CrystalFrog.SLIME_DAMAGE : CrystalFrog.TALENT_BURST_DAMAGE;
        return new DamageSource(frog.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type),
                projectile == null ? frog : projectile, frog);
    }

    public boolean hurt(@Nullable Entity projectile, float amount) {
        return valid() && frog.level() instanceof ServerLevel level && target.hurtServer(level, damageSource(projectile), amount);
    }

    public boolean visibleFrom(Vec3 point) {
        return frog.level().clip(new ClipContext(point, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, frog)).getType() == HitResult.Type.MISS;
    }
}
