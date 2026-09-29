package com.ignilumen.uncannyencounters.entity.crystalfrog;

/** Direct checks of the production speed predicate; no client, server or world is started. */
public final class CrystalFrogLogicChecks {
    public static void main(String[] args) {
        double threshold = CrystalFrogDefense.SPEED_THRESHOLD;
        check(!fast(0, 0, 0), "Standing attack reflected");
        check(!fast(0.216, 0, 0), "Walking attack reflected");
        check(!fast(Math.nextDown(threshold), 0, 0), "Speed below threshold reflected");
        check(fast(threshold, 0, 0) && fast(0, 0, -threshold), "Inclusive horizontal threshold failed");
        check(fast(0.2, 0, 0.2), "Diagonal movement not measured horizontally");
        check(!fast(0.19, 0, 0.19), "Slow diagonal movement reflected");
        check(fast(0, -threshold, 0) && fast(0, -2, 0), "Downward mace attack not reflected");
        check(!fast(0, -Math.nextDown(threshold), 0), "Slow descent reflected");
        check(!fast(0, 2, 0), "Upward movement alone reflected");
        check(!fast(0.2, -0.2, 0), "Sub-threshold horizontal/downward motion incorrectly combined");
        check(fast(2, 0, 0), "Fast mounted/gliding motion not reflected");
        check(!fast(Double.NaN, 0, 0) && !fast(0, Double.NEGATIVE_INFINITY, 0), "Invalid speed accepted");
        System.out.println("Crystal frog: horizontal/downward thresholds, diagonal motion, upward motion and invalid input: PASS");
    }

    private static boolean fast(double x, double y, double z) { return CrystalFrogDefense.isFastAttack(x, y, z); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
