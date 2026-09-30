package com.ignilumen.uncannyencounters.client.particle;

import com.ignilumen.uncannyencounters.effect.FrogEffects;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;

/** Small pixel sprites with distinct motion instead of generic critical-hit sparks. */
public final class FrogFxParticle extends SingleQuadParticle {
    private enum Kind { DROP, SPLAT, CHIP, MARK, WAVE, SEED, GLOW, PEAK }
    private static final FacingCameraMode GROUND = (rotation, camera, partialTick) -> rotation.rotationX(-(float) Math.PI / 2);
    private final Kind kind;
    private final float opacity;

    private FrogFxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                           TextureAtlasSprite sprite, Kind kind) {
        super(level, x, y, z, sprite);
        this.kind = kind;
        setParticleSpeed(vx, vy, vz);
        setSize(0.06F, 0.06F);
        roll = oRoll = random.nextFloat() * (float) Math.PI * 2;
        switch (kind) {
            case DROP -> { quadSize = 0.06F + random.nextFloat() * 0.025F; lifetime = 14; gravity = 0.65F; friction = 0.92F; opacity = 0.95F; }
            case CHIP -> { quadSize = 0.06F + random.nextFloat() * 0.035F; lifetime = 15; gravity = 0.45F; friction = 0.94F; opacity = 0.9F; }
            case SPLAT -> { quadSize = 0.12F + random.nextFloat() * 0.08F; lifetime = 14; opacity = 0.7F; }
            case MARK -> { quadSize = 2.6F; lifetime = 6; opacity = 0.45F; }
            case WAVE -> { quadSize = 2.6F; lifetime = 6; opacity = 0.75F; }
            case SEED -> { quadSize = 0.24F; lifetime = 3; opacity = 0.45F; }
            case GLOW -> { quadSize = 0.29F; lifetime = 3; opacity = 0.7F; }
            case PEAK -> { quadSize = 0.34F; lifetime = 3; opacity = 0.95F; }
            default -> throw new IllegalArgumentException();
        }
        if (kind != Kind.DROP && kind != Kind.CHIP) {
            hasPhysics = false;
            setParticleSpeed(0, 0, 0);
            if (kind != Kind.SPLAT) roll = oRoll = 0;
        }
        alpha = opacity;
    }

    @Override public void tick() {
        oRoll = roll;
        super.tick();
        float t = (float) age / lifetime;
        if (kind == Kind.CHIP) roll += 0.16F;
        if (kind == Kind.MARK) alpha = opacity * (0.45F + t * 0.55F);
        else if (kind == Kind.WAVE) alpha = opacity * Math.max(0, 1 - t);
        else alpha = opacity * Math.min(1, Math.max(0, (1 - t) * 3));
        if (kind == Kind.DROP && onGround) {
            level.addParticle(FrogEffects.SLIME_SPLAT, x, y + 0.012, z, 0, 0, 0);
            remove();
        }
    }

    @Override public FacingCameraMode getFacingCameraMode() {
        return kind == Kind.SPLAT || kind == Kind.MARK || kind == Kind.WAVE ? GROUND : FacingCameraMode.LOOKAT_XYZ;
    }

    @Override public float getQuadSize(float partialTick) {
        if (kind != Kind.WAVE) return quadSize;
        float t = Math.clamp((age + partialTick) / lifetime, 0, 1);
        return quadSize * (0.05F + 0.95F * (1 - (1 - t) * (1 - t)));
    }

    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    @Override protected int getLightCoords(float partialTick) {
        int light = super.getLightCoords(partialTick);
        return kind == Kind.DROP || kind == Kind.SPLAT
                ? LightCoordsUtil.withBlock(light, Math.max(10, LightCoordsUtil.block(light))) : LightCoordsUtil.FULL_BRIGHT;
    }

    public static void initialize() {
        register(FrogEffects.SLIME_DROP, Kind.DROP);
        register(FrogEffects.SLIME_SPLAT, Kind.SPLAT);
        register(FrogEffects.CRYSTAL_CHIP, Kind.CHIP);
        register(FrogEffects.SHOCK_MARK, Kind.MARK);
        register(FrogEffects.SHOCK_WAVE, Kind.WAVE);
        register(FrogEffects.ECHO_SEED, Kind.SEED);
        register(FrogEffects.ECHO_GLOW, Kind.GLOW);
        register(FrogEffects.ECHO_PEAK, Kind.PEAK);
    }

    private static void register(SimpleParticleType type, Kind kind) {
        ParticleProviderRegistry.getInstance().register(type, sprites -> (options, level, x, y, z, vx, vy, vz, random) ->
                new FrogFxParticle(level, x, y, z, vx, vy, vz, sprites.get(random), kind));
    }
}
