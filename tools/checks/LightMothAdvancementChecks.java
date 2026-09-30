import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;

/** Uses the actual advancement codec without starting a client, server or world. */
public final class LightMothAdvancementChecks {
    private static final Path DATA = Path.of("src/main/resources/data/uncannyencounters");

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var ops = RegistryOps.create(JsonOps.INSTANCE, new RegistryOps.RegistryInfoLookup() {
            @Override
            public <T> Optional<HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> registry) {
                return Optional.of(new HolderGetter<T>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public Optional<Holder.Reference<T>> get(ResourceKey<T> key) {
                        var id = key.identifier();
                        // Only references are needed by these advancements. Recipes must exist;
                        // the one mod item is supplied explicitly, without Fabric initialization.
                        if (registry.equals(Registries.RECIPE) && id.getNamespace().equals("uncannyencounters")
                                && Files.isRegularFile(DATA.resolve("recipe/" + id.getPath() + ".json"))) {
                            return Optional.of(Holder.Reference.createStandAlone(this, key));
                        }
                        if (registry.equals(Registries.ITEM)) {
                            if (id.toString().equals("uncannyencounters:moth_scale_dust")) {
                                return Optional.of(Holder.Reference.createStandAlone(this, key));
                            }
                            return (Optional<Holder.Reference<T>>) (Optional<?>) BuiltInRegistries.ITEM.get(id);
                        }
                        return Optional.empty();
                    }
                    @Override
                    public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
                        return Optional.empty();
                    }
                });
            }
        });
        for (String name : new String[]{"moth_lure", "enhanced_moth_lure"}) {
            JsonObject json;
            try (var reader = Files.newBufferedReader(DATA.resolve("advancement/recipes/misc/" + name + ".json"))) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            var parsed = Advancement.CODEC.parse(ops, json).getOrThrow();
            if (parsed.criteria().size() != 2) throw new AssertionError("Lost criteria: " + name);
            JsonObject broken = json.deepCopy();
            JsonObject conditions = broken.getAsJsonObject("criteria").getAsJsonObject("has_recipe")
                    .getAsJsonObject("conditions");
            JsonElement recipes = conditions.remove("recipes");
            conditions.add("recipe", recipes);
            var rejected = Advancement.CODEC.parse(ops, broken);
            if (!rejected.isError() || !rejected.error().orElseThrow().message().contains("No key recipes")) {
                throw new AssertionError("Regression fixture failed to reproduce missing recipes: " + name);
            }
            System.out.println(name + ": complete 26.3 advancement codec PASS; old recipe field rejected");
        }
    }
}
