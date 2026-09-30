package com.ignilumen.uncannyencounters.entity.lightmoth;

import com.ignilumen.uncannyencounters.block.BaitedLureBlock;
import com.ignilumen.uncannyencounters.entity.LightMoth;
import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import com.ignilumen.uncannyencounters.mixin.CubeMoveControlAccessor;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Sources scan nearby loaded entities; mobs never scan a cube of blocks or load chunks. */
public final class MonsterLures {
    public static final int RANGE = LightMoth.ENHANCED_LURE_RANGE;
    public static final int SCAN_INTERVAL = 10;
    public static final int RETALIATION_RANGE = 64;

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damage, baseDamage, damageTaken, blocked) -> {
            if (damageTaken > 0 && entity instanceof Mob mob && mob.isAlive() && LureBait.supports(mob.getType())
                    && damage.getEntity() instanceof LivingEntity attacker && attacker != mob) {
                ((LuredMob) mob).uncannyEncounters$provoke(attacker);
            }
        });
        // Cube mobs deal contact damage without asking Mob.canAttack; this also guards stale attacks.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damage, amount) ->
                !(damage.getEntity() instanceof Mob attacker) || !blocksAttack(attacker, entity));
        ServerTickEvents.START_LEVEL_TICK.register(level -> {
            if (level.getGameTime() % SCAN_INTERVAL != 0) return;
            for (Player player : level.players()) {
                if (!player.isAlive() || player.isSpectator()) continue;
                LureBait main = LureBait.from(player.getMainHandItem());
                LureBait off = LureBait.from(player.getOffhandItem());
                if (main != null) offer(level, new Source(main, player, null));
                if (off != null && off != main) offer(level, new Source(off, player, null));
            }
        });
    }

    public static void offerBlock(ServerLevel level, BlockPos pos, LureBait bait) {
        offer(level, new Source(bait, null, pos.immutable()));
    }

    private static void offer(ServerLevel level, Source source) {
        Vec3 center = source.position();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(RANGE),
                mob -> eligible(mob) && source.bait.attracts(mob.getType()) && mob.distanceToSqr(center) <= RANGE * RANGE)) {
            ((LuredMob) mob).uncannyEncounters$offer(source);
        }
    }

    private static boolean eligible(Mob mob) {
        return mob.isAlive() && !mob.isNoAi() && !mob.isPassenger() && !mob.isLeashed();
    }

    public static @Nullable Vec3 destination(Mob mob) {
        Attraction attraction = ((LuredMob) mob).uncannyEncounters$attraction();
        return attraction == null ? null : attraction.destination(mob);
    }

    public static boolean blocksAttack(Mob mob, LivingEntity target) {
        Attraction attraction = ((LuredMob) mob).uncannyEncounters$attraction();
        if (attraction == null) return false;
        LivingEntity provoker = attraction.provoker(mob);
        return provoker != null ? provoker != target : attraction.hasSource(mob);
    }

    public static @Nullable LivingEntity retaliationTarget(Mob mob) {
        Attraction attraction = ((LuredMob) mob).uncannyEncounters$attraction();
        return attraction == null ? null : attraction.provoker(mob);
    }

    public record Source(LureBait bait, @Nullable Player player, @Nullable BlockPos block) {
        public Vec3 position() { return player != null ? player.position() : Vec3.atCenterOf(block); }

        public boolean valid(Mob mob) {
            if (!eligible(mob) || !bait.attracts(mob.getType())) return false;
            if (player != null) {
                if (!player.isAlive() || player.isSpectator() || player.level() != mob.level()
                        || LureBait.from(player.getMainHandItem()) != bait && LureBait.from(player.getOffhandItem()) != bait) return false;
            } else if (block == null || !mob.level().hasChunkAt(block)
                    || !(mob.level().getBlockState(block).getBlock() instanceof BaitedLureBlock lamp) || lamp.bait() != bait) return false;
            return mob.distanceToSqr(position()) <= RANGE * RANGE;
        }
    }

    public static final class Attraction {
        private @Nullable Source source;
        private @Nullable Path path;
        private int nextPathAt;
        private boolean steering;
        private @Nullable UUID provokerId;
        private @Nullable LivingEntity cachedProvoker;

        public void offer(Mob mob, Source candidate) {
            if (!candidate.valid(mob)) return;
            if (source == null || !source.valid(mob)
                    || mob.distanceToSqr(candidate.position()) + 0.25 < mob.distanceToSqr(source.position())) {
                source = candidate;
                nextPathAt = 0;
            }
            updateCombat(mob);
        }

        public @Nullable Vec3 destination(Mob mob) {
            if (provoker(mob) != null) return null;
            return hasSource(mob) ? source.position() : null;
        }

        private boolean hasSource(Mob mob) {
            if (source != null && source.valid(mob)) return true;
            source = null;
            return false;
        }

        public void provoke(Mob mob, LivingEntity attacker) {
            if (!attacker.isAlive() || attacker.isSpectator() || attacker.level() != mob.level()) return;
            provokerId = attacker.getUUID();
            cachedProvoker = attacker;
            // Release the lure's old route immediately, before vanilla retaliation starts a new one.
            if (path != null && mob.getNavigation().getPath() == path) mob.getNavigation().stop();
            path = null;
            steering = false;
            nextPathAt = 0;
            updateCombat(mob);
        }

        public @Nullable LivingEntity provoker(Mob mob) {
            if (provokerId == null || !(mob.level() instanceof ServerLevel level)) return null;
            LivingEntity attacker = cachedProvoker;
            if (attacker == null && level.getEntity(provokerId) instanceof LivingEntity found) {
                attacker = cachedProvoker = found;
            }
            if (attacker != null && attacker.isAlive() && !attacker.isRemoved() && !attacker.isSpectator()
                    && !(attacker instanceof Player player && player.isCreative())
                    && attacker.level() == level && mob.distanceToSqr(attacker) <= RETALIATION_RANGE * RETALIATION_RANGE) return attacker;
            provokerId = null;
            cachedProvoker = null;
            nextPathAt = 0;
            return null;
        }

        /** Runs before target selection and again before navigation, never cancels the entity's AI. */
        public void updateCombat(Mob mob) {
            LivingEntity attacker = provoker(mob);
            if (attacker != null) {
                if (mob.getTarget() != attacker) mob.setTarget(attacker);
                if (mob instanceof Breeze breeze) breeze.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
            } else if (hasSource(mob)) {
                if (mob.getTargetUnchecked() != null || mob instanceof Enderman) mob.setTarget(null);
                mob.setAggressive(false);
                if (mob.isUsingItem()) mob.stopUsingItem();
                if (mob instanceof Creeper creeper && !creeper.isIgnited()) creeper.setSwellDir(-1);
                if (mob instanceof Breeze breeze) {
                    breeze.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    breeze.getBrain().eraseMemory(MemoryModuleType.BREEZE_SHOOT);
                    breeze.getBrain().eraseMemory(MemoryModuleType.BREEZE_SHOOT_CHARGING);
                }
            }
        }

        public void save(ValueOutput output) {
            if (provokerId != null) output.store("UncannyLureProvoker", UUIDUtil.CODEC, provokerId);
        }

        public void load(ValueInput input) {
            provokerId = input.read("UncannyLureProvoker", UUIDUtil.CODEC).orElse(null);
            cachedProvoker = null;
        }

        private boolean committed(Mob mob) {
            return mob instanceof ZombiePlayer || mob instanceof Breeze
                    && (!mob.onGround() || mob.getPose() == Pose.LONG_JUMPING || mob.getPose() == Pose.INHALING);
        }

        private boolean arrived(Mob mob, Vec3 point) {
            double radius = Math.max(1.5, mob.getBbWidth() * 0.5 + 0.7);
            return mob.distanceToSqr(point) <= radius * radius;
        }

        /** Called before vanilla navigation.tick, preserving its once-per-tick timing. */
        public void prepareNavigation(Mob mob) {
            Vec3 point = destination(mob);
            if (point == null) {
                if (steering) {
                    if (path != null && mob.getNavigation().getPath() == path) mob.getNavigation().stop();
                    mob.getMoveControl().setWait();
                }
                path = null;
                steering = false;
                return;
            }
            if (committed(mob)) return;
            steering = true;
            if (arrived(mob, point) || fliesDirectly(mob, point)) {
                mob.getNavigation().stop();
                path = null;
                return;
            }
            if (mob.tickCount >= nextPathAt) {
                nextPathAt = mob.tickCount + SCAN_INTERVAL;
                path = mob.getNavigation().createPath(BlockPos.containing(point), 1);
            }
            if (path != null && !path.isDone()) {
                // moveTo may retain an equivalent existing Path; follow that object's progress.
                mob.getNavigation().moveTo(path, 1.0);
                path = mob.getNavigation().getPath();
            } else mob.getNavigation().stop();
        }

        /** Calm mobs approach the source; retaliation returns movement to their usual combat AI. */
        public void steer(Mob mob) {
            Vec3 point = destination(mob);
            if (point == null || committed(mob)) return;
            if (arrived(mob, point)) {
                mob.getNavigation().stop();
                mob.getMoveControl().setWait();
                mob.setXxa(0);
                mob.setZza(0);
                return;
            }
            Vec3 next = fliesDirectly(mob, point) ? point
                    : path != null && !path.isDone() ? path.getNextEntityPos(mob) : null;
            if (next == null) {
                mob.getMoveControl().setWait();
                mob.setXxa(0);
                return;
            }
            if (mob instanceof Ghast && source != null && source.block != null) {
                // Its controller rejects an entire flight if the destination overlaps the lamp.
                // Aim beside it, leaving enough room for the ghast's four-block-wide body.
                Vec3 away = mob.position().subtract(point).multiply(1, 0, 1);
                if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
                next = point.add(away.normalize().scale(mob.getBbWidth() * 0.5 + 0.5));
            }
            // Cube mobs ignore MoveControl's wanted coordinates and keep a separate jump heading.
            if (mob.getMoveControl() instanceof CubeMoveControlAccessor cube) {
                float yaw = (float) (Mth.atan2(next.z - mob.getZ(), next.x - mob.getX()) * Mth.RAD_TO_DEG) - 90;
                cube.uncannyEncounters$setDirection(yaw, mob.getTarget() != null);
                cube.uncannyEncounters$setWantedMovement(1.0);
            } else {
                mob.setXxa(0);
                mob.getMoveControl().setWantedPosition(next.x, next.y, next.z, mob instanceof Breeze ? 0.6 : 1.0);
            }
            if (mob instanceof Blaze && next == point && point.y > mob.getY() + 0.5) {
                Vec3 velocity = mob.getDeltaMovement();
                mob.setDeltaMovement(velocity.add(0, (0.3 - velocity.y) * 0.3, 0));
                mob.needsSync = true;
            }
            if (mob.getTarget() == null) mob.getLookControl().setLookAt(point.x, point.y + 0.5, point.z);
        }

        private boolean fliesDirectly(Mob mob, Vec3 point) {
            // GhastMoveControl performs its own swept collision check, as in vanilla.
            if (mob instanceof Ghast) return true;
            if (!(mob instanceof Blaze)) return false;
            BlockHitResult hit = mob.level().clip(new ClipContext(mob.getEyePosition(), point.add(0, 0.5, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            return hit.getType() == HitResult.Type.MISS || source != null && source.block != null && hit.getBlockPos().equals(source.block);
        }
    }

    private MonsterLures() {}
}
