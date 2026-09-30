package com.ignilumen.uncannyencounters.entity.frogkeeper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;

/** Checks actual persisted encounter codecs without starting a server or world. */
public final class FrogKeeperLedgerChecks {
    public static void main(String[] args) {
        BlockPos altar = new BlockPos(0, 64, 0);
        JsonObject legacy = JsonParser.parseString("""
                {"keeper":[1,2,3,4],"altar":[0,64,0],"defeated":true,"dead":true,"ready_at":12345}
                """).getAsJsonObject();
        var oldCompleted = read(legacy);
        check(oldCompleted.activeAt(altar) == null && oldCompleted.readyAt(altar) == 12345,
                "Previously completed encounters must retain their cooldown without reviving a frog");
        for (boolean keeperDiedFirst : new boolean[]{true, false}) {
            JsonObject half = legacy.deepCopy();
            half.addProperty("dead", keeperDiedFirst);
            half.addProperty("frog_dead", !keeperDiedFirst);
            half.addProperty("ready_at", 0);
            half.add("frog", JsonParser.parseString("[5,6,7,8]"));
            half.add("provoker", JsonParser.parseString("[9,10,11,12]"));
            var livingPartner = read(half);
            check(livingPartner.activeAt(altar) != null && livingPartner.readyAt(altar) == 0,
                    "Either surviving partner must reserve the altar with no premature cooldown");
            var encoded = FrogKeeperEncounters.TYPE.codec().encodeStart(JsonOps.INSTANCE, livingPartner).getOrThrow();
            var loaded = FrogKeeperEncounters.TYPE.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
            check(loaded.activeAt(altar) != null && encoded.getAsJsonArray().get(0).getAsJsonObject().has("provoker"),
                    "Saving must preserve the surviving partner and retaliation UUID");
            half.addProperty("dead", true);
            half.addProperty("frog_dead", true);
            half.addProperty("ready_at", 24000);
            var completed = read(half);
            check(completed.activeAt(altar) == null && completed.readyAt(altar) == 24000,
                    "Both deaths must release the altar and preserve the recorded cooldown");
        }
        legacy.addProperty("dead", false);
        legacy.addProperty("ready_at", 0);
        check(read(legacy).activeAt(altar) != null, "Existing active encounters must not complete during migration");
        System.out.println("PASS: legacy migration, both survivor states, retaliation UUID and cooldown persistence.");
    }
    private static FrogKeeperEncounters read(JsonObject object) {
        JsonArray list = new JsonArray();
        list.add(object);
        return FrogKeeperEncounters.TYPE.codec().parse(JsonOps.INSTANCE, list).getOrThrow();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
