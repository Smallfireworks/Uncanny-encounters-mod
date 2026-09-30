package com.ignilumen.uncannyencounters.entity;

import com.ignilumen.uncannyencounters.entity.crystalfrog.*;
import com.ignilumen.uncannyencounters.entity.frogkeeper.FrogKeeperEncounters;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import static com.ignilumen.uncannyencounters.entity.crystalfrog.CrystalFrogTalent.*;

/** Manual server integration checks; compiling this suite does not execute a game. */
public final class FrogBreedingGameTests {
    private CrystalFrog frog(GameTestHelper test, double health, double attack, CrystalFrogTalent... talents) {
        CrystalFrog frog = test.spawn(ModEntities.CRYSTAL_FROG, new Vec3(3, 1, 3));
        frog.configureTraits(health, attack, CrystalFrogStyle.GUARD, List.of(talents));
        return frog;
    }
    private CompoundTag save(GameTestHelper test, CrystalFrog frog) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, test.getLevel().registryAccess());
        frog.saveWithoutId(output);
        return output.buildResult();
    }
    private Player owner(GameTestHelper test) {
        Player owner = new Player(test.getLevel(), new GameProfile(UUID.randomUUID(), "breeder")) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
            @Override public boolean isClientAuthoritative() { return false; }
        };
        owner.snapTo(test.absoluteVec(new Vec3(4, 1, 4)), 0, 0);
        return owner;
    }
    private void floor(GameTestHelper test) {
        for (int x = 0; x <= 9; x++) for (int z = 0; z <= 9; z++) {
            test.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 4; y++) test.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void kingReloadKeepsBaseValuesHealthAndTwoTalents(GameTestHelper test) {
        CrystalFrog frog = frog(test, 60, 8, FROG_KING, RETALIATING_SHELL);
        frog.setHealth(100);
        for (int reload = 0; reload < 3; reload++) {
            var saved = save(test, frog);
            frog.load(TagValueInput.create(ProblemReporter.DISCARDING, test.getLevel().registryAccess(), saved));
            test.assertTrue(frog.getAttributeBaseValue(Attributes.MAX_HEALTH) == 60 && frog.getMaxHealth() == 120
                    && frog.getAttributeValue(Attributes.ATTACK_DAMAGE) == 16 && frog.getHealth() == 100,
                    "Transient king bonuses must neither compound nor truncate saved health");
            test.assertTrue(frog.talents().all().equals(List.of(FROG_KING, RETALIATING_SHELL)), "Both talents must survive reload");
            test.assertTrue(Math.abs(frog.getScale() - 1.5) < 0.001 && !frog.canFallInLove(), "King must be enlarged and infertile");
        }
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void babiesInheritOwnershipOnlyFromTheSameOwnerAndNeverRollWildTraits(GameTestHelper test) {
        CrystalFrog a = frog(test, 56, 10, SCATTER_SLIME, DOUBLE_ECHO);
        CrystalFrog b = frog(test, 56, 10, SCATTER_SLIME, DOUBLE_ECHO);
        Player player = owner(test);
        a.tame(player);
        b.tame(player);
        for (int i = 0; i < 30; i++) {
            CrystalFrog child = (CrystalFrog)a.getBreedOffspring(test.getLevel(), b);
            test.assertTrue(child != null && child.isOwnedBy(player), "Same-owner parents must produce an owned child");
            test.assertTrue(child.getAttributeBaseValue(Attributes.MAX_HEALTH) >= 56
                    && child.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) >= 10, "Breeding must not reroll wild stats");
            child.setBaby(true);
            test.assertTrue(child.isBaby() && !child.canFallInLove(), "Babies cannot breed");
        }
        b.tame(owner(test));
        CrystalFrog mixed = (CrystalFrog)a.getBreedOffspring(test.getLevel(), b);
        test.assertTrue(mixed != null && !mixed.isTame(), "Different owners require a new tame");
        a.setAge(6000);
        test.assertTrue(a.getBreedOffspring(test.getLevel(), b) == null, "Cooldown must prevent another birth");
        test.succeed();
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void twoAbilitiesAreIndependentAndKingCannotSwallowBosses(GameTestHelper test) {
        floor(test);
        CrystalFrog frog = frog(test, 20, 3, CRYSTAL_SHELL, CRYSTAL_ECHO);
        frog.tame(owner(test));
        var target = test.spawn(EntityTypes.PIG, new Vec3(3, 1, 4));
        target.setNoAi(true);
        test.assertTrue(frog.assistOwner(target), "Daily support must establish a legal target");
        frog.hurtServer(test.getLevel(), test.getLevel().damageSources().mobAttack(target), 11);
        test.assertTrue(frog.hasTalentShell(), "The first talent must form a shield");
        frog.setHealth(20);
        frog.assistOwner(target);
        frog.talents().afterMeleeHit(target);
        float health = target.getHealth();
        CrystalFrog king = frog(test, 60, 8, FROG_KING);
        king.tame(owner(test));
        var boss = test.spawn(ModEntities.FROG_KEEPER, new Vec3(4, 1, 4));
        boss.setHealth(1);
        king.assistOwner(boss);
        test.assertTrue(!king.swallow().canStart(), "Boss immunity must override the swallow threshold");
        frog.removeFreeWill();
        test.runAfterDelay(13, () -> {
            test.assertTrue(target.getHealth() < health, "The second talent must execute despite the first talent's shield");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 30)
    public void keeperRequiresARealDuelAndBothDeathsStartTheAltarCooldown(GameTestHelper test) {
        floor(test);
        var keeper = test.spawn(ModEntities.FROG_KEEPER, new Vec3(4.5, 1, 3.5));
        BlockPos altar = test.absolutePos(new BlockPos(4, 1, 0));
        keeper.setAltar(altar);
        Player player = owner(test);
        CrystalFrog challenger = frog(test, 120, 50, CRYSTAL_ECHO);
        challenger.tame(player);
        challenger.snapTo(test.absoluteVec(new Vec3(3, 1, 6)), 0, 0);
        test.runAfterDelay(3, () -> {
            CrystalFrog champion = keeper.companion();
            test.assertTrue(champion != null && champion.getMaxHealth() == 120, "Keeper must own the fixed-strength king");
            var hit = test.getLevel().damageSources().playerAttack(player);
            test.assertTrue(!keeper.hurtServer(test.getLevel(), hit, 500) && !champion.hurtServer(test.getLevel(), hit, 500),
                    "Player damage must not bypass the encounter");
            CrystalFrogDuels.useStick(player, challenger);
            keeper.challenge(player);
            test.assertTrue(challenger.isDuelingWith(champion), "Selecting a frog and clicking the keeper must start a challenge");
            champion.hurtServer(test.getLevel(), hit, 500);
            test.assertTrue(challenger.isDueling(), "Rejected outsider damage must not cancel this challenge");
            FrogTalentAttack.capture(challenger, champion).hurt(null, 10000);
            test.assertTrue(champion.isAlive() && champion.getHealth() == 30 && keeper.challengeDefeated(),
                    "Only a nonlethal frog victory unseals the keeper");
            test.assertTrue(keeper.getTarget() == null && champion.getTarget() == null,
                    "The winning duel hit must leave both partners neutral");
            test.assertTrue(keeper.hurtServer(test.getLevel(), hit, 5), "Keeper must become vulnerable after the win");
            test.assertTrue(keeper.getTarget() == player && champion.getTarget() == player && champion.wantsRetaliation()
                    && !champion.isFrightened(), "Attacking the keeper must provoke both, even below half frog health");
            keeper.hurtServer(test.getLevel(), hit, 10000);
            var ledger = FrogKeeperEncounters.get(test.getLevel());
            test.assertTrue(champion.isAlive() && champion.getTarget() == player && ledger.keeperDead(keeper.getUUID())
                    && ledger.activeAt(altar) != null && ledger.readyAt(altar) == 0,
                    "The frog must survive its keeper and continue fighting without freeing the altar");
            champion.hurtServer(test.getLevel(), hit, 10000);
            test.assertTrue(!champion.isAlive() && ledger.frogDead(keeper.getUUID()) && ledger.activeAt(altar) == null
                    && ledger.readyAt(altar) == test.getLevel().getGameTime() + 12000,
                    "Only the second death begins the persisted ten-minute cooldown");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 30)
    public void attackingTheFrogAlertsAnUnarmedKeeperWhoSurvivesItsDeath(GameTestHelper test) {
        floor(test);
        var keeper = test.spawn(ModEntities.FROG_KEEPER, new Vec3(4.5, 1, 3.5));
        keeper.setAltar(test.absolutePos(new BlockPos(4, 1, 0)));
        Player player = owner(test);
        test.runAfterDelay(3, () -> {
            CrystalFrog champion = keeper.companion();
            test.assertTrue(champion != null, "Keeper must have a companion");
            keeper.wonChallenge();
            test.assertTrue(keeper.getMainHandItem().isEmpty() && keeper.getOffhandItem().isEmpty()
                    && keeper.getMaxHealth() == 24 && keeper.getAttributeValue(Attributes.ATTACK_DAMAGE) == 13
                    && keeper.getAttributeValue(Attributes.ARMOR) == 0, "Keeper must use the agreed unarmed stats");
            var hit = test.getLevel().damageSources().playerAttack(player);
            champion.hurtServer(test.getLevel(), hit, 10000);
            var ledger = FrogKeeperEncounters.get(test.getLevel());
            test.assertTrue(!champion.isAlive() && keeper.isAlive() && keeper.getTarget() == player,
                    "A fatal first hit on the frog must still alert its surviving keeper");
            player.snapTo(keeper.position().add(63, 0, 0), 0, 0);
            test.assertTrue(ledger.retaliationTarget(test.getLevel(), keeper.getUUID(), keeper) == player,
                    "The surviving keeper must retain its target inside 64 blocks");
            player.snapTo(keeper.position().add(65, 0, 0), 0, 0);
            keeper.tick();
            test.assertTrue(keeper.getTarget() == null && keeper.isAlive() && ledger.activeAt(keeper.altar()) != null,
                    "The survivor must disengage, remain alive and keep the altar occupied");
            player.snapTo(keeper.position().add(1, 0, 0), 0, 0);
            test.assertTrue(ledger.retaliationTarget(test.getLevel(), keeper.getUUID(), keeper) == null,
                    "Returning near a neutral survivor must not resume a completed retaliation");
            keeper.hurtServer(test.getLevel(), hit, 10000);
            test.assertTrue(ledger.activeAt(keeper.altar()) == null && ledger.readyAt(keeper.altar()) == test.getLevel().getGameTime() + 12000,
                    "The opposite death order must also begin cooldown only at the second death");
            test.succeed();
        });
    }

    @GameTest(structure = "uncannyencounters-test:arena", maxTicks = 20)
    public void swallowingAnOpponentStopsAtTheDuelFloor(GameTestHelper test) {
        floor(test);
        CrystalFrog king = frog(test, 100, 3, FROG_KING);
        CrystalFrog target = frog(test, 100, 3);
        target.snapTo(test.absoluteVec(new Vec3(3, 1, 4.5)), 0, 0);
        Player owner = owner(test);
        king.tame(owner);
        target.tame(owner);
        CrystalFrogDuels.useStick(owner, king);
        CrystalFrogDuels.useStick(owner, target);
        test.assertTrue(king.isDuelingWith(target), "Fixture must start a legal match");
        target.setHealth(35); // Below the king's 40-point swallow threshold, above the 25-point floor.
        test.assertTrue(king.swallow().canStart(), "A low-health participating frog remains eligible for swallowing");
        king.swallow().start();
        for (int i = 0; i < 10; i++) king.swallow().tick();
        test.assertTrue(target.isAlive() && target.getHealth() == 25 && !king.isDueling(), "Swallow damage must end the duel without killing");
        test.assertTrue(king.tongueTargetId() == -1 && !king.swallow().active(), "The finishing bite must release the tongue context");
        test.succeed();
    }
}
