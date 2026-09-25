package com.ignilumen.uncannyencounters.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/** Keeps the ordinary player gait, adding a small hand-to-mouth eating motion. */
public final class ZombiePlayerModel extends PlayerModel {
    public ZombiePlayerModel(ModelPart root, boolean slim) { super(root, slim); }

    @Override public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);
        if (state.isUsingItem && state.useItemHand == InteractionHand.OFF_HAND) {
            HumanoidArm side = state.useItemHand.asArm(state.mainArm);
            ModelPart arm = getArm(side);
            arm.xRot = -1.5F + head.xRot * 0.4F + Mth.sin(state.ticksUsingItem * 1.7F) * 0.1F;
            arm.yRot = side == HumanoidArm.RIGHT ? -0.4F : 0.4F;
        }
    }
}
