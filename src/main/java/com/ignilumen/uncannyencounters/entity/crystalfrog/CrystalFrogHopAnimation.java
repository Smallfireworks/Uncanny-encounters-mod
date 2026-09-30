package com.ignilumen.uncannyencounters.entity.crystalfrog;

/** Server-selected flight phases and their client-side pose interpolation. */
public final class CrystalFrogHopAnimation {
    public static final byte GROUNDED = 0, RISING = 1, TUCKED = 2, FALLING = 3, DISABLED = 4;
    private float hindLeg, previousHindLeg, frontLeg, previousFrontLeg, pitch, previousPitch;
    private float landing, previousLanding;
    private boolean wasAirborne;

    public static byte phase(boolean onGround, boolean disabled, double verticalSpeed) {
        if (disabled) return DISABLED;
        if (onGround) return GROUNDED;
        if (verticalSpeed > 0.1) return RISING;
        if (verticalSpeed < -0.1) return FALLING;
        return TUCKED;
    }

    public void tick(byte phase) {
        previousHindLeg = hindLeg;
        previousFrontLeg = frontLeg;
        previousPitch = pitch;
        previousLanding = landing;
        if (phase == DISABLED) {
            hindLeg = previousHindLeg = frontLeg = previousFrontLeg = pitch = previousPitch = 0;
            landing = previousLanding = 0;
            wasAirborne = false;
            return;
        }
        boolean airborne = phase == RISING || phase == TUCKED || phase == FALLING;
        float targetHindLeg = switch (phase) { case RISING -> 0.55F; case TUCKED -> -0.55F; case FALLING -> 0.18F; default -> 0; };
        float targetFrontLeg = switch (phase) { case RISING -> -0.3F; case TUCKED -> -0.2F; case FALLING -> 0.4F; default -> 0; };
        float targetPitch = switch (phase) { case RISING -> -0.07F; case FALLING -> 0.06F; default -> 0; };
        // Blend poses once. Filtering velocity and multiplying by an air blend again
        // attenuates short hops before the limbs can reach the intended pose.
        hindLeg += (targetHindLeg - hindLeg) * 0.65F;
        frontLeg += (targetFrontLeg - frontLeg) * 0.65F;
        pitch += (targetPitch - pitch) * 0.65F;
        landing = wasAirborne && phase == GROUNDED ? 1 : Math.max(0, landing - 0.25F);
        wasAirborne = airborne;
    }

    public float hindLeg(float partialTick) { return interpolate(previousHindLeg, hindLeg, partialTick); }
    public float frontLeg(float partialTick) { return interpolate(previousFrontLeg, frontLeg, partialTick); }
    public float pitch(float partialTick) { return interpolate(previousPitch, pitch, partialTick); }
    public float landing(float partialTick) { return interpolate(previousLanding, landing, partialTick); }

    private static float interpolate(float before, float after, float partialTick) {
        return before + (after - before) * Math.clamp(partialTick, 0, 1);
    }
}
