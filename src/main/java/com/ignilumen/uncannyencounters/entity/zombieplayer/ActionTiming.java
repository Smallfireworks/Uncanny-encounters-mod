package com.ignilumen.uncannyencounters.entity.zombieplayer;

/** Tick-based input budget. Idle time never accumulates a burst of clicks. */
final class ActionTiming {
    private long clickTick = Long.MIN_VALUE, attackTick = Long.MIN_VALUE, turnTick = Long.MIN_VALUE;
    private double nextClick = Double.NEGATIVE_INFINITY;
    private float turnRemaining;

    boolean click(long now, int cps) {
        if (now == attackTick || now == clickTick || now + 1.0E-8 < nextClick) return false;
        if (now - nextClick > 1.0) nextClick = now;
        nextClick += 20.0 / Math.clamp(cps, 10, 14);
        clickTick = now;
        return true;
    }

    boolean canAttack(long now) { return clickTick != now && attackTick != now; }
    void attacked(long now) { attackTick = now; }

    float turn(long now, float requested) {
        if (turnTick != now) {
            turnTick = now;
            turnRemaining = 45;
        }
        float used = Math.min(turnRemaining, requested);
        turnRemaining -= used;
        return used;
    }
}
