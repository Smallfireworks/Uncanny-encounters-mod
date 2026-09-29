package com.ignilumen.uncannyencounters.client.render;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.client.model.CrystalFrogModel;
import com.ignilumen.uncannyencounters.client.model.CrystalFrogGeometry;
import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public final class CrystalFrogRenderer extends MobRenderer<CrystalFrog, CrystalFrogRenderState, CrystalFrogModel> {
    private static final Identifier TEXTURE = UncannyEncounters.id("textures/entity/crystal_frog.png");

    public CrystalFrogRenderer(EntityRendererProvider.Context context) {
        super(context, new CrystalFrogModel(CrystalFrogGeometry.load(context.getResourceManager())), 0.32F * CrystalFrog.SIZE_SCALE);
    }
    @Override public CrystalFrogRenderState createRenderState() { return new CrystalFrogRenderState(); }
    @Override public Identifier getTextureLocation(CrystalFrogRenderState state) { return TEXTURE; }
    @Override public void extractRenderState(CrystalFrog entity, CrystalFrogRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.hopProgress = entity.hopProgress(partialTick);
        state.reflectionProgress = entity.reflectionProgress(partialTick);
        state.attackProgress = entity.getSwingAnimation(partialTick);
        state.sitting = entity.isInSittingPose();
        state.tame = entity.isTame();
    }
}
