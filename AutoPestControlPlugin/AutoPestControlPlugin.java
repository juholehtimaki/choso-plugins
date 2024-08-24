package com.theplug.AutoPestControlPlugin;

import com.theplug.AutoPestControlPlugin.States.MinigameState;
import com.theplug.AutoPestControlPlugin.States.PrepareState;
import com.theplug.AutoPestControlPlugin.States.State;
import com.theplug.PaistiBreakHandler.PaistiBreakHandler;
import com.theplug.PaistiUtils.API.*;
import com.theplug.PaistiUtils.API.AttackTickTracker.AttackTickTracker;
import com.theplug.PaistiUtils.Framework.ThreadedScriptRunner;
import com.theplug.PaistiUtils.PathFinding.LocalPathfinder;
import com.theplug.PaistiUtils.Plugin.PaistiUtils;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;


@Slf4j
@PluginDescriptor(name = "<HTML><FONT COLOR=#1BB532>AutoPestControl</FONT></HTML>", description = "Farms pest control", enabledByDefault = false, tags = {"paisti", "choso", "pest control", "pc"})
public class AutoPestControlPlugin extends Plugin {
    @Inject
    public AutoPestControlPluginConfig config;
    @Inject
    private KeyManager keyManager;
    @Inject
    PluginManager pluginManager;

    @Inject
    public AttackTickTracker attackTickTracker;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    EventBus eventBus;

    @Inject
    public FoodStats foodStats;

    @Inject
    public PaistiBreakHandler paistiBreakHandler;

    List<State> states;

    PrepareState prepareState;
    MinigameState minigameState;

    @Inject
    private AutoPestControlPluginSceneOverlay sceneOverlay;

    @Inject
    private AutoPestControlPluginScreenOverlay screenOverlay;

    @Inject
    private ConfigManager configManager;

    public ThreadedScriptRunner runner = new ThreadedScriptRunner();


    private static final WorldPoint PURPLE_PORTAL_WORLD_POINT = new WorldPoint(2634, 2592, 0);
    private static final WorldPoint BLUE_PORTAL_WORLD_POINT = new WorldPoint(2677, 2589, 0);
    private static final WorldPoint YELLOW_PORTAL_WORLD_POINT = new WorldPoint(2670, 2575, 0);
    private static final WorldPoint RED_PORTAL_WORLD_POINT = new WorldPoint(2646, 2574, 0);
    private final WorldPoint WALLED_AREA_CORNER_1 = new WorldPoint(2643, 2585, 0);
    private final WorldPoint WALLED_AREA_CORNER_2 = new WorldPoint(2670, 2606, 0);

    public WorldPoint getWalledAreaCorner1() {
        return Utility.threadSafeGetInstanceWorldPoint(WALLED_AREA_CORNER_1);
    }

    public WorldPoint getWalledAreaCorner2() {
        return Utility.threadSafeGetInstanceWorldPoint(WALLED_AREA_CORNER_2);
    }

    public static WorldPoint getPurplePortalWorldPoint() {
        return Utility.threadSafeGetInstanceWorldPoint(PURPLE_PORTAL_WORLD_POINT);
    }

    public static WorldPoint getBluePortalWorldPoint() {
        return Utility.threadSafeGetInstanceWorldPoint(BLUE_PORTAL_WORLD_POINT);
    }

    public static WorldPoint getYellowPortalWorldPoint() {
        return Utility.threadSafeGetInstanceWorldPoint(YELLOW_PORTAL_WORLD_POINT);
    }

    public static WorldPoint getRedPortalWorldPoint() {
        return Utility.threadSafeGetInstanceWorldPoint(RED_PORTAL_WORLD_POINT);
    }


    public static final HashSet<WorldPoint> doorTiles = new HashSet<>();
    public static final HashSet<WorldPoint> brawlerTiles = new HashSet<>();
    private static final Function<WorldPoint, Boolean> allowPathingOnTiles = doorTiles::contains; // Allow door tiles
    private static final Function<WorldPoint, Boolean> neverGoToTiles = brawlerTiles::contains; // Block tiles that have big boi brawlers on them

