package com.theplug.NMZHelperPlugin;

import com.theplug.OBS.ThreadedRunner;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.GameSimulator.Trackers.ActorTracker;
import com.theplug.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.theplug.PaistiUtils.API.Potions.BoostPotion;
import com.theplug.PaistiUtils.API.Potions.StatusPotion;
import com.theplug.PaistiUtils.API.Prayer.PPrayer;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.theplug.SES.PluginId;
import com.theplug.SES.SG.SG;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(name = "<HTML><FONT COLOR=#1BB532>PNMZHelper</FONT></HTML>", description = "Drinks potions, specs, rock cakes etc. in NMZ", enabledByDefault = false, tags = {"paisti", "nmz"})
public class NMZHelperPlugin extends Plugin {
    int nextPrayerPotAt = generateNextPrayerPotAt();
    int nextAbsorptionAt = generateNextAbsorptionAt();
    int nextRockCakeAt = generateNextRockCakeAt();
    Instant lastOverloadDrankAt = null;
    private InventoryLoadout.InventoryLoadoutSetup specWeaponLoadout = null;
    private InventoryLoadout.InventoryLoadoutSetup loadoutBeforeSwitch = null;
    @Inject
    NMZHelperPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedRunner runner = new ThreadedRunner();
    SG SG = new SG(PluginId.PNMZHELPER, runner);
    private HotkeyListener startHotkeyListener = null;
    private long powerSurgePickedUpAt = 0;
    private long lastSpecUsedAt = 0;
    private static final WorldPoint NMZ_COORD = new WorldPoint(2611, 3115, 0);

