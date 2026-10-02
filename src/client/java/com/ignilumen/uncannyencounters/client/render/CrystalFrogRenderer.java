package com.ignilumen.uncannyencounters.client.render;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.client.model.CrystalFrogModel;
import com.ignilumen.uncannyencounters.client.model.CrystalFrogGeometry;
import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

public final class CrystalFrogRenderer extends MobRenderer<CrystalFrog, CrystalFrogRenderState, CrystalFrogModel> {
    private static final Identifier TEXTURE = UncannyEncounters.id("textures/entity/crystal_frog.png");

    public CrystalFrogRenderer(EntityRendererProvider.Context context) {
        super(context, new CrystalFrogModel(CrystalFrogGeometry.load(context.getResourceManager())), 0.32F * CrystalFrog.SIZE_SCALE);
        addLayer(new CrystalShellLayer(this));
    }
    @Override public CrystalFrogRenderState createRenderState() { return new CrystalFrogRenderState(); }
    @Override public Identifier getTextureLocation(CrystalFrogRenderState state) { return state.variant.equals("normal") ? TEXTURE : UncannyEncounters.id("textures/entity/crystal_frog_" + state.variant + ".png"); }
    @Override protected void scale(CrystalFrogRenderState state, PoseStack poses) {
        poses.scale(state.ageScale, state.ageScale, state.ageScale);
    }
    @Override public void extractRenderState(CrystalFrog entity, CrystalFrogRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.variant = entity.variant().id();
        state.hindLegRotation = entity.hopAnimation().hindLeg(partialTick);
        state.frontLegRotation = entity.hopAnimation().frontLeg(partialTick);
        state.hopPitch = entity.hopAnimation().pitch(partialTick);
        state.landingCompression = entity.hopAnimation().landing(partialTick);
        state.reflectionProgress = entity.reflectionProgress(partialTick);
        state.attackProgress = entity.getSwingAnimation(partialTick);
        state.duelCharge = entity.combatAnimation().charge(partialTick);
        state.duelGuard = entity.combatAnimation().guard(partialTick);
        state.duelRecovery = entity.combatAnimation().recovery(partialTick);
        state.talentSpit = entity.combatAnimation().spit(partialTick);
        state.talentShell = entity.hasTalentShell();
        state.shellStrength = Math.clamp(entity.talentShellHealth() / entity.talents().shellCapacity(), 0, 1);
        state.shellVisibility = entity.combatAnimation().shell(partialTick);
        state.shellFlash = entity.combatAnimation().shellFlash(partialTick);
        state.shellHitPanel = entity.combatAnimation().shellHitPanel();
        state.sitting = entity.isInSittingPose();
        state.tame = entity.isTame();
        var target = entity.level().getEntity(entity.tongueTargetId());
        state.tongueStart = target == null ? null : new Vec3(0, entity.getEyeHeight(), 0).add(entity.getLookAngle().scale(0.2 * entity.getScale()));
        state.tongueEnd = target == null ? null : target.getPosition(partialTick).subtract(entity.getPosition(partialTick)).add(0, target.getBbHeight() * 0.5, 0);
    }

    @Override public void submit(CrystalFrogRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poses, collector, camera);
        if (state.tongueStart == null || state.tongueEnd == null || state.isInvisible) return;
        Vec3 start = state.tongueStart, end = state.tongueEnd;
        Vec3 direction = end.subtract(start).normalize();
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.001) side = new Vec3(1, 0, 0);
        Vec3 a = side.normalize().scale(0.04 * state.scale), b = direction.cross(a).normalize().scale(0.04 * state.scale);
        int light = state.lightCoords;
        collector.submitCustomGeometry(poses, RenderTypes.entityCutout(UncannyEncounters.id("textures/entity/frog_tongue.png")), (pose, vertices) -> {
            for (Vec3 edge : new Vec3[]{a, b}) {
                tongueVertex(vertices, pose, start.subtract(edge), 0, 0, light);
                tongueVertex(vertices, pose, end.subtract(edge), 0, 1, light);
                tongueVertex(vertices, pose, end.add(edge), 1, 1, light);
                tongueVertex(vertices, pose, start.add(edge), 1, 0, light);
            }
        });
    }
    private static void tongueVertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 pos, float u, float v, int light) {
        vertices.addVertex(pose, (float)pos.x, (float)pos.y, (float)pos.z).setColor(-1).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
}
