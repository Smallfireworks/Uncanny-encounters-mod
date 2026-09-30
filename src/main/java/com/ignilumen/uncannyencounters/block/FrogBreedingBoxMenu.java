package com.ignilumen.uncannyencounters.block;

import com.ignilumen.uncannyencounters.UncannyEncounters;
import com.ignilumen.uncannyencounters.entity.crystalfrog.FrogCageData;
import com.ignilumen.uncannyencounters.item.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FrogBreedingBoxMenu extends AbstractContainerMenu {
    public static final MenuType<FrogBreedingBoxMenu> TYPE = Registry.register(BuiltInRegistries.MENU,
            UncannyEncounters.id("frog_breeding_box"), new MenuType<>(FrogBreedingBoxMenu::new, FeatureFlags.VANILLA_SET));
    private final Container box;
    private final DataSlot status, habitat;
    public static void initialize() {}
    public FrogBreedingBoxMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(FrogBreedingBoxBlockEntity.SIZE)); }
    public FrogBreedingBoxMenu(int id, Inventory inventory, Container box) {
        super(TYPE, id);
        this.box = box;
        checkContainerSize(box, FrogBreedingBoxBlockEntity.SIZE);
        addBoxSlot(0, 12, 22);
        addBoxSlot(1, 174, 22);
        addBoxSlot(2, 12, 109);
        addBoxSlot(3, 91, 109);
        for (int slot = 4; slot < 8; slot++) addBoxSlot(slot, 130 + (slot - 4) * 20, 129);
        addStandardInventorySlots(inventory, 81, 156);
        status = box instanceof FrogBreedingBoxBlockEntity entity ? new DataSlot() {
            @Override public int get() { return entity.status(inventory.player); }
            @Override public void set(int value) {}
        } : DataSlot.standalone();
        habitat = box instanceof FrogBreedingBoxBlockEntity entity ? new DataSlot() {
            @Override public int get() { return entity.habitatScore(); }
            @Override public void set(int value) {}
        } : DataSlot.standalone();
        addDataSlot(status);
        addDataSlot(habitat);
    }
    private void addBoxSlot(int index, int x, int y) {
        addSlot(new Slot(box, index, x, y) {
            @Override public boolean mayPlace(ItemStack stack) {
                if (index < 2) return FrogCageData.filled(stack);
                return index == 2 ? stack.is(Items.AMETHYST_SHARD) : index == 3 && stack.is(ModItems.FROG_CAGE);
            }
        });
    }
    public ItemStack parent(int index) { return box.getItem(index); }
    public int status() { return status.get(); }
    public int habitat() { return habitat.get(); }
    @Override public boolean stillValid(Player player) { return box.stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int button) {
        return button == 0 && stillValid(player) && box instanceof FrogBreedingBoxBlockEntity entity && entity.breed(player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 8) {
            if (!moveItemStackTo(stack, 8, slots.size(), true)) return ItemStack.EMPTY;
        } else if (FrogCageData.filled(stack)) {
            if (!moveItemStackTo(stack, 0, 2, false)) return ItemStack.EMPTY;
        } else if (stack.is(Items.AMETHYST_SHARD)) {
            if (!moveItemStackTo(stack, 2, 3, false)) return ItemStack.EMPTY;
        } else if (stack.is(ModItems.FROG_CAGE)) {
            if (!moveItemStackTo(stack, 3, 4, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
