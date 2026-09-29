package com.ignilumen.uncannyencounters.entity.crystalfrog;

/** Speeds are blocks per tick, matching Entity.getKnownSpeed() in 26.3. */
public final class CrystalFrogDefense {
    public static final double SPEED_THRESHOLD = 0.28; // approximately 5.6 blocks/second

    private CrystalFrogDefense() {}

    public static boolean isFastAttack(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        // Downward motion is a separate trigger; upward motion cannot qualify an attack.
        return Math.hypot(x, z) >= SPEED_THRESHOLD || y <= -SPEED_THRESHOLD;
    }
}
