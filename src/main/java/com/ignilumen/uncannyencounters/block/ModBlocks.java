package com.ignilumen.uncannyencounters.block;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.lightmoth.LureBait;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    public static final Block FROG_BREEDING_BOX = registerBreedingBox();
    private static Block registerBreedingBox() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, UncannyEncounters.id("frog_breeding_box"));
        return Registry.register(BuiltInRegistries.BLOCK, key, new FrogBreedingBoxBlock(BlockBehaviour.Properties.of()
                .setId(key).mapColor(MapColor.COLOR_PURPLE).strength(2.5F).sound(SoundType.WOOD).pushReaction(PushReaction.IMMOVEABLE)));
    }
    public static final Block FROG_ALTAR = registerAltar();
    private static Block registerAltar() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, UncannyEncounters.id("frog_altar"));
        return Registry.register(BuiltInRegistries.BLOCK, key, new FrogAltarBlock(BlockBehaviour.Properties.of()
                .setId(key).mapColor(MapColor.COLOR_PURPLE).strength(-1, 3600000).sound(SoundType.AMETHYST)
                .noLootTable().lightLevel(state -> 10).pushReaction(PushReaction.IMMOVEABLE)));
    }
    public static final Block DIMMED_TORCH = registerLight("dimmed_torch", Blocks.TORCH, DimmedLights.Torch::new, 0);
    public static final Block DIMMED_WALL_TORCH = registerLight("dimmed_wall_torch", Blocks.WALL_TORCH, DimmedLights.WallTorch::new, 0);
    public static final Block DIMMED_LANTERN = registerLight("dimmed_lantern", Blocks.LANTERN, DimmedLights.Lantern::new, 0);
    public static final Block MOTH_LURE = registerLight("moth_lure", Blocks.LANTERN, LanternBlock::new, 15);
    public static final Block ENHANCED_MOTH_LURE = registerLight("enhanced_moth_lure", Blocks.LANTERN, EnhancedLureBlock::new, 15);
    public static final Block LURE_LIGHT = registerLureLight();
    public static final Map<LureBait, Block> BAITED_LURES = registerBaitedLures();

    private static Block registerLureLight() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, UncannyEncounters.id("lure_light"));
        // Vanilla LIGHT's map-color function reads WATERLOGGED; this air-only light has no such state.
        return Registry.register(BuiltInRegistries.BLOCK, key, new LureLightBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHT)
                .setId(key).mapColor(MapColor.NONE).replaceable().noLootTable().lightLevel(state -> 15)));
    }

    private static Map<LureBait, Block> registerBaitedLures() {
        Map<LureBait, Block> blocks = new EnumMap<>(LureBait.class);
        for (LureBait bait : LureBait.values()) blocks.put(bait,
                registerLight(bait.blockId(), Blocks.LANTERN, properties -> new BaitedLureBlock(properties, bait), 15));
        return Map.copyOf(blocks);
    }

    private static Block registerLight(String name, Block original,
                                       java.util.function.Function<BlockBehaviour.Properties, Block> factory, int light) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, UncannyEncounters.id(name));
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(original).setId(key).lightLevel(s -> light);
        if (light == 0) properties.overrideLootTable(original.getLootTable());
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties));
    }
    private static final ResourceKey<Block> ZOMBIE_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, UncannyEncounters.id("zombie_block"));
    /**
     * Zombie players' infinite building material. It has no item and no loot, cannot be pushed,
     * and is removed again by {@link com.ignilumen.uncannyencounters.entity.zombieplayer.TemporaryEdits}.
     */
    public static final Block ZOMBIE_BLOCK = Registry.register(BuiltInRegistries.BLOCK, ZOMBIE_BLOCK_KEY,
            new Block(BlockBehaviour.Properties.of().setId(ZOMBIE_BLOCK_KEY).mapColor(MapColor.TERRACOTTA_GREEN)
                    .strength(0.6F).sound(SoundType.WART_BLOCK).noLootTable().pushReaction(PushReaction.IMMOVEABLE)));

    public static void initialize() { EnhancedLureBlock.initialize(); }

    private ModBlocks() {}
}
