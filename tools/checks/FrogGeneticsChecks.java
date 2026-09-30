package com.ignilumen.uncannyencounters.entity.crystalfrog;

import java.util.List;
import net.minecraft.util.RandomSource;
import static com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalent.*;

/** Seeded distribution and invariant checks; does not start Minecraft or a world. */
public final class FrogGeneticsChecks {
    public static void main(String[] args) {
        var random = RandomSource.create(26030930L);
        int count = 100_000, ordinary = 0, advanced = 0, kings = 0, healthMutations = 0;
        var a = List.of(SCATTER_SLIME, RETALIATING_SHELL);
        var b = List.of(SCATTER_SLIME, DOUBLE_ECHO);
        for (int i = 0; i < count; i++) {
            var emptyParents = FrogGenetics.inheritTalents(random, List.of(), List.of());
            if (!emptyParents.isEmpty()) ordinary++;
            check(emptyParents.stream().noneMatch(t -> t.advanced() || t == FROG_KING), "Unqualified parents produced a special talent");
            var spitParents = FrogGenetics.inheritTalents(random, List.of(SLIME_SPIT), List.of(SLIME_SPIT));
            if (spitParents.contains(SCATTER_SLIME)) advanced++;
            check(spitParents.stream().noneMatch(t -> t == RETALIATING_SHELL || t == DOUBLE_ECHO || t == FROG_KING), "Wrong advanced lineage");
            var elite = FrogGenetics.inheritTalents(random, a, b);
            if (elite.contains(FROG_KING)) {
                kings++;
                check(elite.size() == 2 && elite.stream().filter(CrystalFrogTalent::advanced).count() == 1, "King must retain one advanced talent");
            }
            for (var child : List.of(emptyParents, spitParents, elite)) {
                check(child.size() <= 2 && child.stream().map(CrystalFrogTalent::family).distinct().count() == child.size(),
                        "Birth produced duplicate families or more than two talents");
            }
            double health = FrogGenetics.inheritStat(random, 56, 56, true);
            check(health >= 56 && health <= 60, "Positive mutation changed a parent's base value outside the expected range");
            if (health > 56) healthMutations++;
        }
        check(Math.abs(ordinary - 2000) < 180, "Ordinary talent rate is not 2 percent");
        check(Math.abs(advanced - 1000) < 130, "Advanced talent rate is not 1 percent");
        check(Math.abs(kings - 100) < 40, "King rate is not 0.1 percent");
        check(Math.abs(healthMutations - 7500) < 300, "High-stat positive mutation rate is not 7.5 percent");
        check(FrogGenetics.eligibleUpgrades(List.of(CRYSTAL_SHELL), List.of(GROUND_SHOCK)).equals(List.of(RETALIATING_SHELL)), "Shell and shock cross failed");
        check(FrogGenetics.eligibleUpgrades(List.of(CRYSTAL_ECHO), List.of()).isEmpty(), "Double echo requires both parents");
        check(!FrogGenetics.kingEligible(a, List.of(SCATTER_SLIME)), "One elite parent must not qualify for kings");
        System.out.printf("PASS: %,d births per scenario; ordinary=%d, advanced=%d, kings=%d, health mutations=%d. Slots, lineages and positive growth held.%n",
                count, ordinary, advanced, kings, healthMutations);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
