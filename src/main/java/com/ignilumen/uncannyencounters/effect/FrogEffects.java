package com.ignilumen.uncannyencounters.effect;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Presentation-only assets and bounded emission helpers for the four talents. */
public final class FrogEffects {
    public static final SimpleParticleType SLIME_DROP = particle("slime_drop");
    public static final SimpleParticleType SLIME_SPLAT = particle("slime_splat");
    public static final SimpleParticleType CRYSTAL_CHIP = particle("crystal_chip");
    public static final SimpleParticleType SHOCK_MARK = particle("shock_mark");
    public static final SimpleParticleType SHOCK_WAVE = particle("shock_wave");
    public static final SimpleParticleType ECHO_SEED = particle("echo_seed");
    public static final SimpleParticleType ECHO_GLOW = particle("echo_glow");
    public static final SimpleParticleType ECHO_PEAK = particle("echo_peak");

    public static final SoundEvent SLIME_CHARGE = sound("slime_charge"), SLIME_SPIT = sound("slime_spit"), SLIME_SPLASH = sound("slime_splash");
    public static final SoundEvent SHELL_FORM = sound("shell_form"), SHELL_HIT = sound("shell_hit"), SHELL_BREAK = sound("shell_break"), SHELL_FADE = sound("shell_fade");
    public static final SoundEvent SHOCK_LAND = sound("shock_land"), SHOCK_BURST = sound("shock_burst");
    public static final SoundEvent ECHO_CHIME = sound("echo_chime"), ECHO_BURST = sound("echo_burst");

    private static SimpleParticleType particle(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, UncannyEncounters.id("frog_" + name), FabricParticleTypes.simple());
    }

    private static SoundEvent sound(String name) {
        var id = UncannyEncounters.id("crystal_frog." + name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void initialize() {}

    public static void play(ServerLevel level, Vec3 point, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, point.x, point.y, point.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    public static void single(ServerLevel level, SimpleParticleType type, Vec3 point) {
        level.sendParticles(type, point.x, point.y, point.z, 1, 0, 0, 0, 0, 0, 0);
    }

    public static void splash(ServerLevel level, Vec3 point) {
        level.sendParticles(SLIME_DROP, point.x, point.y, point.z, 9, 0.12, 0.06, 0.12, 0.1, 0.05, 0.1);
        play(level, point, SLIME_SPLASH, 0.55F, 1);
    }

    public static void chips(ServerLevel level, Vec3 point, int count, double spread) {
        level.sendParticles(CRYSTAL_CHIP, point.x, point.y, point.z, count, spread, spread * 0.6, spread, 0.09, 0.1, 0.09);
    }

    private static void dust(ServerLevel level, Vec3 point, int count) {
        BlockPos floor = BlockPos.containing(point.add(0, -0.05, 0));
        if (!level.isLoaded(floor)) return;
        var state = level.getBlockState(floor);
        if (!state.isAir() && state.getFluidState().isEmpty()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                point.x, point.y + 0.03, point.z, count, 0.4, 0.02, 0.4, 0.08, 0.04, 0.08);
    }

    public static void shockStart(ServerLevel level, Vec3 point) {
        single(level, SHOCK_MARK, point.add(0, 0.025, 0));
        dust(level, point, 9);
        play(level, point, SHOCK_LAND, 0.65F, 1);
    }

    public static void shockBurst(ServerLevel level, Vec3 point) {
        single(level, SHOCK_WAVE, point.add(0, 0.035, 0));
        dust(level, point, 16);
        play(level, point, SHOCK_BURST, 0.75F, 1);
    }

    public static void echoWarning(ServerLevel level, Vec3 point, long remaining) {
        single(level, remaining > 8 ? ECHO_SEED : remaining > 4 ? ECHO_GLOW : ECHO_PEAK, point.add(0, 0.3, 0));
        if (remaining == 12 || remaining == 8 || remaining == 4)
            play(level, point, ECHO_CHIME, 0.42F, remaining == 12 ? 0.85F : remaining == 8 ? 1 : 1.18F);
    }

    public static void echoBurst(ServerLevel level, Vec3 point) {
        chips(level, point.add(0, 0.3, 0), 18, 0.12);
        play(level, point, ECHO_BURST, 0.65F, 1);
    }

    public static void shellHit(CrystalFrog frog, @Nullable Vec3 source) {
        if (!(frog.level() instanceof ServerLevel level)) return;
        Vec3 direction = source == null ? frog.getLookAngle() : source.subtract(frog.position());
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z);
        if (horizontal.lengthSqr() < 0.001) horizontal = new Vec3(0, 0, 1);
        horizontal = horizontal.normalize();
        double yaw = Math.toRadians(frog.yBodyRot);
        double localX = Math.cos(yaw) * horizontal.x + Math.sin(yaw) * horizontal.z;
        double localZ = Math.sin(yaw) * horizontal.x - Math.cos(yaw) * horizontal.z;
        int panel = Math.floorMod((int) Math.round((Math.atan2(localZ, localX) - frog.tickCount * 0.025) / (Math.PI / 3)), 6);
        level.broadcastEntityEvent(frog, (byte) (CrystalFrog.SHELL_HIT_EVENT_BASE + panel));
        chips(level, frog.position().add(horizontal.scale(0.52)).add(0, 0.28, 0), 4, 0.05);
        play(level, frog.position(), SHELL_HIT, 0.55F, 1);
    }

    public static void shellBreak(CrystalFrog frog) {
        if (!(frog.level() instanceof ServerLevel level)) return;
        level.broadcastEntityEvent(frog, CrystalFrog.SHELL_BREAK_EVENT);
        chips(level, frog.position().add(0, 0.3, 0), 20, 0.4);
        play(level, frog.position(), SHELL_BREAK, 0.7F, 1);
    }

    private FrogEffects() {}
}
