package com.ignilumen.uncannyencounters.client.model;

import com.ignilumen.uncannyencounters.client.render.CrystalFrogRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class CrystalFrogModel extends EntityModel<CrystalFrogRenderState> {
    private final ModelPart body, head, leftArm, rightArm, leftLeg, rightLeg, crystals, bond;

    public CrystalFrogModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = body.getChild("head");
        crystals = body.getChild("crystals");
        bond = body.getChild("bond");
        leftArm = root.getChild("left_arm");
        rightArm = root.getChild("right_arm");
        leftLeg = root.getChild("left_leg");
        rightLeg = root.getChild("right_leg");
    }

    @Override public void setupAnim(CrystalFrogRenderState state) {
        super.setupAnim(state);
        bond.visible = state.tame;
        head.yRot = Math.clamp(state.yRot, -6, 6) * Mth.DEG_TO_RAD;
        head.xRot = Math.clamp(state.xRot, -5, 5) * Mth.DEG_TO_RAD;
        body.y += Mth.sin(state.ageInTicks * 0.08F) * 0.03F;
        if (state.sitting) {
            body.y += 0.25F;
            leftArm.xRot = rightArm.xRot = -0.1F;
            leftLeg.xRot = rightLeg.xRot = -0.15F;
        } else if (state.isInWater) {
            float kick = Mth.sin(state.ageInTicks * 0.45F) * (0.1F + state.walkAnimationSpeed * 0.2F);
            leftLeg.xRot = rightLeg.xRot = -0.1F + kick;
            leftArm.xRot = rightArm.xRot = -0.05F - kick * 0.7F;
            leftLeg.yRot = 0.1F;
            rightLeg.yRot = -0.1F;
        } else {
            float impact = state.landingCompression;
            // Push the hind legs back on ascent, fold at the apex, then reach for the floor.
            leftLeg.xRot = rightLeg.xRot = state.hindLegRotation + impact * 0.1F;
            leftArm.xRot = rightArm.xRot = state.frontLegRotation - impact * 0.06F;
            float pitch = state.hopPitch;
            ModelPart root = root();
            float baseScale = root.yScale;
            root.xScale *= 1 + impact * 0.025F;
            root.yScale *= 1 - impact * 0.045F;
            root.zScale *= 1 + impact * 0.025F;
            root.xRot += pitch;
            // Squash around the feet, pitch around the torso, keeping every mesh part together.
            root.y += 24 * (baseScale - root.yScale) + 21.5F * root.yScale * (1 - Mth.cos(pitch));
            root.z -= 21.5F * root.yScale * Mth.sin(pitch);
        }
        head.z -= Mth.sin(state.attackProgress * Mth.PI) * 0.35F;
        // Show charging and bracing around the same foot anchor as the normal hop animation.
        float crouch = state.duelCharge * 0.09F + state.duelGuard * 0.055F + state.duelRecovery * 0.035F + state.talentSpit * 0.04F;
        ModelPart root = root();
        float before = root.yScale;
        root.yScale *= 1 - crouch;
        root.xScale *= 1 + crouch * 0.3F;
        root.zScale *= 1 + crouch * 0.3F;
        root.y += 24 * (before - root.yScale);
        leftLeg.xRot -= state.duelCharge * 0.25F;
        rightLeg.xRot -= state.duelCharge * 0.25F;
        leftArm.xRot -= state.duelGuard * 0.2F;
        rightArm.xRot -= state.duelGuard * 0.2F;
        head.xScale *= 1 + state.talentSpit * 0.06F;
        head.zScale *= 1 + state.talentSpit * 0.08F;
        float flare = 1 + Mth.sin(state.reflectionProgress * Mth.PI) * 0.025F + state.duelGuard * 0.025F + (state.talentShell ? 0.06F : 0);
        crystals.xScale = crystals.yScale = crystals.zScale = flare;
    }
}
