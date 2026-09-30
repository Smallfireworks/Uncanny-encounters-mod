package com.ignilumen.uncannyencounters.entity.lightmoth;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** Server-owned attraction and retaliation attached to Mob by the AI hook. */
public interface LuredMob {
    MonsterLures.@Nullable Attraction uncannyEncounters$attraction();
    void uncannyEncounters$offer(MonsterLures.Source source);
    void uncannyEncounters$provoke(LivingEntity attacker);
}
