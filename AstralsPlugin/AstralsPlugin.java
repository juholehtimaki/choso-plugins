package com.example.AstralsPlugin;

import com.example.PaistiUtils.API.*;
import com.example.PaistiUtils.API.Spells.Lunar;
import com.example.PaistiUtils.Framework.ThreadedScriptRunner;
import com.example.PaistiUtils.PaistiUtils;
import com.example.PaistiUtils.PathFinding.WebWalker;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(name = "PAstrals", description = "Crafts Astral Runes", enabledByDefault = false, tags = {"Runecrafting", "Paisti"})
public class AstralsPlugin extends Plugin {
    static final WorldPoint BANK_SPOT = new WorldPoint(2099, 3919, 0);
    static final WorldPoint ASTRAL_ALTAR_SPOT = new WorldPoint(2156, 3863, 0);

    @Inject
    AstralsPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    private HotkeyListener startHotkeyListener = null;

    static final int BROKEN_MEDIUM_POUCH_ID = 5511;
    static final int BROKEN_LARGE_POUCH_ID = 5513;

    @Provides
    public AstralsPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AstralsPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PAstrals: PaistiUtils is required for this plugin to work");
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


    @Override
    protected void shutDown() throws Exception {
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "PAstrals");
        }
        runner.stop();
    }

    private boolean handleEssenceCrafting() {
        if (Walking.getPlayerLocation().distanceTo(ASTRAL_ALTAR_SPOT) >= 15) {
            WebWalker.walkTo(ASTRAL_ALTAR_SPOT);
        }
        if (Walking.getPlayerLocation().distanceTo(ASTRAL_ALTAR_SPOT) < 15 && Inventory.search().withName("Pure essence").first().isPresent()) {
            var astralAltar = TileObjects.search().withName("Altar").withAction("Craft-rune").nearestToPlayer();
            if (astralAltar.isEmpty()) {
                return false;
            }
            Interaction.clickTileObject(astralAltar.get(), "Craft-rune");
            Utility.sleepUntilCondition(() -> Inventory.getItemAmount("Pure essence") == 0, 3000);
            var pouches = Inventory.search().matchesWildCardNoCase("*pouch*").filter(p -> !p.getName().contains("Rune")).result();
            for (var p : pouches) {
                Interaction.clickWidget(p, "Empty");
                Utility.sleepGaussian(150, 300);
            }
            Utility.sleepUntilCondition(() -> Inventory.getItemAmount("Pure essence") > 0, 3000);
            if (Inventory.getItemAmount("Pure essence") > 0) {
                Interaction.clickTileObject(astralAltar.get(), "Craft-rune");
                Utility.sleepUntilCondition(() -> Inventory.getItemAmount("Pure essence") == 0, 3000);
            }
            Utility.sleepGaussian(250, 500);
            return true;
        }
        return false;
    }

    private boolean handleBanking() {
        if (!Bank.isNearBank()) {
            WebWalker.walkTo(BANK_SPOT);
        }

        if (Inventory.getItemAmount(BROKEN_MEDIUM_POUCH_ID) > 0 || Inventory.getItemAmount(BROKEN_LARGE_POUCH_ID) > 0) {
            Lunar.NPC_CONTACT.cast("Dark Mage");
            Utility.sleepUntilCondition(Dialog::isConversationWindowUp);
            Utility.sendGameMessage("Attempted to repair pouches", "PAstrals");
            var dialogOptions = new String[]{
                    "repair",
            };
            Dialog.handleGenericDialog(dialogOptions);
        }

        var bank = Bank.openBank();

        if (bank && Utility.sleepUntilCondition(Bank::isOpen)) {
            if (Utility.getBoostedSkillLevel(Skill.HITPOINTS) < Utility.getRealSkillLevel(Skill.HITPOINTS) * 0.70) {
                Bank.withdraw("Shark", 1, false);
                Utility.sleepUntilCondition(() -> Inventory.search().withName("Shark").first().isPresent());
                var sharks = BankInventory.search().withName("Shark").result();
                for (var s : sharks) {
                    Interaction.clickWidget(s, "Eat");
                    Utility.sleepGaussian(150, 300);
                }
            }

            if (Walking.getRunEnergy() < Utility.random(20, 50)) {
                Bank.withdraw("Stamina potion(1)", 1, false);
                Utility.sleepUntilCondition(() -> Inventory.search().withName("Stamina potion(1)").first().isPresent());
                var staminaPotions = BankInventory.search().withName("Stamina potion(1)").result();
                for (var s : staminaPotions) {
                    Interaction.clickWidget(s, "Drink");
                    Utility.sleepGaussian(150, 300);
                }
            }

            var invEmptySlots = Inventory.getEmptySlots();
            if (!Bank.withdraw("Pure essence", invEmptySlots, false)) {
                Utility.sendGameMessage("Could not withdraw pure essences", "PAstrals");
                stop();
            }
            var pouches = BankInventory.search().matchesWildCardNoCase("*pouch*").filter(p -> !p.getName().contains("Rune")).result();
            for (var p : pouches) {
                Interaction.clickWidget(p, "Fill");
                Utility.sleepGaussian(150, 300);
            }

            Utility.sleepGaussian(600, 1000);

            var invEmptySlotsAfterFillingPouches = Inventory.getEmptySlots();
            if (!Bank.withdraw("Pure essence", invEmptySlotsAfterFillingPouches, false)) {
                Utility.sendGameMessage("Could not withdraw pure essences after filling pouches", "PAstrals");
                stop();
            }

            return true;
        }

        return false;
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (e.getGroup().equals("AstralsPluginConfig") && e.getKey().equals("startHotkey")) {
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
        Utility.sendGameMessage("Started", "PAstrals");
        runner.start();
    }

    private void threadedLoop() {
        try {
            if (!Utility.isLoggedIn()) {
                stop();
                return;
            }
            Utility.sleepGaussian(300, 500);
            if (Inventory.getItemAmount("Pure essence") == 0) {
                handleBanking();
            } else {
                handleEssenceCrafting();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
