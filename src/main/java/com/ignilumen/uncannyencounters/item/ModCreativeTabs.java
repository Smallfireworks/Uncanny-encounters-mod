package com.ignilumen.uncannyencounters.item;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.entity.lightmoth.LureBait;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** The mod's usable items in a stable order. A filled cage requires an actual captured frog. */
public final class ModCreativeTabs {
    public static final CreativeModeTab UNCANNY_ENCOUNTERS = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            UncannyEncounters.id("uncanny_encounters"), FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.uncannyencounters"))
                    .icon(() -> new ItemStack(ModItems.ACTIVATED_AMETHYST))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.VISCOUS_AMETHYST);
                        output.accept(ModItems.ACTIVATED_AMETHYST);
                        output.accept(ModItems.MOTH_SCALE_DUST);
                        output.accept(ModItems.FROG_CAGE);
                        output.accept(ModItems.FROG_BREEDING_BOX);
                        output.accept(ModItems.FROG_ALTAR);
                        output.accept(ModItems.MOTH_LURE);
                        output.accept(ModItems.ENHANCED_MOTH_LURE);
                        for (LureBait bait : LureBait.values()) output.accept(ModItems.BAITED_LURES.get(bait));
                        output.accept(ModEntities.CRYSTAL_FROG_SPAWN_EGG);
                        output.accept(ModEntities.FROG_KEEPER_SPAWN_EGG);
                        output.accept(ModEntities.CAVE_ANGLER_SPAWN_EGG);
                        output.accept(ModEntities.ZOMBIE_PLAYER_SPAWN_EGG);
                        output.accept(ModEntities.LIGHT_MOTH_SPAWN_EGG);
                    }).build());

    public static void initialize() {}
    private ModCreativeTabs() {}
}
