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
            float extension = Mth.sin(state.hopProgress * Mth.PI);
            leftLeg.xRot = rightLeg.xRot = extension * 0.35F;
            leftArm.xRot = rightArm.xRot = -extension * 0.25F;
        }
        head.z -= Mth.sin(state.attackProgress * Mth.PI) * 0.35F;
        float flare = 1 + Mth.sin(state.reflectionProgress * Mth.PI) * 0.025F;
        crystals.xScale = crystals.yScale = crystals.zScale = flare;
    }
}
