package com.ignilumen.uncannyencounters.entity.crystalfrog;

/** Smooth only the displayed pose; combat timing remains entirely server-owned. */
public final class CrystalFrogCombatAnimation {
    private float charge, oldCharge, guard, oldGuard, recovery, oldRecovery;
    private float spit, oldSpit;
    private float shell, oldShell, shellFlash, oldShellFlash;
    private int shellHitPanel, brokenHold;

    public void tick(byte pose, boolean casting, float shellHealth) {
        oldShell = shell;
        oldShellFlash = shellFlash;
        if (brokenHold > 0) brokenHold--;
        shell += (((shellHealth > 0 && brokenHold == 0) ? 1 : 0) - shell) * 0.3F;
        shellFlash = Math.max(0, shellFlash - 0.2F);
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
    public float shell(float partialTick) { return blend(oldShell, shell, partialTick); }
    public float shellFlash(float partialTick) { return blend(oldShellFlash, shellFlash, partialTick); }
    public int shellHitPanel() { return shellHitPanel; }
    public void shellHit(int panel) { shellHitPanel = panel; shellFlash = oldShellFlash = 1; }
    public void shellBroken() { shell = oldShell = shellFlash = oldShellFlash = 0; brokenHold = 6; }
    private static float blend(float old, float current, float partialTick) { return old + (current - old) * Math.clamp(partialTick, 0, 1); }
}
