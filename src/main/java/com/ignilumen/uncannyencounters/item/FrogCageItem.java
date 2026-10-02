package com.ignilumen.uncannyencounters.item;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.crystalfrog.FrogCageData;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public final class FrogCageItem extends Item {
    public FrogCageItem(Properties properties) { super(properties); }
    public static InteractionResult capture(Player player, CrystalFrog frog, InteractionHand hand) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!(frog.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (!frog.isAlive() || frog.isRemoved() || frog.isKeeperFrog() || !frog.isTame() || !frog.isOwnedBy(player))
            return tell(player, "own_only");
        if (frog.isDueling() || frog.wantsRetaliation() || frog.isFrightened() || frog.getTarget() != null
                || frog.isInLove() || frog.isPassenger() || frog.isVehicle() || frog.isLeashed()) return tell(player, "busy");
        ItemStack empty = player.getItemInHand(hand);
        if (!empty.is(ModItems.FROG_CAGE)) return InteractionResult.PASS;
        ItemStack filled = FrogCageData.pack(level, frog);
        if (empty.getCount() == 1) player.setItemInHand(hand, filled);
        else {
            if (!player.getInventory().add(filled)) return tell(player, "inventory_full");
            empty.shrink(1);
        }
        frog.talents().clearActive();
        frog.swallow().clear();
        frog.discard();
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.2F);
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!FrogCageData.filled(stack)) return InteractionResult.PASS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).canBeReplaced()) pos = pos.relative(context.getClickedFace());
        if (!player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.FAIL;
        CrystalFrog frog = FrogCageData.unpack(level, stack);
        if (frog == null) return tell(player, "invalid");
        if (frog.getOwnerReference().getUUID().equals(player.getUUID())) frog.setOwner(player);
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            var existing = dimension.getEntity(frog.getUUID());
            if (existing != null && !existing.isRemoved()) return tell(player, "already_out");
        }
        frog.snapTo(Vec3.atBottomCenterOf(pos), player.getYRot(), 0);
        frog.setDeltaMovement(Vec3.ZERO);
        frog.fallDistance = 0;
        if (!level.hasChunkAt(BlockPos.containing(frog.getBoundingBox().minX, frog.getY(), frog.getBoundingBox().minZ))
                || !level.hasChunkAt(BlockPos.containing(frog.getBoundingBox().maxX, frog.getY(), frog.getBoundingBox().maxZ))
                || !level.getWorldBorder().isWithinBounds(frog.getBoundingBox()) || !level.noCollision(frog)) return tell(player, "blocked");
        if (!level.addFreshEntity(frog)) return tell(player, "blocked");
        // Always exchange, including creative mode: one stored entity cannot exist both in hand and in the world.
        player.setItemInHand(context.getHand(), new ItemStack(ModItems.FROG_CAGE));
        frog.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1);
        return InteractionResult.SUCCESS;
    }
    private static InteractionResult tell(Player player, String key) {
        player.sendSystemMessage(Component.translatable("message.uncannyencounters.frog_cage." + key));
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                         Consumer<Component> tooltip, TooltipFlag flag) {
        var frog = FrogCageData.summary(stack);
        if (frog == null) { tooltip.accept(Component.translatable("tooltip.uncannyencounters.frog_cage.empty")); return; }
        tooltip.accept(Component.translatable("tooltip.uncannyencounters.frog_cage.stats", FrogCageData.number(frog.health()),
                FrogCageData.number(frog.maxHealth()), FrogCageData.number(frog.attack())));
        tooltip.accept(frog.variant().description());
        tooltip.accept(frog.style().description());
        for (var talent : frog.talents()) tooltip.accept(talent.description());
        if (frog.infertile()) tooltip.accept(Component.translatable("message.uncannyencounters.crystal_frog.infertile"));
        if (frog.age() != 0) tooltip.accept(Component.translatable(frog.age() < 0 ? "tooltip.uncannyencounters.frog_cage.growth"
                : "tooltip.uncannyencounters.frog_cage.cooldown", (Math.abs(frog.age()) + 19) / 20));
        if (frog.ageLocked()) tooltip.accept(Component.translatable("tooltip.uncannyencounters.frog_cage.age_locked"));
        Component parents = FrogCageData.parentComparison(stack);
        if (parents != null) tooltip.accept(parents);
        tooltip.accept(Component.translatable("tooltip.uncannyencounters.frog_cage.release"));
    }
}
