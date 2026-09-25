package com.ignilumen.uncannyencounters.entity.zombieplayer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import com.ignilumen.uncannyencounters.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block queries for one planning pass, cached because A* revisits the same cells many times, plus
 * the player mining formula. Unloaded chunks read as barriers so plans never enter them.
 */
final class Terrain {
    static final double BODY = 1.8, HALF_WIDTH = 0.3, STEP = 0.6, JUMP = 1.25, SAFE_FALL = 3;
    /** Extra planning cost in ticks, on top of the real time, so walking stays preferred when it is close. */
    static final double OPEN_COST = 4, BREAK_COST = 5 + 6;
    // "Same speed as a player with an iron pickaxe", read as the matching iron tool for each block.
    private static final List<ItemStack> IRON_TOOLS = List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE),
            new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_HOE), new ItemStack(Items.SHEARS), new ItemStack(Items.IRON_SWORD));
    private static final BlockState UNLOADED = Blocks.BARRIER.defaultBlockState();

    final ServerLevel level;
    final boolean mayEdit;
    private final Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<BlockState> simulated = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    Terrain(ServerLevel level, boolean editing) {
        this.level = level;
        mayEdit = editing && level.getGameRules().get(GameRules.MOB_GRIEFING);
    }

    boolean loaded(int x, int z) {
        return level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
    }

    BlockState state(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (simulated.containsKey(key)) return simulated.get(key);
        BlockState state = states.get(key);
        if (state == null) {
            state = loaded(x, z) && !level.isOutsideBuildHeight(y) ? level.getBlockState(cursor.set(x, y, z)) : UNLOADED;
            states.put(key, state);
        }
        return state;
    }

    void simulate(List<Step> steps) {
        simulated.clear();
        for (Step step : steps) apply(step, simulated);
    }

    void commit(Step step) { apply(step, states); }

    private void apply(Step step, Long2ObjectOpenHashMap<BlockState> into) {
        for (BlockPos pos : step.opens()) {
            BlockState state = state(pos.getX(), pos.getY(), pos.getZ());
            if (openable(state)) into.put(pos.asLong(), opened(state));
        }
        for (BlockPos pos : step.breaks()) into.put(pos.asLong(), Blocks.AIR.defaultBlockState());
        if (step.place() != null) into.put(step.place().asLong(), ModBlocks.ZOMBIE_BLOCK.defaultBlockState());
    }

    private VoxelShape collision(int x, int y, int z) {
        return state(x, y, z).getCollisionShape(level, cursor.set(x, y, z));
    }

    /** Height the feet rest at in this cell: on a low block inside it (slab, carpet, snow) or on the block below; NaN if unsupported. */
    double floor(int x, int y, int z) {
        VoxelShape here = collision(x, y, z);
        if (!here.isEmpty() && here.max(Direction.Axis.Y) <= 0.5) return y + here.max(Direction.Axis.Y);
        return floorBelow(x, y, z);
    }

    /** Floor from the block beneath alone, as if the cell itself were mined out. Taller shapes like fences lift it. */
    double floorBelow(int x, int y, int z) {
        VoxelShape below = collision(x, y - 1, z);
        return !below.isEmpty() && below.max(Direction.Axis.Y) >= 0.999 ? y - 1 + below.max(Direction.Axis.Y) : Double.NaN;
    }

    boolean water(int x, int y, int z) {
        return state(x, y, z).getFluidState().is(FluidTags.WATER) && collision(x, y, z).isEmpty();
    }

    /** Cells the body must never enter. */
    boolean hazard(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE);
    }

    /** Standing on burning floors hurts; allowed, but only as a last resort. */
    double floorPenalty(int x, int y, int z) {
        return burns(state(x, y, z)) || burns(state(x, y - 1, z)) ? 40 : 0;
    }

    private static boolean burns(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.CAMPFIRES);
    }

    /** A replaceable cell with a neighbouring face to build against, as a player would need. */
    boolean placeable(int x, int y, int z) {
        if (!mayEdit) return false;
        BlockState state = state(x, y, z);
        if (!state.canBeReplaced() || state.hasBlockEntity()) return false;
        for (Direction direction : Direction.values()) {
            if (!collision(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ()).isEmpty()) return true;
        }
        return false;
    }

    /** What has to happen for a box to be free: doors opened, blocks mined. */
    static final class Clearing {
        final List<BlockPos> opens = new ArrayList<>(2), breaks = new ArrayList<>(4);
        private final LongOpenHashSet seen = new LongOpenHashSet();
        double cost;
    }

    /** Collects everything whose collision overlaps the box; false when something in it cannot be cleared. */
    boolean clear(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Clearing out) {
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        for (int x = Mth.floor(minX); x <= Mth.floor(maxX - 1.0E-7); x++) {
            for (int y = Mth.floor(minY); y <= Mth.floor(maxY - 1.0E-7); y++) {
                for (int z = Mth.floor(minZ); z <= Mth.floor(maxZ - 1.0E-7); z++) {
                    BlockState state = state(x, y, z);
                    if (state.isAir() || !intersects(state.getCollisionShape(level, cursor.set(x, y, z)), x, y, z, box)) continue;
                    if (!out.seen.add(BlockPos.asLong(x, y, z))) continue;
                    if (openable(state) && !intersects(opened(state).getCollisionShape(level, cursor.set(x, y, z)), x, y, z, box)) {
                        out.opens.add(new BlockPos(x, y, z));
                        out.cost += OPEN_COST;
                        continue;
                    }
                    int ticks = mayEdit ? breakTicks(state, cursor.set(x, y, z)) : -1;
                    if (ticks < 0 || lavaAround(x, y, z)) return false;
                    out.breaks.add(new BlockPos(x, y, z));
                    out.cost += ticks + BREAK_COST;
                }
            }
        }
        return true;
    }

    /** Body-wide box centred in one column, between two heights. */
    boolean clearColumn(int x, int z, double bottom, double top, Clearing out) {
        return clear(x + 0.5 - HALF_WIDTH, bottom, z + 0.5 - HALF_WIDTH, x + 0.5 + HALF_WIDTH, top, z + 0.5 + HALF_WIDTH, out);
    }

    /** Region swept by the body sliding from one column centre to a neighbouring one. */
    boolean clearSweep(int x1, int z1, int x2, int z2, double bottom, double top, Clearing out) {
        return clear(Math.min(x1, x2) + 0.5 - HALF_WIDTH, bottom, Math.min(z1, z2) + 0.5 - HALF_WIDTH,
                Math.max(x1, x2) + 0.5 + HALF_WIDTH, top, Math.max(z1, z2) + 0.5 + HALF_WIDTH, out);
    }

    private boolean lavaAround(int x, int y, int z) {
        for (Direction direction : Direction.values()) {
            if (state(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ()).getFluidState().is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    private static boolean intersects(VoxelShape shape, int x, int y, int z, AABB box) {
        if (shape.isEmpty()) return false;
        for (AABB part : shape.toAabbs()) {
            if (part.move(x, y, z).intersects(box)) return true;
        }
        return false;
    }

    static boolean openable(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN)) return false;
        if (state.getBlock() instanceof DoorBlock door) return door.type().canOpenByHand();
        return state.getBlock() instanceof FenceGateBlock || state.is(BlockTags.TRAPDOORS) && !state.is(Blocks.IRON_TRAPDOOR);
    }

    static BlockState opened(BlockState state) {
        return state.setValue(BlockStateProperties.OPEN, true);
    }

    int breakTicks(BlockState state, BlockPos pos) {
        float progress = progress(level, state, pos);
        return progress <= 0 ? -1 : Mth.ceil(1 / progress);
    }

    /** Break progress per tick on the ground, using the player formula with the best matching iron tool. */
    static float progress(BlockGetter level, BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return 0;
        if (hardness == 0) return 1;
        float best = 0;
        for (ItemStack tool : IRON_TOOLS) best = Math.max(best, speed(tool, state, hardness));
        return best;
    }

    private static float speed(ItemStack tool, BlockState state, float hardness) {
        boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        return tool.getDestroySpeed(state) / hardness / (correct ? 30 : 100);
    }
}
