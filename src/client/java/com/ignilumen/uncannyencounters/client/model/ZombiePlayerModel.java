package com.ignilumen.uncannyencounters.client.model;

import com.ignilumen.uncannyencounters.client.render.ZombiePlayerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/** Keeps the ordinary player gait, adding a small hand-to-mouth eating motion. */
public final class ZombiePlayerModel extends HumanoidModel<ZombiePlayerRenderState> {
    private final boolean slim;
    private final ModelPart jacket, leftPants, rightPants, leftSleeve, rightSleeve;

    public ZombiePlayerModel(ModelPart root, boolean slim) {
        super(root, RenderTypes::entityTranslucent);
        this.slim = slim;
        jacket = body.getChild("jacket");
        leftPants = leftLeg.getChild("left_pants");
        rightPants = rightLeg.getChild("right_pants");
        leftSleeve = leftArm.getChild("left_sleeve");
        rightSleeve = rightArm.getChild("right_sleeve");
    }

    @Override public void setupAnim(ZombiePlayerRenderState state) {
        hat.visible = state.showHat;
        jacket.visible = state.showJacket;
        leftPants.visible = state.showLeftPants;
        rightPants.visible = state.showRightPants;
        leftSleeve.visible = state.showLeftSleeve;
        rightSleeve.visible = state.showRightSleeve;
        super.setupAnim(state);
        if (state.isUsingItem && state.useItemHand == InteractionHand.OFF_HAND) {
            HumanoidArm side = state.useItemHand.asArm(state.mainArm);
            ModelPart arm = getArm(side);
            arm.xRot = -1.5F + head.xRot * 0.4F + Mth.sin(state.ticksUsingItem * 1.7F) * 0.1F;
            arm.yRot = side == HumanoidArm.RIGHT ? -0.4F : 0.4F;
        }
    }

    @Override public void translateToHand(ZombiePlayerRenderState state, HumanoidArm arm, PoseStack poseStack) {
        root().translateAndRotate(poseStack);
        ModelPart part = getArm(arm);
        float originalX = part.x;
        if (slim) part.x += 0.5F * (arm == HumanoidArm.RIGHT ? 1 : -1);
        part.translateAndRotate(poseStack);
        part.x = originalX;
    }
}
