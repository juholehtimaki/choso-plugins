package com.PaistiPlugins.NMZHelperPlugin;

import com.PaistiPlugins.PaistiUtils.API.*;
import com.PaistiPlugins.PaistiUtils.API.Loadouts.InventoryLoadout;
import com.PaistiPlugins.PaistiUtils.API.Potions.BoostPotion;
import com.PaistiPlugins.PaistiUtils.API.Prayer.PPrayer;
import com.PaistiPlugins.PaistiUtils.Framework.ThreadedScriptRunner;
import com.PaistiPlugins.PaistiUtils.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(name = "PNMZHelper", description = "Drinks potions, specs, rock cakes etc. in NMZ", enabledByDefault = false, tags = {"Choso", "NMZ"})
public class NMZHelperPlugin extends Plugin {

    int nextPrayerPotAt = generateNextPrayerPotAt();
    int nextAbsorptionAt = generateNextAbsorptionAt();
    int nextRockCakeAt = generateNextRockCakeAt();
    private InventoryLoadout.InventoryLoadoutSetup specWeaponLoadout = null;
    private InventoryLoadout.InventoryLoadoutSetup loadoutBeforeSwitch = null;
    @Inject
    NMZHelperPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    private HotkeyListener startHotkeyListener = null;
    private long powerSurgePickedUpAt = 0;
    private long lastSpecUsedAt = 0;

