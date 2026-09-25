package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server-wide record of each player's current zombie. A death only raises a new zombie while the
 * previous one is gone (killed, discarded or past its lifetime); deaths in between are ignored.
 */
public final class ZombiePlayerSpawns extends SavedData {
    public static final int LIFETIME = 10 * 60 * 20, SPAWN_DELAY = 3 * 20, SEARCH_RADIUS = 8;
    public static final GameRule<Boolean> SPAWNING = GameRuleBuilder.forBoolean(true)
            .category(GameRuleCategory.SPAWNING).buildAndRegister(UncannyEncounters.id("zombie_player_spawning"));

    private record Active(UUID zombie, long expireAt) {
        static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("zombie").forGetter(Active::zombie),
                Codec.LONG.fieldOf("expire_at").forGetter(Active::expireAt)
        ).apply(i, Active::new));
    }

    private record Pending(ResolvableProfile profile, byte skinParts, boolean leftHanded,
                           ResourceKey<Level> dimension, Vec3 pos, long spawnAt) {
        static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResolvableProfile.CODEC.fieldOf("profile").forGetter(Pending::profile),
                Codec.BYTE.fieldOf("skin_parts").forGetter(Pending::skinParts),
                Codec.BOOL.fieldOf("left_handed").forGetter(Pending::leftHanded),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Pending::dimension),
                Vec3.CODEC.fieldOf("pos").forGetter(Pending::pos),
                Codec.LONG.fieldOf("spawn_at").forGetter(Pending::spawnAt)
        ).apply(i, Pending::new));
    }

    private static final Codec<ZombiePlayerSpawns> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Active.CODEC).fieldOf("active").forGetter(s -> s.active),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Pending.CODEC).fieldOf("pending").forGetter(s -> s.pending)
    ).apply(i, ZombiePlayerSpawns::new));
    public static final SavedDataType<ZombiePlayerSpawns> TYPE = new SavedDataType<>(
            UncannyEncounters.id("zombie_players"), ZombiePlayerSpawns::new, CODEC, null);

    private final Map<UUID, Active> active;
    private final Map<UUID, Pending> pending;

    public ZombiePlayerSpawns() {
        this(Map.of(), Map.of());
    }

    private ZombiePlayerSpawns(Map<UUID, Active> saved, Map<UUID, Pending> waiting) {
        active = new HashMap<>(saved);
        pending = new HashMap<>(waiting);
    }

    public static ZombiePlayerSpawns get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) get(player.level().getServer()).onDeath(player);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> get(server).tick(server));
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            TemporaryEdits edits = level.getDataStorage().get(TemporaryEdits.TYPE);
            if (edits != null) edits.tick(level);
        });
    }

    private void onDeath(ServerPlayer player) {
        ServerLevel level = player.level();
        if (!level.getGameRules().get(SPAWNING) || level.getDifficulty() == Difficulty.PEACEFUL
                || player.isCreative() || player.isSpectator()) return;
        UUID owner = player.getUUID();
        long now = level.getGameTime();
        if (isActive(owner, now) || pending.containsKey(owner)) return;
        pending.put(owner, new Pending(ResolvableProfile.createResolved(player.getGameProfile()), ZombiePlayer.skinParts(player),
                player.getMainArm() == HumanoidArm.LEFT, level.dimension(), player.position(), now + SPAWN_DELAY));
        setDirty();
    }

    private void tick(MinecraftServer server) {
        if (pending.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> entry = it.next();
            if (now < entry.getValue().spawnAt) continue;
            it.remove();
            setDirty();
            ServerLevel level = server.getLevel(entry.getValue().dimension);
            if (level != null) spawn(level, entry.getKey(), entry.getValue());
        }
    }

    private void spawn(ServerLevel level, UUID owner, Pending death) {
        if (!level.getGameRules().get(SPAWNING) || level.getDifficulty() == Difficulty.PEACEFUL) return;
        BlockPos deathPos = BlockPos.containing(death.pos);
        BlockPos feet = findSpot(level, deathPos);
        if (feet == null) return;
        ZombiePlayer zombie = ModEntities.ZOMBIE_PLAYER.create(level, EntitySpawnReason.EVENT);
        if (zombie == null) return;
        zombie.snapTo(Vec3.atBottomCenterOf(feet), level.getRandom().nextFloat() * 360, 0);
        long expireAt = level.getGameTime() + LIFETIME;
        zombie.bind(owner, death.profile, death.skinParts, death.leftHanded, deathPos, expireAt, true);
        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.EVENT, null);
        if (level.addFreshEntity(zombie)) {
            active.put(owner, new Active(zombie.getUUID(), expireAt));
            setDirty();
        }
    }

    /** Nearest standable, non-burning spot around the death point; deaths in lava or the void may have none. */
    private static @Nullable BlockPos findSpot(ServerLevel level, BlockPos center) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                center.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            if (pos.distSqr(center) <= SEARCH_RADIUS * SEARCH_RADIUS) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        for (BlockPos pos : candidates) {
            if (standable(level, pos)) return pos;
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        if (pos.getY() <= level.getMinY() || pos.getY() + 2 > level.getMaxY()) return false;
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || level.getBlockState(below).is(Blocks.MAGMA_BLOCK)) return false;
        AABB box = ModEntities.ZOMBIE_PLAYER.getSpawnAABB(Vec3.atBottomCenterOf(pos));
        return level.noCollision(box) && level.getBlockStates(box.inflate(0.5))
                .noneMatch(state -> state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE));
    }

    private boolean isActive(UUID owner, long now) {
        Active current = active.get(owner);
        if (current == null) return false;
        if (now < current.expireAt) return true;
        active.remove(owner);
        setDirty();
        return false;
    }

    /** Called when the tracked zombie dies or is discarded, freeing the slot for the next death. */
    public void release(UUID owner, UUID zombie) {
        Active current = active.get(owner);
        if (current != null && current.zombie.equals(zombie)) {
            active.remove(owner);
            setDirty();
        }
    }
}
