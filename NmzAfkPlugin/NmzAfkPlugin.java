package com.example.NmzAfkPlugin;

import com.example.PaistiUtils.API.*;
import com.example.PaistiUtils.API.Potions.BoostPotion;
import com.example.PaistiUtils.Framework.ThreadedScriptRunner;
import com.example.PaistiUtils.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
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

    private boolean isInsisdeNmz() {
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
            if (BoostPotion.PRAYER_POTION.drink()) {
                Utility.sendGameMessage("Drinking prayer potion", "PNmzAfk");
                nextPrayerPotAt = generateNextPrayerPotAt();
                Utility.sleepGaussian(600, 1500);
                return true;
            }
        }
        return false;
    }

    private boolean handleStatBoostPotions() {
        var potionsToDrink = Utility.runOnClientThread(() -> Arrays.stream(BoostPotion.values()).filter(potion -> {
            if (potion == BoostPotion.PRAYER_POTION) return false;
            if (potion.findInInventory().isEmpty()) return false;
            return potion.isAnyCurrentBoostBelow(config.drinkPotionsBelowBoost());
        }).collect(Collectors.toList()));

        if (potionsToDrink == null || potionsToDrink.isEmpty()) return false;

        var drankPotion = false;

        for (var boostPotion : potionsToDrink) {
            if (boostPotion.name().contains("Overload") && Utility.getBoostedSkillLevel(Skill.HITPOINTS) < 51) {
                Utility.sendGameMessage("Not enough health to drink " + boostPotion.name(), "NmzAfk");
                continue;
            };
            if (boostPotion.drink()) {
                Utility.sendGameMessage("Drank " + boostPotion.name(), "NmzAfk");
                Utility.sleepGaussian(600, 1500);
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
            if(!isInsisdeNmz()) {
                Utility.sendGameMessage("Player must be inside NMZ", "NmzAfk");
                stop();
                return;
            }
            if (handlePrayerPotions()){
                Utility.sleepGaussian(175, 250);
            }
            if (handleStatBoostPotions()) {
                Utility.sleepGaussian(175, 250);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
