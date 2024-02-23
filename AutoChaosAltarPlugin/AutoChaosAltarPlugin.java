package com.PaistiPlugins.AutoChaosAltarPlugin;

import com.PaistiPlugins.PaistiUtils.API.*;
import com.PaistiPlugins.PaistiUtils.Framework.ThreadedScriptRunner;
import com.PaistiPlugins.PaistiUtils.PaistiUtils;
import com.PaistiPlugins.PaistiUtils.PathFinding.LocalPathfinder;
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
@PluginDescriptor(name = "AutoChaosAltar", description = "Automaticly uses bones on chaos altar", enabledByDefault = false, tags = {"choso", "prayer"})
public class AutoChaosAltarPlugin extends Plugin {
    @Inject
    AutoChaosAltarPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    private HotkeyListener startHotkeyListener = null;

    @Provides
    public AutoChaosAltarPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoChaosAltarPluginConfig.class);
    }

    static final WorldPoint WINE_LOCATION = new WorldPoint(2950, 3823, 0);

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("ChaosAltar: PaistiUtils is required for this plugin to work");
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
        if (startHotkeyListener != null) {
            keyManager.unregisterKeyListener(startHotkeyListener);
        }
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "ChaosAltar");
        }
        runner.stop();
    }

    public boolean handleToggleRun() {
        if (Walking.isRunEnabled() || Walking.getRunEnergy() < 15) return false;
        return Walking.setRun(true);
    }

    public boolean isPlayerAlive() {
        return Utility.getBoostedSkillLevel(Skill.HITPOINTS) >= 1;
    }

    public boolean handleBones() {
        if (!isPlayerAlive()) return false;
        var bone = Inventory.search().nameContains("bones").first();
        if (bone.isPresent()) {
            var altar = TileObjects.search().withName("Chaos altar").nearestToPlayer();
            if (altar.isEmpty()) {
                return false;
            }

            if (altar.get().getWorldLocation().distanceTo(Walking.getPlayerLocation()) > 5) {
                return false;
            }

            LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
            if (reachabilityMap.isReachable(altar.get())) {
                return Interaction.useItemOnTileObject(bone.get(), altar.get());
            }
        }
        return false;
    }

    public boolean equipBurningAmulet() {
        if (!isPlayerAlive()) return false;
        var amulet = Inventory.search().matchesWildCardNoCase("Burning amulet*").first();
        if (amulet.isEmpty()) return false;
        var amuletInEquipment = Equipment.search().matchesWildCardNoCase("Burning amulet*").first();
        if (amuletInEquipment.isPresent()) return false;
        return Interaction.clickWidget(amulet.get(), "Wear");
    }

    public boolean handleDialog() {
        if (!isPlayerAlive()) return false;
        if (!Dialog.isConversationWindowUp()) return false;
        var dialogOptions = new String[]{
                "teleport",
        };
        return Dialog.handleGenericDialog(dialogOptions);
    }

    public boolean handleRubAmulet() {
        if (!isPlayerAlive()) return false;
        var bones = Inventory.search().nameContains("bones").result();
        if (bones.size() < 27) return false;
        if (Utility.getWildernessLevelFrom(Walking.getPlayerLocation()) > 0) return false;
        var amuletInEquipment = Equipment.search().matchesWildCardNoCase("Burning amulet*").first();
        if (amuletInEquipment.isEmpty()) return false;
        return Interaction.clickWidget(amuletInEquipment.get(), "Lava Maze");
    }

    public boolean handleBanking() {
        if (!Bank.isOpen()) return false;
        if (Inventory.isFull()) return false;
        var amuletInEquipment = Equipment.search().matchesWildCardNoCase("Burning amulet*").first();
        var amuletEquiped = amuletInEquipment.isPresent();
        if (amuletEquiped) {
            Bank.withdraw("Superior dragon bones", 28, false);
        } else {
            Bank.withdraw("Burning amulet(5)", 1, false);
            Bank.withdraw("Superior dragon bones", 27, false);
        }
        return true;
    }

    public boolean handleSuicide() {
        if (!isPlayerAlive()) return false;

        var bone = Inventory.search().nameContains("bones").first();
        if (bone.isPresent()) {
            return false;
        }

        // Wine of zamorak
        var wine = TileItems.search().withName("Wine of zamorak").first();
        if (wine.isEmpty()) {
            return false;
        }

        LocalPathfinder.ReachabilityMap reachabilityMap = LocalPathfinder.getReachabilityMap();
        if (reachabilityMap.isReachable(WINE_LOCATION)) {
            return Interaction.clickGroundItem(wine.get(), "Take");
        }

        return false;
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (e.getGroup().equals("AutoChaosAltarPluginConfig") && e.getKey().equals("startHotkey")) {
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
        Utility.sendGameMessage("Started", "ChaosAltar");
        runner.start();
    }


    private void threadedLoop() {
        if (!Utility.isLoggedIn()) {
            stop();
            return;
        }
        if (handleBones()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleSuicide()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (equipBurningAmulet()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleDialog()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleRubAmulet()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleBanking()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        if (handleToggleRun()) {
            Utility.sleepGaussian(300, 600);
            return;
        }
        Utility.sleepGaussian(200, 300);
    }
}
