package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.item.ModItems;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class CrystalFrogAi {
    public static void register(CrystalFrog frog, GoalSelector goals) {
        goals.addGoal(0, new Flee(frog));
        goals.addGoal(1, new MeleeAttackGoal(frog, 1.15, true) {
            @Override public boolean canUse() { return frog.wantsRetaliation() && super.canUse(); }
            @Override public boolean canContinueToUse() { return frog.wantsRetaliation() && super.canContinueToUse(); }
            @Override protected boolean canPerformAttack(LivingEntity target) {
                return frog.wantsRetaliation() && super.canPerformAttack(target);
            }
        });
        goals.addGoal(2, new SitWhenOrderedToGoal(frog) {
            @Override public boolean canUse() { return frog.isOrderedToSit() && frog.onGround() && !frog.isInWater(); }
            @Override public boolean canContinueToUse() { return canUse(); }
        });
        goals.addGoal(3, new FollowOwner(frog));
        goals.addGoal(4, new TemptGoal(frog, 1, stack -> stack.is(ModItems.ACTIVATED_AMETHYST), false) {
            @Override public boolean canUse() { return !frog.isTame() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !frog.isTame() && super.canContinueToUse(); }
        });
        goals.addGoal(5, new RandomStrollGoal(frog, 1, 35) {
            @Override public boolean canUse() { return !frog.isOrderedToSit() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !frog.isOrderedToSit() && super.canContinueToUse(); }
        });
        goals.addGoal(6, new LookAtPlayerGoal(frog, Player.class, 6));
        goals.addGoal(7, new RandomLookAroundGoal(frog));
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
            return owner != null && owner.isAlive() && owner.level() == frog.level() && !frog.unableToMoveToOwner();
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
