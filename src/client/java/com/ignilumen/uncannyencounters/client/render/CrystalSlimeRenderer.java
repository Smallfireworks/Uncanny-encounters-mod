package com.ignilumen.uncannyencounters.client.render;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.CrystalSlimeShot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/** A dedicated wet-glob sprite; the collaborator's item artwork remains independent. */
public final class CrystalSlimeRenderer extends EntityRenderer<CrystalSlimeShot, EntityRenderState> {
    private static final Identifier TEXTURE = UncannyEncounters.id("textures/entity/crystal_slime_glob.png");
    public CrystalSlimeRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }

    @Override public void submit(EntityRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose();
        poses.rotate(camera.orientation);
        collector.submitCustomGeometry(poses, RenderTypes.entityTranslucent(TEXTURE), (pose, vertices) -> {
            vertex(vertices, pose, -0.16F, -0.16F, 0, 1);
            vertex(vertices, pose, 0.16F, -0.16F, 1, 1);
            vertex(vertices, pose, 0.16F, 0.16F, 1, 0);
            vertex(vertices, pose, -0.16F, 0.16F, 0, 0);
        });
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v) {
        vertices.addVertex(pose, x, y, 0).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
    }
}
