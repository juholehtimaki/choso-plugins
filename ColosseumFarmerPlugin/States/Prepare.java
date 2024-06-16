package com.theplug.ColosseumFarmerPlugin.States;

import com.theplug.ColosseumFarmerPlugin.ColosseumFarmerPlugin;
import com.theplug.ColosseumFarmerPlugin.ColosseumFarmerPluginConfig;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Prepare implements State {

    static ColosseumFarmerPlugin plugin;
    ColosseumFarmerPluginConfig config;
    InventoryLoadout.InventoryLoadoutSetup loadout;

    private final WorldPoint BANK_WORLDPOINT = new WorldPoint(1780, 3096, 0);

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

    private boolean handleGearSwitch() {
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
        var totalPrayerPointsCount = prayerDoses * BoostPotion.PRAYER_POTION.findBoost(Skill.PRAYER).getBoostAmount() + restoDoses * BoostPotion.SUPER_RESTORE.findBoost(Skill.PRAYER).getBoostAmount();
        if (totalPrayerPointsCount < config.bankUnderPrayerAmount()) {
            if (printMessages)
                Utility.sendGameMessage(String.format("Banking because prayer points amount (%d) is less than the configured %d", totalPrayerPointsCount, config.bankUnderPrayerAmount()), "ColosseumFarmer");
            return true;
        }

        var boostPotionDoses = Arrays.stream(BoostPotion.values()).filter(b -> Arrays.stream(b.getBoosts()).noneMatch(boost -> boost.getSkill() == Skill.PRAYER)).mapToInt(BoostPotion::getTotalDosesInInventory).sum();
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
            WebWalker.walkTo(BANK_WORLDPOINT.dx(Utility.random(-1, 1)).dy(Utility.random(-1, 1)));
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
        if (shouldRestock(false)) return false;
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

    private boolean handleDeathWalking() {
        var graveStone = NPCs.search().withName("Grave").withAction("Loot").first();
        if (graveStone.isEmpty()) {
            return false;
        }
        Interaction.clickNpc(graveStone.get(), "Loot");
        Utility.sleepGaussian(1800, 3000);
        if (!plugin.getMageGear().isSatisfied(true)) {
            plugin.getMageGear().handleSwitchTurbo();
        }
        Utility.sleepGaussian(600, 1200);
        return true;
    }

    private boolean enoughTimeHasPassedAfterDeath() {
        if (!config.deathWalking() || plugin.playerDiedOnTick.get() == -1) return true;
        return Utility.getTickCount() - plugin.playerDiedOnTick.get() > 30;
    }

    private boolean handleDisablePrayers() {
        var allPrayers = Arrays.stream(PPrayer.values()).filter(PPrayer::isActive).collect(Collectors.toList());
        if (allPrayers.isEmpty()) return false;
        for (var prayer : allPrayers) {
            prayer.setEnabledWithoutClicks(false);
        }
        return false;
    }

    @Override
    public void threadedLoop() {
        var isGravePresent = NPCs.search().withName("Grave").withAction("Loot").first().isPresent();
        if (handleDisablePrayers()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (isGravePresent && handleDeathWalking()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (handleGearSwitch()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (isGravePresent) return;
        if (enoughTimeHasPassedAfterDeath() && handleBanking()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        if (enoughTimeHasPassedAfterDeath() && plugin.paistiBreakHandler.shouldBreak(plugin)) {
            Utility.sendGameMessage("Taking a break", "ColosseumFarmer");
            plugin.paistiBreakHandler.startBreak(plugin);

            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !plugin.paistiBreakHandler.isBreakActive(plugin), 99999999, 5000);
        }
        if (enoughTimeHasPassedAfterDeath() && handleEnter()) {
            Utility.sleepGaussian(100, 200);
            return;
        }
        Utility.sleepGaussian(100, 200);
    }
}
