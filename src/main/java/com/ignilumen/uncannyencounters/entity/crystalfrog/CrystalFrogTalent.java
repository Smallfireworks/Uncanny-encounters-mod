package com.ignilumen.uncannyencounters.entity.crystalfrog;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

public enum CrystalFrogTalent {
    NONE("none"), SLIME_SPIT("slime_spit"), CRYSTAL_SHELL("crystal_shell"),
    GROUND_SHOCK("ground_shock"), CRYSTAL_ECHO("crystal_echo"),
    SCATTER_SLIME("scatter_slime"), RETALIATING_SHELL("retaliating_shell"),
    DOUBLE_ECHO("double_echo"), FROG_KING("frog_king");

    private final String id;
    CrystalFrogTalent(String id) { this.id = id; }
    public String id() { return id; }
    public boolean advanced() { return this == SCATTER_SLIME || this == RETALIATING_SHELL || this == DOUBLE_ECHO; }
    public CrystalFrogTalent family() {
        return switch (this) {
            case SCATTER_SLIME -> SLIME_SPIT;
            case RETALIATING_SHELL -> CRYSTAL_SHELL;
            case DOUBLE_ECHO -> CRYSTAL_ECHO;
            default -> this;
        };
    }
    public Component description() { return Component.translatable("talent.uncannyencounters.crystal_frog." + id); }

    public static CrystalFrogTalent from(String id) {
        for (CrystalFrogTalent talent : values()) if (talent.id.equals(id)) return talent;
        return NONE;
    }

    public static CrystalFrogTalent roll(RandomSource random) { return fromRoll(random.nextInt(80)); }

    /** Four winning outcomes out of eighty: 5% total, 1.25% for each individual talent. */
    public static CrystalFrogTalent fromRoll(int roll) {
        if (roll < 0 || roll >= 80) throw new IllegalArgumentException("Talent roll must be in [0, 80)");
        return roll < 4 ? values()[roll + 1] : NONE;
    }
}
