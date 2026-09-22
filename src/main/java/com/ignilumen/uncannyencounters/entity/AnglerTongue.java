package com.ignilumen.uncannyencounters.entity;

import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * One narrow, full-length hitbox lets vanilla melee and projectiles sever the tongue.
 * After a release the entity lingers for {@link #RETRACT_TICKS} so the client can play a
 * retract animation (with a red hurt flash when it was severed by an attack).
 */
public final class AnglerTongue extends Entity {
    public static final int RETRACT_TICKS = 8;
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(AnglerTongue.class, EntityDataSerializers.FLOAT);
    /** Remaining retract animation ticks; 0 while attached to a victim. */
    private static final EntityDataAccessor<Integer> RETRACT = SynchedEntityData.defineId(AnglerTongue.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SEVERED = SynchedEntityData.defineId(AnglerTongue.class, EntityDataSerializers.BOOLEAN);
    private CaveAngler owner;

    public AnglerTongue(EntityType<? extends AnglerTongue> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LENGTH, 1F);
        builder.define(RETRACT, 0);
        builder.define(SEVERED, false);
    }

    public float length() { return entityData.get(LENGTH); }
    public int retractTicks() { return entityData.get(RETRACT); }
    public boolean isRetracting() { return retractTicks() > 0; }
    public boolean isSevered() { return entityData.get(SEVERED); }

    public void attach(CaveAngler owner, double bottomY) {
        this.owner = owner;
        Vec3 top = owner.mouth();
        float length = (float)Math.clamp(top.y - bottomY, 0.1, CaveAngler.REACH + 2);
        entityData.set(LENGTH, length);
        setPos(top.x, top.y - length, top.z);
        refreshDimensions();
    }

    /** Detaches from the victim and starts the retract animation; the entity removes itself afterwards. */
    public void retract(boolean severed) {
        if (isRetracting()) return;
        entityData.set(SEVERED, severed);
        entityData.set(RETRACT, RETRACT_TICKS);
    }

    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.scalable(0.45F, length()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LENGTH.equals(key)) refreshDimensions();
    }
    @Override public boolean isPickable() { return isAlive() && !isRetracting(); }
    @Override public boolean isAttackable() { return !isRetracting(); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRetracting() || owner == null || !owner.isAlive() || amount <= 0 || source.getEntity() == owner) return false;
        owner.severTongue();
        return true;
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (owner == null || !owner.isAlive()) { discard(); return; }
        int retract = retractTicks();
        if (retract > 0) {
            if (retract == 1) discard();
            else entityData.set(RETRACT, retract - 1);
        } else if (owner.tongue() != this) {
            discard();
        }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {}
    @Override protected void addAdditionalSaveData(ValueOutput output) {}
}
