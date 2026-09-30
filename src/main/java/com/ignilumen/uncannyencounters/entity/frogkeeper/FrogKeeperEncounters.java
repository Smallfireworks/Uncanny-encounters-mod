package com.ignilumen.uncannyencounters.entity.frogkeeper;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.FrogKeeper;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Per-dimension pact: challenge, shared retaliation, two independent deaths and altar cooldown. */
public final class FrogKeeperEncounters extends SavedData {
    public static final class Entry {
        private final UUID keeper;
        private final BlockPos altar;
        private boolean defeated, dead, frogDead;
        private long readyAt;
        private Optional<UUID> frog, provoker;
        private BlockPos keeperPos, frogPos;
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("keeper").forGetter((Entry e) -> e.keeper),
                BlockPos.CODEC.fieldOf("altar").forGetter(e -> e.altar),
                Codec.BOOL.fieldOf("defeated").forGetter(e -> e.defeated),
                Codec.BOOL.fieldOf("dead").forGetter(e -> e.dead),
                Codec.LONG.fieldOf("ready_at").forGetter(e -> e.readyAt),
                UUIDUtil.CODEC.optionalFieldOf("frog").forGetter(e -> e.frog),
                Codec.BOOL.optionalFieldOf("frog_dead").forGetter(e -> Optional.of(e.frogDead)),
                UUIDUtil.CODEC.optionalFieldOf("provoker").forGetter(e -> e.provoker),
                BlockPos.CODEC.optionalFieldOf("keeper_pos").forGetter(e -> Optional.of(e.keeperPos)),
                BlockPos.CODEC.optionalFieldOf("frog_pos").forGetter(e -> Optional.of(e.frogPos))).apply(i, Entry::new));

        private Entry(UUID keeper, BlockPos altar, boolean defeated, boolean dead, long readyAt, Optional<UUID> frog,
                      Optional<Boolean> frogDead, Optional<UUID> provoker, Optional<BlockPos> keeperPos, Optional<BlockPos> frogPos) {
            this.keeper = keeper;
            this.altar = altar;
            this.defeated = defeated;
            this.dead = dead;
            this.readyAt = readyAt;
            this.frog = frog;
            // Earlier saves dismissed the pet on keeper death: completed encounters stay completed.
            this.frogDead = frogDead.orElse(dead);
            this.provoker = provoker;
            this.keeperPos = keeperPos.orElse(altar.south(3));
            this.frogPos = frogPos.orElse(altar.south(6));
        }
        public boolean complete() { return dead && frogDead; }
    }
    private static final Codec<FrogKeeperEncounters> CODEC = Entry.CODEC.listOf().xmap(FrogKeeperEncounters::new,
            ledger -> List.copyOf(ledger.entries.values()));
    public static final SavedDataType<FrogKeeperEncounters> TYPE = new SavedDataType<>(
            UncannyEncounters.id("frog_keeper_encounters"), FrogKeeperEncounters::new, CODEC, null);
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, LivingEntity> knownTargets = new HashMap<>();
    private FrogKeeperEncounters() {}
    private FrogKeeperEncounters(List<Entry> saved) { for (Entry e : saved) entries.put(e.keeper, e); }
    public static FrogKeeperEncounters get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public void register(FrogKeeper keeper) {
        Entry e = entries.get(keeper.getUUID());
        if (e == null) {
            e = new Entry(keeper.getUUID(), keeper.altar(), keeper.challengeDefeated(), false, 0,
                    Optional.empty(), Optional.of(false), Optional.empty(), Optional.of(keeper.blockPosition()), Optional.empty());
            entries.put(e.keeper, e);
            setDirty();
        }
        if (e.frog.isEmpty() && keeper.companionId() != null) {
            e.frog = Optional.of(keeper.companionId());
            setDirty();
        }
    }
    public void registerFrog(CrystalFrog frog) {
        Entry e = entries.get(frog.keeperId());
        if (e != null && e.frog.isEmpty()) { e.frog = Optional.of(frog.getUUID()); setDirty(); }
    }
    public boolean keeperDead(UUID keeper) { Entry e = entries.get(keeper); return e != null && e.dead; }
    public boolean frogDead(UUID keeper) { Entry e = entries.get(keeper); return e != null && e.frogDead; }
    public boolean defeated(UUID keeper) { Entry e = entries.get(keeper); return e != null && e.defeated; }
    public @Nullable Entry activeAt(BlockPos altar) {
        for (Entry e : entries.values()) if (e.altar.equals(altar) && !e.complete()) return e;
        return null;
    }
    public long readyAt(BlockPos altar) {
        return entries.values().stream().filter(e -> e.altar.equals(altar)).mapToLong(e -> e.readyAt).max().orElse(0);
    }
    public void defeated(FrogKeeper keeper) {
        register(keeper);
        entries.get(keeper.getUUID()).defeated = true;
        setDirty();
    }
    public void died(FrogKeeper keeper, long time) {
        register(keeper);
        Entry e = entries.get(keeper.getUUID());
        if (e.dead) return;
        e.dead = true;
        if (e.frog.isEmpty()) e.frogDead = true;
        finishIfDead(e, time);
    }
    public void frogDied(UUID keeper, long time) {
        Entry e = entries.get(keeper);
        if (e == null || e.frogDead) return;
        e.frogDead = true;
        finishIfDead(e, time);
    }
    private void finishIfDead(Entry e, long time) {
        if (e.complete()) {
            e.readyAt = time + 12000;
            e.provoker = Optional.empty();
            knownTargets.remove(e.keeper);
        }
        setDirty();
    }
    public @Nullable BlockPos home(UUID keeper, boolean frog) {
        Entry e = entries.get(keeper);
        return e == null ? null : e.altar.south(frog ? 6 : 3);
    }
    public boolean ally(UUID keeper, LivingEntity target) {
        Entry e = entries.get(keeper);
        return target.getUUID().equals(keeper) || target instanceof CrystalFrog frog && keeper.equals(frog.keeperId())
                || e != null && e.frog.filter(target.getUUID()::equals).isPresent();
    }
    private boolean legal(Entry e, LivingEntity attacker, ServerLevel level) {
        return attacker.isAlive() && !attacker.isRemoved() && attacker.level() == level && !attacker.isSpectator()
                && !(attacker instanceof Player player && player.isCreative()) && !ally(e.keeper, attacker);
    }
    public void provoke(ServerLevel level, UUID keeper, LivingEntity attacker) {
        Entry e = entries.get(keeper);
        if (e == null || !e.defeated || e.complete() || !legal(e, attacker, level)) return;
        if (!e.provoker.filter(attacker.getUUID()::equals).isPresent()) {
            e.provoker = Optional.of(attacker.getUUID());
            setDirty();
        }
        knownTargets.put(keeper, attacker);
        if (!e.dead && level.getEntity(keeper) instanceof FrogKeeper host) host.setRetaliationTarget(attacker);
        if (!e.frogDead && e.frog.isPresent() && level.getEntity(e.frog.get()) instanceof CrystalFrog frog)
            frog.setKeeperRetaliationTarget(attacker);
    }
    public @Nullable LivingEntity retaliationTarget(ServerLevel level, UUID keeper, Mob member) {
        Entry e = entries.get(keeper);
        if (e == null || !e.defeated) return null;
        boolean frog = member instanceof CrystalFrog;
        BlockPos position = member.blockPosition();
        if (!position.equals(frog ? e.frogPos : e.keeperPos)) {
            if (frog) e.frogPos = position.immutable(); else e.keeperPos = position.immutable();
            setDirty();
        }
        if (e.provoker.isEmpty()) return null;
        UUID id = e.provoker.get();
        LivingEntity target = knownTargets.get(keeper);
        if (target != null && !legal(e, target, level)) {
            e.provoker = Optional.empty();
            knownTargets.remove(keeper);
            setDirty();
            return null;
        }
        if (target == null || !target.getUUID().equals(id))
            target = level.getEntity(id) instanceof LivingEntity living ? living : null;
        if (target == null) target = level.getServer().getPlayerList().getPlayer(id);
        if (target == null) {
            knownTargets.remove(keeper);
            // Entity data may arrive after the survivor. Keep the UUID; never force-load its chunk.
            return null;
        }
        if (!legal(e, target, level) || !nearPartner(level, target, member, keeper, e.keeperPos, e.dead)
                && !nearPartner(level, target, member, e.frog.orElse(null), e.frogPos, e.frogDead)) {
            e.provoker = Optional.empty();
            knownTargets.remove(keeper);
            setDirty();
            return null;
        }
        knownTargets.put(keeper, target);
        return target;
    }
    private boolean nearPartner(ServerLevel level, LivingEntity target, Mob member, @Nullable UUID id, BlockPos lastPos, boolean dead) {
        if (dead) return false;
        var entity = id == null ? null : id.equals(member.getUUID()) ? member : level.getEntity(id);
        Vec3 position = entity == null ? Vec3.atBottomCenterOf(lastPos) : entity.position();
        return target.distanceToSqr(position) <= 64 * 64;
    }
}
