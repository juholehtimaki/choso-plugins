package com.theplug.ScurriusPlugin.States;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.theplug.ScurriusPlugin.BankingMethod;
import com.theplug.ScurriusPlugin.ScurriusPlugin;
import com.theplug.ScurriusPlugin.ScurriusPluginConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

@Slf4j
public class RestockState implements State {
    static ScurriusPlugin plugin;
    ScurriusPluginConfig config;
    InventoryLoadout.InventoryLoadoutSetup loadout;
    private static int SCURRIUS_BARS = 14203;

    private static int fails = 0;

    public RestockState(ScurriusPlugin plugin, ScurriusPluginConfig config) {
        RestockState.plugin = plugin;
        this.config = config;
        loadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.gearLoadout());
    }

    @Override
    public String name() {
        return "Restocking";
    }

    @Override
    public boolean shouldExecuteState() {
        return !plugin.isInsideScurriusArea();
    }

    @Override
    public void threadedOnGameTick() {

    }

    private static boolean handleRejuvenationPoolPoh() {
        log.debug("handleRejuvenationPoolPoh");
        if (!House.isPlayerInsideHouse()) return false;
        var poolOfRefreshment = TileObjects.search().withName("Ornate pool of Rejuvenation").withAction("Drink").nearestToPlayer();
        if (poolOfRefreshment.isEmpty()) {
            Utility.sleepGaussian(1200, 1600);
            poolOfRefreshment = TileObjects.search().withName("Ornate pool of Rejuvenation").withAction("Drink").nearestToPlayer();
            if (poolOfRefreshment.isEmpty()) {
                return false;
            }
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            Interaction.clickTileObject(poolOfRefreshment.get(), "Drink");
            var restored = Utility.sleepUntilCondition(() -> {
                var _missingHp = Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.getBoostedSkillLevel(Skill.HITPOINTS);
                var _missingPrayer = Utility.getRealSkillLevel(Skill.PRAYER) - Utility.getBoostedSkillLevel(Skill.PRAYER);
                return _missingPrayer <= 0 && _missingHp <= 0;
            }, 10000, 300);
            if (restored) {
                Utility.sleepGaussian(1200, 1400);
                return true;
            }
        }
        return false;
    }

    private static boolean handleRejuvenationPoolFerox() {
        var poolOfRefreshment = TileObjects.search().withName("Pool of Refreshment").withAction("Drink").nearestToPlayer();
        if (poolOfRefreshment.isEmpty()) {
            Utility.sleepGaussian(1200, 1600);
            poolOfRefreshment = TileObjects.search().withName("Pool of Refreshment").withAction("Drink").nearestToPlayer();
            if (poolOfRefreshment.isEmpty()) {
                return false;
            }
        }

        Interaction.clickTileObject(poolOfRefreshment.get(), "Drink");
        Utility.sleepUntilCondition(() -> {
            var _missingHp = Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.getBoostedSkillLevel(Skill.HITPOINTS);
            var _missingPrayer = Utility.getRealSkillLevel(Skill.PRAYER) - Utility.getBoostedSkillLevel(Skill.PRAYER);
            return _missingPrayer <= 0 && _missingHp <= 0;
        }, 10000, 600);
        Utility.sleepGaussian(1200, 1400);
        return Utility.getRealSkillLevel(Skill.HITPOINTS) <= Utility.getBoostedSkillLevel(Skill.HITPOINTS) && Utility.getRealSkillLevel(Skill.PRAYER) <= Utility.getBoostedSkillLevel(Skill.PRAYER);
    }

    public boolean handleRestoreWithPool() {
        log.debug("handleRestoreWithPool");
        var missingHp = Utility.getRealSkillLevel(Skill.HITPOINTS) - Utility.getBoostedSkillLevel(Skill.HITPOINTS);
        var missingPrayer = Utility.getRealSkillLevel(Skill.PRAYER) - Utility.getBoostedSkillLevel(Skill.PRAYER);
        if (missingPrayer <= 0 && missingHp <= 0) {
            return false;
        }

        if (config.bankingMethod() == BankingMethod.HOUSE) {
            if (handleRejuvenationPoolPoh()) {
                return true;
            }
        }

        if (config.bankingMethod() == BankingMethod.FEROX) {
            if (handleRejuvenationPoolFerox()) {
                return true;
            }
        }
        return false;
    }

    public boolean handleGenericBanking() {
        log.debug("handleGenericBanking");
        if (loadout.isSatisfied()) return false;

        boolean successfullyTraveledToBank = false;
        int attempts = 3;
        for (int i = 0; i < attempts; i++) {
            if (Bank.isNearBank()) {
                successfullyTraveledToBank = true;
                break; // Exit the loop if we are already near a bank
            }

            WebWalker.walkToNearestBank();
            Utility.sleepUntilCondition(Bank::isNearBank);

            if (Bank.isNearBank()) {
                successfullyTraveledToBank = true;
                break; // Exit the loop if we reached the bank
            }
        }
        if (!successfullyTraveledToBank) {
            Utility.sendGameMessage("Failed to travel to bank", "AutoScurrius");
            plugin.stop();
            return false;
        }
        Bank.openBank();
        Utility.sleepUntilCondition(Bank::isOpen);
        boolean successfullyWithdrawn = false;
        for (int i = 0; i < attempts; i++) {
            if (loadout.handleWithdraw()) {
                successfullyWithdrawn = true;
                break;
            }
        }
        if (!successfullyWithdrawn) {
            Utility.sendGameMessage("Stopping because not enough supplies", "AutoScurrius");
            plugin.stop();
            return false;
        }
        Bank.closeBank();
        return loadout.isSatisfied();
    }

    public boolean handleBanking() {
        if (loadout.isSatisfied()) return false;

        if (config.bankingMethod() == BankingMethod.HOUSE || config.bankingMethod() == BankingMethod.FEROX) {
            return handleGenericBanking();
        }
        return false;
    }


    public boolean handleToggleRun() {
        if (Walking.isRunEnabled()) return false;
        Walking.setRun(true);
        return true;
    }

    public boolean handleDisablePrayers() {
        boolean disabledPrayer = false;
        for (var prayer : PPrayer.values()) {
            if (prayer.isActive()) {
                disabledPrayer = true;
                prayer.setEnabled(false);
                Utility.sleepGaussian(100, 200);
            }
        }
        return disabledPrayer;
    }

    public boolean handleTravel() {
        log.debug("handleTravel");
        var rocks = TileObjects.search().withId(SCURRIUS_BARS).nearestToPlayer();
        if (rocks.isEmpty() && !WebWalker.walkTo(ScurriusPlugin.SCURRIUS_ENTRANCE_WORLD_POINT.dx(Utility.random(-1, 1)).dy(Utility.random(-1, 1)))) {
            Utility.sendGameMessage("Failed to webwalk to Scurrius", "AutoScurrius");
            plugin.stop();
            return false;
        }

        rocks = TileObjects.search().withId(SCURRIUS_BARS).nearestToPlayer();
        if (rocks.isEmpty()) {
            return false;
        }

        Interaction.clickTileObject(rocks.get(), "Climb-through (private)");
        Utility.sleepUntilCondition(() -> plugin.isInsideScurriusArea() || Dialog.isConversationWindowUp());
        if (Dialog.isConversationWindowUp()) {
            Dialog.handleGenericDialog(new String[]{"Yes, and"});
            return Utility.sleepUntilCondition(() -> plugin.isInsideScurriusArea());
        }

        return plugin.isInsideScurriusArea();
    }


    public boolean handleWalkToScurrius() {
        if (!loadout.isSatisfied()) return false;

        if (config.bankingMethod() == BankingMethod.HOUSE || config.bankingMethod() == BankingMethod.FEROX) {
            if (handleTravel()) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void threadedLoop() {
        if (fails > 3) {
            Utility.sendGameMessage("Restock state failed action handling 3 times, stopping", "AutoScurrius");
            plugin.stop();
            return;
        }

        if (handleToggleRun()) {
            Utility.sleepGaussian(600, 800);
            return;
        }
        if (handleDisablePrayers()) {
            Utility.sleepGaussian(200, 400);
            return;
        }
        if (handleRestoreWithPool()) {
            Utility.sleepGaussian(200, 300);
            Utility.sendGameMessage("Restored stats using pool", "AutoScurrius");
            return;
        }
        if (handleBanking()) {
            Utility.sleepGaussian(200, 400);
            return;
        }
        if (plugin.paistiBreakHandler.shouldBreak(plugin)) {
            Utility.sendGameMessage("Taking a break", "AutoScurrius");
            plugin.paistiBreakHandler.startBreak(plugin);

            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !plugin.paistiBreakHandler.isBreakActive(plugin) || !plugin.paistiBreakHandler.getActivePlugins().contains(plugin), 99999999, 5000);
        }
        if (handleWalkToScurrius()) {
            Utility.sleepGaussian(200, 400);
            return;
        }
        Utility.sleepGaussian(200, 400);
    }
}
