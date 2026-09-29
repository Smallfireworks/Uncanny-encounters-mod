package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import java.util.UUID;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** Observe new owner combat events without a TargetGoal clearing the frog's own retaliation. */
public final class CrystalFrogOwnerSupport {
    private final CrystalFrog frog;
    private @Nullable UUID ownerId;
    private int lastHurtByTime, lastAttackTime;

    public CrystalFrogOwnerSupport(CrystalFrog frog) { this.frog = frog; }

    public void tick() {
        LivingEntity owner = frog.getOwner();
        if (!frog.isTame() || owner == null || !owner.isAlive() || owner.level() != frog.level()) {
            ownerId = null;
            return;
        }
        int hurtByTime = owner.getLastHurtByMobTimestamp();
        int attackTime = owner.getLastHurtMobTimestamp();
        if (!owner.getUUID().equals(ownerId)) {
            ownerId = owner.getUUID();
            lastHurtByTime = hurtByTime;
            lastAttackTime = attackTime;
            return; // Do not replay combat from before taming, loading, or rejoining.
        }
        boolean hurt = hurtByTime != lastHurtByTime;
        boolean attacked = attackTime != lastAttackTime;
        // Consume events while sitting or weak too; standing up must not replay old fights.
        lastHurtByTime = hurtByTime;
        lastAttackTime = attackTime;
        if (hurt) {
            var source = owner.getLastDamageSource(100);
            if (source != null && !source.is(DamageTypeTags.NO_WOLF_RETALIATION)
                    && frog.assistOwner(owner.getLastHurtByMob())) return;
        }
        if (attacked && owner.tickCount - attackTime <= 100) frog.assistOwner(owner.getLastHurtMob());
    }
}