    @Provides
    public NMZHelperPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(NMZHelperPluginConfig.class);
    }

    public int getAvailableNmzBoxes() {
        return 15 - Utility.getVarbitValue(Varbits.DAILY_HERB_BOXES_COLLECTED);
    }

    public int getTotalMoneyInCoffer() {
        return Utility.getVarbitValue(3948) * 1000;
    }

    public int getAvailableNMZPoints() {
        return Utility.getVarpValue(VarPlayer.NMZ_REWARD_POINTS);
    }

    public int getSuperRangingInBarrelCount() {
        return Utility.getVarbitValue(3951);
    }

    public int getSuperMagicInBarrelCount() {
        return Utility.getVarbitValue(3952);
    }

    public int getOverloadsInBarrelCount() {
        return Utility.getVarbitValue(3953);
    }

    public int getAbsorptionsInBarrelCount() {
        return Utility.getVarbitValue(3954);
    }

    public boolean isNmzDreamPrepared() {
        return Utility.getVarbitValue(3946) != 0;
    }


    @Override
    protected void startUp() throws Exception {
        loadoutBeforeSwitch = null;

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        startHotkeyListener = config.startHotkey() != null ? new HotkeyListener(() -> config.startHotkey()) {
            @Override
            public void hotkeyPressed() {
                PaistiUtils.runOnExecutor(() -> {
                    if (runner.isRunning()) {
                        stop();
                    } else {
                        start();
                    }
                    return null;
                });
            }
        } : null;
        if (startHotkeyListener != null) {
            keyManager.registerKeyListener(startHotkeyListener);
        }
    }

    private int generateNextPrayerPotAt() {
        return Utility.random(12, 20);
    }

    private int generateNextAbsorptionAt() {
        return Utility.random(150, 500);
    }

    private static final int[] rockCakeHpAtChoices = new int[]{
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3
    };

    private int generateNextRockCakeAt() {
        // Pick a random value from a predefined array to have a bias towards 2, 3...
        return rockCakeHpAtChoices[Utility.random(0, rockCakeHpAtChoices.length - 1)];
    }

    private boolean isInsideNmz() {
        return Utility.isInInstancedRegion();
    }


    @Override
    protected void shutDown() throws Exception {
        if (startHotkeyListener != null) {
            keyManager.unregisterKeyListener(startHotkeyListener);
        }
        runner.stop();
    }

    private void stop() {
        Utility.sendGameMessage("Stopped", "PNMZHelper");
        runner.stop();
    }

    private boolean handlePrayerPotions() {
        if (Utility.getBoostedSkillLevel(Skill.PRAYER) <= nextPrayerPotAt) {
            // Drink prayer potions until full prayer
            var drankPotion = drinkPrayerPotionIfGoodValue();
            if (!drankPotion) return false;
            Utility.sendGameMessage("Drinking prayer potions", "PNMZHelper");
            Utility.sleepGaussian(1800, 2400);
            drinkPrayerPotionIfGoodValue();
            return true;
        }
        return false;
    }

    public boolean drinkPrayerPotionIfGoodValue() {
        BoostPotion prayerBoostPot = BoostPotion.PRAYER_POTION.findInInventory().isEmpty() ? BoostPotion.SUPER_RESTORE : BoostPotion.PRAYER_POTION;
        var potionInInventory = prayerBoostPot.findInInventory();
        if (potionInInventory.isEmpty()) return false;

        var missingPrayer = Math.abs(prayerBoostPot.findBoost(Skill.PRAYER).getCurrentBoostAmount());
        if (missingPrayer >= prayerBoostPot.findBoost(Skill.PRAYER).getBoostAmount() + Utility.random(1, 5)) {
            if (prayerBoostPot.drink()) {
                return true;
            }
        }

        return false;
    }

    private boolean handleAbsorptionPotions() {
        var absorb = Inventory.search().matchesWildcard("Absorption*").first();
        if (absorb.isEmpty()) return false;

        if (Utility.getVarbitValue(Varbits.NMZ_ABSORPTION) > nextAbsorptionAt) return false;
        Utility.sendGameMessage("Drinking absorption potions", "PNMZHelper");

        Utility.sleepUntilCondition(() -> {
            Utility.sleepGaussian(60, 120);
            var absorb2 = Inventory.search().matchesWildcard("Absorption*").first();
            if (absorb2.isEmpty()) return true;
            if (Utility.getVarbitValue(Varbits.NMZ_ABSORPTION) > 950) return true;
            if (!Utility.isInInstancedRegion()) return true;
            var currentAbsorption = Utility.getVarbitValue(Varbits.NMZ_ABSORPTION);
            Interaction.clickWidget(absorb2.get(), "Drink");
            Utility.sleepUntilCondition(() -> Utility.getVarbitValue(Varbits.NMZ_ABSORPTION) > currentAbsorption, 1200, 600);
            Utility.sleepOneTick();
            Utility.sleepOneTick();
            return false;
        }, 28000, 600);

        nextAbsorptionAt = generateNextAbsorptionAt();
        return true;
    }

    private boolean handleRockCaking() {
        var hpReduceItem = Inventory.search().withName("Dwarven rock cake", "Locator orb").onlyUnnoted().first();
        if (hpReduceItem.isEmpty()) return false;
        if (PPrayer.PROTECT_FROM_MELEE.isActive()) return false;
        if (Utility.getBoostedSkillLevel(Skill.HITPOINTS) < nextRockCakeAt) return false;
        if (BoostPotion.OVERLOAD_POTION.findInInventory().isPresent()) {
            if (BoostPotion.OVERLOAD_POTION.isAnyStatBoostBelow(1) && Utility.getBoostedSkillLevel(Skill.HITPOINTS) > 50) {
                // Don't rock cake if overload should be used
                return false;
            }
        }
        if (lastOverloadDrankAt != null
                && Instant.now().isAfter(lastOverloadDrankAt.plusSeconds(290))
                && Instant.now().isBefore(lastOverloadDrankAt.plusSeconds(306))) {
            // Don't rock cake if overload was drank about 5 mins ago
            return false;
        }
        Utility.sendGameMessage("Reducing HP to 1", "PNMZHelper");
        nextRockCakeAt = generateNextRockCakeAt();

        Utility.sleepUntilCondition(() -> {
            if (BoostPotion.OVERLOAD_POTION.findInInventory().isPresent()) {
                if (BoostPotion.OVERLOAD_POTION.isAnyStatBoostBelow(1) && Utility.getBoostedSkillLevel(Skill.HITPOINTS) >= 50) {
                    BoostPotion.OVERLOAD_POTION.drink();
                    // Don't rock cake if overload should be used
                    return true;
                }
            }
            if (lastOverloadDrankAt != null
                    && Instant.now().isAfter(lastOverloadDrankAt.plusSeconds(290))
                    && Instant.now().isBefore(lastOverloadDrankAt.plusSeconds(306))) {
                // Don't rock cake if overload was drank about 5 mins ago
                return true;
            }
            if (!Utility.isInInstancedRegion()) return true;
            if (Utility.getBoostedSkillLevel(Skill.HITPOINTS) <= 1) return true;

            var hpReduceItem2 = Inventory.search().withName("Dwarven rock cake", "Locator orb").onlyUnnoted().first();
            if (hpReduceItem2.isEmpty()) return false;
            Interaction.clickWidget(hpReduceItem2.get(), "Guzzle", "Feel");
            Utility.sleepOneTick();
            Utility.sleepGaussian(50, 200);
            return false;
        }, 18000, 600);
        return true;
    }

    private boolean handleStatBoostPotions() {
        var potionsToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> {
            if (potion.findBoost(Skill.PRAYER) != null) return false;
            if (potion.findInInventory().isEmpty()) return false;
            return potion.isAnyStatBoostBelow(config.drinkPotionsBelowBoost());
        }).collect(Collectors.toList()));

        if (potionsToDrink == null || potionsToDrink.isEmpty()) return false;

        var drankPotion = false;
        for (var boostPotion : potionsToDrink) {
            if (boostPotion == BoostPotion.OVERLOAD_POTION && Utility.getBoostedSkillLevel(Skill.HITPOINTS) < 51) {
                continue;
            }

            if (!drankPotion && boostPotion.drink()) {
                Utility.sendGameMessage("Drank " + boostPotion.name(), "PNMZHelper");
                if (boostPotion == BoostPotion.OVERLOAD_POTION) {
                    lastOverloadDrankAt = Instant.now();
                    // Sleep longer for overloads so plugin won't click on rock cake too early
                    Utility.sleepGaussian(8000, 10000);
                } else {
                    Utility.sleepGaussian(1800, 2400);
                }
                drankPotion = true;
            }
        }
        return drankPotion;
    }

    private boolean useSpecUntilOutOfEnergy = false;

    private boolean isPowerSurgeActive() {
        return System.currentTimeMillis() - powerSurgePickedUpAt < 45000;
    }

    private boolean shouldSpec() {
        if (!config.useSpecialAttack()) return false;
        if (config.onlySpecDuringPowerSurge() && !isPowerSurgeActive()) return false;
        var specEnergy = Utility.getSpecialAttackEnergy();
        if (specEnergy < config.specEnergyMinimum()) {
            if (System.currentTimeMillis() - lastSpecUsedAt > 3600) {
                useSpecUntilOutOfEnergy = false;
            }
            return false;
        }
        if (specEnergy >= 100 || useSpecUntilOutOfEnergy) {
            useSpecUntilOutOfEnergy = true;
            return true;
        }
        return false;
    }

    public boolean handlePowerups() {
        if (config.useZapper()) {
            var zapper = TileObjects.search().withId(26256).withAction("Activate").nearestToPlayer();
            if (zapper.isPresent()) {
                Utility.sendGameMessage("Using zapper", "PNMZHelper");
                Interaction.clickTileObject(zapper.get(), "Activate");
                Utility.sleepUntilCondition(() -> TileObjects.search().withId(26256).withAction("Activate").nearestToPlayer().isEmpty());
                return true;
            }
        }
        if (config.useRecurrentDamage()) {
            var recurrentDamage = TileObjects.search().withId(26265).withAction("Activate").nearestToPlayer();
            if (recurrentDamage.isPresent()) {
                Utility.sendGameMessage("Using recurrent damage", "PNMZHelper");
                Interaction.clickTileObject(recurrentDamage.get(), "Activate");
                Utility.sleepUntilCondition(() -> TileObjects.search().withId(26265).withAction("Activate").nearestToPlayer().isEmpty());
                return true;
            }
        }
        if (config.usePowerSurge() && config.useSpecialAttack()) {
            var powerSurge = TileObjects.search().withId(26264).withAction("Activate").nearestToPlayer();
            if (powerSurge.isPresent()) {
                Utility.sendGameMessage("Using power surge", "PNMZHelper");
                Interaction.clickTileObject(powerSurge.get(), "Activate");
                Utility.sleepUntilCondition(() -> TileObjects.search().withId(26264).withAction("Activate").nearestToPlayer().isEmpty());
                return true;
            }
        }
        return false;
    }

    public boolean handlePrayerToggling() {
        if (config.offensivePrayDuringPowerSurge()) {
            if (Utility.getBoostedSkillLevel(Skill.PRAYER) == 0) return false;
            if (isPowerSurgeActive()) {
                return PPrayer.enableBestOffensivePrayers(true);
            } else {
                return PPrayer.disableOffensivePrayers();
            }
        }
        return false;
    }

    public boolean handleSpecialAttacking() {
        if (!config.useSpecialAttack()) return false;
        var shouldSpec = shouldSpec();
        if (loadoutBeforeSwitch != null && !shouldSpec && !isPowerSurgeActive()) {
            Utility.sendGameMessage("Switching back to previous gear", "PNMZHelper");
            loadoutBeforeSwitch.handleSwitch();
            Utility.sleepGaussian(600, 700);
            if (loadoutBeforeSwitch.isSatisfied(true)) {
                loadoutBeforeSwitch = null;
            }
            return true;
        }

        if (!shouldSpec) return false;

        var haveRequiredGear = specWeaponLoadout.getEquipmentInstructions().stream().allMatch(req -> req.findInEquipment().isPresent() || !req.findInInventory().isEmpty());
        if (!haveRequiredGear) return false;

        var tempLoadoutBeforeSwitch = InventoryLoadout.InventoryLoadoutSetup.getOnlyEquipmentFromCurrentItems();
        var didSwitches = false;
        didSwitches = specWeaponLoadout.handleSwitch();
        if (didSwitches) {
            Utility.sendGameMessage("Switched to spec gear", "PNMZHelper");
            loadoutBeforeSwitch = tempLoadoutBeforeSwitch;
        }
        if (!Utility.isSpecialAttackEnabled()
                && Utility.getSpecialAttackEnergy() >= config.specEnergyMinimum()
                && specWeaponLoadout.isSatisfied(true)) {
            Utility.specialAttack();
            lastSpecUsedAt = System.currentTimeMillis();
            Utility.sendGameMessage("Special attacking", "PNMZHelper");
        }

        return true;
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (e.getGroup().equals("NMZHelperPluginConfig") && e.getKey().equals("startHotkey")) {
            if (startHotkeyListener != null) {
                keyManager.unregisterKeyListener(startHotkeyListener);
            }
            startHotkeyListener = config.startHotkey() != null ? new HotkeyListener(() -> config.startHotkey()) {
                @Override
                public void hotkeyPressed() {
                    PaistiUtils.runOnExecutor(() -> {
                        if (runner.isRunning()) {
                            stop();
                        } else {
                            start();
                        }
                        return null;
                    });
                }
            } : null;
            if (startHotkeyListener != null) {
                keyManager.registerKeyListener(startHotkeyListener);
            }
        }
    }

    private void start() {
        Utility.sendGameMessage("Started", "PNMZHelper");
        loadoutBeforeSwitch = null;
        specWeaponLoadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.specEquipmentString());
        runner.start();
    }

    private int getOverloadsWithdrawQuantity() {
        return config.overloadsQuantity() - BoostPotion.OVERLOAD_POTION.getTotalDosesInInventory();
    }

    private int getAbsorptionWithdrawQuantity() {
        return config.absorptionQuantity() - StatusPotion.ABSORPTION.getTotalDosesInInventory();
    }

    private int getSuperRangingWithdrawQuantity() {
        return config.superRangingQuantity() - BoostPotion.SUPER_RANGING_POTION.getTotalDosesInInventory();
    }

    private int getSuperMagicWithdrawQuantity() {
        return config.superMagicQuantity() - BoostPotion.SUPER_MAGIC_POTION.getTotalDosesInInventory();
    }

    private void handleRestocking() {
        if (Walking.getPlayerLocation().distanceTo(NMZ_COORD) > 12 && !WebWalker.walkTo(NMZ_COORD)) {
            Utility.sendGameMessage("Failed to walk to NMZ", "PNMZHelper");
            stop();
            return;
        }
        handleDepositExcessPotions();
        if (shouldBuyPotions()) {
            handleBuyPotions();
            return;
        }
        if (shouldWithdrawPotions()) {
            handleWithdrawPotions();
        }

        handleEnterNmz();
    }

    private void handleEnterNmz() {
        if (!isNmzDreamPrepared()) {
            if (getTotalMoneyInCoffer() < 25000) {
                Utility.sendGameMessage("Not enough money in coffer to enter NMZ", "PNMZHelper");
                stop();
                return;
            }
            var dominic = NPCs.search().withName("Dominic Onion").nearestToPlayer();
            if (dominic.isEmpty()) {
                Utility.sendGameMessage("No Dominic Onion found", "PNMZHelper");
                return;
            }
            Interaction.clickNpc(dominic.get(), "Dream");
            if (!Utility.sleepUntilCondition(Dialog::isConversationWindowUp)) {
                Utility.sendGameMessage("Failed to open conversation window to enter NMZ", "PNMZHelper");
                return;
            }
            Dialog.handleGenericDialog(new String[]{"Yes", "Previous"});
            return;
        }

        Worldhopping.hopToNext(false);
        Utility.sleepGaussian(1200, 1400);

        var nmzPotion = TileObjects.search().withName("Potion").withAction("Drink").withinDistance(8).nearestToPlayer();
        if (nmzPotion.isEmpty()) {
            Utility.sendGameMessage("No 'potion' object found to enter NMZ", "PNMZHelper");
            return;
        }

        Interaction.clickTileObject(nmzPotion.get(), "Drink");
        if (!Utility.sleepUntilCondition(() -> Widgets.isValidAndVisible(Widgets.getWidget(129, 6)))) {
            Utility.sendGameMessage("Failed to open NMZ dream start interface", "PNMZHelper");
            return;
        }

        Interaction.clickWidget(Widgets.getWidget(129, 6), "Continue");
        if (Utility.sleepUntilCondition(Utility::isInInstancedRegion, 6000, 600)) {
            Utility.sleepGaussian(1200, 1400);
            Walking.sceneWalk(Walking.getPlayerLocation().dy(Utility.random(18, 22)).dx(Utility.random(-9, -5)));
        }
        return;
    }

    private boolean shouldBuyPotions() {
        int overloadsToBuy = getOverloadsWithdrawQuantity() - getOverloadsInBarrelCount();
        int absorptionsToBuy = getAbsorptionWithdrawQuantity() - getAbsorptionsInBarrelCount();
        int superRangingToBuy = getSuperRangingWithdrawQuantity() - getSuperRangingInBarrelCount();
        int superMagicToBuy = getSuperMagicWithdrawQuantity() - getSuperMagicInBarrelCount();
        return overloadsToBuy > 0 || absorptionsToBuy > 0 || superMagicToBuy > 0 || superRangingToBuy > 0;
    }

    private void handleBuyPotions() {
        int overloadsToBuy = getOverloadsWithdrawQuantity() - getOverloadsInBarrelCount();
        int absorptionsToBuy = getAbsorptionWithdrawQuantity() - getAbsorptionsInBarrelCount();
        int superRangingToBuy = getSuperRangingWithdrawQuantity() - getSuperRangingInBarrelCount();
        int superMagicToBuy = getSuperMagicWithdrawQuantity() - getSuperMagicInBarrelCount();

        Utility.sendGameMessage("Buying potions", "PNMZHelper");
        if (!Widgets.isValidAndVisible(Widgets.getWidget(206, 0))) {
            var chest = TileObjects.search().withName("Rewards chest").nearestToPlayer();
            if (chest.isEmpty()) {
                Utility.sendGameMessage("No rewards chest found", "PNMZHelper");
                return;
            }
            Interaction.clickTileObject(chest.get(), "Search");
        }
        if (!Utility.sleepUntilCondition(() -> Widgets.isValidAndVisible(Widgets.getWidget(206, 0)))) {
            Utility.sendGameMessage("Failed to open rewards chest", "PNMZHelper");
            return;
        }

        if (!Utility.sleepUntilCondition(() -> {
            if (Widgets.isValidAndVisible(Widgets.getWidget(206, 6))) {
                return true;
            }

            var benefitsTabParent = Widgets.getWidget(206, 2);
            var benefitsTab = benefitsTabParent.getChild(4);
            if (Widgets.isValidAndVisible(benefitsTab)) {
                Interaction.clickWidget(benefitsTab, "Benefits");
                return false;
            }
            return false;
        }, 8000, 1200)) {
            Utility.sendGameMessage("Failed to open benefits tab", "PNMZHelper");
            return;
        }

        if (overloadsToBuy > 0) {
            int price = overloadsToBuy * 1500;
            if (price > getAvailableNMZPoints()) {
                Utility.sendGameMessage("Not enough NMZ points to buy overloads", "PNMZHelper");
                stop();
                return;
            }

            var overloadBuyWidget = Widgets.search().nameContains("Overload (1)").withAction("Buy-X").first();
            if (overloadBuyWidget.isEmpty()) {
                Utility.sendGameMessage("No overload buy widget found", "PNMZHelper");
                return;
            }

            Interaction.clickWidget(overloadBuyWidget.get(), "Buy-X");
            if (!Utility.sleepUntilCondition(() -> Utility.getVarClientIntValue(VarClientInt.INPUT_TYPE) == 7)) {
                Utility.sendGameMessage("Failed to get text input to buy overloads", "PNMZHelper");
                return;
            }
            Utility.sleepGaussian(600, 900);
            int buyQuantity = overloadsToBuy;
            if (getAvailableNMZPoints() > 500000) {
                buyQuantity *= 2;
            }
            Keyboard.typeString(Integer.toString(buyQuantity));
            Utility.sleepGaussian(600, 800);
            Keyboard.pressEnter();
            Utility.sleepGaussian(1200, 1400);
        }

        if (absorptionsToBuy > 0) {
            int price = absorptionsToBuy * 1000;
            if (price > getAvailableNMZPoints()) {
                Utility.sendGameMessage("Not enough NMZ points to buy absorptions", "PNMZHelper");
                stop();
                return;
            }

            var absorptionBuyWidget = Widgets.search().nameContains("Absorption (1)").withAction("Buy-X").first();
            if (absorptionBuyWidget.isEmpty()) {
                Utility.sendGameMessage("No absorption buy widget found", "PNMZHelper");
                return;
            }

            Interaction.clickWidget(absorptionBuyWidget.get(), "Buy-X");
            if (!Utility.sleepUntilCondition(() -> Utility.getVarClientIntValue(VarClientInt.INPUT_TYPE) == 7)) {
                Utility.sendGameMessage("Failed to get text input to buy absorptions", "PNMZHelper");
                return;
            }
            Utility.sleepGaussian(600, 900);
            int buyQuantity = absorptionsToBuy;
            if (getAvailableNMZPoints() > 500000) {
                buyQuantity *= 2;
            }
            Keyboard.typeString(Integer.toString(buyQuantity));
            Utility.sleepGaussian(600, 800);
            Keyboard.pressEnter();
            Utility.sleepGaussian(1200, 1400);
        }

        if (superMagicToBuy > 0) {
            int price = superMagicToBuy * 250;
            if (price > getAvailableNMZPoints()) {
                Utility.sendGameMessage("Not enough NMZ points to buy super magic potions", "PNMZHelper");
                stop();
                return;
            }

            var superMagicBuyWidget = Widgets.search().nameContains("Super magic").withAction("Buy-X").first();
            if (superMagicBuyWidget.isEmpty()) {
                Utility.sendGameMessage("No super magic buy widget found", "PNMZHelper");
                return;
            }

            Interaction.clickWidget(superMagicBuyWidget.get(), "Buy-X");
            if (!Utility.sleepUntilCondition(() -> Utility.getVarClientIntValue(VarClientInt.INPUT_TYPE) == 7)) {
                Utility.sendGameMessage("Failed to get text input to buy super magic potions", "PNMZHelper");
                return;
            }
            Utility.sleepGaussian(600, 900);
            int buyQuantity = superMagicToBuy;
            if (getAvailableNMZPoints() > 500000) {
                buyQuantity *= 2;
            }
            Keyboard.typeString(Integer.toString(buyQuantity));
            Utility.sleepGaussian(600, 800);
            Keyboard.pressEnter();
            Utility.sleepGaussian(1200, 1400);
        }

        if (superRangingToBuy > 0) {
            int price = superRangingToBuy * 250;
            if (price > getAvailableNMZPoints()) {
                Utility.sendGameMessage("Not enough NMZ points to buy super ranging potions", "PNMZHelper");
                stop();
                return;
            }

            var superRangingBuyWidget = Widgets.search().nameContains("Super rang").withAction("Buy-X").first();
            if (superRangingBuyWidget.isEmpty()) {
                Utility.sendGameMessage("No super ranging buy widget found", "PNMZHelper");
                return;
            }

            Interaction.clickWidget(superRangingBuyWidget.get(), "Buy-X");
            if (!Utility.sleepUntilCondition(() -> Utility.getVarClientIntValue(VarClientInt.INPUT_TYPE) == 7)) {
                Utility.sendGameMessage("Failed to get text input to buy super ranging potions", "PNMZHelper");
                return;
            }
            Utility.sleepGaussian(600, 900);
            int buyQuantity = superRangingToBuy;
            if (getAvailableNMZPoints() > 500000) {
                buyQuantity *= 2;
            }
            Keyboard.typeString(Integer.toString(buyQuantity));
            Utility.sleepGaussian(600, 800);
            Keyboard.pressEnter();
            Utility.sleepGaussian(1200, 1400);
        }
    }

    private void handleDepositExcessPotions() {
        if (getOverloadsWithdrawQuantity() < 0) {
            var overloadBarrel = TileObjects.search().withId(ObjectID.OVERLOAD_POTION).nearestToPlayer();
            if (overloadBarrel.isEmpty()) {
                Utility.sendGameMessage("No overload barrel found", "PNMZHelper");
                return;
            }
            Utility.sendGameMessage("Depositing " + getOverloadsWithdrawQuantity() + " overloads", "PNMZHelper");
            Interaction.clickTileObject(overloadBarrel.get(), "Store");
            if (!Utility.sleepUntilCondition(Dialog::isConversationWindowUp)) {
                Utility.sendGameMessage("Failed to open conversation window to deposit overloads", "PNMZHelper");
                return;
            }
            Dialog.handleGenericDialog(new String[]{"Yes"});
        }

        if (getAbsorptionWithdrawQuantity() < 0) {
            var absorptionBarrel = TileObjects.search().withId(ObjectID.ABSORPTION_POTION).nearestToPlayer();
            if (absorptionBarrel.isEmpty()) {
                Utility.sendGameMessage("No absorption barrel found", "PNMZHelper");
                return;
            }
            Utility.sendGameMessage("Depositing " + getAbsorptionWithdrawQuantity() + " absorptions", "PNMZHelper");
            Interaction.clickTileObject(absorptionBarrel.get(), "Store");
            if (!Utility.sleepUntilCondition(Dialog::isConversationWindowUp)) {
                Utility.sendGameMessage("Failed to open conversation window to deposit absorptions", "PNMZHelper");
                return;
            }
            Dialog.handleGenericDialog(new String[]{"Yes"});
        }

        if (getSuperRangingWithdrawQuantity() < 0) {
            var superRangingBarrel = TileObjects.search().withId(ObjectID.SUPER_RANGING_POTION).nearestToPlayer();
            if (superRangingBarrel.isEmpty()) {
                Utility.sendGameMessage("No super ranging barrel found", "PNMZHelper");
                return;
            }
            Utility.sendGameMessage("Depositing " + getSuperRangingWithdrawQuantity() + " super ranging potions", "PNMZHelper");
            Interaction.clickTileObject(superRangingBarrel.get(), "Store");
            if (!Utility.sleepUntilCondition(Dialog::isConversationWindowUp)) {
                Utility.sendGameMessage("Failed to open conversation window to deposit super ranging potions", "PNMZHelper");
                return;
            }
            Dialog.handleGenericDialog(new String[]{"Yes"});
        }

        if (getSuperMagicWithdrawQuantity() < 0) {
            var superMagicBarrel = TileObjects.search().withId(ObjectID.SUPER_MAGIC_POTION).nearestToPlayer();
            if (superMagicBarrel.isEmpty()) {
                Utility.sendGameMessage("No super magic barrel found", "PNMZHelper");
                return;
            }
            Utility.sendGameMessage("Depositing " + getSuperMagicWithdrawQuantity() + " super magic potions", "PNMZHelper");
            Interaction.clickTileObject(superMagicBarrel.get(), "Store");
            if (!Utility.sleepUntilCondition(Dialog::isConversationWindowUp)) {
                Utility.sendGameMessage("Failed to open conversation window to deposit super magic potions", "PNMZHelper");
                return;
            }
            Dialog.handleGenericDialog(new String[]{"Yes"});
        }
    }

    private boolean shouldWithdrawPotions() {
        return getOverloadsWithdrawQuantity() > 0 || getAbsorptionWithdrawQuantity() > 0 || getSuperRangingWithdrawQuantity() > 0 || getSuperMagicWithdrawQuantity() > 0;
    }

    private void handleWithdrawPotions() {
        if (getOverloadsWithdrawQuantity() > 0) {
            var overloadBarrel = TileObjects.search().withId(ObjectID.OVERLOAD_POTION).nearestToPlayer();
            if (overloadBarrel.isEmpty()) {
                Utility.sendGameMessage("No overload barrel found", "PNMZHelper");
                return;
            }
            Utility.sendGameMessage("Withdrawing " + getOverloadsWithdrawQuantity() + " overloads", "PNMZHelper");
            Interaction.clickTileObject(overloadBarrel.get(), "Take");
            if (!Utility.sleepUntilCondition(() -> Utility.getVarClientIntValue(VarClientInt.INPUT_TYPE) == 7)) {
                Utility.sendGameMessage("Failed to get text input to withdraw overloads", "PNMZHelper");
                return;
            }
            Utility.sleepGaussian(600, 900);
            Keyboard.typeString(Integer.toString(getOverloadsWithdrawQuantity()));
            Utility.sleepGaussian(600, 800);
            Keyboard.pressEnter();
            Utility.sleepUntilCondition(() -> BoostPotion.OVERLOAD_POTION.getTotalDosesInInventory() >= config.overloadsQuantity());
        }

        if (getAbsorptionWithdrawQuantity() > 0) {
            var absorptionBarrel = TileObjects.search().withId(ObjectID.ABSORPTION_POTION).nearestToPlayer();
            if (absorptionBarrel.isEmpty()) {
                Utility.sendGameMessage("No absorption barrel found", "PNMZHelper");
                return;
            }
            Utility.sendGameMessage("Withdrawing " + getAbsorptionWithdrawQuantity() + " absorptions", "PNMZHelper");
            Interaction.clickTileObject(absorptionBarrel.get(), "Take");
            if (!Utility.sleepUntilCondition(() -> Utility.getVarClientIntValue(VarClientInt.INPUT_TYPE) == 7)) {
                Utility.sendGameMessage("Failed to get text input to withdraw absorptions", "PNMZHelper");
                return;
            }
            Utility.sleepGaussian(600, 900);
            Keyboard.typeString(Integer.toString(getAbsorptionWithdrawQuantity()));
            Utility.sleepGaussian(600, 800);
            Keyboard.pressEnter();
            Utility.sleepUntilCondition(() -> StatusPotion.ABSORPTION.getTotalDosesInInventory() >= config.absorptionQuantity());
        }

        if (getSuperRangingWithdrawQuantity() > 0) {
            var superRangingBarrel = TileObjects.search().withId(ObjectID.SUPER_RANGING_POTION).nearestToPlayer();
            if (superRangingBarrel.isEmpty()) {
                Utility.sendGameMessage("No super ranging barrel found", "PNMZHelper");
                return;
            }
            Utility.sendGameMessage("Withdrawing " + getSuperRangingWithdrawQuantity() + " super ranging potions", "PNMZHelper");
            Interaction.clickTileObject(superRangingBarrel.get(), "Take");
            if (!Utility.sleepUntilCondition(() -> Utility.getVarClientIntValue(VarClientInt.INPUT_TYPE) == 7)) {
                Utility.sendGameMessage("Failed to get text input to withdraw super ranging potions", "PNMZHelper");
                return;
            }
            Utility.sleepGaussian(600, 900);
            Keyboard.typeString(Integer.toString(getSuperRangingWithdrawQuantity()));
            Utility.sleepGaussian(600, 800);
            Keyboard.pressEnter();
            Utility.sleepUntilCondition(() -> BoostPotion.SUPER_RANGING_POTION.getTotalDosesInInventory() >= config.superRangingQuantity());
        }

        if (getSuperMagicWithdrawQuantity() > 0) {
            var superMagicBarrel = TileObjects.search().withId(ObjectID.SUPER_MAGIC_POTION).nearestToPlayer();
            if (superMagicBarrel.isEmpty()) {
                Utility.sendGameMessage("No super magic barrel found", "PNMZHelper");
                return;
            }
            Utility.sendGameMessage("Withdrawing " + getSuperMagicWithdrawQuantity() + " super magic potions", "PNMZHelper");
            Interaction.clickTileObject(superMagicBarrel.get(), "Take");
            if (!Utility.sleepUntilCondition(() -> Utility.getVarClientIntValue(VarClientInt.INPUT_TYPE) == 7)) {
                Utility.sendGameMessage("Failed to get text input to withdraw super magic potions", "PNMZHelper");
                return;
            }
            Utility.sleepGaussian(600, 900);
            Keyboard.typeString(Integer.toString(getSuperMagicWithdrawQuantity()));
            Utility.sleepGaussian(600, 800);
            Keyboard.pressEnter();
            Utility.sleepUntilCondition(() -> BoostPotion.SUPER_MAGIC_POTION.getTotalDosesInInventory() >= config.superMagicQuantity());
        }
    }

    private void threadedLoop() {
        if (!Utility.isLoggedIn() && !Utility.sleepUntilCondition(Utility::isLoggedIn, 8000, 600)) {
            stop();
            return;
        }
        Utility.sleepGaussian(300, 500);
        if (!isInsideNmz()) {
            if (!config.fullAutoMode()) {
                Utility.sendGameMessage("Outside NMZ. Stopping", "PNMZHelper");
                stop();
                return;
            } else {
                handleRestocking();
                return;
            }
        }

        if (!Utility.isAutoRetaliateEnabled()) {
            Utility.sendGameMessage("Enabling auto retaliate", "PNMZHelper");
            Utility.setAutoRetaliate(true);
            Utility.sleepGaussian(600, 800);
            return;
        }
        if (!isPowerSurgeActive() && handleAbsorptionPotions()) {
            Utility.sleepGaussian(1600, 1900);
            return;
        }
        if (handlePrayerPotions()) {
            Utility.sleepGaussian(1600, 1900);
            return;
        }
        if (handleStatBoostPotions()) {
            Utility.sleepGaussian(175, 250);
            return;
        }
        if (handleRockCaking()) {
            Utility.sleepGaussian(175, 250);
            return;
        }
        if (handlePrayerToggling()) {
            Utility.sleepGaussian(175, 250);
        }
        if (handleSpecialAttacking()) {
            Utility.sleepGaussian(175, 250);
            return;
        }
        if (handlePowerups()) {
            Utility.sleepGaussian(175, 250);
            return;
        }

        if (Utility.getInteractionTarget() == null
                && Utility.getTickCount() - ActorTracker.getInstance().getLocalPlayerTracker().getPredictedNextPossibleAttackTick() > 14) {
            var npc = NPCs.search().withAction("Attack").alive().nearestToPlayer();
            if (npc.isPresent()) {
                Interaction.clickNpc(npc.get(), "Attack");
                Utility.sleepGaussian(1200, 1400);
            }
        }

        if (!useSpecUntilOutOfEnergy) {
            // Sleep longer if we didn't have anything to do, and we're not actively speccing
            Utility.sleepGaussian(2000, 4000);
        }

    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (event.getMessage().toLowerCase().contains("feel a surge of special attack power")) {
            powerSurgePickedUpAt = System.currentTimeMillis();
        } else if (event.getMessage().toLowerCase().contains("surge of special attack power has ended")) {
            powerSurgePickedUpAt = -1;
        }
    }
}
