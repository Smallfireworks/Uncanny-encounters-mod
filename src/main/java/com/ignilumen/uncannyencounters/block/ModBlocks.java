package com.ignilumen.uncannyencounters.block;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    private static final ResourceKey<Block> ZOMBIE_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, UncannyEncounters.id("zombie_block"));
    /**
     * Zombie players' infinite building material. It has no item and no loot, cannot be pushed,
     * and is removed again by {@link com.ignilumen.uncannyencounters.entity.zombieplayer.TemporaryEdits}.
     */
    public static final Block ZOMBIE_BLOCK = Registry.register(BuiltInRegistries.BLOCK, ZOMBIE_BLOCK_KEY,
            new Block(BlockBehaviour.Properties.of().setId(ZOMBIE_BLOCK_KEY).mapColor(MapColor.TERRACOTTA_GREEN)
                    .strength(0.6F).sound(SoundType.WART_BLOCK).noLootTable().pushReaction(PushReaction.IMMOVEABLE)));

    public static void initialize() {}

    private ModBlocks() {}
}
