package com.ignilumen.uncannyencounters.entity.crystalfrog;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import static com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalent.*;

/** Birth-only rolls. Wild initialization never calls this inheritance path. */
public final class FrogGenetics {
    public static double mutationChance(double inherited, double wildCeiling) {
        return 0.15 * Math.min(1, wildCeiling / Math.max(inherited, 0.001));
    }

    public static double inheritStat(RandomSource random, double a, double b, boolean health) {
        double value = random.nextBoolean() ? a : b;
        if (random.nextDouble() < mutationChance(value, health ? 28 : 5))
            value += health ? 1 + random.nextInt(4) : 0.2 + random.nextDouble() * 0.4;
        return value;
    }

    public static List<CrystalFrogTalent> inheritTalents(RandomSource random, List<CrystalFrogTalent> a, List<CrystalFrogTalent> b) {
        List<CrystalFrogTalent> union = new ArrayList<>(a);
        for (var talent : b) if (!union.contains(talent)) union.add(talent);
        union.remove(NONE);
        union.remove(FROG_KING);
        if (kingEligible(a, b) && random.nextDouble() < 0.001) {
            var advanced = union.stream().filter(CrystalFrogTalent::advanced).toList();
            return List.of(FROG_KING, advanced.get(random.nextInt(advanced.size())));
        }
        List<CrystalFrogTalent> child = new ArrayList<>();
        // Shuffle within each priority tier: enum order must not favor one lineage.
        shuffle(union, random);
        for (boolean shared : new boolean[]{true, false}) for (var talent : union) {
            if ((a.contains(talent) && b.contains(talent)) == shared && random.nextDouble() < (shared ? 0.9 : 0.5))
                add(child, talent);
        }
        List<CrystalFrogTalent> upgrades = eligibleUpgrades(a, b);
        if (!upgrades.isEmpty() && random.nextDouble() < 0.01) {
            // Prefer replacing the corresponding inherited basic skill, then a free slot.
            shuffle(upgrades, random);
            upgrades.sort(java.util.Comparator.comparingInt(t -> child.contains(t.family()) ? 0 : 1));
            for (var talent : upgrades) if (add(child, talent)) break;
        }
        if (random.nextDouble() < 0.02 && child.size() < 2) {
            var basics = new ArrayList<>(List.of(SLIME_SPIT, CRYSTAL_SHELL, GROUND_SHOCK, CRYSTAL_ECHO));
            basics.removeIf(t -> child.stream().anyMatch(c -> c.family() == t));
            if (!basics.isEmpty()) add(child, basics.get(random.nextInt(basics.size())));
        }
        return List.copyOf(child);
    }

    public static boolean kingEligible(List<CrystalFrogTalent> a, List<CrystalFrogTalent> b) {
        return a.stream().filter(CrystalFrogTalent::advanced).distinct().count() == 2
                && b.stream().filter(CrystalFrogTalent::advanced).distinct().count() == 2;
    }

    public static List<CrystalFrogTalent> eligibleUpgrades(List<CrystalFrogTalent> a, List<CrystalFrogTalent> b) {
        List<CrystalFrogTalent> result = new ArrayList<>();
        if (lineage(a, SLIME_SPIT) || lineage(b, SLIME_SPIT)) result.add(SCATTER_SLIME);
        if ((lineage(a, CRYSTAL_SHELL) || lineage(b, CRYSTAL_SHELL))
                && (lineage(a, GROUND_SHOCK) || lineage(b, GROUND_SHOCK))) result.add(RETALIATING_SHELL);
        if (lineage(a, CRYSTAL_ECHO) && lineage(b, CRYSTAL_ECHO)) result.add(DOUBLE_ECHO);
        return result;
    }

    private static boolean lineage(List<CrystalFrogTalent> talents, CrystalFrogTalent family) {
        return talents.stream().anyMatch(t -> t.family() == family);
    }

    /** Deduplicates families, upgrades in place, and never drops an unrelated talent. */
    public static boolean add(List<CrystalFrogTalent> talents, CrystalFrogTalent talent) {
        if (talent == NONE || talents.contains(talent)) return false;
        for (int i = 0; i < talents.size(); i++) if (talents.get(i).family() == talent.family()) {
            if (!talent.advanced()) return false;
            talents.set(i, talent);
            return true;
        }
        if (talents.size() >= 2) return false;
        talents.add(talent);
        return true;
    }

    private static <T> void shuffle(List<T> values, RandomSource random) {
        for (int i = values.size() - 1; i > 0; i--) java.util.Collections.swap(values, i, random.nextInt(i + 1));
    }

    private FrogGenetics() {}
}
