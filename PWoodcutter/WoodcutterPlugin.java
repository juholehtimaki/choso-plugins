package com.theplug.PWoodcutter;

import com.theplug.LogBurnerPlugin.FiremakingUtils;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.theplug.PaistiUtils.PathFinding.WebWalker;
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
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import java.util.ArrayList;

@Slf4j
@PluginDescriptor(name = "PWoodcutter", description = "Power woodcuts selected tree", enabledByDefault = false, tags = {"paisti", "woodcutting"})
public class WoodcutterPlugin extends Plugin {

    @Inject
    WoodcutterPluginConfig config;
    @Inject
    PluginManager pluginManager;
    @Inject
    private KeyManager keyManager;
    ThreadedScriptRunner runner = new ThreadedScriptRunner();
    private HotkeyListener startHotkeyListener = null;

    ArrayList<ArrayList<WorldPoint>> firemakingLanes = null;

    private long lastActionTime = 0;
    WorldPoint startingLocation;
    @Inject
    private WoodcutterPluginSceneOverlay sceneOverlay;
    @Inject
    OverlayManager overlayManager;

    @Inject
    PaistiBreakHandler paistiBreakHandler;

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
        overlayManager.add(sceneOverlay);

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

        paistiBreakHandler.registerPlugin(this);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(sceneOverlay);
        if (startHotkeyListener != null) {
            keyManager.unregisterKeyListener(startHotkeyListener);
        }
        stop();
        paistiBreakHandler.unregisterPlugin(this);
    }

    private void stop() {
        if (runner.isRunning()) {
            Utility.sendGameMessage("Stopped", "PWoodcutter");
            paistiBreakHandler.stopPlugin(this);
        }
        firemakingLanes = null;
        runner.stop();
    }

    private void useDragonAxeSpec() {
        if (Utility.getSpecialAttackEnergy() < 100) return;
        if (Equipment.search().withName("Dragon axe").result().isEmpty() && Equipment.search().withName("Dragon felling axe").result().isEmpty())
            return;
        Utility.specialAttack();
        Utility.sleepGaussian(500, 800);
    }

    private boolean handleChopping() {
        if (!Utility.isIdle() || Inventory.isFull()) {
            return false;
        }
        if (Walking.getPlayerLocation().distanceTo(startingLocation) > config.treeRangeRadius() + 10) {
            Utility.sendGameMessage("Walking to chopping area", "PWoodcutter");
            WebWalker.walkToExact(startingLocation);
        }
        if (System.currentTimeMillis() - lastActionTime > 60000 && Walking.getPlayerLocation().distanceTo(startingLocation) > config.treeRangeRadius() - 5) {
            Utility.sendGameMessage("Idle for too long, maybe stuck? Walking to chopping area", "PWoodcutter");
            WebWalker.walkTo(startingLocation);
        }
        var treeToChop = TileObjects
                .search()
                .withName(config.selectedTree().toString())
                .withAction("Chop down")
                .withinDistanceToPoint(config.treeRangeRadius(), startingLocation)
                .nearestToPlayerTrueDistance();
        if (treeToChop.isEmpty()) {
            if (Walking.getPlayerLocation().distanceTo(startingLocation) > 4) {
                Utility.sendGameMessage("No trees found, walking to chopping area", "PWoodcutter");
                if (!WebWalker.walkTo(startingLocation)) {
                    Utility.sleepGaussian(1200, 1800);
                    Utility.sendGameMessage("Failed to walk to chopping area", "PWoodcutter");
                    return false;
                }
            }
            Utility.sendGameMessage("No trees found, waiting for trees to spawn", "PWoodcutter");
            return false;
        }
        useDragonAxeSpec();
        Interaction.clickTileObject(treeToChop.get(), "Chop down");
        Utility.sleepGaussian(1200, 1800);
        if (Utility.sleepUntilCondition(() -> !Utility.isIdle(), 5000)) {
            lastActionTime = System.currentTimeMillis();
            Utility.sleepGaussian(500, 2000);
            return true;
        }
        return false;
    }

    private boolean handleHopOnPlayerNearby() {
        if (!config.hopOnPlayerNearby()) return false;
        boolean shouldHop = Boolean.TRUE.equals(Utility.runOnClientThread(() -> {
            var players = PaistiUtils.getClient().getPlayers();
            return players.stream().anyMatch(p ->
                    !p.equals(PaistiUtils.getClient().getLocalPlayer())
                            && p.getWorldLocation().distanceTo(Walking.getPlayerLocation()) <= config.treeRangeRadius() + 2);
        }));

        if (shouldHop) {
            Utility.sendGameMessage("Player nearby. Hopping worlds.", "PWoodcutter");
            return Worldhopping.hopToNext(false);
        }
        return false;
    }

    private boolean handleDropping() {
        if (!Inventory.isFull()) return false;
        var logs = Inventory.search().nameContains("logs").result();
        boolean logsDropped = false;
        for (var log : logs) {
            Interaction.clickWidget(log, "Drop");
            Utility.sleepGaussian(200, 400);
            logsDropped = true;
        }
        return logsDropped;
    }

    private boolean handleFullInventory() {
        if (!Inventory.isFull()) return false;
        lastActionTime = System.currentTimeMillis();

        if (config.bankLogsInsteadOfDrop()) {
            if (!WebWalker.walkToNearestBank()) {
                Utility.sendGameMessage("Failed to walk to nearest bank", "PWoodcutter");
                stop();
                return false;
            }
            if (Bank.openBank()) {
                if (!Utility.sleepUntilCondition(Bank::isOpen, 10000, 1200)) {
                    Utility.sendGameMessage("Failed to open bank", "PWoodcutter");
                } else {
                    var logs = Inventory.search().matchesWildCardNoCase("*logs").onlyUnnoted().first();
                    if (logs.isPresent()) {
                        Bank.depositAll(logs.get());
                    }
                    if (Utility.sleepUntilCondition(() -> Inventory.getItemAmountWildcard("*logs") == 0, 3000, 600)) {
                        log.info("Deposited logs");
                        return true;
                    } else {
                        Utility.sendGameMessage("Failed to deposit logs", "PWoodcutter");
                    }
                }
            }
        }
        if (config.burnLogsInsteadOfDrop()) {
            if (firemakingLanes == null) {
                if (Walking.getPlayerLocation().distanceTo(startingLocation) > 8) {
                    if (!WebWalker.walkToExact(startingLocation)) {
                        Utility.sendGameMessage("Could not webwalk to starting location before firemaking", "PWoodcutter");
                    }
                }
                firemakingLanes = FiremakingUtils.generateFiremakingLanesNearPlayer();
                if (firemakingLanes == null || firemakingLanes.size() == 0) {
                    Utility.sendGameMessage("Could not generate firemaking lanes", "PWoodcutter");
                    stop();
                    return true;
                }
            }

            try {
                FiremakingUtils.fireMakeAllLogsOnLanes(firemakingLanes);
                return true;
            } catch (RuntimeException e) {
                Utility.sendGameMessage("Error when burning logs: " + e, "PWoodcutter");
                e.printStackTrace();
                return false;
            }
        }

        return handleDropping();
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
        Utility.sendGameMessage("Started", "PWoodcutter");
        startingLocation = Walking.getPlayerLocation();
        lastActionTime = System.currentTimeMillis();
        firemakingLanes = null;
        paistiBreakHandler.startPlugin(this);
        if (firemakingLanes == null && config.burnLogsInsteadOfDrop()) {
            firemakingLanes = FiremakingUtils.generateFiremakingLanesNearPlayer();
            if (firemakingLanes == null || firemakingLanes.size() == 0) {
                Utility.sendGameMessage("Could not generate firemaking lanes", "PWoodcutter");
                stop();
                return;
            }
        }
        runner.start();
    }

    private void threadedLoop() {
        if (!Utility.isLoggedIn()) {
            stop();
            return;
        }
        Utility.sleepGaussian(300, 500);
        if (paistiBreakHandler.shouldBreak(this)) {
            Utility.sendGameMessage("Taking a break", "PWoodcutter");
            Utility.sleepGaussian(2000, 3000);
            paistiBreakHandler.startBreak(this);
            Utility.sleepGaussian(1000, 2000);
            Utility.sleepUntilCondition(() -> !paistiBreakHandler.isBreakActive(this) && Utility.isLoggedIn(), 99999999, 5000);
            lastActionTime = System.currentTimeMillis();
            return;
        }
        if (handleHopOnPlayerNearby()) {
            Utility.sleepGaussian(1200, 1800);
            Utility.sleepUntilCondition(Utility::isLoggedIn, 10000, 1200);
            Utility.sleepGaussian(1200, 1800);
            return;
        }
        if (handleChopping()) {
            Utility.sleepGaussian(175, 250);
            return;
        }
        if (handleFullInventory()) {
            Utility.sleepGaussian(175, 250);
            return;
        }
        if (playerHasReachedTargetedLevel()) {
            Utility.sendGameMessage("Stopping since player has reached the targeted level", "PWoodcutter");
            paistiBreakHandler.logoutNow(this);
            stop();
        }
    }
}
