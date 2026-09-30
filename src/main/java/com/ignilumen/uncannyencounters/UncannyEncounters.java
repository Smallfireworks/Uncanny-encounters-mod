package com.ignilumen.uncannyencounters;

import net.fabricmc.api.ModInitializer;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.block.ModBlocks;
import com.ignilumen.uncannyencounters.item.ModItems;
import com.ignilumen.uncannyencounters.item.ModCreativeTabs;
import com.ignilumen.uncannyencounters.entity.zombieplayer.ZombiePlayerSpawns;
import com.ignilumen.uncannyencounters.entity.lightmoth.MothLights;
import com.ignilumen.uncannyencounters.entity.lightmoth.MonsterLures;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogDuels;
import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalents;
import com.ignilumen.uncannyencounters.effect.FrogEffects;
import com.ignilumen.uncannyencounters.entity.crystalfrog.GeodeFrogSpawns;
import com.ignilumen.uncannyencounters.worldgen.FrogCourtStructure;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UncannyEncounters implements ModInitializer {
	public static final String MOD_ID = "uncannyencounters";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		FrogEffects.initialize();
		ModBlocks.initialize();
		ModItems.initialize();
		ModEntities.initialize();
		ModCreativeTabs.initialize();
		com.ignilumen.uncannyencounters.block.FrogBreedingBoxBlockEntity.initialize();
		FrogCourtStructure.initialize();
		GeodeFrogSpawns.initialize();
		ZombiePlayerSpawns.initialize();
		MothLights.initialize();
		MonsterLures.initialize();
		CrystalFrogDuels.initialize();
		CrystalFrogTalents.initialize();
		LOGGER.info("Uncanny Encounters initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