    public LocalPathfinder.ReachabilityMap getPestControlReachabilityMap() {
        var dts = List.of(
                new WorldPoint(2642, 2592, 0),
                new WorldPoint(2643, 2592, 0),
                new WorldPoint(2656, 2585, 0),
                new WorldPoint(2656, 2584, 0),
                new WorldPoint(2670, 2593, 0),
                new WorldPoint(2671, 2593, 0)
        );

        doorTiles.clear();
        for (var d : dts) {
            doorTiles.add(Utility.threadSafeGetInstanceWorldPoint(d));
        }

        brawlerTiles.clear();
        var brawlers = NPCs.search().withName("Brawler").result();
        for (var brawlyBoi : brawlers) {
            brawlerTiles.add(brawlyBoi.getWorldLocation());
            brawlerTiles.add(brawlyBoi.getWorldLocation().dx(1));
            brawlerTiles.add(brawlyBoi.getWorldLocation().dy(1));
            brawlerTiles.add(brawlyBoi.getWorldLocation().dx(1).dy(1));
        }


        return LocalPathfinder.getReachabilityMap(
                Walking.getPlayerLocation(),
                neverGoToTiles,
                allowPathingOnTiles,
                null,
                null
        );
    }

    public static WorldPoint getNextTileToHandle(List<WorldPoint> path) {
        var reachabilityMap = LocalPathfinder.getReachabilityMap();
        WorldPoint nearestReachableTile = null;
        int nearestReachableIndex = -1;
        Integer nearestTileCost = null;
        WorldPoint nextTileToHandle = null;
        for (int i = 0; i < path.size(); i++) {
            var tile = path.get(i);
            if (tile.distanceTo(Walking.getPlayerLocation()) > 20) continue;
            if (reachabilityMap.isReachable(tile) && (nearestTileCost == null || reachabilityMap.getCostTo(tile) < nearestTileCost)) {
                nearestReachableTile = tile;
                nearestReachableIndex = i;
                nearestTileCost = reachabilityMap.getCostTo(tile);
            }
        }
        if (nearestReachableTile == null) {
            log.error("Could not find a nearby reachable tile in the path");
            return null;
        }

        for (int i2 = Math.min(nearestReachableIndex, path.size() - 1); i2 < path.size(); i2++) {
            WorldPoint tile = path.get(i2);
            WorldPoint nextTile = i2 + 1 < path.size() ? path.get(i2 + 1) : null;
            WorldPoint prevTile = i2 - 1 >= 0 ? path.get(i2 - 1) : null;
            boolean canBeReachedByWalking = reachabilityMap.isReachable(tile);
            if (prevTile != null && canBeReachedByWalking) {
                // Trace the path from the previous node to the current node and see if we can walk to the current node via that path
                var dx = tile.getX() - prevTile.getX();
                var dy = tile.getY() - prevTile.getY();
                canBeReachedByWalking = LocalPathfinder.canWalkToDirection(prevTile, dx, dy);
            }


            boolean isWithinReasonableDistance = Walking.getPlayerLocation().distanceTo(tile) < 8;
            if (canBeReachedByWalking && isWithinReasonableDistance) {
                // As this node can still be reached, we want to handle the next one (maybe a door blocking the path)
                if (nextTile == null) {
                    // If there is no next node left, just return this one
                    nextTileToHandle = tile;
                    break;
                }
                nextTileToHandle = nextTile;
            } else {
                // We've hit the furthest node we can reach
                break;
            }
        }

        return nextTileToHandle;
    }

    public static AtomicReference<WorldPoint> next_tile_debug_boi = new AtomicReference<>(null);

    public boolean progressWalkingOnPestPath(List<WorldPoint> path) {
        if (path == null) return true;
        if (path.isEmpty()) return true;

        var nextTileToHandle = getNextTileToHandle(path);
        if (nextTileToHandle == null) {
            log.error("Could not find next tile to handle");
            return false;
        }
        next_tile_debug_boi.set(nextTileToHandle);

        var reachabilityMap = LocalPathfinder.getReachabilityMap();
        if (reachabilityMap.isReachable(nextTileToHandle) && reachabilityMap.getCostTo(nextTileToHandle) <= Walking.getPlayerLocation().distanceTo(nextTileToHandle) + 3) {
            if (Walking.sceneWalk(nextTileToHandle)) {
                return Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().distanceTo(nextTileToHandle) <= 2);
            } else {
                return false;
            }
        }

        // Handle doors etc.
        var doorObject = TileObjects.search()
                .withAction("Open")
                .withinDistanceToPoint(2, nextTileToHandle)
                .nearestToPoint(nextTileToHandle);
        if (doorObject.isEmpty()) {
            log.error("Could not find door object");
            return false;
        }

