package com.ignilumen.uncannyencounters.client.render;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.FrogKeeper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public final class FrogKeeperRenderer extends HumanoidMobRenderer<FrogKeeper, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(UncannyEncounters.id("frog_keeper"), "main");
    private static final Identifier TEXTURE = UncannyEncounters.id("textures/entity/frog_keeper.png");
    public FrogKeeperRenderer(EntityRendererProvider.Context context) { super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.4F); }
    @Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
    @Override public Identifier getTextureLocation(HumanoidRenderState state) { return TEXTURE; }
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0);
        var root = mesh.getRoot();
        root.getChild("head").addOrReplaceChild("crystal_crown", CubeListBuilder.create().texOffs(52, 0)
                .addBox(-1, -5, -1, 2, 5, 2), PartPose.offsetAndRotation(-3, -7, 1, 0.2F, 0.4F, -0.3F));
        root.getChild("body").addOrReplaceChild("crystal_shoulder", CubeListBuilder.create().texOffs(52, 0)
                .addBox(-1.5F, -5, -1.5F, 3, 6, 3), PartPose.offsetAndRotation(-5, 1, 1, -0.25F, 0, -0.6F));
        root.getChild("left_arm").addOrReplaceChild("crystal_forearm", CubeListBuilder.create().texOffs(52, 0)
                .addBox(-1, -4, -1, 2, 5, 2), PartPose.offsetAndRotation(3, 7, 0, 0, 0, 0.45F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
