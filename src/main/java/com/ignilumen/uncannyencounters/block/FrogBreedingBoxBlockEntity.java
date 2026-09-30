package com.ignilumen.uncannyencounters.block;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.crystalfrog.FrogBreedingHabitat;
import com.ignilumen.uncannyencounters.entity.crystalfrog.FrogCageData;
import com.ignilumen.uncannyencounters.item.ModItems;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

/** Two parents, two material slots, four offspring slots; births are server-side button transactions. */
public final class FrogBreedingBoxBlockEntity extends BaseContainerBlockEntity {
    public static final int SIZE = 8, SHARDS = 2, CAGES = 3, FIRST_CHILD = 4;
    public static final BlockEntityType<FrogBreedingBoxBlockEntity> TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            UncannyEncounters.id("frog_breeding_box"), new BlockEntityType<>(FrogBreedingBoxBlockEntity::new, Set.of(ModBlocks.FROG_BREEDING_BOX)));
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int ageTicks, habitatScore;

    public FrogBreedingBoxBlockEntity(BlockPos pos, BlockState state) { super(TYPE, pos, state); }
    public static void initialize() { FrogBreedingBoxMenu.initialize(); }
    @Override public int getContainerSize() { return SIZE; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> value) { items = value; }
    @Override protected Component getDefaultName() { return Component.translatable("block.uncannyencounters.frog_breeding_box"); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 2) return FrogCageData.filled(stack);
        return slot == SHARDS ? stack.is(Items.AMETHYST_SHARD) : slot == CAGES && stack.is(ModItems.FROG_CAGE);
    }
    public int habitatScore() { return habitatScore; }
    public void refreshHabitat() {
        if (level instanceof ServerLevel server) habitatScore = FrogBreedingHabitat.score(server, worldPosition);
    }
    public int status(Player player) {
        var a = FrogCageData.summary(items.get(0));
        var b = FrogCageData.summary(items.get(1));
        if (a == null || b == null) return 1;
        if (FrogCageData.identity(items.get(0)).isEmpty() || FrogCageData.identity(items.get(1)).isEmpty()) return 1;
        if (FrogCageData.identity(items.get(0)).equals(FrogCageData.identity(items.get(1)))) return 7;
        if (a.owner().isEmpty() || b.owner().isEmpty() || !a.owner().get().equals(player.getUUID()) || !b.owner().get().equals(player.getUUID())) return 2;
        if (a.king() || b.king()) return 3;
        if (a.age() != 0 || b.age() != 0) return 4;
        if (!items.get(SHARDS).is(Items.AMETHYST_SHARD) || items.get(SHARDS).getCount() < 2 || !items.get(CAGES).is(ModItems.FROG_CAGE)) return 5;
        if (emptyChildSlot() < 0) return 6;
        return 0;
    }
    private int emptyChildSlot() {
        for (int slot = FIRST_CHILD; slot < SIZE; slot++) if (items.get(slot).isEmpty()) return slot;
        return -1;
    }
    public boolean breed(Player player) {
        if (!(level instanceof ServerLevel server) || player.isSpectator() || !stillValid(player) || status(player) != 0) return false;
        var first = FrogCageData.unpack(server, items.get(0));
        var second = FrogCageData.unpack(server, items.get(1));
        if (first == null || second == null || first.getUUID().equals(second.getUUID())
                || first.getOwnerReference() == null || second.getOwnerReference() == null
                || !first.getOwnerReference().getUUID().equals(player.getUUID())
                || !second.getOwnerReference().getUUID().equals(player.getUUID())) return false;
        first.setOwner(player);
        second.setOwner(player);
        int output = emptyChildSlot();
        if (output < 0) return false;
        // These are temporary data objects, never inserted into the world or ticked as entities.
        Vec3 birth = Vec3.atCenterOf(worldPosition);
        first.setPos(birth);
        second.setPos(birth);
        refreshHabitat();
        var child = first.getBreedOffspring(server, second);
        if (!(child instanceof com.ignilumen.uncannyencounters.entity.CrystalFrog baby)) return false;
        baby.setBaby(true);
        baby.setPos(birth);
        ItemStack offspring = FrogCageData.pack(server, baby);
        FrogCageData.recordParents(offspring, items.get(0), items.get(1));
        first.setAge(6000);
        second.setAge(6000);
        ItemStack parentA = FrogCageData.pack(server, first), parentB = FrogCageData.pack(server, second);
        // All checks/serialization precede this mutation; a second click sees the new cooldown.
        items.set(0, parentA);
        items.set(1, parentB);
        items.set(output, offspring);
        items.get(SHARDS).shrink(2);
        items.get(CAGES).shrink(1);
        setChanged();
        if (player instanceof ServerPlayer breeder) {
            breeder.awardStat(Stats.ANIMALS_BRED);
            CriteriaTriggers.BRED_ANIMALS.trigger(breeder, first, second, baby);
        }
        server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.3F);
        return true;
    }
    public void tickStoredFrogs() {
        if (++ageTicks < 20) return;
        ageTicks = 0;
        boolean changed = false;
        for (int slot = 0; slot < SIZE; slot++) if (slot != SHARDS && slot != CAGES)
            changed |= FrogCageData.advanceAge(items.get(slot), 20);
        if (changed) setChanged();
    }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        refreshHabitat();
        return new FrogBreedingBoxMenu(id, inventory, this);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        ageTicks = 0;
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }
}
