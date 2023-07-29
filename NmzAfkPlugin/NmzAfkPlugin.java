package com.example.NmzAfkPlugin;

import com.example.PaistiUtils.API.*;
import com.example.PaistiUtils.API.Potions.BoostPotion;
import com.example.PaistiUtils.Framework.ThreadedScriptRunner;
import com.example.PaistiUtils.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
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
@PluginDescriptor(name = "PNmzAfk", description = "Afks in NMZ", enabledByDefault = false, tags = {"Combat", "Choso"})
public class NmzAfkPlugin extends Plugin {

    int nextPrayerPotAt = generateNextPrayerPotAt();
    int nextAbsorptionAt = generateNextAbsorptionAt();
    int nextRockCakeat = generateNextRockCakeAt();

    @Inject
    NmzAfkPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    private HotkeyListener startHotkeyListener = null;

    @Provides
    public NmzAfkPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(NmzAfkPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PNmzAfk: PaistiUtils is required for this plugin to work");
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
                PaistiUtils.getOffThreadExecutor().submit(() -> {
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
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4
    };
    private int generateNextRockCakeAt() {
        // Pick a random value from a predefined array to have a bias towards 2, 3...
        return rockCakeHpAtChoices[Utility.random(0, rockCakeHpAtChoices.length - 1)];
    }

    private boolean isInsideNmz() {
        int ESCAPE_POTION_ID = 26276;
        return Utility.isInInstancedRegion() && TileObjects.search().withId(ESCAPE_POTION_ID).withAction("Drink").first().isPresent();
    }


    @Override
    protected void shutDown() throws Exception {
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "PNmzAfk");
        }
        runner.stop();
    }

    private boolean handlePrayerPotions() {
        if(Utility.getBoostedSkillLevel(Skill.PRAYER) <= nextPrayerPotAt) {
            // Drink prayer potions until full prayer
            var drankPotion = drinkPrayerPotionIfGoodValue();
            if (!drankPotion) return false;
            Utility.sendGameMessage("Drinking prayer potions", "PNmzAfk");
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

    private boolean handleAbsorptionPotions(){
        var absorb = Inventory.search().matchesWildCardNoCase("Absorption*").first();
        if (absorb.isEmpty()) return false;

        var currentAbsorption = Utility.getVarbitValue(Varbits.NMZ_ABSORPTION);
        if (currentAbsorption > nextAbsorptionAt) return false;
        Utility.sendGameMessage("Drinking absorption potions", "PNmzAfk");

        while (absorb.isPresent() && currentAbsorption < 950) {
            Interaction.clickWidget(absorb.get(), "Drink");
            Utility.sleepGaussian(1800, 2400);
            currentAbsorption = Utility.getVarbitValue(Varbits.NMZ_ABSORPTION);
            absorb = Inventory.search().matchesWildCardNoCase("Absorption*").first();
        }

        nextAbsorptionAt = generateNextAbsorptionAt();
        return true;
    }

    private boolean handleRockCaking(){
        var hpReduceItem = Inventory.search().withNameInArr("Dwarven rock cake", "Locator orb").onlyUnnoted().first();
        if (hpReduceItem.isEmpty()) return false;
        if (Utility.getBoostedSkillLevel(Skill.HITPOINTS) < nextRockCakeat) return false;
        Utility.sendGameMessage("Reducing HP to 1", "PNmzAfk");
        nextRockCakeat = generateNextRockCakeAt();
        while (Utility.getBoostedSkillLevel(Skill.HITPOINTS) > 1) {
            Interaction.clickWidget(hpReduceItem.get(), "Guzzle", "Feel");
            Utility.sleepGaussian(500, 900);
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
                Utility.sendGameMessage("Not enough health to drink " + boostPotion.name(), "NmzAfk");
                continue;
            };
            if (boostPotion.drink()) {
                Utility.sendGameMessage("Drank " + boostPotion.name(), "NmzAfk");
                if (boostPotion == BoostPotion.OVERLOAD_POTION) {
                    // Sleep longer for overloads so plugin wont click on rock cake too early
                    Utility.sleepGaussian(8000, 10000);
                } else {
                    Utility.sleepGaussian(1800, 2400);
                }
                drankPotion = true;
            }
        }
        return drankPotion;
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (e.getGroup().equals("NmzAfkPluginConfig") && e.getKey().equals("startHotkey")) {
            if (startHotkeyListener != null) {
                keyManager.unregisterKeyListener(startHotkeyListener);
            }
            startHotkeyListener = config.startHotkey() != null ? new HotkeyListener(() -> config.startHotkey()) {
                @Override
                public void hotkeyPressed() {
                    PaistiUtils.getOffThreadExecutor().submit(() -> {
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
        Utility.sendGameMessage("Started", "NmzAfk");
        runner.start();
    }

    private void threadedLoop() {
        try {
            if (!Utility.isLoggedIn()) {
                stop();
                return;
            }
            Utility.sleepGaussian(300, 500);
            if(!isInsideNmz()) {
                Utility.sendGameMessage("Player must be inside NMZ", "NmzAfk");
                stop();
                return;
            }
            if (handleAbsorptionPotions()) {
                Utility.sleepGaussian(175, 250);
                return;
            }
            if (handlePrayerPotions()){
                Utility.sleepGaussian(175, 250);
                return;
            }
            if (handleStatBoostPotions()) {
                Utility.sleepGaussian(175, 250);
                return;
            }
            if (handleRockCaking()){
                Utility.sleepGaussian(175, 250);
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
