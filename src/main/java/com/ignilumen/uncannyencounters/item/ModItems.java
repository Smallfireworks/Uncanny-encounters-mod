package com.ignilumen.uncannyencounters.item;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Item VISCOUS_AMETHYST = register("viscous_amethyst");
    public static final Item ACTIVATED_AMETHYST = register("activated_amethyst");

    private static Item register(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, UncannyEncounters.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(VISCOUS_AMETHYST);
            entries.accept(ACTIVATED_AMETHYST);
        });
    }

    private ModItems() {}
}
