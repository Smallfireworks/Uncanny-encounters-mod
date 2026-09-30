package com.ignilumen.uncannyencounters.block;

import com.ignilumen.uncannyencounters.entity.FrogKeeper;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.entity.frogkeeper.FrogKeeperEncounters;
import com.ignilumen.uncannyencounters.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The altar is immovable; the world ledger retains cooldown even after creative replacement. */
public final class FrogAltarBlock extends Block {
    public FrogAltarBlock(Properties properties) { super(properties); }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                    Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.ACTIVATED_AMETHYST)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level instanceof ServerLevel server) || player.isSpectator()) return InteractionResult.SUCCESS;
        var ledger = FrogKeeperEncounters.get(server);
        if (ledger.activeAt(pos) != null) { FrogKeeper.tell(player, "already_present"); return InteractionResult.SUCCESS; }
        long remaining = ledger.readyAt(pos) - server.getGameTime();
        if (remaining > 0) { FrogKeeper.tell(player, "cooldown", (remaining + 19) / 20); return InteractionResult.SUCCESS; }
        BlockPos keeperPos = pos.south(3), frogPos = pos.south(6);
        if (!free(server, keeperPos, 0.65, 1.95) || !free(server, frogPos, 1.25, 0.8)) {
            FrogKeeper.tell(player, "space_blocked"); return InteractionResult.SUCCESS;
        }
        FrogKeeper keeper = ModEntities.FROG_KEEPER.create(server, EntitySpawnReason.TRIGGERED);
        if (keeper == null) return InteractionResult.FAIL;
        keeper.setAltar(pos);
        keeper.snapTo(Vec3.atBottomCenterOf(keeperPos), 180, 0);
        if (!server.addFreshEntity(keeper)) return InteractionResult.FAIL;
        ledger.register(keeper);
        stack.consume(1, player);
        keeper.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1, 0.6F);
        server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                30, 0.5, 0.6, 0.5, 0.02, 0.03, 0.02);
        FrogKeeper.tell(player, "instructions");
        return InteractionResult.SUCCESS;
    }
    private static boolean free(ServerLevel level, BlockPos pos, double width, double height) {
        if (!level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        Vec3 p = Vec3.atBottomCenterOf(pos);
        AABB bounds = new AABB(p.x - width / 2, p.y, p.z - width / 2, p.x + width / 2, p.y + height, p.z + width / 2);
        return level.noCollision(bounds);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) FrogKeeper.tell(player, "altar");
        return InteractionResult.SUCCESS;
    }
}
