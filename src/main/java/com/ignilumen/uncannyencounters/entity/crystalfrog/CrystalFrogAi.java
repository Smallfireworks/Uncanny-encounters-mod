package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.item.ModItems;
import com.ignilumen.uncannyencounters.entity.frogkeeper.ReturnToFrogCourtGoal;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class CrystalFrogAi {
    public static void register(CrystalFrog frog, GoalSelector goals) {
        goals.addGoal(0, new FrogBreatheAirGoal(frog));
        goals.addGoal(1, new Flee(frog));
        goals.addGoal(1, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
            @Override public boolean canUse() { return frog.variantCombat().canUse(); }
            @Override public boolean canContinueToUse() { return frog.variantCombat().canContinueToUse(); }
            @Override public boolean requiresUpdateEveryTick() { return true; }
            @Override public void start() { frog.variantCombat().start(); }
            @Override public void tick() { frog.variantCombat().tick(); }
            @Override public void stop() { frog.variantCombat().stop(); }
        });
        goals.addGoal(1, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
            @Override public boolean canUse() { return frog.swallow().canStart(); }
            @Override public boolean canContinueToUse() { return frog.swallow().active(); }
            @Override public boolean requiresUpdateEveryTick() { return true; }
            @Override public void start() { frog.swallow().start(); }
            @Override public void tick() { frog.swallow().tick(); }
            @Override public void stop() { frog.swallow().clear(); }
        });
        goals.addGoal(1, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
            @Override public boolean canUse() { return frog.talents().canStartSpit(); }
            @Override public boolean canContinueToUse() { return frog.talents().isCasting(); }
            @Override public boolean requiresUpdateEveryTick() { return true; }
            @Override public void start() { frog.talents().startCasting(); }
            @Override public void tick() { frog.talents().tickCasting(); }
            @Override public void stop() { frog.talents().stopCasting(); }
        });
        goals.addGoal(2, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
            @Override public boolean canUse() { return frog.isDueling(); }
            @Override public boolean canContinueToUse() { return frog.isDueling(); }
            @Override public boolean requiresUpdateEveryTick() { return true; }
            @Override public void tick() { frog.duelCombat().tick(); }
            @Override public void stop() {
                if (!frog.isDueling()) frog.duelCombat().reset();
                else frog.getNavigation().stop();
            }
        });
        goals.addGoal(2, new MeleeAttackGoal(frog, 1.15, true) {
            @Override public boolean canUse() { return !frog.isDueling() && frog.wantsRetaliation() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !frog.isDueling() && frog.wantsRetaliation() && super.canContinueToUse(); }
            @Override protected boolean canPerformAttack(LivingEntity target) {
                return !frog.isDueling() && frog.wantsRetaliation() && super.canPerformAttack(target);
            }
        });
        goals.addGoal(3, new SitWhenOrderedToGoal(frog) {
            @Override public boolean canUse() { return frog.isOrderedToSit() && frog.onGround() && !frog.isInWater(); }
            @Override public boolean canContinueToUse() { return canUse(); }
        });
        goals.addGoal(4, new FollowOwner(frog));
        goals.addGoal(4, new ReturnToFrogCourtGoal(frog, frog::keeperId));
        goals.addGoal(3, new BreedGoal(frog, 1) {
            @Override public boolean canUse() { return frog.breedingAvailable() && super.canUse(); }
            @Override public boolean canContinueToUse() { return frog.breedingAvailable() && super.canContinueToUse(); }
        });
        goals.addGoal(4, new FollowParentGoal(frog, 1));
        goals.addGoal(5, new TemptGoal(frog, 1, stack -> stack.is(ModItems.ACTIVATED_AMETHYST), false) {
            @Override public boolean canUse() { return !frog.isTame() && frog.variant() != CrystalFrogVariant.ZOMBIE && super.canUse(); }
            @Override public boolean canContinueToUse() { return !frog.isTame() && frog.variant() != CrystalFrogVariant.ZOMBIE && super.canContinueToUse(); }
        });
        goals.addGoal(6, new RandomStrollGoal(frog, 1, 35) {
            @Override public boolean canUse() { return !frog.isKeeperFrog() && !frog.isOrderedToSit() && !frog.isDueling() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !frog.isKeeperFrog() && !frog.isOrderedToSit() && !frog.isDueling() && super.canContinueToUse(); }
        });
        goals.addGoal(7, new LookAtPlayerGoal(frog, Player.class, 6));
        goals.addGoal(8, new RandomLookAroundGoal(frog));
    }

    private static final class Flee extends PanicGoal {
        private final CrystalFrog frog;
        Flee(CrystalFrog frog) { super(frog, 1.35); this.frog = frog; }
        @Override protected boolean shouldPanic() { return frog.isFrightened(); }
        @Override protected boolean findRandomPosition() {
            Vec3 threat = frog.threatPosition();
            Vec3 destination = threat == null ? null : DefaultRandomPos.getPosAway(frog, 8, 3, threat);
            if (destination == null) return super.findRandomPosition();
            posX = destination.x;
            posY = destination.y;
            posZ = destination.z;
            return true;
        }
        @Override public void start() {
            frog.setInSittingPose(false);
            super.start();
        }
        @Override public boolean canContinueToUse() { return shouldPanic() && super.canContinueToUse(); }
    }

    /** Vanilla FollowOwnerGoal rejects AmphibiousPathNavigation in 26.3. */
    private static final class FollowOwner extends Goal {
        private final CrystalFrog frog;
        private @Nullable LivingEntity owner;
        private int pathCooldown;

        FollowOwner(CrystalFrog frog) {
            this.frog = frog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean canUse() {
            owner = frog.getOwner();
            return validOwner() && frog.distanceToSqr(owner) > 16;
        }
        private boolean validOwner() {
            return !frog.isKeeperFrog() && owner != null && owner.isAlive() && owner.level() == frog.level() && !frog.isDueling()
                    && !frog.isInLove() && !frog.unableToMoveToOwner();
        }
        @Override public boolean canContinueToUse() { return validOwner() && frog.distanceToSqr(owner) > 4; }
        @Override public void start() { pathCooldown = 0; }
        @Override public void stop() {
            owner = null;
            frog.getNavigation().stop();
        }
        @Override public void tick() {
            if (owner == null) return;
            frog.getLookControl().setLookAt(owner, 15, 30);
            if (--pathCooldown <= 0) {
                pathCooldown = adjustedTickDelay(10);
                if (frog.shouldTryTeleportToOwner()) frog.tryToTeleportToOwner();
                else frog.getNavigation().moveTo(owner, 1.15);
            }
        }
    }

    private CrystalFrogAi() {}
}
