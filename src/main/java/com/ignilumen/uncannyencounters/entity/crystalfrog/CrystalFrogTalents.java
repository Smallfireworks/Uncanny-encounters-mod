package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.CrystalSlimeShot;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Rare abilities shared by duels, self-defence and owner support. */
public final class CrystalFrogTalents {
    public static final int SPIT_WINDUP = 10, SPIT_COOLDOWN = 120, SHELL_DURATION = 60;
    public static final int SHOCK_DELAY = 6, SHOCK_COOLDOWN = 120, ECHO_DELAY = 12, ECHO_COOLDOWN = 100;
    private static final Identifier SLIME_SLOW = UncannyEncounters.id("crystal_slime_slow");
    private record Slime(FrogTalentAttack attack, long expires) {}
    private static final Map<LivingEntity, Slime> SLIMED = new HashMap<>();
    private record Burst(FrogTalentAttack attack, Vec3 origin, long expires, boolean shock) {}

    private final CrystalFrog frog;
    private CrystalFrogTalent talent = CrystalFrogTalent.NONE;
    private boolean initialized, shellUsed;
    private float shellHealth;
    private long shellExpires, spitReadyAt, echoReadyAt, shockReadyAt;
    private @Nullable Burst burst;
    private @Nullable FrogTalentAttack casting;
    private int castTicks, airborneTicks;

