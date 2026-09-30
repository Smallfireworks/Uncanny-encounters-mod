package com.ignilumen.uncannyencounters.client.render;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.client.model.LightMothModel;
import com.ignilumen.uncannyencounters.client.model.TriangleMeshGeometry;
import com.ignilumen.uncannyencounters.entity.LightMoth;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public final class LightMothRenderer extends MobRenderer<LightMoth,LightMothRenderState,LightMothModel> {
    private static final Identifier TEXTURE=UncannyEncounters.id("textures/entity/light_moth.png");
    public LightMothRenderer(EntityRendererProvider.Context context) {
        super(context,new LightMothModel(TriangleMeshGeometry.load(context.getResourceManager(),
                UncannyEncounters.id("geometry/light_moth.json"),1)),0.15F);
    }
    @Override public LightMothRenderState createRenderState() { return new LightMothRenderState(); }
    @Override public Identifier getTextureLocation(LightMothRenderState state) { return TEXTURE; }
    @Override public void extractRenderState(LightMoth entity, LightMothRenderState state, float partialTick) {
        super.extractRenderState(entity,state,partialTick);
        state.behavior=entity.behavior();
    }
}
