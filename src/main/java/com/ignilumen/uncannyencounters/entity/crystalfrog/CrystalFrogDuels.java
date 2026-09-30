package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Owner-approved sparring. Selection and invitations never retain entities or load chunks. */
public final class CrystalFrogDuels {
    public static final float DEFEAT_FRACTION = 0.25F;
    private static final int INVITE_TICKS = 600, MATCH_TICKS = 2400;
    private static final Map<UUID, Selection> SELECTED = new HashMap<>();
    private static final Map<UUID, Challenge> CHALLENGES = new HashMap<>();

    private record Selection(UUID frog, ResourceKey<Level> dimension, int expires) {}
    private record Challenge(UUID first, UUID second, UUID challenger, ResourceKey<Level> dimension, int expires) {}

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            SELECTED.values().removeIf(selection -> selection.expires <= server.getTickCount());
            CHALLENGES.values().removeIf(challenge -> challenge.expires <= server.getTickCount());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SELECTED.clear();
            CHALLENGES.clear();
        });
    }

    public static void useStick(Player player, CrystalFrog frog) {
        if (!(frog.level() instanceof ServerLevel level) || player.isSpectator()) return;
        if (!frog.isTame()) { tell(player, "tame_first"); return; }
        if (frog.isDueling()) {
            if (frog.isOwnedBy(player)) frog.cancelDuel("cancelled");
            else tell(player, "busy");
            return;
        }
        int now = level.getServer().getTickCount();
        Challenge invitation = CHALLENGES.get(player.getUUID());
        if (frog.isOwnedBy(player) && invitation != null && invitation.second.equals(frog.getUUID())) {
            CHALLENGES.remove(player.getUUID());
            if (invitation.expires <= now || !invitation.dimension.equals(level.dimension())) {
                tell(player, "expired");
                return;
            }
            CrystalFrog first = find(level, invitation.first);
            if (first == null || !(first.getOwner() instanceof Player owner)
                    || !owner.getUUID().equals(invitation.challenger)) {
                tell(player, "expired");
                return;
            }
            start(player, first, frog);
            return;
        }

        Selection selection = SELECTED.get(player.getUUID());
        CrystalFrog first = selection != null && selection.expires > now && selection.dimension.equals(level.dimension())
                ? find(level, selection.frog) : null;
        if (first != null && !first.isOwnedBy(player)) first = null;
        if (first == frog) {
            SELECTED.remove(player.getUUID());
            tell(player, "deselected");
            return;
        }
        if (first == null) {
            SELECTED.remove(player.getUUID());
            if (!frog.isOwnedBy(player)) { tell(player, "select_own"); return; }
            if (!ready(frog)) { tell(player, "not_ready"); return; }
            SELECTED.put(player.getUUID(), new Selection(frog.getUUID(), level.dimension(), now + INVITE_TICKS));
            tell(player, "selected", frog.getDisplayName());
            return;
        }

        if (!canStart(first, frog)) { tell(player, "not_ready"); return; }
        SELECTED.remove(player.getUUID());
        if (frog.isOwnedBy(player)) {
            start(player, first, frog);
        } else if (frog.getOwner() instanceof Player other) {
            CHALLENGES.values().removeIf(challenge -> challenge.challenger.equals(player.getUUID()));
            CHALLENGES.put(other.getUUID(), new Challenge(first.getUUID(), frog.getUUID(), player.getUUID(),
                    level.dimension(), now + INVITE_TICKS));
            tell(player, "challenge_sent", other.getDisplayName());
            tell(other, "challenge_received", player.getDisplayName(), frog.getDisplayName());
        } else tell(player, "not_ready");
    }

    private static @Nullable CrystalFrog find(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof CrystalFrog frog ? frog : null;
    }

    private static boolean ready(CrystalFrog frog) {
        return frog.isAlive() && frog.isTame() && !frog.isDueling() && !frog.isNoAi()
                && !frog.isPassenger() && !frog.isLeashed() && !frog.isFrightened() && !frog.wantsRetaliation()
                && frog.getHealth() >= frog.getMaxHealth()
                && frog.getOwner() instanceof Player owner && owner.isAlive() && !owner.isSpectator()
                && owner.level() == frog.level() && owner.distanceToSqr(frog) <= 32 * 32;
    }

    private static boolean canStart(CrystalFrog first, CrystalFrog second) {
        return first != second && first.level() == second.level() && first.distanceToSqr(second) <= 16 * 16
                && ready(first) && ready(second);
    }

    private static void start(Player caller, CrystalFrog first, CrystalFrog second) {
        if (!canStart(first, second)) { tell(caller, "not_ready"); return; }
        Player firstOwner = (Player) first.getOwner(), secondOwner = (Player) second.getOwner();
        SELECTED.remove(firstOwner.getUUID());
        SELECTED.remove(secondOwner.getUUID());
        CHALLENGES.values().removeIf(challenge -> challenge.first.equals(first.getUUID()) || challenge.second.equals(first.getUUID())
                || challenge.first.equals(second.getUUID()) || challenge.second.equals(second.getUUID()));
        Match match = new Match(first, second);
        first.beginDuel(match, second);
        second.beginDuel(match, first);
        match.tellOwners("started");
    }

    public static void tell(Player player, String key, Object... arguments) {
        player.sendSystemMessage(Component.translatable("message.uncannyencounters.crystal_frog.duel." + key, arguments));
    }

    public static final class Match {
        private final CrystalFrog first, second;
        private final Player firstOwner, secondOwner;
        private final int expires;
        private boolean ended;

        private Match(CrystalFrog first, CrystalFrog second) {
            this.first = first;
            this.second = second;
            firstOwner = (Player) first.getOwner();
            secondOwner = (Player) second.getOwner();
            expires = ((ServerLevel) first.level()).getServer().getTickCount() + MATCH_TICKS;
        }

        public boolean opponents(CrystalFrog a, CrystalFrog b) {
            return !ended && (a == first && b == second || a == second && b == first);
        }

        public void tick() {
            if (ended) return;
            if (!present(first, firstOwner) || !present(second, secondOwner) || first.level() != second.level()
                    || first.distanceToSqr(second) > 24 * 24) { cancel("interrupted"); return; }
            if (((ServerLevel) first.level()).getServer().getTickCount() >= expires) { cancel("timeout"); return; }
            if (first.getHealth() <= first.getMaxHealth() * DEFEAT_FRACTION) defeated(first);
            else if (second.getHealth() <= second.getMaxHealth() * DEFEAT_FRACTION) defeated(second);
        }

        private boolean present(CrystalFrog frog, Player owner) {
            return frog.isAlive() && !frog.isNoAi() && !frog.isPassenger() && !frog.isLeashed()
                    && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator() && frog.isOwnedBy(owner)
                    && owner.level() == frog.level() && owner.distanceToSqr(frog) <= 32 * 32;
        }

        public void defeated(CrystalFrog loser) {
            if (ended) return;
            CrystalFrog winner = loser == first ? second : first;
            finish(winner);
            tellOwners("won", (winner == first ? firstOwner : secondOwner).getDisplayName(), winner.getDisplayName());
        }

        public void cancel(String reason) {
            if (ended) return;
            finish(null);
            tellOwners(reason);
        }

        private void finish(@Nullable CrystalFrog winner) {
            ended = true;
            first.finishDuel(this, winner == first);
            second.finishDuel(this, winner == second);
        }

        private void tellOwners(String key, Object... arguments) {
            tell(firstOwner, key, arguments);
            if (secondOwner != firstOwner) tell(secondOwner, key, arguments);
        }
    }

    private CrystalFrogDuels() {}
}
