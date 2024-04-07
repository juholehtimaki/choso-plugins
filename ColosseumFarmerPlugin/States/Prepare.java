package com.theplug.ColosseumFarmerPlugin.States;

import com.theplug.ColosseumFarmerPlugin.ColosseumFarmerPlugin;
import com.theplug.ColosseumFarmerPlugin.ColosseumFarmerPluginConfig;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Spells.Ancient;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;

import java.util.Arrays;
import java.util.List;

public class Prepare implements State {

    static ColosseumFarmerPlugin plugin;
    ColosseumFarmerPluginConfig config;
    InventoryLoadout.InventoryLoadoutSetup loadout;
    public Prepare(ColosseumFarmerPlugin plugin, ColosseumFarmerPluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        loadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.loadout());
    }
    @Override
    public String name() {
        return "Preparing";
    }

    @Override
    public boolean shouldExecuteState() {
        return !Utility.isInInstancedRegion();
    }

    @Override
    public void threadedOnGameTick() {

    }

    private boolean enableAutoCast() {
        if (Ancient.ICE_BARRAGE.canCast()) {
            return Utility.setAutocastSpell("ICE_BARRAGE");
        } else if (Ancient.ICE_BURST.canCast()) {
            return Utility.setAutocastSpell("ICE_BURST");
        }
        else {
            Utility.sendGameMessage("No runes to auto cast freeze spell", "ColosseumFarmer");
            plugin.stop();
            return false;
        }
    }

    private boolean handleGearSwitch(){
        if (!plugin.getMageGear().isSatisfied(true)) {
            return plugin.getMageGear().handleSwitchTurbo();
        }
        return false;
    }

    private List<Widget> getFoodItems() {
        return Inventory.search().onlyUnnoted().filter((item) -> plugin.foodStats.getHealAmount(item.getItemId()) >= 8).result();
    }

    private boolean shouldRestock(boolean printMessages) {
        var totalFoodHealing = getFoodItems().stream().mapToInt(item -> plugin.foodStats.getHealAmount(item.getItemId())).sum();
        if (totalFoodHealing < config.bankUnderHpAmount()) {
            if (printMessages)
                Utility.sendGameMessage(String.format("Banking because total hp (%d) is less than the configured %d", totalFoodHealing, config.bankUnderHpAmount()), "ColosseumFarmer");
            return true;
        }

        var prayerDoses = BoostPotion.PRAYER_POTION.getTotalDosesInInventory();
        var restoDoses = BoostPotion.SUPER_RESTORE.getTotalDosesInInventory();
        var totalPrayerPointsCount =  prayerDoses * BoostPotion.PRAYER_POTION.findBoost(Skill.PRAYER).getBoostAmount() + restoDoses * BoostPotion.SUPER_RESTORE.findBoost(Skill.PRAYER).getBoostAmount();
        if (totalPrayerPointsCount < config.bankUnderPrayerAmount()) {
            if (printMessages)
                Utility.sendGameMessage(String.format("Banking because prayer points amount (%d) is less than the configured %d", totalPrayerPointsCount, config.bankUnderPrayerAmount()), "ColosseumFarmer");
            return true;
        }

        var boostPotionDoses = Arrays.stream(BoostPotion.values()).mapToInt(BoostPotion::getTotalDosesInInventory).sum();
        if (boostPotionDoses < config.bankUnderBoostDoseAmount()) {
            if (printMessages)
                Utility.sendGameMessage(String.format("Banking because boost potion doses left (%d) is less than the configured %d", boostPotionDoses, config.bankUnderBoostDoseAmount()), "ColosseumFarmer");
            return true;
        }

        return false;
    }

    public boolean handleGenericBanking() {
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
            Utility.sendGameMessage("Failed to travel to bank", "ColosseumFarmer");
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
            Utility.sendGameMessage("Stopping because not enough supplies", "ColosseumFarmer");
            plugin.stop();
            return false;
        }
        Bank.closeBank();
        return loadout.isSatisfied();
    }

    private boolean handleBanking() {
        if (!shouldRestock(true)) return false;
        if (loadout.isSatisfied()) return false;
        return handleGenericBanking();
    }

    private boolean handleEnter() {
        var enterance = TileObjects.search().withId(plugin.ENTRANCE_OBJECT_ID).nearestToPlayer();
        if (enterance.isEmpty() && !WebWalker.walkTo(plugin.COLOSSEUM_ENTRANCE_WORLD_POINT.dx(Utility.random(-1, 1)).dy(Utility.random(-1, 1)))) {
            Utility.sendGameMessage("Failed to webwalk to colosseum entrance", "ColosseumFarmer");
            plugin.stop();
            return false;
        }

        enterance = TileObjects.search().withId(plugin.ENTRANCE_OBJECT_ID).nearestToPlayer();
        if (enterance.isEmpty()) {
            return false;
        }

        Interaction.clickTileObject(enterance.get(), "Enter");
        return Utility.sleepUntilCondition(Utility::isInInstancedRegion);
    }

    @Override
    public void threadedLoop() {
        if (handleGearSwitch()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (!enableAutoCast()) {
            Utility.sendGameMessage("Failed to set auto cast", "ColosseumFarmer");
            plugin.stop();
            return;
        }
        if (handleBanking()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (plugin.paistiBreakHandler.shouldBreak(plugin)) {
            Utility.sendGameMessage("Taking a break", "ColosseumFarmer");
            plugin.paistiBreakHandler.startBreak(plugin);

            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !plugin.paistiBreakHandler.isBreakActive(plugin) && Utility.isLoggedIn(), 99999999, 5000);
        }
        if (handleEnter()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        Utility.sleepGaussian(100, 200);
    }
}
