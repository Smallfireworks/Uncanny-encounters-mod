package com.ignilumen.uncannyencounters.client.render;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.client.model.CaveAnglerModel;
import com.ignilumen.uncannyencounters.entity.CaveAngler;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public final class CaveAnglerRenderer extends MobRenderer<CaveAngler, CaveAnglerRenderState, CaveAnglerModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(UncannyEncounters.id("cave_angler"), "main");
    public static final Identifier TEXTURE = UncannyEncounters.id("textures/entity/cave_angler.png");

    public CaveAnglerRenderer(EntityRendererProvider.Context context) {
        super(context, new CaveAnglerModel(context.bakeLayer(LAYER)), 0);
    }
    @Override public CaveAnglerRenderState createRenderState() { return new CaveAnglerRenderState(); }
    @Override public Identifier getTextureLocation(CaveAnglerRenderState state) { return TEXTURE; }
    @Override public void extractRenderState(CaveAngler entity, CaveAnglerRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.phase = entity.phase();
        state.bodyRot = 0;
    }
}
