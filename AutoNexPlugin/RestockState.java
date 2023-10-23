package com.theplug.AutoNexPlugin;

import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.RuneLite;
import net.runelite.client.game.WorldService;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

@Slf4j
public class RestockState implements State {

    AutoNexPlugin plugin;

    InventoryLoadout.InventoryLoadoutSetup loadout;

    AutoNexPluginConfig config;

    static final int NEX_DOOR = 42967;

    static WorldService worldService = RuneLite.getInjector().getInstance(WorldService.class);

    public RestockState(AutoNexPlugin plugin, AutoNexPluginConfig config) {
        super();
        this.plugin = plugin;
        this.config = config;
        loadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.gearLoadout());
    }

    @Override
    public String name() {
        return "RestockState";
    }

    @Override
    public boolean shouldExecuteState() {
        return plugin.isInsideBankRoom();
    }

    @Override
    public void threadedOnGameTick() {

    }

    public boolean handleMissingStats() {
        boolean isFullHp = Utility.getBoostedSkillLevel(Skill.HITPOINTS) >= Utility.getRealSkillLevel(Skill.HITPOINTS);
        boolean isFullPrayAndRange = Utility.getBoostedSkillLevel(Skill.PRAYER) >= Utility.getRealSkillLevel(Skill.PRAYER) && Utility.getBoostedSkillLevel(Skill.RANGED) >= Utility.getRealSkillLevel(Skill.RANGED);

        if (isFullHp && isFullPrayAndRange) return false;

        Utility.sendGameMessage("Restoring HP and reduced stats", "AutoNex");

        var bank = Bank.openBank();
        var interactedWithBank = false;
        if (bank && Utility.sleepUntilCondition(Bank::isOpen)) {
            Bank.depositInventory();
            interactedWithBank = true;
            Utility.sleepGaussian(1200, 1800);

            if (!isFullHp) {
                if (!Bank.withdraw("Shark", 5, false)) {
                    Utility.sendGameMessage("Not enough sharks");
                    plugin.stop();
                }
                Utility.sleepUntilCondition(() -> Inventory.search().withName("Shark").first().isPresent());
                var sharks = BankInventory.search().withName("Shark").result();
                for (var s : sharks) {
                    if (Utility.getBoostedSkillLevel(Skill.HITPOINTS) >= Utility.getRealSkillLevel(Skill.HITPOINTS)) {
                        Utility.sleepGaussian(2000, 3000);
                        break;
                    }
                    Interaction.clickWidget(s, "Eat");
                    Utility.sleepGaussian(2000, 3000);
                }
            }


            if (!isFullPrayAndRange) {
                if (!Bank.withdraw("Super restore(4)", 1, false)) {
                    Utility.sendGameMessage("Not super restores");
                    plugin.stop();
                }
                Utility.sleepUntilCondition(() -> Inventory.search().withName("Super restore(4)").first().isPresent());
                for (int i = 0; i < 4; i++) {
                    var superRestore = BankInventory.search().matchesWildCardNoCase("Super restore*").first();
                    if (Utility.getBoostedSkillLevel(Skill.PRAYER) >= Utility.getRealSkillLevel(Skill.PRAYER) && Utility.getBoostedSkillLevel(Skill.RANGED) >= Utility.getRealSkillLevel(Skill.RANGED)) {
                        Utility.sleepGaussian(2000, 3000);
                        break;
                    }
                    Interaction.clickWidget(superRestore.get(), "Drink");
                    Utility.sleepGaussian(2000, 3000);
                }
            }

            if (Inventory.getEmptySlots() <= 28) {
                Bank.depositInventory();
                Utility.sleepGaussian(1200, 1800);
            }
        }
        return interactedWithBank;
    }

    public boolean handleLoadout() {
        if (loadout.isSatisfied()) return false;
        var successfullyWithdrew = loadout.handleWithdraw();
        if (!successfullyWithdrew) {
            plugin.stop();
            Utility.sendGameMessage("Failed to withdraw loadout", "AutoNex");
            return false;
        }
        return true;
    }

    public boolean handleEnterNex() {
        WorldResult worldResult = worldService.getWorlds();
        World currentWorld = worldResult.findWorld(Utility.getWorldId());

        if (config.onlyMassWorlds()) {
            if (currentWorld.getId() != 505 && currentWorld.getId() != 332) {
                Utility.sendGameMessage("Must be in world 505 or 332", "AutoNex");
                plugin.stop();
                return false;
            }
        }

        var isLoadOutSatisfied = loadout.isSatisfied();
        boolean isFullHp = Utility.getBoostedSkillLevel(Skill.HITPOINTS) >= Utility.getRealSkillLevel(Skill.HITPOINTS);
        boolean isFullPrayAndRange = Utility.getBoostedSkillLevel(Skill.PRAYER) >= Utility.getRealSkillLevel(Skill.PRAYER) && Utility.getBoostedSkillLevel(Skill.RANGED) >= Utility.getRealSkillLevel(Skill.RANGED);

        if (isLoadOutSatisfied && isFullHp && isFullPrayAndRange) {
            var nexDoor = TileObjects.search().withId(NEX_DOOR).withAction("Pass (normal)").nearestToPlayer();
            if (nexDoor.isEmpty()) return false;
            return Interaction.clickTileObject(nexDoor.get(), "Pass (normal)");
        }
        return false;
    }

    public boolean handlePrayers() {
        var offensivePrayer = plugin.getOffensivePray().get();
        if (PPrayer.PROTECT_FROM_MAGIC.isActive() || offensivePrayer.isActive()) {
            offensivePrayer.setEnabled(false);
            PPrayer.PROTECT_FROM_MAGIC.setEnabled(false);
            return true;
        }
        return false;
    }

    @Override
    public void threadedLoop() {
        if (!config.assistMode()) {
            if (plugin.getStopHasBeenRequested()) {
                Utility.sendGameMessage("Stopping since a stop had been requested", "AutoNex");
                handlePrayers();
                plugin.stop();
                return;
            }
            if (handlePrayers()) {
                Utility.sleepGaussian(200, 300);
                return;
            }
            if (handleMissingStats()) {
                Utility.sleepGaussian(200, 300);
                return;
            }
            if (handleLoadout()) {
                Utility.sleepGaussian(200, 300);
                return;
            }
            if (handleEnterNex()) {
                Utility.sleepGaussian(2000, 4000);
                return;
            }
        }
        Utility.sleepGaussian(50, 100);
    }
}
