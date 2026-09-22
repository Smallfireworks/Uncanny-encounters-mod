package com.ignilumen.uncannyencounters.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.client.model.CaveAnglerGeometry;
import com.ignilumen.uncannyencounters.client.render.CaveAnglerRenderer;
import com.ignilumen.uncannyencounters.client.render.AnglerTongueRenderer;

public class UncannyEncountersClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(CaveAnglerRenderer.LAYER, CaveAnglerGeometry::createLayer);
		ModelLayerRegistry.registerModelLayer(AnglerTongueRenderer.LAYER, AnglerTongueRenderer::createLayer);
		EntityRenderers.register(ModEntities.CAVE_ANGLER, CaveAnglerRenderer::new);
		EntityRenderers.register(ModEntities.ANGLER_TONGUE, AnglerTongueRenderer::new);
	}
}
