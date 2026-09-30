package com.ignilumen.uncannyencounters.entity;

import com.ignilumen.uncannyencounters.block.ModBlocks;
import com.ignilumen.uncannyencounters.entity.lightmoth.MothLights;
import com.ignilumen.uncannyencounters.item.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Neutral flying creature; lamp changes are server-owned and expire without this entity. */
public final class LightMoth extends PathfinderMob {
    public static final byte WANDER = 0, WARNING = 1, FEEDING = 2, LURED = 3, FLEEING = 4;
    public static final int WARNING_TICKS = 60, LIGHT_RANGE = 10, LURE_RANGE = 12, ENHANCED_LURE_RANGE = 24;
    private static final EntityDataAccessor<Byte> BEHAVIOR = SynchedEntityData.defineId(LightMoth.class, EntityDataSerializers.BYTE);
    private @Nullable BlockPos lamp, blockLure, rejectedLamp;
    private @Nullable Player handLure;
    private @Nullable Vec3 wanderPoint, fleeFrom;
    private @Nullable UUID rejectedHandLure;
    private int nearLampTicks, targetTicks, fleeTicks, rejectUntil, nextPathAt, lureStallTicks, handRejectUntil;
    private double lastLureDistance = Double.MAX_VALUE;

    public LightMoth(EntityType<? extends LightMoth> type, Level level) {
        super(type,level);
        moveControl = new FlyingMoveControl<>(this,20,true);
        setNoGravity(true);
        setPathfindingMalus(PathType.WATER,-1);
        setPathfindingMalus(PathType.WATER_BORDER,-1);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR,-1);
        setPathfindingMalus(PathType.FIRE,-1);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH,6).add(Attributes.MOVEMENT_SPEED,0.3)
                .add(Attributes.FLYING_SPEED,0.35).add(Attributes.FOLLOW_RANGE,ENHANCED_LURE_RANGE);
    }
    @Override protected PathNavigation createNavigation(Level level) { return new FlyingPathNavigation(this,level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BEHAVIOR,WANDER);
    }
    public byte behavior() { return entityData.get(BEHAVIOR); }
    private void behavior(byte value) { entityData.set(BEHAVIOR,value); }
    public static boolean canSpawn(EntityType<LightMoth> type, ServerLevelAccessor level,
                                   EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return pos.getY()<48 && !level.canSeeSky(pos) && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).isAir()
                && level.getLevel().noCollision(type.getSpawnAABB(Vec3.atBottomCenterOf(pos)));
    }
    @Override public void travel(Vec3 input) {
        if (isInWater()) { super.travel(input); return; }
        moveRelative(0.1F,input);
        move(MoverType.SELF,getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.8));
    }
    @Override protected void checkFallDamage(double ya, boolean ground, BlockState state, BlockPos pos) {}

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (fleeTicks>0) {
            fleeTicks--;
            behavior(FLEEING);
            if (wanderPoint==null || distanceToSqr(wanderPoint)<0.5 || tickCount%20==0) {
                Vec3 away = fleeFrom == null ? new Vec3(1,0,0) : position().subtract(fleeFrom);
                if (away.lengthSqr()<0.01) away=new Vec3(1,0,0);
                wanderPoint = position().add(away.normalize().scale(4)).add(0,0.5,0);
            }
            flyTo(wanderPoint,1.5);
            return;
        }
        if ((tickCount+getId())%10==0) chooseHandLure(level);
        if (handLure!=null && (!handLure.isAlive() || handLure.isSpectator()
                || lureRange(handLure)==0 || distanceToSqr(handLure)>Math.pow(lureRange(handLure)+2,2))) handLure=null;
        if (blockLure!=null && (!level.isLoaded(blockLure) || blockLureRange(level.getBlockState(blockLure))==0
                || distanceToSqr(Vec3.atCenterOf(blockLure))>Math.pow(blockLureRange(level.getBlockState(blockLure))+2,2))) blockLure=null;
        if (handLure==null && (tickCount+getId())%40==0) scanLights(level);
        if (handLure!=null || blockLure!=null) {
            abandonLamp();
            behavior(LURED);
            Vec3 center = handLure!=null ? handLure.position().add(0,1.1,0) : Vec3.atCenterOf(blockLure).add(0,0.5,0);
            if (tickCount%20==0) {
                double distance=distanceToSqr(center);
                lureStallTicks=distance>4 && distance>=lastLureDistance-0.25 ? lureStallTicks+20 : 0;
                lastLureDistance=distance;
                if (lureStallTicks>=100) {
                    if (handLure!=null) { rejectedHandLure=handLure.getUUID(); handRejectUntil=tickCount+100; }
                    else { rejectedLamp=blockLure; rejectUntil=tickCount+200; }
                    handLure=null; blockLure=null; wanderPoint=null; navigation.stop();
                    resetLureProgress(); behavior(WANDER); return;
                }
            }
            flyTo(orbit(center,handLure!=null ? 1.1 : 0.7),1.05);
            return;
        }
        resetLureProgress();
        if (lamp!=null) {
            MothLights lights=MothLights.get(level);
            boolean valid=level.isLoaded(lamp) && lights.available(lamp,getUUID())
                    && (MothLights.edible(level.getBlockState(lamp)) || lights.owns(lamp,getUUID()));
            if (!valid || !level.getGameRules().get(GameRules.MOB_GRIEFING)) abandonLamp();
            else {
                Vec3 center=Vec3.atCenterOf(lamp).add(0,0.35,0);
                boolean nearby=distanceToSqr(center)<2.5 && visibleLamp(lamp);
                if (nearby) {
                    targetTicks=0;
                    nearLampTicks++;
                    if (nearLampTicks<WARNING_TICKS) {
                        behavior(WARNING);
                        if (nearLampTicks%10==0) level.sendParticles(ParticleTypes.SMOKE,
                                lamp.getX()+0.5,lamp.getY()+0.9,lamp.getZ()+0.5,2,0.12,0.1,0.12,0,0,0);
                        if (nearLampTicks==1) playSound(SoundEvents.BEE_LOOP,0.15F,0.7F);
                    } else {
                        behavior(FEEDING);
                        if (!lights.maintain(level,lamp,getUUID())) abandonLamp();
                    }
                } else {
                    behavior(WARNING);
                    nearLampTicks=0;
                    if (++targetTicks>100) {
                        rejectedLamp=lamp; rejectUntil=tickCount+200;
                        abandonLamp();
                    }
                }
                if (lamp!=null) { flyTo(orbit(center,0.65),0.85); return; }
            }
        }
        behavior(WANDER);
        if (wanderPoint==null || distanceToSqr(wanderPoint)<0.5 || tickCount%60==0 || horizontalCollision) {
            wanderPoint=position().add(random.nextInt(9)-4,random.nextInt(5)-2,random.nextInt(9)-4);
        }
        flyTo(wanderPoint,0.75);
    }

    private Vec3 orbit(Vec3 center, double radius) {
        double angle=(tickCount+getId()*13)*0.075;
        return center.add(Math.cos(angle)*radius,Math.sin(angle*0.6)*0.15,Math.sin(angle)*radius);
    }
    private void flyTo(Vec3 point, double speed) {
        if (!level().hasChunkAt(BlockPos.containing(point))) return;
        if (visible(point) && level().noCollision(this,getBoundingBox().move(point.subtract(position())))) {
            navigation.stop();
            moveControl.setWantedPosition(point.x,point.y,point.z,speed);
        } else if (tickCount>=nextPathAt) {
            nextPathAt=tickCount+10;
            navigation.moveTo(point.x,point.y,point.z,speed);
        }
    }
    private boolean visible(Vec3 point) {
        return level().clip(new ClipContext(getEyePosition(),point,ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    private boolean visibleLamp(BlockPos pos) {
        BlockHitResult hit=level().clip(new ClipContext(getEyePosition(),Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        return hit.getType()==HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
    private static int itemLureRange(ItemStack item) {
        if (item.is(ModItems.ENHANCED_MOTH_LURE)) return ENHANCED_LURE_RANGE;
        return item.is(ModItems.MOTH_LURE) ? LURE_RANGE : 0;
    }
    private static int lureRange(Player player) { return Math.max(itemLureRange(player.getMainHandItem()),itemLureRange(player.getOffhandItem())); }
    private static int blockLureRange(BlockState state) {
        if (state.is(ModBlocks.ENHANCED_MOTH_LURE)) return ENHANCED_LURE_RANGE;
        return state.is(ModBlocks.MOTH_LURE) ? LURE_RANGE : 0;
    }
    private void chooseHandLure(ServerLevel level) {
        Player best=handLure;
        double score=best==null || lureRange(best)==0 ? Double.MAX_VALUE : distanceToSqr(best)/Math.pow(lureRange(best),2)*0.8;
        for (Player player : level.players()) {
            int range=lureRange(player);
            if (range==0 || player.isSpectator() || !player.isAlive() || distanceToSqr(player)>range*range
                    || player.getUUID().equals(rejectedHandLure) && tickCount<handRejectUntil) continue;
            double candidate=distanceToSqr(player)/(range*range);
            if (candidate<score) { best=player; score=candidate; }
        }
        if (handLure!=best) resetLureProgress();
        handLure=best;
    }
    private void scanLights(ServerLevel level) {
        MothLights lights=MothLights.get(level);
        BlockPos origin=blockPosition(), bestLamp=lamp, bestLure=blockLure;
        double lampScore=bestLamp==null ? Double.MAX_VALUE : distanceToSqr(Vec3.atCenterOf(bestLamp))*0.8;
        double lureScore=bestLure==null ? Double.MAX_VALUE : distanceToSqr(Vec3.atCenterOf(bestLure))
                /Math.pow(blockLureRange(level.getBlockState(bestLure)),2)*0.8;
        boolean mayDim=level.getGameRules().get(GameRules.MOB_GRIEFING);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-24,-6,-24),origin.offset(24,6,24))) {
            double distance=distanceToSqr(Vec3.atCenterOf(pos));
            if (distance>ENHANCED_LURE_RANGE*ENHANCED_LURE_RANGE || !level.isLoaded(pos)) continue;
            BlockState state=level.getBlockState(pos);
            int range=blockLureRange(state);
            if (range>0 && distance<=range*range && distance/(range*range)<lureScore
                    && (!pos.equals(rejectedLamp) || tickCount>=rejectUntil) && visibleLamp(pos)) {
                bestLure=pos.immutable(); lureScore=distance/(range*range);
            } else if (lamp==null && mayDim && distance<LIGHT_RANGE*LIGHT_RANGE && distance<lampScore && MothLights.edible(state)
                    && lights.available(pos,getUUID()) && (!pos.equals(rejectedLamp) || tickCount>=rejectUntil) && visibleLamp(pos)) {
                bestLamp=pos.immutable(); lampScore=distance;
            }
        }
        if (!java.util.Objects.equals(blockLure,bestLure)) resetLureProgress();
        blockLure=bestLure;
        if (!java.util.Objects.equals(bestLamp,lamp)) { abandonLamp(); lamp=bestLamp; }
    }
    private void abandonLamp() { lamp=null; nearLampTicks=0; targetTicks=0; }
    private void resetLureProgress() { lureStallTicks=0; lastLureDistance=Double.MAX_VALUE; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt=super.hurtServer(level,source,amount);
        if (hurt && isAlive()) {
            rejectedLamp=lamp; rejectUntil=tickCount+200;
            abandonLamp(); handLure=null; blockLure=null;
            fleeTicks=100; fleeFrom=source.getSourcePosition(); wanderPoint=null;
            navigation.stop(); behavior(FLEEING);
        }
        return hurt;
    }
    @Override public void die(DamageSource source) {
        if (level() instanceof ServerLevel server) MothLights.get(server).release(server,getUUID());
        super.die(source);
    }
    @Override public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel server) MothLights.get(server).release(server,getUUID());
        super.remove(reason);
    }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.BAT_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.BAT_DEATH; }
    @Override protected float getSoundVolume() { return 0.25F; }
    @Override protected int getBaseExperienceReward(ServerLevel level) { return 1; }
}
