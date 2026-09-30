package com.ignilumen.uncannyencounters.entity.crystalfrog;

/** Smooth only the displayed pose; combat timing remains entirely server-owned. */
public final class CrystalFrogCombatAnimation {
    private float charge, oldCharge, guard, oldGuard, recovery, oldRecovery;
    private float spit, oldSpit;

    public void tick(byte pose, boolean casting) {
        oldSpit = spit;
        spit += ((casting ? 1 : 0) - spit) * 0.35F;
        oldCharge = charge;
        oldGuard = guard;
        oldRecovery = recovery;
        charge += ((pose == CrystalFrogDuelCombat.WINDUP || pose == CrystalFrogDuelCombat.COUNTER ? 1 : 0) - charge) * 0.35F;
        guard += ((pose == CrystalFrogDuelCombat.GUARDING ? 1 : 0) - guard) * 0.4F;
        recovery += ((pose == CrystalFrogDuelCombat.RECOVERY ? 1 : 0) - recovery) * 0.3F;
    }

    public float charge(float partialTick) { return blend(oldCharge, charge, partialTick); }
    public float guard(float partialTick) { return blend(oldGuard, guard, partialTick); }
    public float recovery(float partialTick) { return blend(oldRecovery, recovery, partialTick); }
    public float spit(float partialTick) { return blend(oldSpit, spit, partialTick); }
    private static float blend(float old, float current, float partialTick) { return old + (current - old) * Math.clamp(partialTick, 0, 1); }
}
