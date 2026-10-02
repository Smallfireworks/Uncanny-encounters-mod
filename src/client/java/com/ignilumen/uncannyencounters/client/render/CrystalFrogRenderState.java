package com.ignilumen.uncannyencounters.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class CrystalFrogRenderState extends LivingEntityRenderState {
    public String variant = "normal";
    public net.minecraft.world.phys.Vec3 tongueStart, tongueEnd;
    public float hindLegRotation, frontLegRotation, hopPitch, landingCompression, reflectionProgress, attackProgress;
    public float duelCharge, duelGuard, duelRecovery, talentSpit;
    public boolean sitting, tame, talentShell;
    public float shellStrength, shellVisibility, shellFlash;
    public int shellHitPanel;
}