        var door = doorObject.get();
        if (Interaction.clickTileObject(door, "Open")) {
            Utility.sleepUntilCondition(() -> TileObjects.search().withinDistanceToPoint(1, door.getWorldLocation()).withAction("Open").empty(), 6000, 600);
            Walking.sceneWalk(nextTileToHandle);
            Utility.sleepUntilCondition(() -> Walking.getPlayerLocation().equals(nextTileToHandle), 3600, 600);
            WorldPoint corner1 = getWalledAreaCorner1();
            WorldPoint corner2 = getWalledAreaCorner2();
            Geometry.Cuboid walledArea = new Geometry.Cuboid(corner1.getX(), corner1.getY(), corner1.getPlane(), corner2.getX(), corner2.getY(), corner2.getPlane());

            var playerInsideTheFence = walledArea.contains(Walking.getPlayerLocation());

            Boolean shouldNotCloseTheDoor = Utility.runOnClientThread(() -> PaistiUtils.getClient().getPlayers().stream().anyMatch(p -> {
                if ((p != PaistiUtils.getClient().getLocalPlayer()
                        && p.getWorldLocation().distanceTo(Walking.getPlayerLocation()) <= 4
                        && playerInsideTheFence) != walledArea.contains(p.getWorldLocation()))
                    return true;
                return false;
            }));

            if (Boolean.FALSE.equals(shouldNotCloseTheDoor)) {
                var closeDoor = TileObjects.search().withinDistance(3).withAction("Close").nearestToPlayer();
                if (closeDoor.isPresent()) {
                    Interaction.clickTileObject(closeDoor.get(), "Close");
                    Utility.sleepUntilCondition(() -> TileObjects.search().withinDistance(3).withAction("Close").empty(), 1200, 600);
                }
            }
            return true;
        }

        return false;
    }


    @Provides
    public AutoPestControlPluginConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoPestControlPluginConfig.class);
    }

    public boolean isRunning() {
        return runner.isRunning();
    }

    private final HotkeyListener startHotkeyListener = new HotkeyListener(() -> config.startHotkey() != null ? config.startHotkey() : new Keybind(0, 0)) {
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
    };

    public void start() {
        Utility.sendGameMessage("Started", "AutoPestControl");
        initialize();
        paistiBreakHandler.startPlugin(this);
        runner.start();
    }

    @Override
    protected void startUp() throws Exception {
        keyManager.registerKeyListener(startHotkeyListener);
        overlayManager.add(sceneOverlay);
        overlayManager.add(screenOverlay);

        runner.setLoopAction(() -> {
            this.threadedLoop();
            return null;
        });

        runner.setOnGameTickAction(() -> {
            this.threadedOnGameTick();
            return null;
        });

        paistiBreakHandler.registerPlugin(this);
    }

    private void threadedLoop() {
        if (!Utility.isLoggedIn()) {
            if (!Utility.sleepUntilCondition(Utility::isLoggedIn, 10000, 300)) {
                log.info("Player is not logged in, stopping");
                stop();
                return;
            }
        }
        for (var state : states) {
            if (state.shouldExecuteState()) {
                state.threadedLoop();
                return;
            }
        }
        Utility.sleepGaussian(100, 200);
    }

    @Override
    protected void shutDown() throws Exception {
        paistiBreakHandler.unregisterPlugin(this);
        stop();
        keyManager.unregisterKeyListener(startHotkeyListener);
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(screenOverlay);
    }

    private void initialize() {
        if (states != null) {
            for (var state : states) {
                eventBus.unregister(state);
            }
        }

        prepareState = new PrepareState(this, config);
        minigameState = new MinigameState(this, config);

        states = List.of(
                prepareState,
                minigameState
        );

        for (var state : states) {
            eventBus.register(state);
        }
    }

    private void threadedOnGameTick() {
        for (var state : states) {
            if (state.shouldExecuteState()) {
                state.threadedOnGameTick();
                break;
            }
        }
    }


    public void stop() {
        if (Utility.isLoggedIn()) {
            Utility.sendGameMessage("Stopped", "AutoPestControl");
        }
        paistiBreakHandler.stopPlugin(this);
        runner.stop();
    }


    @Subscribe(priority = 100)
    public void onGameTick(GameTick e) {
        if (!isRunning()) return;

        runner.onGameTick();
    }

    public Duration getRunTimeDuration() {
        return Duration.between(runner.getStartedAt(), Instant.now());
    }
}