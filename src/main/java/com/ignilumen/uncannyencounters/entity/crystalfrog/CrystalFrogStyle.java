package com.ignilumen.uncannyencounters.entity.crystalfrog;

import java.util.UUID;
import net.minecraft.network.chat.Component;

/** A stable fighting style, independent of the frog's health and attack rolls. */
public enum CrystalFrogStyle {
    POUNCER("pouncer"), SKIRMISHER("skirmisher"), GUARD("guard");

    private final String id;
    CrystalFrogStyle(String id) { this.id = id; }
    public String id() { return id; }
    public Component description() { return Component.translatable("style.uncannyencounters.crystal_frog." + id); }

    public static CrystalFrogStyle from(String id, UUID fallback) {
        for (CrystalFrogStyle style : values()) if (style.id.equals(id)) return style;
        // Old frogs receive a repeatable style even if a chunk is reloaded before its next save.
        return values()[Math.floorMod(fallback.hashCode(), values().length)];
    }
}
