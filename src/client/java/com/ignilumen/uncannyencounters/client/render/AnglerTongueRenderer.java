package com.ignilumen.uncannyencounters.client.render;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.AnglerTongue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class AnglerTongueRenderer extends EntityRenderer<AnglerTongue, AnglerTongueRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(UncannyEncounters.id("angler_tongue"), "main");
    private final TongueModel model;

    public AnglerTongueRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new TongueModel(context.bakeLayer(LAYER));
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        for (int i = 0; i < 28; i++) {
            PartDefinition segment = mesh.getRoot().addOrReplaceChild("segment_" + i,
                    CubeListBuilder.create().texOffs(0, 64).addBox(-1.4F, 0, -1.4F, 2.8F, 8, 2.8F),
                    PartPose.offset(0, i * 8, 0));
            if (i % 2 == 0) {
                float side = i % 4 == 0 ? 1 : -1;
                segment.addOrReplaceChild("barb", CubeListBuilder.create().texOffs(0, 96)
                        .addBox(-0.75F, 0, -0.75F, 1.5F, 4, 1.5F),
                        PartPose.offsetAndRotation(side * 1.5F, 2, 0, 0, 0, -side * 0.65F));
            }
        }
        mesh.getRoot().addOrReplaceChild("hook", CubeListBuilder.create().texOffs(0, 96)
                // Extend below the first segment so their bottom faces are not coplanar.
                .addBox(-3, -0.25F, -2, 6, 2, 4), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(AnglerTongue entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        float fraction = entity.isRetracting()
                ? Math.clamp((entity.retractTicks() - partialTick) / AnglerTongue.RETRACT_TICKS, 0F, 1F)
                : 1F;
        state.length = entity.length() * fraction;
        state.retractOffset = entity.length() - state.length;
        state.hurt = entity.isSevered() && entity.isRetracting();
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose();
        // Move the tip towards the mouth while shortening, leaving the top fixed.
        poses.translate(0, state.retractOffset, 0);
        int overlay = OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(state.hurt));
        collector.submitModel(model, state, poses, RenderTypes.entityCutoutCull(CaveAnglerRenderer.TEXTURE),
                state.lightCoords, overlay, -1, null, state.outlineColor);
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }

    public static final class State extends EntityRenderState {
        public float length;
        public float retractOffset;
        public boolean hurt;
    }

    private static final class TongueModel extends EntityModel<State> {
        TongueModel(ModelPart root) { super(root); }
        @Override public void setupAnim(State state) {
            super.setupAnim(state);
            for (int i = 0; i < 28; i++) {
                ModelPart segment = root.getChild("segment_" + i);
                float remaining = state.length * 2 - i;
                segment.visible = remaining > 0;
                segment.yScale = Math.clamp(remaining, 0, 1);
            }
        }
    }
}
