package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.effect.FrogEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A short, interruptible tongue catch. Targets retain normal collision and damage rules. */
public final class FrogKingSwallow {
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE, UncannyEncounters.id("frog_swallow_immune"));
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, UncannyEncounters.id("crystal_swallow"));
    private final CrystalFrog frog;
    private @Nullable FrogTalentAttack attack;
    private int ticks;
    private long readyAt;

    public FrogKingSwallow(CrystalFrog frog) { this.frog = frog; }
    public void save(ValueOutput output) { output.putLong("CrystalSwallowReadyAt", readyAt); }
    public void load(ValueInput input) { readyAt = input.getLongOr("CrystalSwallowReadyAt", 0); clear(); }
    public float damage() { return frog.getMaxHealth() * 0.2F; }
    private boolean eligible(LivingEntity target) {
        return frog.isKing() && !frog.isBaby() && !target.getType().builtInRegistryHolder().is(IMMUNE)
                && frog.isTalentTarget(target) && target.getHealth() < damage()
                && frog.distanceToSqr(target) <= 2.5 * 2.5 && frog.getSensing().hasLineOfSight(target);
    }
    public boolean canStart() {
        return frog.level().getGameTime() >= readyAt && frog.getTarget() != null && eligible(frog.getTarget())
                && !frog.isTalentCasting() && (!frog.isDueling() || frog.duelCombat().action() == CrystalFrogDuelCombat.NORMAL);
    }
    public void start() {
        if (frog.getTarget() == null) return;
        attack = FrogTalentAttack.capture(frog, frog.getTarget());
        ticks = 0;
        readyAt = frog.level().getGameTime() + 160;
        frog.setTongueTarget(attack.target().getId());
        frog.setTalentCasting(true);
        frog.playSound(SoundEvents.FROG_TONGUE, 0.9F, 0.7F);
    }
    public boolean active() { return attack != null && attack.valid() && ticks < 12; }
    public void checkContext() { if (attack != null && !attack.valid()) clear(); }
    public void tick() {
        if (!active() || !eligible(attack.target())) { clear(); return; }
        LivingEntity target = attack.target();
        frog.getNavigation().stop();
        frog.getMoveControl().setWait();
        frog.getLookControl().setLookAt(target, 90, 90);
        ++ticks;
        if (ticks >= 6 && ticks < 10 && attack.visibleFrom(frog.getEyePosition())) {
            Vec3 pull = frog.getEyePosition().subtract(target.getBoundingBox().getCenter());
            target.setDeltaMovement(pull.normalize().scale(Math.min(0.5, pull.length())));
            target.needsSync = true;
        }
        if (ticks == 10 && frog.level() instanceof ServerLevel level && attack.visibleFrom(frog.getEyePosition())) {
            DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE), frog);
            target.hurtServer(level, source, damage());
            FrogEffects.splash(level, frog.getEyePosition());
            frog.playSound(SoundEvents.FROG_EAT, 1, 0.7F);
            // Never remove a living target, including when armor, a shield or a duel floor saves it.
            if (target.isAlive()) {
                Vec3 away = target.position().subtract(frog.position());
                if (away.horizontalDistanceSqr() < 0.001) away = frog.getLookAngle();
                target.knockback(0.7, -away.x, -away.z, source, damage(), true);
            }
            clear();
        }
    }
    public void clear() {
        attack = null;
        ticks = 0;
        frog.setTongueTarget(-1);
        frog.setTalentCasting(false);
    }
}
