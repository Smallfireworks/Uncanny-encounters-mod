package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.block.ModBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Per-dimension journal of every block a zombie player breaks or places. Each position remembers
 * its first original state and block-entity data, and is put back exactly when its timer runs out.
 * Removal skips drops, container spills and neighbour updates, so nothing is duplicated and no
 * chain reaction (falling sand, popping torches, collapsing doors) changes what gets restored.
 */
public final class TemporaryEdits extends SavedData {
    public static final int BROKEN_TICKS = 60 * 20, PLACED_TICKS = 30 * 20, CRACK_TICKS = 3 * 20;
    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SUPPRESS_DROPS;
    private static final Codec<TemporaryEdits> CODEC = Edit.CODEC.listOf()
            .xmap(TemporaryEdits::new, edits -> List.copyOf(edits.edits.values()));
    // Mod data has no vanilla data fixer; Fabric API skips fixing for a null type.
    public static final SavedDataType<TemporaryEdits> TYPE = new SavedDataType<>(
            UncannyEncounters.id("zombie_player_edits"), TemporaryEdits::new, CODEC, null);

    /** {@code left} is what the zombie left behind (air, a fluid, or a zombie block). */
    private record Edit(BlockPos pos, BlockState original, Optional<CompoundTag> data, BlockState left,
                        long restoreAt, Optional<BlockPos> partner, long clearAt, Optional<BlockState> underneath) {
        Edit(BlockPos pos, BlockState original, Optional<CompoundTag> data, BlockState left,
             long restoreAt, Optional<BlockPos> partner) {
            this(pos, original, data, left, restoreAt, partner, 0, Optional.empty());
        }
        static final Codec<Edit> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Edit::pos),
                BlockState.CODEC.fieldOf("original").forGetter(Edit::original),
                CompoundTag.CODEC.optionalFieldOf("block_entity").forGetter(Edit::data),
                BlockState.CODEC.fieldOf("left").forGetter(Edit::left),
                Codec.LONG.fieldOf("restore_at").forGetter(Edit::restoreAt),
                BlockPos.CODEC.optionalFieldOf("partner").forGetter(Edit::partner),
                Codec.LONG.optionalFieldOf("clear_at", 0L).forGetter(Edit::clearAt),
                BlockState.CODEC.optionalFieldOf("underneath").forGetter(Edit::underneath)
        ).apply(i, Edit::new));
    }

    private final Long2ObjectLinkedOpenHashMap<Edit> edits = new Long2ObjectLinkedOpenHashMap<>();

    public TemporaryEdits() {}

    private TemporaryEdits(List<Edit> saved) {
        for (Edit edit : saved) edits.put(edit.pos.asLong(), edit);
    }

    public static TemporaryEdits get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Removes a block, and the other half of a two-part block, without drops or chain reactions. */
    public boolean breakBlock(ServerLevel level, BlockPos pos, @Nullable Entity breaker) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || !canEdit(level, pos)) return false;
        BlockPos partner = partnerOf(level, pos, state);
        if (partner != null && (!level.isLoaded(partner) || !canEdit(level, partner))) return false;
        long due = level.getGameTime() + BROKEN_TICKS;
        remove(level, pos, partner, due, breaker);
        if (partner != null) remove(level, partner, pos, due, breaker);
        level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(state));
        setDirty();
        return true;
    }

    private boolean canEdit(ServerLevel level, BlockPos pos) {
        Edit existing = edits.get(pos.asLong());
        return existing == null || vacant(level.getBlockState(pos), existing);
    }

    private void remove(ServerLevel level, BlockPos pos, @Nullable BlockPos partner, long due, @Nullable Entity breaker) {
        BlockState state = level.getBlockState(pos);
        BlockState left = state.getFluidState().createLegacyBlock();
        Edit old = edits.get(pos.asLong());
        Optional<BlockPos> link = Optional.ofNullable(partner).map(BlockPos::immutable);
        if (old != null) {
            track(new Edit(old.pos, old.original, old.data, left, Math.min(old.restoreAt, due), old.partner.or(() -> link)));
        } else {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            Optional<CompoundTag> data = blockEntity == null ? Optional.empty()
                    : Optional.of(blockEntity.saveWithFullMetadata(level.registryAccess()));
            track(new Edit(pos.immutable(), state, data, left, due, link));
        }
        level.setBlock(pos, left, REMOVE_FLAGS);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(breaker, state));
    }

    /** Fills a replaceable position (air, fluid, grass...) with a zombie block. */
    public boolean placeBlock(ServerLevel level, BlockPos pos, @Nullable Entity placer) {
        BlockState state = level.getBlockState(pos);
        if (!state.canBeReplaced() || state.hasBlockEntity() || !canEdit(level, pos)) return false;
        BlockState block = ModBlocks.ZOMBIE_BLOCK.defaultBlockState();
        long due = level.getGameTime() + PLACED_TICKS;
        Edit old = edits.get(pos.asLong());
        track(old != null ? new Edit(old.pos, old.original, old.data, block, old.restoreAt, old.partner, due, Optional.of(old.left))
                : new Edit(pos.immutable(), state, Optional.empty(), block, due, Optional.empty()));
        level.setBlock(pos, block, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        SoundType sound = block.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(placer, block));
        setDirty();
        return true;
    }

    private void track(Edit edit) {
        if (edit.original.equals(edit.left) && edit.data.isEmpty() && edit.partner.isEmpty()) edits.remove(edit.pos.asLong());
        else edits.put(edit.pos.asLong(), edit);
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (edits.isEmpty() || now % 5 != 0) return;
        List<Edit> due = new ArrayList<>();
        for (Edit edit : edits.values()) {
            if (!level.isLoaded(edit.pos)) continue;
            long remaining = (edit.clearAt > 0 ? Math.min(edit.clearAt, edit.restoreAt) : edit.restoreAt) - now;
            if (remaining <= 0) due.add(edit);
            else if (remaining <= CRACK_TICKS && level.getBlockState(edit.pos).is(ModBlocks.ZOMBIE_BLOCK)) {
                level.destroyBlockProgress(crackId(edit.pos), edit.pos, (int)((CRACK_TICKS - remaining) * 10 / CRACK_TICKS));
            }
        }
        for (Edit edit : due) {
            if (edits.get(edit.pos.asLong()) != edit) continue;
            if (edit.clearAt > 0 && edit.clearAt <= now && edit.restoreAt > now) {
                if (level.getBlockState(edit.pos).is(ModBlocks.ZOMBIE_BLOCK))
                    level.setBlock(edit.pos, edit.underneath.orElse(Blocks.AIR.defaultBlockState()), REMOVE_FLAGS);
                edits.put(edit.pos.asLong(), new Edit(edit.pos, edit.original, edit.data,
                        edit.underneath.orElse(Blocks.AIR.defaultBlockState()), edit.restoreAt, edit.partner));
                level.destroyBlockProgress(crackId(edit.pos), edit.pos, -1);
                setDirty();
            } else restore(level, edit);
        }
    }

    private void restore(ServerLevel level, Edit edit) {
        List<Edit> group = new ArrayList<>(2);
        group.add(edit);
        edit.partner.map(p -> edits.get(p.asLong())).ifPresent(group::add);
        boolean conflict = false;
        for (Edit part : group) {
            if (!level.isLoaded(part.pos)) return;
            if (!vacant(level.getBlockState(part.pos), part)) conflict = true;
            else if (occupied(level, part)) return; // never close a block around a living entity
        }
        for (Edit part : group) {
            edits.remove(part.pos.asLong());
            level.destroyBlockProgress(crackId(part.pos), part.pos, -1);
        }
        setDirty();
        if (conflict) {
            refund(level, group);
            return;
        }
        // Place every part before any shape update, otherwise a lone door or bed half removes itself.
        for (Edit part : group) level.setBlock(part.pos, part.original, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (Edit part : group) {
            if (part.data.isEmpty()) continue;
            BlockEntity blockEntity = level.getBlockEntity(part.pos);
            if (blockEntity == null) continue;
            blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), part.data.get()));
            blockEntity.setChanged();
            level.sendBlockUpdated(part.pos, part.original, part.original, Block.UPDATE_CLIENTS);
        }
        // Keep the captured states exact; normal neighbour changes can update them afterwards.
    }

    /** Flowing water, snow or grass that crept into the gap is not a player's block and is simply overwritten. */
    private static boolean vacant(BlockState current, Edit edit) {
        return current.isAir() || current.equals(edit.left) || current.canBeReplaced() && !current.hasBlockEntity();
    }

    private static boolean occupied(ServerLevel level, Edit edit) {
        for (AABB box : edit.original.getCollisionShape(level, edit.pos).toAabbs()) {
            if (!level.getEntitiesOfClass(LivingEntity.class, box.move(edit.pos), entity -> !entity.isSpectator()).isEmpty()) return true;
        }
        return false;
    }

    /**
     * A player built into the gap: keep their block and drop the original as a tool-less vanilla break
     * would (stone gives cobblestone, spawners nothing). Loot tables pick the one half of a door or bed
     * that drops; container contents and other block-entity items spill as usual.
     */
    private static void refund(ServerLevel level, List<Edit> group) {
        for (Edit part : group) {
            if (level.getBlockState(part.pos).is(ModBlocks.ZOMBIE_BLOCK)) level.setBlock(part.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            BlockEntity blockEntity = part.data.map(tag -> BlockEntity.loadStatic(part.pos, part.original, tag, level.registryAccess())).orElse(null);
            Block.dropResources(part.original, level, part.pos, blockEntity);
            if (blockEntity != null) {
                blockEntity.setLevel(level);
                blockEntity.preRemoveSideEffects(part.pos, part.original);
            }
        }
    }

    private static @Nullable BlockPos partnerOf(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos other;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            return level.getBlockState(other).is(state.getBlock()) ? other : null;
        }
        if (state.getBlock() instanceof AbstractBedBlock) {
            other = pos.relative(AbstractBedBlock.getConnectedDirection(state));
            return level.getBlockState(other).is(state.getBlock()) ? other : null;
        }
        if (state.getBlock() instanceof PistonHeadBlock) {
            other = pos.relative(state.getValue(PistonHeadBlock.FACING).getOpposite());
            return level.getBlockState(other).getBlock() instanceof PistonBaseBlock ? other : null;
        }
        if (state.getBlock() instanceof PistonBaseBlock && state.getValue(PistonBaseBlock.EXTENDED)) {
            other = pos.relative(state.getValue(PistonBaseBlock.FACING));
            return level.getBlockState(other).getBlock() instanceof PistonHeadBlock ? other : null;
        }
        return null;
    }

    /** Negative, so crack overlays never collide with an entity id. */
    private static int crackId(BlockPos pos) {
        return -1 - (Long.hashCode(pos.asLong()) & Integer.MAX_VALUE);
    }
}
