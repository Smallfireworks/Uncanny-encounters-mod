package com.ignilumen.uncannyencounters.entity.lightmoth;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.block.ModBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Exclusive, expiring lamp claims. No chunks are loaded to restore a light. */
public final class MothLights extends SavedData {
    public static final int RESTORE_DELAY = 60;
    private record Claim(BlockPos pos, UUID owner, long expires) {
        static final Codec<Claim> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Claim::pos),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Claim::owner),
                Codec.LONG.fieldOf("expires").forGetter(Claim::expires)
        ).apply(i, Claim::new));
    }
    private static final Codec<MothLights> CODEC = Claim.CODEC.listOf()
            .xmap(MothLights::new, s -> List.copyOf(s.claims.values()));
    public static final SavedDataType<MothLights> TYPE = new SavedDataType<>(
            UncannyEncounters.id("moth_lights"), MothLights::new, CODEC, null);
    private final Map<BlockPos, Claim> claims = new HashMap<>();
    public MothLights() {}
    private MothLights(List<Claim> saved) { for (Claim c : saved) claims.put(c.pos, c); }
    public static MothLights get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            MothLights lights = level.getDataStorage().get(TYPE);
            if (lights != null) lights.tick(level);
        });
    }
    public static boolean edible(BlockState state) {
        return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN);
    }
    public static @Nullable BlockState dimmed(BlockState state) {
        if (state.is(Blocks.TORCH)) return ModBlocks.DIMMED_TORCH.defaultBlockState();
        if (state.is(Blocks.WALL_TORCH)) return ModBlocks.DIMMED_WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (state.is(Blocks.LANTERN)) return ModBlocks.DIMMED_LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, state.getValue(BlockStateProperties.HANGING))
                .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
        return null;
    }
    public static @Nullable BlockState original(BlockState state) {
        if (state.is(ModBlocks.DIMMED_TORCH)) return Blocks.TORCH.defaultBlockState();
        if (state.is(ModBlocks.DIMMED_WALL_TORCH)) return Blocks.WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (state.is(ModBlocks.DIMMED_LANTERN)) return Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, state.getValue(BlockStateProperties.HANGING))
                .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
        return null;
    }
    public boolean available(BlockPos pos, UUID owner) {
        Claim c = claims.get(pos);
        return c == null || c.owner.equals(owner);
    }
    public boolean owns(BlockPos pos, UUID owner) {
        Claim c = claims.get(pos);
        return c != null && c.owner.equals(owner);
    }
    public boolean maintain(ServerLevel level, BlockPos pos, UUID owner) {
        if (!level.isLoaded(pos) || !available(pos, owner)) return false;
        Claim previous = claims.get(pos);
        BlockState current = level.getBlockState(pos), dark = dimmed(current);
        if (previous == null) {
            for (Claim c : claims.values()) if (!c.pos.equals(pos) && c.owner.equals(owner)
                    && c.expires>level.getGameTime()) return false;
            if (dark == null || !current.canSurvive(level, pos)) return false;
            claims.put(pos.immutable(), new Claim(pos.immutable(), owner, level.getGameTime()+RESTORE_DELAY));
            setDirty();
            if (!level.setBlock(pos, dark, Block.UPDATE_ALL)) { claims.remove(pos); return false; }
        } else {
            if (original(current) == null) { claims.remove(pos); setDirty(); return false; }
            claims.put(pos.immutable(), new Claim(pos.immutable(), owner, level.getGameTime()+RESTORE_DELAY));
            setDirty();
        }
        return true;
    }
    /** Driving away lets the existing claim expire; death releases it immediately. */
    public void release(ServerLevel level, UUID owner) {
        for (Claim c : new ArrayList<>(claims.values())) if (c.owner.equals(owner)) {
            claims.put(c.pos, new Claim(c.pos,c.owner,level.getGameTime()));
            if (level.isLoaded(c.pos)) restore(level,c.pos);
            setDirty();
        }
    }
    public void check(ServerLevel level, BlockPos pos) {
        Claim c = claims.get(pos);
        if (c == null || c.expires <= level.getGameTime()) restore(level,pos);
    }
    private void tick(ServerLevel level) {
        if (level.getGameTime()%5 != 0) return;
        for (Claim c : new ArrayList<>(claims.values())) {
            if (!level.isLoaded(c.pos)) continue;
            if (original(level.getBlockState(c.pos)) == null) { claims.remove(c.pos); setDirty(); }
            else if (c.expires <= level.getGameTime()) restore(level,c.pos);
        }
    }
    private void restore(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        BlockState light = original(level.getBlockState(pos));
        // Preserve current direction/water state; never overwrite a replacement block.
        if (light != null) {
            if (light.canSurvive(level,pos)) level.setBlock(pos,light,Block.UPDATE_ALL);
            else level.destroyBlock(pos,true);
        }
        if (claims.remove(pos) != null) setDirty();
    }
}
