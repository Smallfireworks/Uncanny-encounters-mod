package com.ignilumen.uncannyencounters.entity;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ModEntities {
    public static final EntityType<CaveAngler> CAVE_ANGLER = register("cave_angler",
            EntityType.Builder.of(CaveAngler::new, MobCategory.MONSTER)
                    .sized(1.0F, 1.75F).eyeHeight(0.4F).clientTrackingRange(10).updateInterval(1));
    public static final EntityType<AnglerTongue> ANGLER_TONGUE = register("angler_tongue",
            EntityType.Builder.of(AnglerTongue::new, MobCategory.MISC)
                    .sized(0.45F, 1).clientTrackingRange(10).updateInterval(1).noSave().noSummon());
    public static final Item CAVE_ANGLER_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM,
            UncannyEncounters.id("cave_angler_spawn_egg"), new SpawnEggItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, UncannyEncounters.id("cave_angler_spawn_egg")))
                    .spawnEgg(CAVE_ANGLER)));

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, UncannyEncounters.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(CAVE_ANGLER, CaveAngler.createAttributes());
        SpawnPlacements.register(CAVE_ANGLER, SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CaveAngler::canSpawn);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, CAVE_ANGLER, 10, 1, 1);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
                .register(entries -> entries.accept(CAVE_ANGLER_SPAWN_EGG));
    }

    private ModEntities() {}
}
