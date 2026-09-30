package com.ignilumen.uncannyencounters.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.client.model.CaveAnglerGeometry;
import com.ignilumen.uncannyencounters.client.render.CrystalFrogRenderer;
import com.ignilumen.uncannyencounters.client.render.LightMothRenderer;
import com.ignilumen.uncannyencounters.client.render.CaveAnglerRenderer;
import com.ignilumen.uncannyencounters.client.render.AnglerTongueRenderer;
import com.ignilumen.uncannyencounters.client.render.ZombiePlayerRenderer;

public class UncannyEncountersClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.CRYSTAL_SLIME_SHOT, context -> new ThrownItemRenderer<>(context, 0.6F, true));
		EntityRenderers.register(ModEntities.LIGHT_MOTH, LightMothRenderer::new);
		EntityRenderers.register(ModEntities.CRYSTAL_FROG, CrystalFrogRenderer::new);
		EntityRenderers.register(ModEntities.ZOMBIE_PLAYER, ZombiePlayerRenderer::new);
		ModelLayerRegistry.registerModelLayer(CaveAnglerRenderer.LAYER, CaveAnglerGeometry::createLayer);
		ModelLayerRegistry.registerModelLayer(AnglerTongueRenderer.LAYER, AnglerTongueRenderer::createLayer);
		EntityRenderers.register(ModEntities.CAVE_ANGLER, CaveAnglerRenderer::new);
		EntityRenderers.register(ModEntities.ANGLER_TONGUE, AnglerTongueRenderer::new);
	}
}
