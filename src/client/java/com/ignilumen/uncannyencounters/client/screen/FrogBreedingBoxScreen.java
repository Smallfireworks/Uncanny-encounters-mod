package com.ignilumen.uncannyencounters.client.screen;

import com.ignilumen.uncannyencounters.block.FrogBreedingBoxMenu;
import com.ignilumen.uncannyencounters.entity.crystalfrog.FrogCageData;
import com.ignilumen.uncannyencounters.entity.crystalfrog.FrogGenetics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Fixed parent cards and separate offspring slots, using the vanilla container input/sync protocol. */
public final class FrogBreedingBoxScreen extends AbstractContainerScreen<FrogBreedingBoxMenu> {
    private static final int TEXT = 0xFF404040, MUTED = 0xFF606060;
    private static final Identifier CONTAINER_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");
    private Button breed;
    public FrogBreedingBoxScreen(FrogBreedingBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, 236);
        inventoryLabelX = 81;
        inventoryLabelY = 146;
    }
    @Override protected void init() {
        super.init();
        breed = addRenderableWidget(Button.builder(Component.translatable("screen.uncannyencounters.breeding.breed"), button -> {
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 224, topPos + 107, 90, 20).build());
        updateButton();
    }
    @Override protected void containerTick() { super.containerTick(); updateButton(); }
    private void updateButton() {
        breed.active = menu.status() == 0;
        breed.setTooltip(Tooltip.create(Component.translatable("screen.uncannyencounters.breeding.status." + menu.status())));
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        // Slice the vanilla 176x166 panel around our larger layout; slots are drawn independently.
        blitPanel(graphics, x + 7, y + 7, imageWidth - 14, imageHeight - 14, 8, 8, 1, 1);
        blitPanel(graphics, x, y, 7, 7, 0, 0, 7, 7);
        blitPanel(graphics, x + imageWidth - 7, y, 7, 7, 169, 0, 7, 7);
        blitPanel(graphics, x, y + imageHeight - 7, 7, 7, 0, 159, 7, 7);
        blitPanel(graphics, x + imageWidth - 7, y + imageHeight - 7, 7, 7, 169, 159, 7, 7);
        blitPanel(graphics, x + 7, y, imageWidth - 14, 7, 7, 0, 162, 7);
        blitPanel(graphics, x + 7, y + imageHeight - 7, imageWidth - 14, 7, 7, 159, 162, 7);
        blitPanel(graphics, x, y + 7, 7, imageHeight - 14, 0, 7, 7, 152);
        blitPanel(graphics, x + imageWidth - 7, y + 7, 7, imageHeight - 14, 169, 7, 7, 152);
        graphics.fill(x + 161, y + 21, x + 162, y + 101, 0xFF8B8B8B);
        graphics.fill(x + 162, y + 21, x + 163, y + 101, 0xFFFFFFFF);
        for (var slot : menu.slots) {
            blitPanel(graphics, x + slot.x - 1, y + slot.y - 1, 18, 18, 61, 16, 18, 18);
        }
    }
    private void blitPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                           int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, x, y, u, v, width, height,
                sourceWidth, sourceHeight, 256, 256);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, 10, 7, TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        parentCard(graphics, 0, 12);
        parentCard(graphics, 1, 174);
        graphics.text(font, Component.translatable("screen.uncannyencounters.breeding.shards"), 34, 113, MUTED, false);
        graphics.text(font, Component.translatable("screen.uncannyencounters.breeding.cage"), 113, 113, MUTED, false);
        graphics.text(font, Component.translatable("screen.uncannyencounters.breeding.children"), 12, 133, TEXT, false);
        var a = FrogCageData.summary(menu.parent(0));
        var b = FrogCageData.summary(menu.parent(1));
        boolean affinity = a != null && a.nursery() || b != null && b.nursery();
        String multiplier = String.format(java.util.Locale.ROOT, "%.2f", FrogGenetics.habitatMultiplier(affinity ? menu.habitat() : 0));
        fitted(graphics, Component.translatable("screen.uncannyencounters.breeding.habitat", menu.habitat(), multiplier),
                217, 133, 96, affinity ? TEXT : MUTED);
    }
    private void parentCard(GuiGraphicsExtractor graphics, int index, int x) {
        var item = menu.parent(index);
        var frog = FrogCageData.summary(item);
        graphics.text(font, Component.translatable("screen.uncannyencounters.breeding.parent." + index), x + 22, 21, MUTED, false);
        if (frog == null) {
            fitted(graphics, Component.translatable("screen.uncannyencounters.breeding.insert"), x, 55, 130, MUTED);
            return;
        }
        fitted(graphics, FrogCageData.frogName(item), x + 22, 31, 114, TEXT);
        fitted(graphics, Component.translatable("screen.uncannyencounters.breeding.health", FrogCageData.number(frog.health()), FrogCageData.number(frog.maxHealth())), x, 43, 134, TEXT);
        fitted(graphics, Component.translatable("screen.uncannyencounters.breeding.attack", FrogCageData.number(frog.attack())), x, 53, 134, TEXT);
        fitted(graphics, frog.style().description(), x, 63, 134, MUTED);
        if (frog.talents().isEmpty()) fitted(graphics, Component.translatable("talent.uncannyencounters.crystal_frog.none"), x, 73, 134, MUTED);
        for (int i = 0; i < frog.talents().size(); i++) fitted(graphics, frog.talents().get(i).description(), x, 73 + 10 * i, 134, TEXT);
        int seconds = (Math.abs(frog.age()) + 19) / 20;
        String time = String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        String state = frog.age() < 0 ? (frog.ageLocked() ? "locked" : "growing") : frog.infertile() ? "infertile" : frog.age() > 0 ? "cooldown" : "adult";
        fitted(graphics, Component.translatable("screen.uncannyencounters.breeding." + state, time), x, 93, 134, MUTED);
    }
    private void fitted(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        String value = text.getString();
        if (font.width(value) > width) value = font.plainSubstrByWidth(value, width - font.width("…")) + "…";
        graphics.text(font, value, x, y, color, false);
    }
}
