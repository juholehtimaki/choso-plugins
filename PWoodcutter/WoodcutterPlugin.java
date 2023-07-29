package com.example.PWoodcutter;
import com.example.PaistiUtils.API.*;
import com.example.PaistiUtils.Framework.ThreadedScriptRunner;
import com.example.PaistiUtils.PaistiUtils;
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
@PluginDescriptor(name = "PWoodcutter", description = "Power woodcuts selected tree", enabledByDefault = false, tags = {"Woodcutting", "Choso"})
public class WoodcutterPlugin extends Plugin {

    @Inject
    WoodcutterPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    private HotkeyListener startHotkeyListener = null;

    WorldPoint startingLocation;

    @Provides
    public WoodcutterPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(WoodcutterPluginConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        var paistiUtilsPlugin = pluginManager.getPlugins().stream().filter(p -> p instanceof PaistiUtils).findFirst();
        if (paistiUtilsPlugin.isEmpty() || !pluginManager.isPluginEnabled(paistiUtilsPlugin.get())) {
            log.info("PWoodcutter: PaistiUtils is required for this plugin to work");
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

    @Override
    protected void shutDown() throws Exception {
        runner.stop();
    }

    private void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "PWoodcutter");
        }
        runner.stop();
    }

    private boolean handleChopping() {
        if (!Utility.isIdle()) {
            return false;
        }
        var treeToChop = TileObjects.search().withName(config.selectedTree().toString()).withAction("Chop down").nearestToPlayer();
        if (treeToChop.isEmpty() || startingLocation.distanceTo(treeToChop.get().getWorldLocation()) > config.treeRangeRadius()) {
            return false;
        }
        Interaction.clickTileObject(treeToChop.get(), "Chop down");
        if (Utility.sleepUntilCondition(Utility::isIdle, 5000)){
            Utility.sleepGaussian(500, 2000);
            return true;
        }
        return false;
    }

    private boolean handleDropping() {
        if (!Inventory.isFull()) return false;
        var logs = Inventory.search().nameContains("logs").result();
        boolean logsDropped = false;
        for(var log : logs) {
            Interaction.clickWidget(log, "Drop");
            Utility.sleepGaussian(200, 400);
            logsDropped = true;
        }
        return logsDropped;
    }

    public boolean playerIsWithinSelectedRange() {
        return Walking.getPlayerLocation().distanceTo(startingLocation) < config.treeRangeRadius();
    }

    public boolean playerHasReachedTargetedLevel() {
        return Utility.getRealSkillLevel(Skill.WOODCUTTING) >= config.targetLevel();
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged e) {
        if (e.getGroup().equals("WoodcutterPluginConfig") && e.getKey().equals("startHotkey")) {
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
        Utility.sendGameMessage("Started", "PWoodcutter");
        System.out.println();
        startingLocation = Walking.getPlayerLocation();
        runner.start();
    }

    private void threadedLoop() {
        try {
            if (!Utility.isLoggedIn()) {
                stop();
                return;
            }
            Utility.sleepGaussian(300, 500);
            if (handleChopping()) {
                Utility.sleepGaussian(175, 250);
                return;
            }
            if (handleDropping()) {
                Utility.sleepGaussian(175, 250);
                return;
            }
            if (!playerIsWithinSelectedRange()){
                Utility.sendGameMessage("Stopping since player is not within selected range", "PWoodcutter");
                stop();
            }
            if (playerHasReachedTargetedLevel()){
                Utility.sendGameMessage("Stopping since player has reached the targeted level", "PWoodcutter");
                stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