    public CrystalFrogTalents(CrystalFrog frog) { this.frog = frog; }
    public CrystalFrogTalent talent() { return talent; }
    private long now() { return frog.level().getGameTime(); }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var iterator = SLIMED.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                LivingEntity victim = entry.getKey();
                Slime slime = entry.getValue();
                if (!slime.attack.valid() || victim.level().getGameTime() >= slime.expires) {
                    var speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speed != null) speed.removeModifier(SLIME_SLOW);
                    iterator.remove();
                } else if (server.getTickCount() % 5 == 0 && victim.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.WITCH, victim.getX(), victim.getY(0.2), victim.getZ(),
                            2, 0.2, 0.1, 0.2, 0, 0, 0);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (LivingEntity victim : SLIMED.keySet()) {
                var speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.removeModifier(SLIME_SLOW);
            }
            SLIMED.clear();
        });
    }

    public static void applySlime(FrogTalentAttack attack) {
        if (!attack.valid() || SLIMED.containsKey(attack.target())) return;
        var speed = attack.target().getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.hasModifier(SLIME_SLOW)) return;
        speed.addTransientModifier(new AttributeModifier(SLIME_SLOW, -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        SLIMED.put(attack.target(), new Slime(attack, attack.frog().level().getGameTime() + 30));
    }

    public void initializeTalent() {
        if (initialized || frog.level().isClientSide()) return;
        initialized = true;
        talent = CrystalFrogTalent.roll(frog.getRandom());
    }

    public void save(ValueOutput output) {
        initializeTalent();
        output.putString("CrystalFrogTalent", talent.id());
        output.putBoolean("CrystalShellUsed", shellUsed);
        output.putFloat("CrystalShellHealth", shellHealth);
        output.putLong("CrystalShellExpires", shellExpires);
        output.putLong("CrystalSpitReadyAt", spitReadyAt);
        output.putLong("CrystalEchoReadyAt", echoReadyAt);
        output.putLong("CrystalShockReadyAt", shockReadyAt);
    }

    public void load(ValueInput input, boolean existingFrog) {
        var saved = input.getString("CrystalFrogTalent");
        initialized = existingFrog || saved.isPresent();
        talent = CrystalFrogTalent.from(saved.orElse("none"));
        shellUsed = input.getBooleanOr("CrystalShellUsed", false);
        shellHealth = Math.clamp(input.getFloatOr("CrystalShellHealth", 0), 0, 4);
        shellExpires = input.getLongOr("CrystalShellExpires", 0);
        spitReadyAt = input.getLongOr("CrystalSpitReadyAt", 0);
        echoReadyAt = input.getLongOr("CrystalEchoReadyAt", 0);
        shockReadyAt = input.getLongOr("CrystalShockReadyAt", 0);
        burst = null;
        stopCasting();
        airborneTicks = 0;
    }

    public void beginDuel() {
        clearActive();
        shellUsed = false;
        spitReadyAt = now() + 20;
        echoReadyAt = now();
        shockReadyAt = now() + 20;
    }

    public void clearActive() {
        burst = null;
        shellHealth = 0;
        shellExpires = 0;
        frog.setTalentShell(false);
        stopCasting();
        airborneTicks = 0;
    }

    public void tick() {
        if (frog.level().isClientSide()) return;
        if (shellExpires <= now()) shellHealth = 0;
        frog.setTalentShell(shellHealth > 0);
        if (!frog.isDueling() && !frog.wantsRetaliation() && frog.getHealth() >= frog.getMaxHealth()) shellUsed = false;
        if (shellHealth > 0 && frog.tickCount % 6 == 0 && frog.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, frog.getX(), frog.getY(0.5), frog.getZ(),
                    4, 0.4, 0.25, 0.4, 0, 0.01, 0);
        }
        Burst pending = burst;
        if (pending == null) return;
        if (!pending.attack.valid()) { burst = null; return; }
        if (now() >= pending.expires) {
            burst = null;
            detonate(pending);
        } else if (frog.tickCount % 2 == 0) ring(pending.origin, pending.shock ? 2.5 : 1.25, false);
    }

    /** Runs after travel so standing still and the spawn's first ground contact cannot trigger a shock. */
    public void afterMovement() {
        if (frog.level().isClientSide()) return;
        if (!frog.onGround() && !frog.isInWater()) { airborneTicks++; return; }
        boolean landed = airborneTicks >= 3 && frog.onGround() && !frog.isInWater();
        airborneTicks = 0;
        LivingEntity target = frog.getTarget();
        if (landed && talent == CrystalFrogTalent.GROUND_SHOCK && now() >= shockReadyAt
                && target != null && frog.isTalentTarget(target) && frog.distanceToSqr(target) <= 4 * 4) {
            shockReadyAt = now() + SHOCK_COOLDOWN;
            burst = new Burst(FrogTalentAttack.capture(frog, target), frog.position(), now() + SHOCK_DELAY, true);
        }
    }

    public void afterMeleeHit(LivingEntity target) {
        if (talent != CrystalFrogTalent.CRYSTAL_ECHO || now() < echoReadyAt || !frog.isTalentTarget(target)) return;
        echoReadyAt = now() + ECHO_COOLDOWN;
        burst = new Burst(FrogTalentAttack.capture(frog, target), target.position(), now() + ECHO_DELAY, false);
    }

    public void afterHurt(float previousHealth) {
        if (talent != CrystalFrogTalent.CRYSTAL_SHELL || shellUsed || !frog.isAlive()
                || previousHealth <= frog.getMaxHealth() * 0.5F || frog.getHealth() > frog.getMaxHealth() * 0.5F) return;
        shellUsed = true;
        shellHealth = 4;
        shellExpires = now() + SHELL_DURATION;
        frog.setTalentShell(true);
        frog.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.4F);
    }

    /** Called after armor and resistance, only for a hit vanilla actually accepts. */
    public float absorb(float amount) {
        if (talent != CrystalFrogTalent.CRYSTAL_SHELL || shellExpires <= now() || shellHealth <= 0 || amount <= 0) return amount;
        float absorbed = Math.min(shellHealth, amount);
        shellHealth -= absorbed;
        frog.setTalentShell(shellHealth > 0);
        return amount - absorbed;
    }

    public boolean canStartSpit() {
        LivingEntity target = frog.getTarget();
        return talent == CrystalFrogTalent.SLIME_SPIT && now() >= spitReadyAt && target != null
                && frog.isTalentTarget(target) && (frog.onGround() || frog.isInWater())
                && (!frog.isDueling() || frog.duelCombat().action() == CrystalFrogDuelCombat.NORMAL)
                && inSpitRange(target) && frog.getSensing().hasLineOfSight(target);
    }

    private boolean inSpitRange(LivingEntity target) {
        double distance = frog.distanceToSqr(target);
        return distance >= 3 * 3 && distance <= 8 * 8;
    }

    public void startCasting() {
        LivingEntity target = frog.getTarget();
        if (target == null) return;
        casting = FrogTalentAttack.capture(frog, target);
        castTicks = SPIT_WINDUP;
        spitReadyAt = now() + SPIT_COOLDOWN;
        frog.setTalentCasting(true);
        frog.getNavigation().stop();
        frog.getMoveControl().setWait();
        frog.playSound(SoundEvents.FROG_AMBIENT, 0.4F, 1.5F);
    }

    public boolean isCasting() {
        return casting != null && castTicks > 0 && casting.valid() && inSpitRange(casting.target());
    }

    public void tickCasting() {
        if (!isCasting()) { stopCasting(); return; }
        frog.getNavigation().stop();
        frog.getMoveControl().setWait();
        frog.getLookControl().setLookAt(casting.target(), 20, 20);
        if (--castTicks == 0) {
            if (casting.visibleFrom(frog.getEyePosition()) && frog.level() instanceof ServerLevel level) {
                level.addFreshEntity(new CrystalSlimeShot(casting));
                frog.playSound(SoundEvents.SLIME_ATTACK, 0.65F, 1.4F);
            }
            stopCasting();
        }
    }

    public void stopCasting() {
        casting = null;
        castTicks = 0;
        frog.setTalentCasting(false);
    }

    private void detonate(Burst pending) {
        ring(pending.origin, pending.shock ? 2.5 : 1.25, true);
        LivingEntity target = pending.attack.target();
        Vec3 offset = target.position().subtract(pending.origin);
        double radius = pending.shock ? 2.5 : 1.25;
        if (offset.horizontalDistanceSqr() > radius * radius || Math.abs(offset.y) > 0.8
                || pending.shock && !target.onGround() || !pending.attack.visibleFrom(pending.origin.add(0, 0.25, 0))) return;
        float damage = pending.shock ? 1 : 2;
        if (pending.attack.hurt(null, damage) && pending.shock && pending.attack.valid()) {
            target.knockback(0.6, -offset.x, -offset.z, pending.attack.damageSource(null), damage, true);
        }
    }

    private void ring(Vec3 center, double radius, boolean burst) {
        if (!(frog.level() instanceof ServerLevel level)) return;
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            level.sendParticles(burst ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.END_ROD,
                    center.x + Math.cos(angle) * radius, center.y + 0.12, center.z + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0, burst ? 0.06 : 0, 0);
        }
        if (burst) level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_BREAK,
                frog.getSoundSource(), 0.65F, 1.3F);
    }
}
