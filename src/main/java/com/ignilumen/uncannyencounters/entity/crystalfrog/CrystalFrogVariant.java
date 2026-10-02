package com.ignilumen.uncannyencounters.entity.crystalfrog;

import net.minecraft.network.chat.Component;

/** An irreversible phenotype, independent of the two inherited talent slots. */
public enum CrystalFrogVariant {
    NORMAL("normal"), ZOMBIE("zombie"), ECHO("echo"), ENDER("ender");

    private final String id;
    CrystalFrogVariant(String id) { this.id = id; }
    public String id() { return id; }
    public Component description() { return Component.translatable("variant.uncannyencounters.crystal_frog." + id); }
    public static CrystalFrogVariant from(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        return NORMAL;
    }
}
