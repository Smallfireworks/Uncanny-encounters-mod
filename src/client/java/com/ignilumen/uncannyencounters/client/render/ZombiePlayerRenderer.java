package com.ignilumen.uncannyencounters.client.render;

import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import com.ignilumen.uncannyencounters.client.model.ZombiePlayerModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;

public final class ZombiePlayerRenderer extends HumanoidMobRenderer<ZombiePlayer, AvatarRenderState, PlayerModel> {
    private final PlayerModel wide, slim;
    private final PlayerSkinRenderCache skins;

    public ZombiePlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombiePlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        wide = model;
        slim = new ZombiePlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        skins = context.getPlayerSkinRenderCache();
    }

    @Override public AvatarRenderState createRenderState() { return new AvatarRenderState(); }
    @Override public Identifier getTextureLocation(AvatarRenderState state) { return state.skin.body().texturePath(); }
    @Override protected int getModelTint(AvatarRenderState state) { return 0xFF91B579; }

    @Override public void extractRenderState(ZombiePlayer entity, AvatarRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.skin = skins.getOrDefault(entity.profile()).playerSkin();
        byte mask = entity.skinParts();
        state.showHat = shown(mask, PlayerModelPart.HAT);
        state.showJacket = shown(mask, PlayerModelPart.JACKET);
        state.showLeftPants = shown(mask, PlayerModelPart.LEFT_PANTS_LEG);
        state.showRightPants = shown(mask, PlayerModelPart.RIGHT_PANTS_LEG);
        state.showLeftSleeve = shown(mask, PlayerModelPart.LEFT_SLEEVE);
        state.showRightSleeve = shown(mask, PlayerModelPart.RIGHT_SLEEVE);
    }

    private static boolean shown(byte mask, PlayerModelPart part) { return (mask & part.getMask()) != 0; }

    @Override protected HumanoidModel.ArmPose getArmPose(ZombiePlayer entity, HumanoidArm arm) {
        var item = entity.getItemHeldByArm(arm);
        if (item.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        if (item.getItem() instanceof BowItem && entity.isUsingItem()) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        if (item.getItem() instanceof CrossbowItem) {
            if (entity.isUsingItem()) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            if (CrossbowItem.isCharged(item)) return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    @Override public void submit(AvatarRenderState state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
        model = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
        super.submit(state, stack, collector, camera);
    }
}
