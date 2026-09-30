package com.ignilumen.uncannyencounters.item;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.block.ModBlocks;
import com.ignilumen.uncannyencounters.entity.lightmoth.LureBait;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

public final class ModItems {
    public static final Item MOTH_SCALE_DUST = register("moth_scale_dust");
    public static final Item MOTH_LURE = registerBlock("moth_lure",ModBlocks.MOTH_LURE);
    public static final Item ENHANCED_MOTH_LURE = registerBlock("enhanced_moth_lure",ModBlocks.ENHANCED_MOTH_LURE);
    public static final Map<LureBait, Item> BAITED_LURES = registerBaitedLures();
    public static final Item VISCOUS_AMETHYST = register("viscous_amethyst");
    public static final Item ACTIVATED_AMETHYST = register("activated_amethyst");

    private static Item register(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, UncannyEncounters.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }
    private static Map<LureBait, Item> registerBaitedLures() {
        Map<LureBait, Item> items = new EnumMap<>(LureBait.class);
        for (LureBait bait : LureBait.values()) items.put(bait, registerBlock(bait.blockId(), ModBlocks.BAITED_LURES.get(bait)));
        return Map.copyOf(items);
    }
    private static Item registerBlock(String name, Block block) {
        ResourceKey<Item> key=ResourceKey.create(Registries.ITEM,UncannyEncounters.id(name));
        return Registry.register(BuiltInRegistries.ITEM,key,new BlockItem(block,new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(VISCOUS_AMETHYST);
            entries.accept(ACTIVATED_AMETHYST);
            entries.accept(MOTH_SCALE_DUST);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(MOTH_LURE);
            entries.accept(ENHANCED_MOTH_LURE);
            for (LureBait bait : LureBait.values()) entries.accept(BAITED_LURES.get(bait));
        });
    }

    private ModItems() {}
}
