package com.ignilumen.uncannyencounters.entity;

import com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalents;
import com.ignilumen.uncannyencounters.entity.crystalfrog.FrogTalentAttack;
import com.ignilumen.uncannyencounters.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A slow, non-homing projectile. Only its original, still-valid opponent can be damaged. */
public final class CrystalSlimeShot extends ThrowableItemProjectile {
    private @Nullable FrogTalentAttack attack;
    private Vec3 origin = Vec3.ZERO;

    public CrystalSlimeShot(EntityType<? extends CrystalSlimeShot> type, Level level) { super(type, level); }

    public CrystalSlimeShot(FrogTalentAttack attack) {
        this(ModEntities.CRYSTAL_SLIME_SHOT, attack.frog().level());
        this.attack = attack;
        setOwner(attack.frog());
        setPos(attack.frog().getX(), attack.frog().getEyeY(), attack.frog().getZ());
        origin = position();
        Vec3 aim = attack.target().getBoundingBox().getCenter().subtract(origin);
        double flightTime = aim.horizontalDistance() / 0.65;
        shoot(aim.x, aim.y + 0.5 * getDefaultGravity() * flightTime * flightTime, aim.z, 0.65F, 1.0F);
    }

    @Override protected Item getDefaultItem() { return ModItems.VISCOUS_AMETHYST; }
    @Override protected double getDefaultGravity() { return 0.015; }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }

    public boolean validFor(CrystalFrog target) {
        return attack != null && attack.target() == target && attack.valid() && getOwner() == attack.frog();
    }

    public Vec3 incomingPoint(CrystalFrog target) {
        return target.position().subtract(getDeltaMovement());
    }

    @Override public void tick() {
        if (!level().isClientSide() && (attack == null || !attack.valid() || getOwner() != attack.frog()
                || tickCount >= 30 || position().distanceToSqr(origin) > 8 * 8
                || !level().isLoaded(BlockPos.containing(position().add(getDeltaMovement()))))) {
            discard();
            return;
        }
        super.tick();
        if (level().isClientSide() && !isRemoved()) level().addParticle(ParticleTypes.WITCH,
                getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (attack == null || hit.getEntity() != attack.target() || !attack.valid()) return;
        boolean guarded = hit.getEntity() instanceof CrystalFrog frog
                && frog.duelCombat().canGuardFrom(attack.frog(), incomingPoint(frog));
        if (attack.hurt(this, 2) && !guarded && attack.valid()) CrystalFrogTalents.applySlime(attack);
    }

    @Override protected void onHitBlock(BlockHitResult hit) {}

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WITCH, getX(), getY(), getZ(), 8, 0.15, 0.1, 0.15, 0, 0.02, 0);
            discard();
        }
    }
}
