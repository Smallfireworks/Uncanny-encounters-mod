package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Variant abilities use the same exact-match lifetime as inherited talents. */
public final class FrogVariantCombat extends Goal {
    private final CrystalFrog frog;
    private @Nullable FrogTalentAttack attack;
    private long readyAt;
    private int remaining;

    public FrogVariantCombat(CrystalFrog frog) {
        this.frog = frog;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() {
        var target = frog.getTarget();
        return (frog.variant() == CrystalFrogVariant.ECHO || frog.variant() == CrystalFrogVariant.ENDER)
                && frog.level().getGameTime() >= readyAt && !frog.isBaby() && !frog.isSeekingAir()
                && !frog.isPassenger() && !frog.swallow().active() && !frog.talents().isCasting()
                && (frog.onGround() || frog.isInWater()) && target != null && frog.isTalentTarget(target)
                && frog.distanceToSqr(target) <= 144
                && (!frog.isDueling() || frog.duelCombat().action() == CrystalFrogDuelCombat.NORMAL);
    }

    @Override public void start() {
        var target = frog.getTarget();
        if (target == null) return;
        attack = FrogTalentAttack.capture(frog, target);
        remaining = frog.variant() == CrystalFrogVariant.ECHO ? 20 : 1;
        readyAt = frog.level().getGameTime() + 120;
        frog.getNavigation().stop();
        frog.getMoveControl().setWait();
        if (frog.variant() == CrystalFrogVariant.ECHO) {
            frog.setTalentCasting(true);
            frog.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 0.7F, 1.3F);
        }
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public boolean canContinueToUse() {
        return remaining > 0 && attack != null && attack.valid() && !frog.isSeekingAir()
                && !frog.isPassenger() && frog.distanceToSqr(attack.target()) <= 144;
    }

    @Override public void tick() {
        if (!canContinueToUse()) { clear(); return; }
        FrogTalentAttack current = attack;
        frog.getNavigation().stop();
        frog.getMoveControl().setWait();
        frog.getLookControl().setLookAt(current.target(), 30, 30);
        if (--remaining > 0 || !(frog.level() instanceof ServerLevel level)) return;
        if (frog.variant() == CrystalFrogVariant.ECHO) sonicBoom(level, current);
        else teleportAttack(level, current);
        clear();
    }

    private void sonicBoom(ServerLevel level, FrogTalentAttack current) {
        Vec3 source = frog.getEyePosition();
        Vec3 delta = current.target().getEyePosition().subtract(source);
        Vec3 direction = delta.normalize();
        for (int i = 1; i <= Math.ceil(delta.length()); i++) {
            Vec3 point = source.add(direction.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        frog.playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 1.3F);
        // Vanilla sonic damage bypasses armor and walls; CrystalFrog still enforces duel limits.
        if (current.valid()) current.target().hurtServer(level, level.damageSources().sonicBoom(frog), frog.talents().damage(4));
    }

    private void teleportAttack(ServerLevel level, FrogTalentAttack current) {
        var target = current.target();
        double radius = (frog.getBbWidth() + target.getBbWidth()) / 2 + 0.3;
        double startAngle = frog.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 0; i < 12; i++) for (int dy : new int[]{0, 1, -1, 2, -2}) {
            double angle = startAngle + i * Math.PI / 6;
            BlockPos pos = BlockPos.containing(target.getX() + Math.cos(angle) * radius,
                    target.getY() + dy, target.getZ() + Math.sin(angle) * radius);
            Vec3 feet = Vec3.atBottomCenterOf(pos);
            var box = frog.getBoundingBox().move(feet.subtract(frog.position()));
            if (current.match() != null && !current.match().allowsPosition(frog, feet)) continue;
            if (!level.hasChunkAt(pos.below())
                    || !level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                    || !level.hasChunkAt(BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                    || !level.getWorldBorder().isWithinBounds(box)) continue;
            boolean dangerous = false;
            // Include the collision query's one-block margin, and all four possible chunks at a corner.
            for (BlockPos occupied : BlockPos.betweenClosed(BlockPos.containing(box.minX - 1, box.minY - 1, box.minZ - 1),
                    BlockPos.containing(box.maxX + 1, box.maxY + 1, box.maxZ + 1))) {
                if (!level.hasChunkAt(occupied)) { dangerous = true; break; }
            }
            if (dangerous || !current.valid()) continue;
            var floor = level.getBlockState(pos.below());
            if (!floor.isFaceSturdy(level, pos.below(), Direction.UP) || frog.getType().isBlockDangerous(floor)
                    || !level.noCollision(frog, box) || level.containsAnyLiquid(box)) continue;
            for (BlockPos occupied : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                if (frog.getType().isBlockDangerous(level.getBlockState(occupied))) { dangerous = true; break; }
            }
            if (dangerous || !current.visibleFrom(feet.add(0, frog.getEyeHeight(), 0))) continue;
            Vec3 old = frog.position();
            frog.teleportTo(feet.x, feet.y, feet.z);
            frog.setDeltaMovement(Vec3.ZERO);
            frog.fallDistance = 0;
            frog.needsSync = true;
            level.gameEvent(GameEvent.TELEPORT, old, GameEvent.Context.of(frog));
            level.sendParticles(ParticleTypes.PORTAL, old.x, old.y + 0.3, old.z, 20, 0.3, 0.3, 0.3, 0.1);
            level.sendParticles(ParticleTypes.PORTAL, feet.x, feet.y + 0.3, feet.z, 20, 0.3, 0.3, 0.3, 0.1);
            level.playSound(null, old.x, old.y, old.z, SoundEvents.ENDERMAN_TELEPORT, frog.getSoundSource(), 0.7F, 1.2F);
            frog.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.2F);
            if (current.valid() && frog.isWithinMeleeAttackRange(target) && current.visibleFrom(frog.getEyePosition())) {
                frog.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
                frog.doHurtTarget(level, target);
            }
            return;
        }
    }

    @Override public void stop() { clear(); }
    public void clear() {
        attack = null;
        remaining = 0;
        frog.setTalentCasting(false);
    }
    public void save(ValueOutput output) { output.putLong("CrystalVariantReadyAt", readyAt); }
    public void load(ValueInput input) {
        readyAt = input.getLongOr("CrystalVariantReadyAt", 0);
        clear();
    }
}
