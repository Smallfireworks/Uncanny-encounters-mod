package com.ignilumen.uncannyencounters.client.render;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.client.model.CrystalFrogModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;

/** Six translucent facets follow the frog and fade without becoming detached particle trails. */
public final class CrystalShellLayer extends RenderLayer<CrystalFrogRenderState, CrystalFrogModel> {
    private static final Identifier TEXTURE = UncannyEncounters.id("textures/entity/crystal_shell_panel.png");

    public CrystalShellLayer(RenderLayerParent<CrystalFrogRenderState, CrystalFrogModel> parent) { super(parent); }

    @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, CrystalFrogRenderState state, float yaw, float pitch) {
        if (state.isInvisible || state.shellVisibility < 0.01F) return;
        // Copy values: the collector executes geometry later, after render-state extraction.
        float visibility = state.shellVisibility, strength = state.shellStrength, flash = state.shellFlash;
        int hitPanel = state.shellHitPanel;
        float age = state.ageInTicks;
        collector.order(1).submitCustomGeometry(poses, RenderTypes.entityTranslucent(TEXTURE), (pose, vertices) -> {
            for (int i = 0; i < 6; i++) {
                double angle = age * 0.025 + i * Math.PI / 3;
                float nx = (float) Math.cos(angle), nz = (float) Math.sin(angle);
                float radius = 0.43F + visibility * 0.16F;
                float cx = nx * radius, cz = nz * radius;
                float tx = -nz * 0.145F, tz = nx * 0.145F;
                float alpha = visibility * (0.32F + strength * 0.45F);
                if (i == hitPanel) alpha = Math.min(1, alpha + flash * 0.65F);
                int color = ARGB.colorFromFloat(alpha, 1, 1, 1);
                float top = 0.90F, bottom = 1.48F;
                vertex(vertices, pose, cx - tx, top, cz - tz, 0, 0, color, nx, nz);
                vertex(vertices, pose, cx - tx, bottom, cz - tz, 0, 1, color, nx, nz);
                vertex(vertices, pose, cx + tx, bottom, cz + tz, 1, 1, color, nx, nz);
                vertex(vertices, pose, cx + tx, top, cz + tz, 1, 0, color, nx, nz);
                // The 26.3 entity-translucent pipeline is already double-sided.
            }
        });
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int color, float nx, float nz) {
        vertices.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, nx, 0, nz);
    }
}