    @Provides
    public NMZHelperPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(NMZHelperPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PNMZHelper: PaistiUtils is required for this plugin to work");
            pluginManager.setPluginEnabled(this, false);
            return;
        }

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
        return Utility.random(12, 46);
    }

    private int generateNextAbsorptionAt() {
        return Utility.random(150, 500);
    }

    private static final int[] rockCakeHpAtChoices = new int[]{
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4
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
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "PNMZHelper");
        }
        runner.stop();
    }

    private boolean handlePrayerPotions() {
        if (Utility.getBoostedSkillLevel(Skill.PRAYER) <= nextPrayerPotAt) {
            // Drink prayer potions until full prayer
            var drankPotion = drinkPrayerPotionIfGoodValue();
            if (!drankPotion) return false;
            Utility.sendGameMessage("Drinking prayer potions", "PNMZHelper");
            while (drankPotion) {
                Utility.sleepGaussian(1800, 2400);
                drankPotion = drinkPrayerPotionIfGoodValue();
            }
            return true;
        }
        return false;
    }

    public boolean drinkPrayerPotionIfGoodValue() {
        BoostPotion prayerBoostPot = BoostPotion.PRAYER_POTION.findInInventory().isEmpty() ? BoostPotion.SUPER_RESTORE : BoostPotion.PRAYER_POTION;
        var potionInInventory = prayerBoostPot.findInInventory();
        if (potionInInventory.isEmpty()) return false;

        var missingPrayer = Math.abs(prayerBoostPot.findBoost(Skill.PRAYER).getCurrentBoostAmount());
        if (missingPrayer >= prayerBoostPot.findBoost(Skill.PRAYER).getBoostAmount()) {
            if (prayerBoostPot.drink()) {
                return true;
            }
        }

        return false;
    }

    private boolean handleAbsorptionPotions() {
        var absorb = Inventory.search().matchesWildCardNoCase("Absorption*").first();
        if (absorb.isEmpty()) return false;

        var currentAbsorption = Utility.getVarbitValue(Varbits.NMZ_ABSORPTION);
        if (currentAbsorption > nextAbsorptionAt) return false;
        Utility.sendGameMessage("Drinking absorption potions", "PNMZHelper");

        while (absorb.isPresent() && currentAbsorption < 950) {
            Interaction.clickWidget(absorb.get(), "Drink");
            Utility.sleepGaussian(1000, 1800);
            currentAbsorption = Utility.getVarbitValue(Varbits.NMZ_ABSORPTION);
            absorb = Inventory.search().matchesWildCardNoCase("Absorption*").first();
        }

        nextAbsorptionAt = generateNextAbsorptionAt();
        return true;
    }

    private boolean handleRockCaking() {
        var hpReduceItem = Inventory.search().withNameInArr("Dwarven rock cake", "Locator orb").onlyUnnoted().first();
        if (hpReduceItem.isEmpty()) return false;
        if (Utility.getBoostedSkillLevel(Skill.HITPOINTS) < nextRockCakeAt) return false;
        if (BoostPotion.OVERLOAD_POTION.findInInventory().isPresent()) {
            if (BoostPotion.OVERLOAD_POTION.isAnyCurrentBoostBelow(1) && Utility.getBoostedSkillLevel(Skill.HITPOINTS) > 50) {
                // Don't rock cake if overload should be used
                return false;
            }
        }
        Utility.sendGameMessage("Reducing HP to 1", "PNMZHelper");
        nextRockCakeAt = generateNextRockCakeAt();
        while (Utility.getBoostedSkillLevel(Skill.HITPOINTS) > 1) {
            Interaction.clickWidget(hpReduceItem.get(), "Guzzle", "Feel");
            Utility.sleepGaussian(400, 700);
            if (BoostPotion.OVERLOAD_POTION.findInInventory().isPresent()) {
                if (BoostPotion.OVERLOAD_POTION.isAnyCurrentBoostBelow(1) && Utility.getBoostedSkillLevel(Skill.HITPOINTS) > 50) {
                    // Don't rock cake if overload should be used
                    return true;
                }
            }
        }
        return true;
    }

    private boolean handleStatBoostPotions() {
        var potionsToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> {
            if (potion.findBoost(Skill.PRAYER) != null) return false;
            if (potion.findInInventory().isEmpty()) return false;
            return potion.isAnyCurrentBoostBelow(config.drinkPotionsBelowBoost());
        }).collect(Collectors.toList()));

        if (potionsToDrink == null || potionsToDrink.isEmpty()) return false;

        var drankPotion = false;
        for (var boostPotion : potionsToDrink) {
            if (boostPotion == BoostPotion.OVERLOAD_POTION && Utility.getBoostedSkillLevel(Skill.HITPOINTS) < 51) {
                Utility.sendGameMessage("Not enough health to drink " + boostPotion.name(), "PNMZHelper");
                continue;
            }
            ;
            if (boostPotion.drink()) {
                Utility.sendGameMessage("Drank " + boostPotion.name(), "PNMZHelper");
                if (boostPotion == BoostPotion.OVERLOAD_POTION) {
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
        return System.currentTimeMillis() - powerSurgePickedUpAt < 30000;
    }

    private boolean shouldSpec() {
        if (!config.useSpecialAttack()) return false;
        if (config.onlySpecDuringPowerSurge() && !isPowerSurgeActive()) return false;
        var specEnergy = Utility.getSpecialAttackEnergy();
        if (specEnergy < config.specEnergyMinimum()) {
            if (System.currentTimeMillis() - lastSpecUsedAt > 4200) {
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
        if (config.quickPrayDuringPowerSurge()) {
            if (Utility.getBoostedSkillLevel(Skill.PRAYER) == 0) return false;
            if (isPowerSurgeActive() && !PPrayer.isQuickPrayerActive()) {
                PPrayer.setQuickPrayerEnabled(true);
                return true;
            }
            if (!isPowerSurgeActive() && PPrayer.isQuickPrayerActive()) {
                PPrayer.setQuickPrayerEnabled(false);
                return true;
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
        if (!Utility.isSpecialAttackEnabled() && Utility.getSpecialAttackEnergy() >= config.specEnergyMinimum()) {
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
        specWeaponLoadout = InventoryLoadout.InventoryLoadoutSetup.deserializeFromString(config.specEquipmentString());
        runner.start();
    }

    private void threadedLoop() {
        if (!Utility.isLoggedIn()) {
            stop();
            return;
        }
        Utility.sleepGaussian(300, 500);
        if (!isInsideNmz()) {
            Utility.sendGameMessage("Outside NMZ. Stopping", "PNMZHelper");
            stop();
            return;
        }
        if (handleAbsorptionPotions()) {
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
        if (handleSpecialAttacking()) {
            Utility.sleepGaussian(175, 250);
            return;
        }
        if (handlePowerups()) {
            Utility.sleepGaussian(175, 250);
            return;
        }
        if (handlePrayerToggling()) {
            Utility.sleepGaussian(175, 250);
            return;
        }
        if (!useSpecUntilOutOfEnergy) {
            // Sleep longer if we didn't have anything to do, and we're not actively speccing
            Utility.sleepGaussian(2000, 4000);
        }

    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (event.getMessage().toLowerCase().contains("surge of special attack power")) {
            powerSurgePickedUpAt = System.currentTimeMillis();
        }
    }
}
