package com.ignilumen.uncannyencounters;

import com.ignilumen.uncannyencounters.entity.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.GameType;

/** In-game render smoke test and real networked player capture/escape test. */
public final class CaveAnglerClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            var connection = world.getConnection();
            server.runCommand("difficulty normal");
            server.runCommand("fill -6 80 -6 6 90 8 air");
            server.runCommand("fill -4 79 -4 4 79 4 stone");
            server.runCommand("fill -4 89 -4 4 89 4 stone");
            AtomicReference<CaveAngler> anglerRef = new AtomicReference<>();
            AtomicReference<Pig> pigRef = new AtomicReference<>();
            server.runOnServer(s -> {
                var level = connection.getServerLevel();
                var player = connection.getServerPlayer();
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(5, 83, 10);
                CaveAngler angler = ModEntities.CAVE_ANGLER.create(level, EntitySpawnReason.COMMAND);
                angler.setPos(0.5, 86, 0.5);
                angler.setPersistenceRequired();
                level.addFreshEntity(angler);
                Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
                pig.setPos(0.5, 80, 0.8125);
                pig.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
                level.addFreshEntity(pig);
                angler.setLastHurtByMob(pig);
                anglerRef.set(angler);
                pigRef.set(pig);
            });
            server.waitFor(s -> anglerRef.get().captive() == pigRef.get()
                    && Math.abs(pigRef.get().getY() - 81) < 0.15);
            server.runCommand("tick freeze");
            connection.waitForChunksRender();
            connection.waitForClientboundEntityUpdates(ModEntities.CAVE_ANGLER, ModEntities.ANGLER_TONGUE);
            context.runOnClient(client -> {
                client.player.setYRot(155F);
                client.player.setXRot(0F);
            });
            context.waitTicks(5);
            context.takeScreenshot("cave-angler-capture");
            server.runOnServer(s -> {
                pigRef.get().discard();
                anglerRef.get().discard();
                var level = connection.getServerLevel();
                CaveAngler angler = ModEntities.CAVE_ANGLER.create(level, EntitySpawnReason.COMMAND);
                angler.setPos(0.5, 86, 0.5);
                angler.setPersistenceRequired();
                level.addFreshEntity(angler);
                anglerRef.set(angler);
                var player = connection.getServerPlayer();
                player.setGameMode(GameType.SURVIVAL);
                player.teleportTo(0.5, 80, 0.8125);
            });
            server.runCommand("tick unfreeze");
            server.waitFor(s -> anglerRef.get().captive() == connection.getServerPlayer()
                    && Math.abs(connection.getServerPlayer().getY() - 81) < 0.15);
            context.getInput().holdKey(options -> options.keySprint);
            context.getInput().holdKeyFor(options -> options.keyUp, 15);
            context.getInput().releaseKey(options -> options.keySprint);
            server.runOnServer(s -> {
                var player = connection.getServerPlayer();
                if (anglerRef.get().captive() != player || Math.abs(player.getX() - 0.5) > 0.65
                        || Math.abs(player.getZ() - 0.8125) > 0.65) {
                    throw new AssertionError("A captured player must not be able to sprint away");
                }
            });
            connection.waitForClientboundEntityUpdates(ModEntities.ANGLER_TONGUE);
            int tongueId = server.computeOnServer(s -> anglerRef.get().tongue().getId());
            context.runOnClient(client -> {
                var tongue = client.level.getEntity(tongueId);
                if (tongue == null) throw new AssertionError("Tongue was not synchronized to the client");
                client.gameMode.attack(client.player, tongue);
            });
            server.waitFor(s -> anglerRef.get().captive() == null);
            server.runOnServer(s -> {
                if (anglerRef.get().recoveryTicks() < 250) throw new AssertionError("Player attack did not sever the tongue");
            });
        }
    }
}
