package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.CrystalSlimeShot;
import com.ignilumen.uncannyencounters.effect.FrogEffects;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
    private record Burst(FrogTalentAttack attack, Vec3 origin, long expires, boolean shock, float power) {}

    private final CrystalFrog frog;
    private CrystalFrogTalent talent = CrystalFrogTalent.NONE;
    private CrystalFrogTalent secondTalent = CrystalFrogTalent.NONE;
    private boolean initialized, shellUsed;
    private float shellHealth;
    private long shellExpires, spitReadyAt, echoReadyAt, shockReadyAt;
    private long lastEchoVisualAt = Long.MIN_VALUE;
    private final List<Burst> bursts = new ArrayList<>();
    private @Nullable FrogTalentAttack casting;
    private int castTicks, airborneTicks;

    public CrystalFrogTalents(CrystalFrog frog) { this.frog = frog; }
    public CrystalFrogTalent talent() { return talent; }
    public List<CrystalFrogTalent> all() {
        return java.util.stream.Stream.of(talent, secondTalent).filter(t -> t != CrystalFrogTalent.NONE).toList();
    }
    public boolean has(CrystalFrogTalent value) { return talent == value || secondTalent == value; }
    public boolean hasFamily(CrystalFrogTalent value) { return all().stream().anyMatch(t -> t.family() == value); }
    public Component description() {
        if (talent == CrystalFrogTalent.NONE) return talent.description();
        return secondTalent == CrystalFrogTalent.NONE ? talent.description()
                : Component.empty().append(talent.description()).append(" / ").append(secondTalent.description());
    }
    public void setTalents(List<CrystalFrogTalent> values) {
        List<CrystalFrogTalent> normalized = new ArrayList<>();
        for (var value : values) FrogGenetics.add(normalized, value);
        talent = normalized.isEmpty() ? CrystalFrogTalent.NONE : normalized.getFirst();
        secondTalent = normalized.size() < 2 ? CrystalFrogTalent.NONE : normalized.get(1);
        initialized = true;
        frog.applyKingAttributes();
    }
    public float damage(float basicDamage) {
        return basicDamage * (float)Math.sqrt(Math.max(1, frog.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) / 3));
    }
    public float shellCapacity() {
        return 4 * (float)Math.sqrt(Math.max(1, frog.getAttributeBaseValue(Attributes.MAX_HEALTH) / 20));
    }
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
                    level.sendParticles(victim.onGround() ? FrogEffects.SLIME_SPLAT : FrogEffects.SLIME_DROP,
                            victim.getX(), victim.getY() + 0.025, victim.getZ(), 2, 0.18, 0, 0.18, 0, 0, 0);
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
        output.putString("CrystalFrogSecondTalent", secondTalent.id());
        output.putBoolean("CrystalShellUsed", shellUsed);
        output.putFloat("CrystalShellHealth", shellHealth);
        output.putLong("CrystalShellExpires", shellExpires);
        output.putLong("CrystalSpitReadyAt", spitReadyAt);
        output.putLong("CrystalEchoReadyAt", echoReadyAt);
        output.putLong("CrystalShockReadyAt", shockReadyAt);
    }

    public void load(ValueInput input, boolean existingFrog) {
        var saved = input.getString("CrystalFrogTalent");
        boolean shouldInitialize = existingFrog || saved.isPresent() || input.getString("CrystalFrogSecondTalent").isPresent();
        setTalents(List.of(CrystalFrogTalent.from(saved.orElse("none")),
                CrystalFrogTalent.from(input.getStringOr("CrystalFrogSecondTalent", "none"))));
        initialized = shouldInitialize;
        shellUsed = input.getBooleanOr("CrystalShellUsed", false);
        shellHealth = Math.clamp(input.getFloatOr("CrystalShellHealth", 0), 0, shellCapacity());
        shellExpires = input.getLongOr("CrystalShellExpires", 0);
        spitReadyAt = input.getLongOr("CrystalSpitReadyAt", 0);
        echoReadyAt = input.getLongOr("CrystalEchoReadyAt", 0);
        shockReadyAt = input.getLongOr("CrystalShockReadyAt", 0);
        if (shellExpires <= now()) shellHealth = 0;
        frog.setTalentShellHealth(shellHealth);
        bursts.clear();
        lastEchoVisualAt = Long.MIN_VALUE;
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
        bursts.clear();
        fadeShell();
        stopCasting();
        airborneTicks = 0;
    }

    public void tick() {
        if (frog.level().isClientSide()) return;
        if (shellExpires <= now() && shellHealth > 0) fadeShell();
        frog.setTalentShellHealth(shellHealth);
        if (!frog.isDueling() && !frog.wantsRetaliation() && frog.getHealth() >= frog.getMaxHealth()) shellUsed = false;
        for (Burst pending : List.copyOf(bursts)) {
            if (!pending.attack.valid()) { bursts.remove(pending); continue; }
            if (now() >= pending.expires) {
                bursts.remove(pending);
                detonate(pending);
            } else if (!pending.shock && pending.expires - now() <= ECHO_DELAY
                    && lastEchoVisualAt != now() && (pending.expires - now()) % 2 == 0
                    && frog.level() instanceof ServerLevel level) {
                lastEchoVisualAt = now();
                FrogEffects.echoWarning(level, pending.origin, pending.expires - now());
            }
        }
    }

    /** Runs after travel so standing still and the spawn's first ground contact cannot trigger a shock. */
    public void afterMovement() {
        if (frog.level().isClientSide()) return;
        if (!frog.onGround() && !frog.isInWater()) { airborneTicks++; return; }
        boolean landed = airborneTicks >= 3 && frog.onGround() && !frog.isInWater();
        airborneTicks = 0;
        LivingEntity target = frog.getTarget();
        if (landed && has(CrystalFrogTalent.GROUND_SHOCK) && now() >= shockReadyAt
                && target != null && frog.isTalentTarget(target) && frog.distanceToSqr(target) <= 4 * 4) {
            shockReadyAt = now() + SHOCK_COOLDOWN;
            bursts.add(new Burst(FrogTalentAttack.capture(frog, target), frog.position(), now() + SHOCK_DELAY, true, 1));
            if (frog.level() instanceof ServerLevel level) FrogEffects.shockStart(level, frog.position());
        }
    }

    public void afterMeleeHit(LivingEntity target) {
        if (!hasFamily(CrystalFrogTalent.CRYSTAL_ECHO) || now() < echoReadyAt || !frog.isTalentTarget(target)) return;
        echoReadyAt = now() + ECHO_COOLDOWN;
        FrogTalentAttack attack = FrogTalentAttack.capture(frog, target);
        bursts.add(new Burst(attack, target.position(), now() + ECHO_DELAY, false, 1));
        if (has(CrystalFrogTalent.DOUBLE_ECHO))
            bursts.add(new Burst(attack, target.position(), now() + ECHO_DELAY * 2, false, 0.6F));
        lastEchoVisualAt = now();
        if (frog.level() instanceof ServerLevel level) FrogEffects.echoWarning(level, target.position(), ECHO_DELAY);
    }

    public void afterHurt(float previousHealth) {
        if (!hasFamily(CrystalFrogTalent.CRYSTAL_SHELL) || shellUsed || !frog.isAlive()
                || previousHealth <= frog.getMaxHealth() * 0.5F || frog.getHealth() > frog.getMaxHealth() * 0.5F) return;
        shellUsed = true;
        shellHealth = shellCapacity();
        shellExpires = now() + SHELL_DURATION;
        frog.setTalentShellHealth(shellHealth);
        frog.playSound(FrogEffects.SHELL_FORM, 0.7F, 1);
    }

    /** Called after armor and resistance, only for a hit vanilla actually accepts. */
    public float absorb(float amount, @Nullable Vec3 source) {
        if (!hasFamily(CrystalFrogTalent.CRYSTAL_SHELL) || shellExpires <= now() || shellHealth <= 0 || amount <= 0) return amount;
        float absorbed = Math.min(shellHealth, amount);
        shellHealth -= absorbed;
        frog.setTalentShellHealth(shellHealth);
        if (shellHealth > 0) FrogEffects.shellHit(frog, source);
        else {
            FrogEffects.shellBreak(frog);
            LivingEntity target = frog.getTarget();
            if (has(CrystalFrogTalent.RETALIATING_SHELL) && target != null && frog.isTalentTarget(target)) {
                bursts.add(new Burst(FrogTalentAttack.capture(frog, target), frog.position(), now() + SHOCK_DELAY, true, 2));
                if (frog.level() instanceof ServerLevel level) FrogEffects.shockStart(level, frog.position());
            }
        }
        return amount - absorbed;
    }

    private void fadeShell() {
        if (shellHealth > 0 && !frog.level().isClientSide()) frog.playSound(FrogEffects.SHELL_FADE, 0.4F, 1);
        shellHealth = 0;
        shellExpires = 0;
        frog.setTalentShellHealth(0);
    }

    public boolean canStartSpit() {
        LivingEntity target = frog.getTarget();
        return frog.variant() != CrystalFrogVariant.ECHO && hasFamily(CrystalFrogTalent.SLIME_SPIT) && now() >= spitReadyAt && target != null
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
        spitReadyAt = now() + (has(CrystalFrogTalent.SCATTER_SLIME) ? 180 : SPIT_COOLDOWN);
        frog.setTalentCasting(true);
        frog.getNavigation().stop();
        frog.getMoveControl().setWait();
        frog.playSound(FrogEffects.SLIME_CHARGE, 0.55F, 1);
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
                if (has(CrystalFrogTalent.SCATTER_SLIME)) {
                    for (float angle : new float[]{-12, 0, 12})
                        level.addFreshEntity(new CrystalSlimeShot(casting, angle, damage(1.4F)));
                } else level.addFreshEntity(new CrystalSlimeShot(casting));
                frog.playSound(FrogEffects.SLIME_SPIT, 0.65F, 1);
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
        if (frog.level() instanceof ServerLevel level) {
            if (pending.shock) FrogEffects.shockBurst(level, pending.origin);
            else FrogEffects.echoBurst(level, pending.origin);
        }
        LivingEntity target = pending.attack.target();
        Vec3 offset = target.position().subtract(pending.origin);
        double radius = pending.shock ? 2.5 : 1.25;
        if (offset.horizontalDistanceSqr() > radius * radius || Math.abs(offset.y) > 0.8
                || pending.shock && !target.onGround() || !pending.attack.visibleFrom(pending.origin.add(0, 0.25, 0))) return;
        float damage = damage((pending.shock ? 1 : 2) * pending.power);
        if (pending.attack.hurt(null, damage) && pending.shock && pending.attack.valid()) {
            target.knockback(0.6, -offset.x, -offset.z, pending.attack.damageSource(null), damage, true);
        }
    }

}
