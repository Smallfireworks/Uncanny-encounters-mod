package com.ignilumen.uncannyencounters.entity.lightmoth;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import com.ignilumen.uncannyencounters.block.BaitedLureBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Explicit families: shared drops do not implicitly attract unrelated mobs. */
public enum LureBait {
    ROTTEN_FLESH("zombie", "husk", "drowned", "uncannyencounters:zombie_player"),
    BONE("skeleton", "stray", "bogged", "wither_skeleton"),
    STRING("spider", "cave_spider"),
    GUNPOWDER("creeper"),
    ENDER_PEARL("enderman"),
    SLIME_BALL("slime"),
    MAGMA_CREAM("magma_cube"),
    BLAZE_ROD("blaze"),
    BREEZE_ROD("breeze"),
    GHAST_TEAR("ghast"),
    PRISMARINE_SHARD("guardian", "elder_guardian");

    private final Set<Identifier> entities;

    LureBait(String... entities) {
        this.entities = Arrays.stream(entities).map(Identifier::parse).collect(Collectors.toUnmodifiableSet());
    }

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String blockId() { return "enhanced_moth_lure_" + id(); }
    public boolean attracts(EntityType<?> type) { return entities.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type)); }

    public static boolean supports(EntityType<?> type) {
        for (LureBait bait : values()) if (bait.attracts(type)) return true;
        return false;
    }

    public static @Nullable LureBait from(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                && item.getBlock() instanceof BaitedLureBlock block ? block.bait() : null;
    }
}
