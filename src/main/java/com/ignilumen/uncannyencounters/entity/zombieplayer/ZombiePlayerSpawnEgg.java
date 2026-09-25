package com.ignilumen.uncannyencounters.entity.zombieplayer;

import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.entity.ZombiePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/** Testing copies inherit the actual egg user's skin and never consume the death-spawn slot. */
public final class ZombiePlayerSpawnEgg extends SpawnEggItem {
    public ZombiePlayerSpawnEgg(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos clicked = context.getClickedPos();
        boolean offset = !level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty();
        return spawn(level, context.getPlayer(), context.getItemInHand(),
                offset ? clicked.relative(context.getClickedFace()) : clicked, true,
                offset && context.getClickedFace() == Direction.UP);
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK || !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof LiquidBlock))
            return InteractionResult.PASS;
        if (!level.mayInteract(player, hit.getBlockPos()) || !player.mayUseItemAt(hit.getBlockPos(), hit.getDirection(), player.getItemInHand(hand)))
            return InteractionResult.FAIL;
        return level instanceof ServerLevel server ? spawn(server, player, player.getItemInHand(hand), hit.getBlockPos(), false, false)
                : InteractionResult.SUCCESS;
    }

    private InteractionResult spawn(ServerLevel level, @Nullable Player player, ItemStack stack, BlockPos pos, boolean down, boolean up) {
        ZombiePlayer zombie = ModEntities.ZOMBIE_PLAYER.spawn(level, stack, player, pos, EntitySpawnReason.SPAWN_ITEM_USE, down, up);
        if (zombie == null) return InteractionResult.FAIL;
        if (player != null) zombie.bind(player.getUUID(), ResolvableProfile.createResolved(player.getGameProfile()),
                ZombiePlayer.skinParts(player), player.getMainArm() == HumanoidArm.LEFT, zombie.blockPosition(),
                level.getGameTime() + ZombiePlayerSpawns.LIFETIME, false);
        stack.consume(1, player);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        return InteractionResult.SUCCESS;
    }
}
