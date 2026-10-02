package com.ignilumen.uncannyencounters.entity.crystalfrog;

import com.ignilumen.uncannyencounters.entity.CrystalFrog;
import com.ignilumen.uncannyencounters.entity.ModEntities;
import com.ignilumen.uncannyencounters.item.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

/** Full frog data stays on a non-stackable item; the small summary is safe to display on the client. */
public final class FrogCageData {
    public record Summary(float health, float maxHealth, double attack, int age, boolean ageLocked,
                          CrystalFrogStyle style, List<CrystalFrogTalent> talents, Optional<UUID> owner, CrystalFrogVariant variant) {
        public boolean king() { return talents.contains(CrystalFrogTalent.FROG_KING); }
        public boolean infertile() { return king() || variant != CrystalFrogVariant.NORMAL; }
        public boolean nursery() { return talents.contains(CrystalFrogTalent.CRYSTAL_NURSERY); }
    }
    public static boolean filled(ItemStack stack) {
        return stack.is(ModItems.CAGED_CRYSTAL_FROG) && data(stack).getCompound("Frog").isPresent();
    }
    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
    public static Optional<UUID> identity(ItemStack stack) {
        return data(stack).getCompound("Frog").flatMap(t -> t.read("UUID", UUIDUtil.CODEC));
    }
    public static @Nullable Summary summary(ItemStack stack) {
        if (!stack.is(ModItems.CAGED_CRYSTAL_FROG)) return null;
        var tag = data(stack).getCompound("Summary");
        if (tag.isEmpty()) return null;
        CompoundTag s = tag.get();
        return new Summary(s.getFloatOr("Health", 0), s.getFloatOr("MaxHealth", 0), s.getDoubleOr("Attack", 0),
                s.getIntOr("Age", 0), s.getBooleanOr("AgeLocked", false), CrystalFrogStyle.from(s.getStringOr("Style", "guard"), new UUID(0, 0)),
                List.of(CrystalFrogTalent.from(s.getStringOr("Talent", "none")), CrystalFrogTalent.from(s.getStringOr("SecondTalent", "none")))
                        .stream().filter(t -> t != CrystalFrogTalent.NONE).toList(), s.read("Owner", UUIDUtil.CODEC), CrystalFrogVariant.from(s.getStringOr("Variant", "normal")));
    }
    public static Component frogName(ItemStack stack) {
        var summary = summary(stack);
        Component name = summary == null || summary.variant() == CrystalFrogVariant.NORMAL
                ? Component.translatable("entity.uncannyencounters.crystal_frog") : summary.variant().description();
        return stack.getOrDefault(DataComponents.CUSTOM_NAME, name);
    }
    public static ItemStack pack(ServerLevel level, CrystalFrog frog) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        frog.saveWithoutId(output);
        CompoundTag entity = output.buildResult();
        for (String key : List.of("Pos", "Motion", "Rotation", "Passengers", "Leash", "fall_distance")) entity.remove(key);
        CompoundTag summary = new CompoundTag();
        summary.putFloat("Health", frog.getHealth());
        summary.putFloat("MaxHealth", frog.getMaxHealth());
        summary.putDouble("Attack", frog.getAttributeValue(Attributes.ATTACK_DAMAGE));
        summary.putInt("Age", frog.getAge());
        summary.putBoolean("AgeLocked", entity.getBooleanOr("AgeLocked", false));
        summary.putString("Variant", frog.variant().id());
        summary.putString("Style", frog.duelStyle().id());
        var talents = frog.talents().all();
        summary.putString("Talent", talents.isEmpty() ? "none" : talents.getFirst().id());
        summary.putString("SecondTalent", talents.size() < 2 ? "none" : talents.get(1).id());
        if (frog.getOwnerReference() != null) summary.store("Owner", UUIDUtil.CODEC, frog.getOwnerReference().getUUID());
        CompoundTag data = new CompoundTag();
        data.put("Frog", entity);
        data.put("Summary", summary);
        entity.getCompound("CrystalFrogParents").ifPresent(parents -> data.put("Parents", parents.copy()));
        ItemStack stack = new ItemStack(ModItems.CAGED_CRYSTAL_FROG);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        if (frog.hasCustomName()) stack.set(DataComponents.CUSTOM_NAME, frog.getCustomName());
        return stack;
    }
    public static @Nullable CrystalFrog unpack(ServerLevel level, ItemStack stack) {
        if (!filled(stack)) return null;
        var tag = data(stack).getCompound("Frog").orElseThrow();
        CrystalFrog frog = new CrystalFrog(ModEntities.CRYSTAL_FROG, level);
        frog.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        if (!frog.isAlive() || frog.isKeeperFrog() || !frog.isTame() || frog.getOwnerReference() == null) return null;
        if (stack.has(DataComponents.CUSTOM_NAME)) frog.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        frog.setPersistenceRequired();
        return frog;
    }
    public static boolean advanceAge(ItemStack stack, int ticks) {
        if (!filled(stack)) return false;
        CompoundTag data = data(stack), entity = data.getCompound("Frog").orElseThrow();
        int oldAge = entity.getIntOr("Age", 0);
        if (oldAge == 0 || oldAge < 0 && entity.getBooleanOr("AgeLocked", false)) return false;
        int age = oldAge < 0 ? Math.min(0, oldAge + ticks) : Math.max(0, oldAge - ticks);
        entity.putInt("Age", age);
        data.getCompound("Summary").ifPresent(s -> s.putInt("Age", age));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return true;
    }
    public static void recordParents(ItemStack child, ItemStack first, ItemStack second) {
        CustomData.update(DataComponents.CUSTOM_DATA, child, tag -> {
            CompoundTag parents = new CompoundTag();
            data(first).getCompound("Summary").ifPresent(s -> parents.put("A", s.copy()));
            data(second).getCompound("Summary").ifPresent(s -> parents.put("B", s.copy()));
            tag.put("Parents", parents);
            tag.getCompound("Frog").ifPresent(entity -> entity.put("CrystalFrogParents", parents.copy()));
        });
    }
    public static @Nullable Component parentComparison(ItemStack stack) {
        var parents = data(stack).getCompound("Parents");
        if (parents.isEmpty()) return null;
        var a = parents.get().getCompound("A");
        var b = parents.get().getCompound("B");
        if (a.isEmpty() || b.isEmpty()) return null;
        return Component.translatable("tooltip.uncannyencounters.frog_cage.parents",
                number(a.get().getFloatOr("MaxHealth", 0)), number(a.get().getDoubleOr("Attack", 0)),
                number(b.get().getFloatOr("MaxHealth", 0)), number(b.get().getDoubleOr("Attack", 0)));
    }
    public static String number(double value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }
    private FrogCageData() {}
}
